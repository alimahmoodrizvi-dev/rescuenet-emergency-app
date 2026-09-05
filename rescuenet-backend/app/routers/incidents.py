from datetime import datetime, timezone
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.auth import get_current_user, require_roles
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
        return existing

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
    )
    db.add(incident)
    db.commit()
    db.refresh(incident)

    await manager.broadcast("incident_created", IncidentResponse.model_validate(incident).model_dump())
    return incident


@router.get("", response_model=list[IncidentResponse])
def list_incidents(
    type: Optional[IncidentType] = None,
    severity: Optional[Severity] = None,
    status_filter: Optional[IncidentStatus] = Query(default=None, alias="status"),
    since: Optional[datetime] = None,
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
    return query.order_by(EmergencyIncident.created_at.desc()).limit(500).all()


@router.get("/{incident_id}", response_model=IncidentResponse)
def get_incident(incident_id: str, db: Session = Depends(get_db)):
    incident = db.get(EmergencyIncident, incident_id)
    if incident is None:
        raise HTTPException(status_code=404, detail="Incident not found")
    return incident


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

    await manager.broadcast("incident_updated", IncidentResponse.model_validate(incident).model_dump())
    return incident
