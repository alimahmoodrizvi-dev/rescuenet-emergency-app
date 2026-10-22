from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app import incident_view, safety_service
from app.auth import get_current_user
from app.database import get_db
from app.models import EmergencyIncident, User
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
    created: list[EmergencyIncident] = []
    safety_outcomes: list[safety_service.SafetyOutcome] = []

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
                    # Kept per-incident only. NOT copied onto `current_user`: this device may just
                    # be carrying someone else's report over the mesh, and it must not inherit
                    # that person's phone number.
                    reporter_phone=incident_req.reporter_phone,
                    source="relay",  # arrived via the batch/mesh path, not a direct live POST
                )
                db.add(incident)
                db.flush()
                created.append(incident)
                accepted += 1

            elif item.entity_type == "safety_status":
                status_req = SafetyStatusRequest.model_validate(item.payload)
                safety_outcomes.append(safety_service.apply_status(db, current_user, status_req))
                accepted += 1
            else:
                rejected += 1
        except Exception:
            rejected += 1

    db.commit()

    # Broadcast the *full* incident only after the commit succeeded, so the Command Center
    # can show relayed (offline-mesh) help requests live — with location and details — exactly
    # like direct ones, instead of receiving a bare ID for a row that might not be saved yet.
    for incident in created:
        db.refresh(incident)
        await incident_view.broadcast_incident(db, "incident_created", incident)
    for outcome in safety_outcomes:
        await safety_service.broadcast_outcome(db, outcome)

    return SyncBatchResult(accepted=accepted, duplicates=duplicates, rejected=rejected)
