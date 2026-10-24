"""Builds IncidentResponse objects — the single place phone numbers and "the reporter says
they're safe" are added.

Privacy rule: a reporter's phone number is only ever included when `reveal_private=True`, and
that is only passed for (a) authenticated Command Center staff over REST or (b) the staff-only
live websocket. Every response a citizen's device can receive is built with reveal_private=False
and so carries no phone number and no status information about anyone.
"""
from __future__ import annotations

from typing import Iterable, Optional

from sqlalchemy import or_
from sqlalchemy.orm import Session

from app.models import EmergencyIncident, SafetyStatusUpdate, User, UserRole
from app.schemas import IncidentResponse

# Roles that log in to the Command Center dashboard.
STAFF_ROLES = (UserRole.VOLUNTEER, UserRole.RESCUE_OPERATOR, UserRole.COMMAND_CENTER_ADMIN)


def build_responses(db: Session, incidents: Iterable[EmergencyIncident], *, reveal_private: bool) -> list[IncidentResponse]:
    incidents = list(incidents)
    responses = [IncidentResponse.model_validate(i) for i in incidents]

    if not reveal_private:
        for response in responses:
            response.reporter_phone = None
        return responses
    if not incidents:
        return responses

    # What each reporter has said about themselves most recently. An incident is matched to its
    # reporter by device AND by phone number: a report relayed over the mesh is credited to the
    # phone that carried it, so the device alone would miss the person who actually sent it.
    reporter_ids = {i.reporter_id for i in incidents if i.reporter_id}
    phones = {i.reporter_phone for i in incidents if i.reporter_phone}
    conditions = []
    if reporter_ids:
        conditions.append(User.id.in_(reporter_ids))
    if phones:
        conditions.append(User.phone.in_(phones))
    if not conditions:
        return responses

    rows = (
        db.query(SafetyStatusUpdate, User)
        .join(User, User.id == SafetyStatusUpdate.user_id)
        .filter(or_(*conditions))
        .order_by(SafetyStatusUpdate.created_at.desc())
        .all()
    )
    latest_by_user: dict[str, SafetyStatusUpdate] = {}
    latest_by_phone: dict[str, SafetyStatusUpdate] = {}
    for update, user in rows:  # newest first, so the first one seen for a key is its latest
        latest_by_user.setdefault(user.id, update)
        if user.phone:
            latest_by_phone.setdefault(user.phone, update)

    for incident, response in zip(incidents, responses):
        candidates = [
            update
            for update in (latest_by_user.get(incident.reporter_id), latest_by_phone.get(incident.reporter_phone))
            if update is not None and update.created_at >= incident.created_at  # only what they said *since* asking for help
        ]
        if candidates:
            latest = max(candidates, key=lambda u: u.created_at)
            response.reporter_status = latest.status.value
            response.reporter_status_at = latest.created_at
    return responses


def is_staff(user: Optional[User]) -> bool:
    return user is not None and user.role in STAFF_ROLES


async def broadcast_incident(db: Session, event_type: str, incident: EmergencyIncident) -> None:
    """Push an incident event: staff connections get the full picture, anonymous ones the
    scrubbed public view."""
    from app.websocket import manager  # local import: websocket.py has no app dependencies
    await manager.broadcast(
        event_type,
        build_response(db, incident, reveal_private=True).model_dump(),
        public_data=build_response(db, incident, reveal_private=False).model_dump(),
    )


def build_response(db: Session, incident: EmergencyIncident, *, reveal_private: bool) -> IncidentResponse:
    return build_responses(db, [incident], reveal_private=reveal_private)[0]
