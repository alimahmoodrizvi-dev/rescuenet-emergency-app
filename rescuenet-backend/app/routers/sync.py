from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth import get_current_user
from app.database import get_db
from app.models import EmergencyIncident, SafetyStatusUpdate, User
from app.schemas import IncidentCreateRequest, SafetyStatusRequest, SyncBatchRequest, SyncBatchResult
from app.websocket import manager

router = APIRouter(prefix="/api/sync", tags=["sync"])


@router.post("/batch", response_model=SyncBatchResult)
async def sync_batch(
    req: SyncBatchRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """The server-side end of the pipeline described in Part 21 and implemented by the
    Android app's SyncWorker (see its `TODO(Phase 5)` markers) and MeshRelayManager: this is
    where queued offline reports/status-updates — including ones this device only holds
    because it relayed them for a *different* phone over the mesh — finally land. Every
    incident item is deduped by event_uuid exactly like POST /api/incidents; this endpoint is
    just a bulk-friendly variant of the same logic for catching up after a period offline."""
    accepted = duplicates = rejected = 0

    for item in req.items:
        try:
            if item.entity_type == "incident":
                incident_req = IncidentCreateRequest.model_validate(item.payload)
                existing = db.query(EmergencyIncident).filter(
                    EmergencyIncident.event_uuid == incident_req.event_uuid
                ).first()
                if existing:
                    duplicates += 1
                    continue
                incident = EmergencyIncident(
                    event_uuid=incident_req.event_uuid,
                    reporter_id=current_user.id,
                    type=incident_req.type,
                    people_count=incident_req.people_count,
                    injury_level=incident_req.injury_level,
                    needs=",".join(incident_req.needs),
                    description=incident_req.description,
                    latitude=incident_req.latitude,
                    longitude=incident_req.longitude,
                    location_accuracy_m=incident_req.location_accuracy_m,
                    source="relay",  # arrived via the batch/mesh path, not a direct live POST
                )
                db.add(incident)
                db.flush()
                accepted += 1
                await manager.broadcast("incident_created", {"event_uuid": incident.event_uuid, "via": "sync_batch"})

            elif item.entity_type == "safety_status":
                status_req = SafetyStatusRequest.model_validate(item.payload)
                db.add(SafetyStatusUpdate(
                    user_id=current_user.id, status=status_req.status,
                    latitude=status_req.latitude, longitude=status_req.longitude,
                ))
                accepted += 1
            else:
                rejected += 1
        except Exception:
            rejected += 1

    db.commit()
    return SyncBatchResult(accepted=accepted, duplicates=duplicates, rejected=rejected)
