from fastapi import APIRouter, Depends
from sqlalchemy import func
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import AIAnalysis, EmergencyIncident, IncidentStatus, NetworkMessage, Resource, ResourceStatus, Severity
from app.schemas import DashboardStatistics

router = APIRouter(prefix="/api/dashboard", tags=["dashboard"])


@router.get("/statistics", response_model=DashboardStatistics)
def get_statistics(db: Session = Depends(get_db)):
    total = db.query(func.count(EmergencyIncident.id)).scalar() or 0
    critical = db.query(func.count(EmergencyIncident.id)).filter(EmergencyIncident.severity == Severity.CRITICAL).scalar() or 0
    resolved = db.query(func.count(EmergencyIncident.id)).filter(EmergencyIncident.status == IncidentStatus.RESOLVED).scalar() or 0
    active_resources = db.query(func.count(Resource.id)).filter(Resource.status != ResourceStatus.UNAVAILABLE).scalar() or 0
    avg_confidence = db.query(func.avg(AIAnalysis.confidence)).scalar()
    relayed = db.query(func.count(NetworkMessage.id)).scalar() or 0

    return DashboardStatistics(
        total_incidents=total,
        critical_incidents=critical,
        resolved_incidents=resolved,
        active_resources=active_resources,
        average_ai_confidence=round(avg_confidence, 1) if avg_confidence is not None else None,
        network_messages_received=relayed,
    )
