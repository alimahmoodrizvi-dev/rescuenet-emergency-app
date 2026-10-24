"""Recording "I'm safe" (and other self-reported status) so the Command Center can see it.

Used by both entry points — the direct POST /api/safety-status and the offline sync batch — so
they behave identically: store the update, remember the person's phone number, and for "SAFE"
leave a note on every not-yet-closed incident that person reported, so a coordinator looking at
that incident sees straight away that the person says they are safe.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Optional

from sqlalchemy import func, or_
from sqlalchemy.orm import Session

from app import incident_view
from app.models import (
    EmergencyIncident, IncidentStatus, IncidentUpdate, SafetyStatus, SafetyStatusUpdate, User,
)
from app.schemas import SafetyCheckinResponse, SafetyStatusRequest
from app.websocket import manager

SAFE_NOTE = "Reporter marked themselves safe"
# A second "I'm safe" within this window is stored but doesn't re-notify the Command Center.
REPEAT_WINDOW = timedelta(minutes=2)


@dataclass
class SafetyOutcome:
    user_id: str
    update_id: str
    notify: bool
    incident_ids: list[str] = field(default_factory=list)


def _naive_now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def _reporter_incidents(db: Session, user: User):
    """Not-yet-closed incidents this person reported (by device, or by their phone number)."""
    conditions = [EmergencyIncident.reporter_id == user.id]
    if user.phone:
        conditions.append(EmergencyIncident.reporter_phone == user.phone)
    return db.query(EmergencyIncident).filter(or_(*conditions), EmergencyIncident.status != IncidentStatus.CLOSED)


def apply_status(db: Session, user: User, req: SafetyStatusRequest) -> SafetyOutcome:
    if req.phone:
        user.phone = req.phone

    previous = (
        db.query(SafetyStatusUpdate)
        .filter(SafetyStatusUpdate.user_id == user.id)
        .order_by(SafetyStatusUpdate.created_at.desc())
        .first()
    )
    update = SafetyStatusUpdate(
        user_id=user.id, status=req.status, latitude=req.latitude, longitude=req.longitude,
    )
    db.add(update)
    db.flush()

    is_repeat = (
        previous is not None
        and previous.status == req.status
        and update.created_at - previous.created_at < REPEAT_WINDOW
    )

    touched: list[str] = []
    if req.status == SafetyStatus.SAFE:
        for incident in _reporter_incidents(db, user).all():
            last = (
                db.query(IncidentUpdate)
                .filter(IncidentUpdate.incident_id == incident.id)
                .order_by(IncidentUpdate.created_at.desc())
                .first()
            )
            if last is not None and last.actor_id is None and last.update_text == SAFE_NOTE:
                continue  # already noted; don't pile up a note per tap
            db.add(IncidentUpdate(incident_id=incident.id, actor_id=None, update_text=SAFE_NOTE))
            incident.updated_at = _naive_now()
            touched.append(incident.id)

    return SafetyOutcome(user_id=user.id, update_id=update.id, notify=not is_repeat, incident_ids=touched)


def _label(user: User) -> str:
    return user.name or f"Device user {user.id[-4:]}"


def _checkin(user: User, update: SafetyStatusUpdate, open_incident: Optional[EmergencyIncident]) -> SafetyCheckinResponse:
    return SafetyCheckinResponse(
        user_id=user.id, label=_label(user), phone=user.phone, status=update.status,
        latitude=update.latitude, longitude=update.longitude, updated_at=update.created_at,
        open_incident_id=open_incident.id if open_incident else None,
        open_incident_type=open_incident.type.value if open_incident else None,
    )


def list_checkins(db: Session, limit: int = 500) -> list[SafetyCheckinResponse]:
    """The latest status of every person who has ever checked in, newest first."""
    latest = (
        db.query(SafetyStatusUpdate.user_id, func.max(SafetyStatusUpdate.created_at).label("latest_at"))
        .group_by(SafetyStatusUpdate.user_id)
        .subquery()
    )
    rows = (
        db.query(SafetyStatusUpdate, User)
        .join(latest, (SafetyStatusUpdate.user_id == latest.c.user_id) & (SafetyStatusUpdate.created_at == latest.c.latest_at))
        .join(User, User.id == SafetyStatusUpdate.user_id)
        .order_by(SafetyStatusUpdate.created_at.desc())
        .limit(limit)
        .all()
    )
    user_ids = {u.id for _, u in rows}
    phones = {u.phone for _, u in rows if u.phone}
    conditions = [EmergencyIncident.reporter_id.in_(user_ids)] if user_ids else []
    if phones:
        conditions.append(EmergencyIncident.reporter_phone.in_(phones))
    open_by_user: dict[str, EmergencyIncident] = {}
    open_by_phone: dict[str, EmergencyIncident] = {}
    if conditions:
        for incident in (
            db.query(EmergencyIncident)
            .filter(or_(*conditions), EmergencyIncident.status != IncidentStatus.CLOSED)
            .order_by(EmergencyIncident.created_at.desc())
            .all()
        ):
            open_by_user.setdefault(incident.reporter_id, incident)
            if incident.reporter_phone:
                open_by_phone.setdefault(incident.reporter_phone, incident)

    seen: set[str] = set()
    result: list[SafetyCheckinResponse] = []
    for update, user in rows:
        if user.id in seen:
            continue  # two updates tied on the same timestamp
        seen.add(user.id)
        open_incident = open_by_user.get(user.id) or (open_by_phone.get(user.phone) if user.phone else None)
        result.append(_checkin(user, update, open_incident))
    return result


async def broadcast_outcome(db: Session, outcome: SafetyOutcome) -> None:
    """Tell connected Command Centers. Call only after the transaction has been committed."""
    for incident_id in outcome.incident_ids:
        incident = db.get(EmergencyIncident, incident_id)
        if incident is not None:
            await incident_view.broadcast_incident(db, "incident_updated", incident)
    if not outcome.notify:
        return
    update = db.get(SafetyStatusUpdate, outcome.update_id)
    user = db.get(User, outcome.user_id)
    if update is None or user is None:
        return
    open_incident = _reporter_incidents(db, user).order_by(EmergencyIncident.created_at.desc()).first()
    # Staff only: it names a person and carries their phone number and last known position.
    await manager.broadcast("safety_status", _checkin(user, update, open_incident).model_dump(), staff_only=True)
