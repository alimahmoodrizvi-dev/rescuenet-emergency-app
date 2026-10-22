"""Call-back phone numbers, GPS-less reports, and "I'm safe" visible to the Command Center."""
import json
import uuid
from datetime import timedelta

import pytest
from sqlalchemy import text

from app.auth import hash_password
from app.database import SessionLocal, engine
from app.migrations import ADDED_COLUMNS, ensure_columns
from app.models import (
    EmergencyIncident, IncidentStatus, IncidentUpdate, SafetyStatusUpdate, User, UserRole,
)
from app.phone import lenient_float, normalize_phone

def _phone():
    """A number nobody else in the shared test database is using — the same number means the
    same person, so tests must not share one."""
    return "+9230" + "".join(str(uuid.uuid4().int % 10) for _ in range(8))


def _device(client):
    resp = client.post("/api/auth/device-register", json={"device_uuid": str(uuid.uuid4())})
    assert resp.status_code == 200
    token = resp.json()["access_token"]
    user_id = client.get("/api/safety/checkins", headers={"Authorization": f"Bearer {token}"})  # 403 for citizens
    assert user_id.status_code == 403
    db = SessionLocal()
    try:
        from app.auth import decode_token
        return {"Authorization": f"Bearer {token}"}, decode_token(token)["sub"]
    finally:
        db.close()


def _staff(client, role=UserRole.COMMAND_CENTER_ADMIN):
    name = f"staff-{uuid.uuid4().hex[:8]}"
    db = SessionLocal()
    db.add(User(name=name, role=role, hashed_password=hash_password("pw12345")))
    db.commit()
    db.close()
    resp = client.post("/api/auth/org-login", json={"username": name, "password": "pw12345"})
    assert resp.status_code == 200, resp.text
    return {"Authorization": f"Bearer {resp.json()['access_token']}"}, resp.json()["access_token"]


def _report(event_uuid=None, **extra):
    body = {"event_uuid": event_uuid or str(uuid.uuid4()), "type": "TRAPPED", "people_count": 2,
            "injury_level": "MINOR", "description": "stuck", "latitude": 24.87, "longitude": 67.05}
    body.update(extra)
    return body


def _sync(client, headers, *items):
    return client.post("/api/sync/batch", headers=headers, json={"device_uuid": "dev", "items": [
        {"entity_type": et, "payload": p} for et, p in items]})


def _incident(client, staff_headers, event_uuid):
    listing = client.get("/api/incidents", headers=staff_headers).json()
    return next(i for i in listing if i["event_uuid"] == event_uuid)


# ----------------------------------------------------------------------------- unit: phone cleaning

@pytest.mark.parametrize("raw,expected", [
    ("+92 300-123 4567", "+923001234567"),
    ("(021) 111-2222", "0211112222"),
    ("03001234567", "03001234567"),
    ("  +1.415.555.2671 ", "+14155552671"),
    ("12345", None),                 # too short
    ("1234567890123456", None),      # too long
    ("call me", None), ("", None), (None, None), (12345678, None), ("+92abc", None),
])
def test_normalize_phone(raw, expected):
    assert normalize_phone(raw) == expected


def test_lenient_float():
    assert lenient_float(24.5) == 24.5 and lenient_float(3) == 3.0
    assert lenient_float("unknown") is None and lenient_float(None) is None and lenient_float(True) is None


# ----------------------------------------------------------------------------- reports

def test_report_without_gps_is_accepted_not_rejected(client):
    """Regression: phones with no GPS fix send the text "unknown" for lat/lng, which used to
    get the whole report rejected (and retried forever), so it never reached the Command Center."""
    headers, _ = _device(client)
    staff, _ = _staff(client)
    ev = str(uuid.uuid4())
    result = _sync(client, headers, ("incident", _report(ev, latitude="unknown", longitude="unknown"))).json()
    assert result == {"accepted": 1, "duplicates": 0, "rejected": 0}
    incident = _incident(client, staff, ev)
    assert incident["latitude"] is None and incident["longitude"] is None


def test_direct_report_without_gps_also_accepted(client):
    headers, _ = _device(client)
    resp = client.post("/api/incidents", headers=headers, json=_report(latitude="unknown", longitude="unknown"))
    assert resp.status_code == 201 and resp.json()["latitude"] is None


def test_phone_is_stored_normalized_and_needs_survive(client):
    phone = _phone()
    headers, _ = _device(client)
    staff, _ = _staff(client)
    ev = str(uuid.uuid4())
    digits = phone[3:]                                   # the part after "+92"
    formatted = f"+92 {digits[:3]}-{digits[3:6]} {digits[6:]}"
    _sync(client, headers, ("incident", _report(ev, reporter_phone=formatted, needs=["MEDICAL_TEAM", "BOAT"])))
    incident = _incident(client, staff, ev)
    assert incident["reporter_phone"] == phone
    assert incident["needs"] == "MEDICAL_TEAM,BOAT"


def test_an_unusable_phone_never_blocks_the_report(client):
    headers, _ = _device(client)
    staff, _ = _staff(client)
    ev = str(uuid.uuid4())
    result = _sync(client, headers, ("incident", _report(ev, reporter_phone="call me maybe"))).json()
    assert result["accepted"] == 1 and result["rejected"] == 0
    assert _incident(client, staff, ev)["reporter_phone"] is None


# ----------------------------------------------------------------------------- privacy

def test_phone_numbers_are_visible_to_staff_only(client):
    phone = _phone()
    headers, _ = _device(client)
    staff, _ = _staff(client)
    ev = str(uuid.uuid4())
    created = client.post("/api/incidents", headers=headers, json=_report(ev, reporter_phone=phone, source="app"))
    assert created.status_code == 201
    incident_id = created.json()["id"]

    assert created.json()["reporter_phone"] is None, "the reporter's own response needs no echo of the number"
    # Re-submitting the same event (dedup path) must not hand the number to whoever asks.
    again = client.post("/api/incidents", headers=headers, json=_report(ev, reporter_phone=phone, source="app"))
    assert again.json()["reporter_phone"] is None

    anonymous = [i for i in client.get("/api/incidents").json() if i["event_uuid"] == ev][0]
    citizen = [i for i in client.get("/api/incidents", headers=headers).json() if i["event_uuid"] == ev][0]
    assert anonymous["reporter_phone"] is None and citizen["reporter_phone"] is None
    assert client.get(f"/api/incidents/{incident_id}").json()["reporter_phone"] is None
    assert client.get(f"/api/incidents/{incident_id}", headers=headers).json()["reporter_phone"] is None

    assert _incident(client, staff, ev)["reporter_phone"] == phone
    assert client.get(f"/api/incidents/{incident_id}", headers=staff).json()["reporter_phone"] == phone
    # a garbage token is treated as anonymous, not as an error
    bad = client.get("/api/incidents", headers={"Authorization": "Bearer nonsense"})
    assert bad.status_code == 200 and all(i["reporter_phone"] is None for i in bad.json())


def test_live_feed_gives_phone_numbers_to_staff_connections_only(client):
    phone = _phone()
    headers, _ = _device(client)
    _, staff_token = _staff(client)
    ev = str(uuid.uuid4())
    with client.websocket_connect(f"/ws/incidents?token={staff_token}") as staff_ws, \
            client.websocket_connect("/ws/incidents") as anon_ws, \
            client.websocket_connect("/ws/incidents?token=garbage") as bad_ws:
        _sync(client, headers, ("incident", _report(ev, reporter_phone=phone)))
        staff_msg = json.loads(staff_ws.receive_text())
        anon_msg = json.loads(anon_ws.receive_text())
        bad_msg = json.loads(bad_ws.receive_text())
    assert staff_msg["event"] == anon_msg["event"] == "incident_created"
    assert staff_msg["data"]["reporter_phone"] == phone
    assert anon_msg["data"]["reporter_phone"] is None and bad_msg["data"]["reporter_phone"] is None
    assert anon_msg["data"]["event_uuid"] == ev  # still a working public feed, just without the private parts


# ----------------------------------------------------------------------------- "I'm safe" -> Command Center

def test_im_safe_via_sync_marks_the_reporter_safe_on_their_incident(client):
    phone = _phone()
    headers, _ = _device(client)
    staff, staff_token = _staff(client)
    ev = str(uuid.uuid4())
    _sync(client, headers, ("incident", _report(ev, reporter_phone=phone)))
    assert _incident(client, staff, ev)["reporter_status"] is None

    with client.websocket_connect(f"/ws/incidents?token={staff_token}") as ws:
        _sync(client, headers, ("safety_status", {"status": "SAFE", "at": 1760000000000,
                                                   "latitude": 24.88, "longitude": 67.06, "phone": phone}))
        events = [json.loads(ws.receive_text()), json.loads(ws.receive_text())]

    incident = _incident(client, staff, ev)
    assert incident["reporter_status"] == "SAFE" and incident["reporter_status_at"]
    by_event = {e["event"]: e["data"] for e in events}
    assert by_event["incident_updated"]["reporter_status"] == "SAFE"
    assert by_event["incident_updated"]["event_uuid"] == ev
    checkin = by_event["safety_status"]
    assert checkin["status"] == "SAFE" and checkin["phone"] == phone
    assert checkin["latitude"] == 24.88 and checkin["open_incident_id"] == incident["id"]

    db = SessionLocal()
    notes = db.query(IncidentUpdate).filter_by(incident_id=incident["id"], update_text="Reporter marked themselves safe").all()
    db.close()
    assert len(notes) == 1 and notes[0].actor_id is None, "the timeline records it as a system note"


def test_im_safe_via_direct_post_does_the_same(client):
    phone = _phone()
    headers, _ = _device(client)
    staff, _ = _staff(client)
    ev = str(uuid.uuid4())
    _sync(client, headers, ("incident", _report(ev)))
    assert client.post("/api/safety-status", headers=headers, json={"status": "SAFE", "phone": phone}).status_code == 201
    assert _incident(client, staff, ev)["reporter_status"] == "SAFE"


def test_public_viewers_never_learn_who_is_safe(client):
    phone = _phone()
    headers, _ = _device(client)
    _, staff_token = _staff(client)
    ev = str(uuid.uuid4())
    _sync(client, headers, ("incident", _report(ev)))
    with client.websocket_connect("/ws/incidents") as anon_ws, client.websocket_connect(f"/ws/incidents?token={staff_token}") as staff_ws:
        _sync(client, headers, ("safety_status", {"status": "SAFE", "phone": phone}))
        anon_first = json.loads(anon_ws.receive_text())
        # A later, unrelated event proves the staff-only safety_status was skipped for anonymous viewers.
        client.post("/api/incidents", headers=headers, json=_report())
        anon_second = json.loads(anon_ws.receive_text())
        staff_events = [json.loads(staff_ws.receive_text())["event"] for _ in range(3)]
    assert anon_first["event"] == "incident_updated" and anon_first["data"]["reporter_status"] is None
    assert anon_second["event"] == "incident_created"
    assert "safety_status" in staff_events
    anonymous = [i for i in client.get("/api/incidents").json() if i["event_uuid"] == ev][0]
    assert anonymous["reporter_status"] is None and anonymous["reporter_status_at"] is None


def test_a_relayed_report_is_matched_to_its_sender_by_phone_number(client):
    phone = _phone()
    """A report carried over the mesh is credited to the carrying phone, so the sender's own
    "I'm safe" (from their own device) has to be matched by phone number instead."""
    carrier, _ = _device(client)
    sender, _ = _device(client)
    staff, _ = _staff(client)
    ev = str(uuid.uuid4())
    _sync(client, carrier, ("incident", _report(ev, reporter_phone=phone)))
    assert _incident(client, staff, ev)["reporter_status"] is None
    _sync(client, sender, ("safety_status", {"status": "SAFE", "phone": phone}))
    assert _incident(client, staff, ev)["reporter_status"] == "SAFE"


def test_safe_before_asking_for_help_does_not_count(client):
    phone = _phone()
    headers, _ = _device(client)
    staff, _ = _staff(client)
    _sync(client, headers, ("safety_status", {"status": "SAFE", "phone": phone}))
    ev = str(uuid.uuid4())
    _sync(client, headers, ("incident", _report(ev, reporter_phone=phone)))
    assert _incident(client, staff, ev)["reporter_status"] is None


def test_repeat_taps_do_not_spam_notes_or_notifications(client):
    headers, _ = _device(client)
    staff, staff_token = _staff(client)
    ev = str(uuid.uuid4())
    _sync(client, headers, ("incident", _report(ev)))
    with client.websocket_connect(f"/ws/incidents?token={staff_token}") as ws:
        _sync(client, headers, ("safety_status", {"status": "SAFE"}))
        first = [json.loads(ws.receive_text())["event"] for _ in range(2)]
        _sync(client, headers, ("safety_status", {"status": "SAFE"}))           # immediate second tap
        client.post("/api/incidents", headers=headers, json=_report())           # marker event
        marker = json.loads(ws.receive_text())
    assert sorted(first) == ["incident_updated", "safety_status"]
    assert marker["event"] == "incident_created", "the repeat tap must not have produced a notification"
    incident = _incident(client, staff, ev)
    db = SessionLocal()
    notes = db.query(IncidentUpdate).filter_by(incident_id=incident["id"], update_text="Reporter marked themselves safe").count()
    stored = db.query(SafetyStatusUpdate).count()
    db.close()
    assert notes == 1 and stored >= 2, "both taps are still recorded; only the noise is suppressed"


def test_closed_incidents_are_left_alone(client):
    headers, _ = _device(client)
    staff, _ = _staff(client)
    ev = str(uuid.uuid4())
    _sync(client, headers, ("incident", _report(ev)))
    incident = _incident(client, staff, ev)
    assert client.post(f"/api/incidents/{incident['id']}/updates", headers=staff,
                       json={"update_text": "done", "status_change": "CLOSED"}).status_code == 200
    _sync(client, headers, ("safety_status", {"status": "SAFE"}))
    db = SessionLocal()
    notes = db.query(IncidentUpdate).filter_by(incident_id=incident["id"], update_text="Reporter marked themselves safe").count()
    db.close()
    assert notes == 0


# ----------------------------------------------------------------------------- check-ins list

def test_checkins_endpoint_is_staff_only(client):
    headers, _ = _device(client)
    assert client.get("/api/safety/checkins").status_code == 401
    assert client.get("/api/safety/checkins", headers=headers).status_code == 403
    staff, _ = _staff(client)
    assert client.get("/api/safety/checkins", headers=staff).status_code == 200


def test_checkins_lists_latest_status_per_person_with_phone_and_open_incident(client):
    phone = _phone()
    headers, user_id = _device(client)
    staff, _ = _staff(client)
    ev = str(uuid.uuid4())
    _sync(client, headers, ("incident", _report(ev, type="FLOOD")))
    _sync(client, headers, ("safety_status", {"status": "NEEDS_HELP"}))
    _sync(client, headers, ("safety_status", {"status": "SAFE", "phone": phone, "latitude": 24.9, "longitude": 67.1}))

    mine = [c for c in client.get("/api/safety/checkins", headers=staff).json() if c["user_id"] == user_id]
    assert len(mine) == 1, "one row per person, showing only their latest status"
    row = mine[0]
    assert row["status"] == "SAFE" and row["phone"] == phone
    assert row["latitude"] == 24.9 and row["longitude"] == 67.1
    assert row["label"] == f"Device user {user_id[-4:]}"
    assert row["open_incident_type"] == "FLOOD" and row["open_incident_id"]


# ----------------------------------------------------------------------------- migration

def _put_database_back_into_pre_feature_shape():
    with engine.begin() as conn:
        for table, column, _ in ADDED_COLUMNS:
            conn.execute(text(f"ALTER TABLE {table} DROP COLUMN {column}"))
    # SQLite connections already sitting in this test session's pool still remember the old table
    # layout; a real start-up begins with an empty pool, so mimic that.
    engine.dispose()


def test_startup_migration_adds_missing_columns_and_is_idempotent(client):
    phone = _phone()
    _put_database_back_into_pre_feature_shape()
    added = ensure_columns(engine)
    assert sorted(added) == sorted(f"{t}.{c}" for t, c, _ in ADDED_COLUMNS)
    assert ensure_columns(engine) == [], "running it again must change nothing"
    headers, _ = _device(client)                         # and the app works on the migrated schema
    assert _sync(client, headers, ("incident", _report(reporter_phone=phone))).json()["accepted"] == 1


def test_migration_survives_another_process_adding_the_column_first(client, monkeypatch):
    """Two app processes starting at once: both see the column missing, one adds it, the other's
    ALTER then fails with "duplicate column". That must not crash the second one's start-up."""
    from app import migrations
    real = migrations._column_state
    first_looks = set()

    def stale_first_look(eng, table, column):
        if (table, column) not in first_looks:
            first_looks.add((table, column))
            return False        # "missing" — but the other process has just added it
        return real(eng, table, column)

    monkeypatch.setattr(migrations, "_column_state", stale_first_look)
    assert ensure_columns(engine) == []  # tolerated, and nothing was (re)added by us
