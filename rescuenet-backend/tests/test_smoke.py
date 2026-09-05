"""
Core happy-path coverage for the Part 10 API surface. Run with: pytest tests/test_smoke.py -v
See conftest.py for the shared `client` fixture and test database setup.
"""


def _register_device(client, device_uuid: str) -> str:
    resp = client.post("/api/auth/device-register", json={"device_uuid": device_uuid})
    assert resp.status_code == 200
    return resp.json()["access_token"]


def test_health(client):
    assert client.get("/health").json() == {"status": "healthy"}


def test_device_register_is_idempotent(client):
    token1 = _register_device(client, "smoke-device-1")
    token2 = _register_device(client, "smoke-device-1")
    assert token1 and token2  # both succeed; a second call refreshes, doesn't duplicate the user


def test_incident_create_and_dedup(client):
    token = _register_device(client, "smoke-device-2")
    headers = {"Authorization": f"Bearer {token}"}
    payload = {
        "event_uuid": "smoke-evt-1", "type": "FLOOD", "people_count": 4,
        "injury_level": "SERIOUS", "needs": ["RESCUE_TEAM"], "description": "test",
        "latitude": 24.86, "longitude": 67.03,
    }
    first = client.post("/api/incidents", json=payload, headers=headers)
    assert first.status_code == 201
    first_id = first.json()["id"]

    second = client.post("/api/incidents", json={**payload, "description": "different text, same event_uuid"}, headers=headers)
    assert second.status_code == 201
    assert second.json()["id"] == first_id, "dedup by event_uuid must return the same row, not create a new one"

    listing = client.get("/api/incidents").json()
    assert sum(1 for i in listing if i["event_uuid"] == "smoke-evt-1") == 1


def test_role_gating_blocks_citizen_from_creating_alerts(client):
    token = _register_device(client, "smoke-device-3")
    headers = {"Authorization": f"Bearer {token}"}
    resp = client.post(
        "/api/alerts",
        json={"type": "FLOOD_WARNING", "severity": "WARNING", "message": "test", "is_demo": True},
        headers=headers,
    )
    assert resp.status_code == 403


def test_ai_analyze_mixed_language_matches_part6_example(client):
    token = _register_device(client, "smoke-device-4")
    headers = {"Authorization": f"Bearer {token}"}
    resp = client.post(
        "/api/ai/analyze-incident",
        json={"raw_text": "Mere ghar mein pani aa raha hai. Hum 6 log hain aur meri mother injured hain."},
        headers=headers,
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["incident_type"] == "FLOOD"
    assert body["people_count"] == 6
    assert body["injuries_present"] is True
    assert body["is_simulated"] is True  # no API key configured in tests -> MockProvider


def test_sync_batch_dedups_against_direct_post(client):
    token = _register_device(client, "smoke-device-5")
    headers = {"Authorization": f"Bearer {token}"}
    direct = client.post(
        "/api/incidents",
        json={"event_uuid": "smoke-evt-sync", "type": "FIRE", "people_count": 1,
              "injury_level": "NONE", "needs": [], "description": "direct"},
        headers=headers,
    )
    assert direct.status_code == 201

    batch = client.post(
        "/api/sync/batch",
        json={"device_uuid": "smoke-device-5", "items": [
            {"entity_type": "incident", "payload": {
                "event_uuid": "smoke-evt-sync", "type": "FIRE", "people_count": 1,
                "injury_level": "NONE", "needs": [], "description": "same event via relay",
            }},
        ]},
        headers=headers,
    )
    assert batch.status_code == 200
    assert batch.json()["duplicates"] == 1
    assert batch.json()["accepted"] == 0


def test_shelters_and_hospitals_seeded(client):
    assert len(client.get("/api/shelters").json()) >= 1
    assert len(client.get("/api/hospitals").json()) >= 1
