import math
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.auth import require_roles
from app.database import get_db
from app.models import (
    EmergencyIncident, Resource, ResourceAssignment, ResourceStatus, User, UserRole,
)
from app.schemas import (
    ResourceAssignmentResponse, ResourceAssignRequest, ResourceCreateRequest, ResourceResponse,
    ResourceUpdateRequest,
)
from app.websocket import manager

router = APIRouter(prefix="/api/resources", tags=["resources"])


@router.post("", response_model=ResourceResponse, status_code=status.HTTP_201_CREATED)
async def create_resource(
    req: ResourceCreateRequest,
    current_user: User = Depends(require_roles(UserRole.RESCUE_OPERATOR, UserRole.COMMAND_CENTER_ADMIN)),
    db: Session = Depends(get_db),
):
    resource = Resource(
        name=req.name, type=req.type, latitude=req.latitude, longitude=req.longitude,
        capacity=req.capacity, capabilities=req.capabilities,
    )
    db.add(resource)
    db.commit()
    db.refresh(resource)

    await manager.broadcast("resource_created", ResourceResponse.model_validate(resource).model_dump())
    return resource


@router.patch("/{resource_id}", response_model=ResourceResponse)
async def update_resource(
    resource_id: str,
    req: ResourceUpdateRequest,
    current_user: User = Depends(require_roles(UserRole.RESCUE_OPERATOR, UserRole.COMMAND_CENTER_ADMIN)),
    db: Session = Depends(get_db),
):
    resource = db.get(Resource, resource_id)
    if resource is None:
        raise HTTPException(status_code=404, detail="Resource not found")

    for field, value in req.model_dump(exclude_unset=True).items():
        setattr(resource, field, value)
    db.commit()
    db.refresh(resource)

    await manager.broadcast("resource_updated", ResourceResponse.model_validate(resource).model_dump())
    return resource


@router.delete("/{resource_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_resource(
    resource_id: str,
    current_user: User = Depends(require_roles(UserRole.RESCUE_OPERATOR, UserRole.COMMAND_CENTER_ADMIN)),
    db: Session = Depends(get_db),
):
    resource = db.get(Resource, resource_id)
    if resource is None:
        raise HTTPException(status_code=404, detail="Resource not found")
    db.delete(resource)
    db.commit()
    await manager.broadcast("resource_deleted", {"id": resource_id})


def _haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlambda / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


@router.get("", response_model=list[ResourceResponse])
def list_resources(
    resource_type: Optional[str] = None,
    resource_status: Optional[ResourceStatus] = None,
    db: Session = Depends(get_db),
):
    query = db.query(Resource)
    if resource_type:
        query = query.filter(Resource.type == resource_type)
    if resource_status:
        query = query.filter(Resource.status == resource_status)
    return query.all()


@router.post("/{resource_id}/assign", response_model=ResourceAssignmentResponse)
async def assign_resource(
    resource_id: str,
    req: ResourceAssignRequest,
    current_user: User = Depends(require_roles(UserRole.RESCUE_OPERATOR, UserRole.COMMAND_CENTER_ADMIN)),
    db: Session = Depends(get_db),
):
    resource = db.get(Resource, resource_id)
    incident = db.get(EmergencyIncident, req.incident_id)
    if resource is None or incident is None:
        raise HTTPException(status_code=404, detail="Resource or incident not found")

    assignment = ResourceAssignment(
        resource_id=resource.id, incident_id=incident.id,
        assigned_by=current_user.id, eta_minutes=req.eta_minutes,
    )
    resource.status = ResourceStatus.BUSY
    db.add(assignment)
    db.commit()
    db.refresh(assignment)

    await manager.broadcast("resource_assigned", ResourceAssignmentResponse.model_validate(assignment).model_dump())
    return assignment


@router.get("/recommend/{incident_id}")
def recommend_resource(incident_id: str, db: Session = Depends(get_db)):
    """Part 12 — Smart Resource Matching. Scores every AVAILABLE resource by distance (with
    an assumed average response speed to estimate ETA) and returns the top match plus the
    reasoning behind it, so the command center sees *why*, not just a black-box pick. Real
    deployments would also weight resource type against the incident's required-resources
    list and true road-network ETA (not straight-line distance) — noted here rather than
    silently approximated as exact."""
    incident = db.get(EmergencyIncident, incident_id)
    if incident is None or incident.latitude is None or incident.longitude is None:
        raise HTTPException(status_code=400, detail="Incident has no location to match against")

    candidates = db.query(Resource).filter(Resource.status == ResourceStatus.AVAILABLE).all()
    scored = []
    for r in candidates:
        if r.latitude is None or r.longitude is None:
            continue
        distance_km = _haversine_km(incident.latitude, incident.longitude, r.latitude, r.longitude)
        assumed_speed_kmh = 30  # rough urban-response assumption, stated explicitly, not hidden
        eta_minutes = max(1, round((distance_km / assumed_speed_kmh) * 60))
        scored.append({"resource": r, "distance_km": round(distance_km, 2), "eta_minutes": eta_minutes})

    if not scored:
        return {"recommendation": None, "reason": "No available resources with known locations."}

    best = min(scored, key=lambda s: s["distance_km"])
    return {
        "recommendation": {
            "resource_id": best["resource"].id,
            "name": best["resource"].name,
            "type": best["resource"].type,
            "distance_km": best["distance_km"],
            "eta_minutes": best["eta_minutes"],
            "capabilities": best["resource"].capabilities,
        },
        "reason": (
            f"Closest available resource ({best['distance_km']} km, "
            f"~{best['eta_minutes']} min at an assumed {30} km/h) among {len(scored)} candidates evaluated."
        ),
        "is_simulated_eta": True,  # straight-line distance / assumed speed, not real routing — Part 36
    }
