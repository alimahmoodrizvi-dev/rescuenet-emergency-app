"""
Phase 9 — failure-mode and edge-case coverage, per the Part 39 testing checklist, for
everything that's actually exercisable server-side. GPS/Bluetooth/low-battery/permission-
denial are Android-side concerns and are covered instead by TESTING.md's manual/hardware
checklist and the Android app's own graceful-degradation code paths (LocationProvider
returning null, BleTransportProvider no-op'ing without permission, etc.) — nothing about
those can be meaningfully unit-tested from the backend.

Run with: pytest tests/test_failure_modes.py -v
"""


def _register_device(client, device_uuid: str) -> str:
    resp = client.post("/api/auth/device-register", json={"device_uuid": device_uuid})
    assert resp.status_code == 200
    return resp.json()["access_token"]


# ---------------------------------------------------------------------------
# GPS failure equivalent: a report must still succeed with no location (Part 29 —
# "the emergency-report function should still work locally when possible").
# ---------------------------------------------------------------------------

def test_incident_without_location_still_succeeds(client):
    token = _register_device(client, "fail-device-gps")
    headers = {"Authorization": f"Bearer {token}"}
    resp = client.post(
        "/api/incidents",
        json={"event_uuid": "fail-evt-no-gps", "type": "MEDICAL", "people_count": 1,
              "injury_level": "SERIOUS", "needs": ["AMBULANCE"], "description": "no gps fix available"},
        headers=headers,
    )
    assert resp.status_code == 201
    assert resp.json()["latitude"] is None


def test_resource_recommend_requires_location_and_fails_clearly(client):
    token = _register_device(client, "fail-device-gps-2")
    headers = {"Authorization": f"Bearer {token}"}
    incident = client.post(
        "/api/incidents",
        json={"event_uuid": "fail-evt-no-gps-2", "type": "FIRE", "people_count": 2,
              "injury_level": "NONE", "needs": [], "description": "no location"},
        headers=headers,
    ).json()

    resp = client.get(f"/api/resources/recommend/{incident['id']}")
    assert resp.status_code == 400
    assert "location" in resp.json()["detail"].lower()


# ---------------------------------------------------------------------------
# AI failure / degenerate input: must never crash, must never overstate confidence
# (Part 26).
# ---------------------------------------------------------------------------

def test_ai_analyze_handles_empty_text_without_crashing(client):
    token = _register_device(client, "fail-device-ai")
    headers = {"Authorization": f"Bearer {token}"}
    resp = client.post("/api/ai/analyze-incident", json={"raw_text": ""}, headers=headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["incident_type"] == "OTHER"
    assert body["confidence"] < 70  # an empty message must never look confident


def test_ai_analyze_handles_gibberish_without_crashing(client):
    token = _register_device(client, "fail-device-ai-2")
    headers = {"Authorization": f"Bearer {token}"}
    resp = client.post(
        "/api/ai/analyze-incident",
        json={"raw_text": "asdkfj 29304 !!! \u0000\u0001 random noise"},
        headers=headers,
    )
    assert resp.status_code == 200


# ---------------------------------------------------------------------------
# Auth / permission-denial equivalents.
# ---------------------------------------------------------------------------

def test_protected_endpoint_rejects_missing_token(client):
    resp = client.post("/api/ai/analyze-incident", json={"raw_text": "fire"})
    assert resp.status_code == 401


def test_protected_endpoint_rejects_garbage_token(client):
    resp = client.post(
        "/api/ai/analyze-incident",
        json={"raw_text": "fire"},
        headers={"Authorization": "Bearer not-a-real-token"},
    )
    assert resp.status_code == 401


def test_public_read_endpoints_work_without_auth(client):
    # Shelters/hospitals/incident-list are intentionally public reads (Part 4 — nearby help
    # must be visible even to a citizen who hasn't triggered any auth flow yet).
    assert client.get("/api/shelters").status_code == 200
    assert client.get("/api/hospitals").status_code == 200
    assert client.get("/api/incidents").status_code == 200


# ---------------------------------------------------------------------------
# Multiple devices, duplicate messages (Part 21 — "never duplicate emergency incidents"),
# including the case where two *different* devices both end up submitting the same event —
# e.g. a report relayed over the mesh to a second phone that also has its own connectivity.
# ---------------------------------------------------------------------------

def test_two_different_devices_submitting_same_event_uuid_dedups(client):
    token_a = _register_device(client, "fail-device-multi-a")
    token_b = _register_device(client, "fail-device-multi-b")

    payload = {
        "event_uuid": "fail-evt-multi-device", "type": "EARTHQUAKE", "people_count": 5,
        "injury_level": "SERIOUS", "needs": ["RESCUE_TEAM"], "description": "shaking",
    }
    first = client.post("/api/incidents", json=payload, headers={"Authorization": f"Bearer {token_a}"})
    # Device B relayed the same event over the mesh and is now submitting it as if it were
    # its own — simulating exactly what MeshRelayManager -> SyncWorker does on the Android side.
    second = client.post("/api/incidents", json=payload, headers={"Authorization": f"Bearer {token_b}"})

    assert first.status_code == 201
    assert second.status_code == 201
    assert first.json()["id"] == second.json()["id"], "must dedup across devices, not just within one device's own retries"

    all_incidents = client.get("/api/incidents").json()
    assert sum(1 for i in all_incidents if i["event_uuid"] == "fail-evt-multi-device") == 1


def test_sync_batch_dedups_across_devices_too(client):
    token_a = _register_device(client, "fail-device-multi-c")
    token_b = _register_device(client, "fail-device-multi-d")

    direct = client.post(
        "/api/incidents",
        json={"event_uuid": "fail-evt-multi-batch", "type": "FLOOD", "people_count": 3,
              "injury_level": "NONE", "needs": [], "description": "direct from device A"},
        headers={"Authorization": f"Bearer {token_a}"},
    )
    assert direct.status_code == 201

    # Device B relayed the same report and is now bulk-syncing it, as SyncWorker does after
    # a period offline.
    batch = client.post(
        "/api/sync/batch",
        json={"device_uuid": "fail-device-multi-d", "items": [
            {"entity_type": "incident", "payload": {
                "event_uuid": "fail-evt-multi-batch", "type": "FLOOD", "people_count": 3,
                "injury_level": "NONE", "needs": [], "description": "relayed copy from device B",
            }},
        ]},
        headers={"Authorization": f"Bearer {token_b}"},
    )
    assert batch.status_code == 200
    assert batch.json() == {"accepted": 0, "duplicates": 1, "rejected": 0}


# ---------------------------------------------------------------------------
# Clustering / resource-assignment edge cases.
# ---------------------------------------------------------------------------

def test_clustering_ignores_isolated_pairs_below_minimum_group_size(client):
    token = _register_device(client, "fail-device-cluster")
    headers = {"Authorization": f"Bearer {token}"}
    # Only two nearby incidents — Part 12's clustering requires at least three to call it a
    # cluster, not two coincidentally-close reports.
    for i in range(2):
        client.post(
            "/api/incidents",
            json={"event_uuid": f"fail-evt-pair-{i}", "type": "FIRE", "people_count": 1,
                  "injury_level": "NONE", "needs": [], "description": "pair",
                  "latitude": 25.0001 * (i + 1), "longitude": 66.0001 * (i + 1)},
            headers=headers,
        )
    result = client.post("/api/ai/cluster?radius_km=50").json()
    pair_clusters = [c for c in result["clusters"] if all("fail-evt-pair" in iid or True for iid in c["incident_ids"])]
    # Just assert no cluster contains exactly these 2 isolated incidents as a full cluster of size 2 —
    # the endpoint's own >=3 rule already guarantees this, this test documents that guarantee explicitly.
    assert all(c["incident_count"] >= 3 for c in result["clusters"])


def test_assign_resource_to_nonexistent_ids_returns_404(client):
    token = _register_device(client, "fail-device-assign")
    admin_token = None
    # Create an org admin inline for this test via the same hashing path the CLI script uses.
    from app.auth import hash_password
    from app.database import SessionLocal
    from app.models import User, UserRole

    db = SessionLocal()
    try:
        db.add(User(name="fail-test-admin", role=UserRole.COMMAND_CENTER_ADMIN, hashed_password=hash_password("x")))
        db.commit()
    finally:
        db.close()

    login = client.post("/api/auth/org-login", json={"username": "fail-test-admin", "password": "x"})
    admin_token = login.json()["access_token"]

    resp = client.post(
        "/api/resources/nonexistent-resource-id/assign",
        json={"incident_id": "nonexistent-incident-id"},
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert resp.status_code == 404


# ---------------------------------------------------------------------------
# Alert expiry (Part 15 — expired alerts must not keep showing as active).
# ---------------------------------------------------------------------------

def test_expired_alert_is_excluded_from_active_list(client):
    from datetime import datetime, timedelta, timezone
    from app.auth import hash_password
    from app.database import SessionLocal
    from app.models import User, UserRole

    db = SessionLocal()
    try:
        db.add(User(name="fail-test-operator", role=UserRole.RESCUE_OPERATOR, hashed_password=hash_password("x")))
        db.commit()
    finally:
        db.close()

    token = client.post("/api/auth/org-login", json={"username": "fail-test-operator", "password": "x"}).json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    expired_at = (datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(hours=1)).isoformat()
    client.post(
        "/api/alerts",
        json={"type": "ROAD_CLOSURE", "severity": "INFORMATION", "message": "already expired",
              "is_demo": True, "expires_at": expired_at},
        headers=headers,
    )
    active_messages = [a["message"] for a in client.get("/api/alerts").json()]
    assert "already expired" not in active_messages
