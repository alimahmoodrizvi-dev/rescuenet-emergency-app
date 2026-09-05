import json

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.ai_provider import AIProvider, get_ai_provider
from app.auth import get_current_user
from app.database import get_db
from app.models import AIAnalysis, EmergencyIncident, User
from app.schemas import AIAnalyzeIncidentRequest, AIAnalyzeIncidentResponse

router = APIRouter(prefix="/api/ai", tags=["ai"])


@router.post("/analyze-incident", response_model=AIAnalyzeIncidentResponse)
async def analyze_incident(
    req: AIAnalyzeIncidentRequest,
    current_user: User = Depends(get_current_user),
    provider: AIProvider = Depends(get_ai_provider),
):
    """Part 6/8 — structures freeform text into a labeled, confidence-scored incident
    summary. This endpoint alone does not create or modify an EmergencyIncident row; the
    client (or /api/incidents caller) decides what to do with the result, consistent with
    Part 26: AI output is assistance, not an authoritative action."""
    result = await provider.analyze_incident_text(req.raw_text, req.language_hint)
    return AIAnalyzeIncidentResponse(
        incident_type=result["incident_type"],
        severity=result["severity"],
        people_count=result.get("people_count"),
        injuries_present=result.get("injuries_present"),
        required_resources=result.get("required_resources", []),
        confidence=result["confidence"],
        model_provider=provider.name,
        is_simulated=provider.name == "mock",
    )


@router.post("/analyze-incident/{incident_id}/attach", response_model=AIAnalyzeIncidentResponse)
async def analyze_and_attach(
    incident_id: str,
    req: AIAnalyzeIncidentRequest,
    current_user: User = Depends(get_current_user),
    provider: AIProvider = Depends(get_ai_provider),
    db: Session = Depends(get_db),
):
    """Convenience endpoint used by /api/ai/cluster and the command center: analyzes text and
    persists the result against a specific incident (Part 9's AIAnalysis table), setting the
    incident's severity from the AI result."""
    incident = db.get(EmergencyIncident, incident_id)
    if incident is None:
        raise HTTPException(status_code=404, detail="Incident not found")

    result = await provider.analyze_incident_text(req.raw_text, req.language_hint)

    existing = db.query(AIAnalysis).filter(AIAnalysis.incident_id == incident_id).first()
    if existing:
        db.delete(existing)
        db.flush()

    analysis = AIAnalysis(
        incident_id=incident_id,
        model_provider=provider.name,
        raw_output_json=json.dumps(result, default=str),
        confidence=result["confidence"],
        is_simulated=provider.name == "mock",
    )
    db.add(analysis)
    incident.severity = result["severity"]
    db.commit()

    return AIAnalyzeIncidentResponse(
        incident_type=result["incident_type"],
        severity=result["severity"],
        people_count=result.get("people_count"),
        injuries_present=result.get("injuries_present"),
        required_resources=result.get("required_resources", []),
        confidence=result["confidence"],
        model_provider=provider.name,
        is_simulated=provider.name == "mock",
    )


@router.post("/cluster")
def cluster_incidents(radius_km: float = 2.0, db: Session = Depends(get_db)):
    """Part 10/12 — Incident clustering. Naive but honest proximity clustering: greedily
    groups open incidents within `radius_km` of each other using the same haversine distance
    used for resource matching. A production version would use a real spatial index
    (PostGIS + DBSCAN, per Part 8's architecture note) instead of this O(n^2) pass -- fine for
    a hackathon-scale dataset, explicitly not fine at city scale, which is stated here rather
    than silently left as a scaling landmine."""
    from app.models import IncidentStatus
    from app.routers.resources import _haversine_km

    incidents = (
        db.query(EmergencyIncident)
        .filter(EmergencyIncident.status != IncidentStatus.RESOLVED)
        .filter(EmergencyIncident.latitude.isnot(None))
        .all()
    )

    visited: set[str] = set()
    clusters = []
    for i in incidents:
        if i.id in visited:
            continue
        group = [i]
        visited.add(i.id)
        for j in incidents:
            if j.id in visited:
                continue
            if _haversine_km(i.latitude, i.longitude, j.latitude, j.longitude) <= radius_km:
                group.append(j)
                visited.add(j.id)
        if len(group) >= 3:  # a "cluster" needs at least a few reports, not two coincidental ones
            type_values = [g.type.value for g in group]
            clusters.append({
                "incident_count": len(group),
                "incident_ids": [g.id for g in group],
                "likely_type": max(set(type_values), key=type_values.count),
                "estimated_people_affected": sum(g.people_count for g in group),
                "center_latitude": sum(g.latitude for g in group) / len(group),
                "center_longitude": sum(g.longitude for g in group) / len(group),
            })

    return {"clusters": clusters, "radius_km": radius_km}
