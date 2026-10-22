from datetime import datetime, timezone
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app import incident_view
from app.auth import get_current_user, optional_current_user, require_roles
from app.database import get_db
from app.models import (
    EmergencyIncident, IncidentStatus, IncidentType, IncidentUpdate, Severity, User, UserRole,
)
from app.schemas import IncidentCreateRequest, IncidentResponse, IncidentUpdateRequest
from app.websocket import manager

router = APIRouter(prefix="/api/incidents", tags=["incidents"])


@router.post("", response_model=IncidentResponse, status_code=status.HTTP_201_CREATED)
async def create_incident(
    req: IncidentCreateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """De-dup on event_uuid (Part 21 — 'never duplicate emergency incidents'): if this exact
    report already exists (e.g. it arrived once directly and once via a mesh relay hop),
    return the existing row instead of creating a second one."""
    existing = db.query(EmergencyIncident).filter(EmergencyIncident.event_uuid == req.event_uuid).first()
    if existing:
        # Whoever asks gets the public view: never the reporter's phone or status.
        return incident_view.build_response(db, existing, reveal_private=False)

    incident = EmergencyIncident(
        event_uuid=req.event_uuid,
        reporter_id=current_user.id,
        type=req.type,
        people_count=req.people_count,
        injury_level=req.injury_level,
        needs=",".join(req.needs),
        description=req.description,
        latitude=req.latitude,
        longitude=req.longitude,
        location_accuracy_m=req.location_accuracy_m,
        source=req.source,
        reporter_phone=req.reporter_phone,
    )
    db.add(incident)
    if req.reporter_phone and req.source == "app":
        current_user.phone = req.reporter_phone  # the device itself shared its own number
    db.commit()
    db.refresh(incident)

    # Command Center staff (live feed) get the full picture; the reporter gets the public view.
    await incident_view.broadcast_incident(db, "incident_created", incident)
    return incident_view.build_response(db, incident, reveal_private=False)


@router.get("", response_model=list[IncidentResponse])
def list_incidents(
    type: Optional[IncidentType] = None,
    severity: Optional[Severity] = None,
    status_filter: Optional[IncidentStatus] = Query(default=None, alias="status"),
    since: Optional[datetime] = None,
    viewer: Optional[User] = Depends(optional_current_user),
    db: Session = Depends(get_db),
):
    """Supports the Part 9 filter set (type/severity/time/status); area and required-resource
    filtering are left as a follow-up once PostGIS is wired in for real geo queries (Part 12
    notes PostGIS as the recommended extension — plain lat/lng bounding-box filtering would
    be a reasonable interim step but isn't implemented in this prototype)."""
    query = db.query(EmergencyIncident)
    if type:
        query = query.filter(EmergencyIncident.type == type)
    if severity:
        query = query.filter(EmergencyIncident.severity == severity)
    if status_filter:
        query = query.filter(EmergencyIncident.status == status_filter)
    if since:
        query = query.filter(EmergencyIncident.created_at >= since)
    incidents = query.order_by(EmergencyIncident.created_at.desc()).limit(500).all()
    # Still publicly readable as before; only logged-in staff also see phone numbers and safe status.
    return incident_view.build_responses(db, incidents, reveal_private=incident_view.is_staff(viewer))


@router.get("/{incident_id}", response_model=IncidentResponse)
def get_incident(
    incident_id: str,
    viewer: Optional[User] = Depends(optional_current_user),
    db: Session = Depends(get_db),
):
    incident = db.get(EmergencyIncident, incident_id)
    if incident is None:
        raise HTTPException(status_code=404, detail="Incident not found")
    return incident_view.build_response(db, incident, reveal_private=incident_view.is_staff(viewer))


@router.delete("/{incident_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_incident(
    incident_id: str,
    current_user: User = Depends(require_roles(UserRole.RESCUE_OPERATOR, UserRole.COMMAND_CENTER_ADMIN)),
    db: Session = Depends(get_db),
):
    """Only CLOSED incidents can be deleted — an open or in-progress incident disappearing
    from the map could hide a real emergency. The ORM cascade on EmergencyIncident also
    removes its updates, resource assignments, and AI analysis rows."""
    incident = db.get(EmergencyIncident, incident_id)
    if incident is None:
        raise HTTPException(status_code=404, detail="Incident not found")
    if incident.status != IncidentStatus.CLOSED:
        raise HTTPException(status_code=409, detail="Only closed incidents can be deleted")
    db.delete(incident)
    db.commit()
    await manager.broadcast("incident_deleted", {"id": incident_id})


@router.post("/{incident_id}/updates", response_model=IncidentResponse)
async def add_incident_update(
    incident_id: str,
    req: IncidentUpdateRequest,
    current_user: User = Depends(
        require_roles(UserRole.VOLUNTEER, UserRole.RESCUE_OPERATOR, UserRole.COMMAND_CENTER_ADMIN)
    ),
    db: Session = Depends(get_db),
):
    incident = db.get(EmergencyIncident, incident_id)
    if incident is None:
        raise HTTPException(status_code=404, detail="Incident not found")

    update = IncidentUpdate(
        incident_id=incident.id, actor_id=current_user.id,
        update_text=req.update_text, status_change=req.status_change,
    )
    db.add(update)
    if req.status_change:
        incident.status = req.status_change
    incident.updated_at = datetime.now(timezone.utc).replace(tzinfo=None)
    db.commit()
    db.refresh(incident)

    await incident_view.broadcast_incident(db, "incident_updated", incident)
    return incident_view.build_response(db, incident, reveal_private=True)  # endpoint is staff-only
