from datetime import datetime, timezone

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth import require_roles
from app.database import get_db
from app.models import Alert, User, UserRole
from app.schemas import AlertCreateRequest, AlertResponse
from app.websocket import manager

router = APIRouter(prefix="/api/alerts", tags=["alerts"])


@router.post("", response_model=AlertResponse, status_code=201)
async def create_alert(
    req: AlertCreateRequest,
    current_user: User = Depends(require_roles(UserRole.RESCUE_OPERATOR, UserRole.COMMAND_CENTER_ADMIN)),
    db: Session = Depends(get_db),
):
    """Part 15/36 — real official alerts vs demo alerts must never be ambiguous. `is_demo`
    defaults True in the schema; a caller must explicitly pass false, and this prototype does
    not expose any path to mark an alert as an official government warning — that
    integration is future work (Part 17), not something to fake here."""
    alert = Alert(
        type=req.type, severity=req.severity, message=req.message,
        is_demo=req.is_demo, created_by=current_user.id, expires_at=req.expires_at,
    )
    db.add(alert)
    db.commit()
    db.refresh(alert)

    await manager.broadcast("alert_created", AlertResponse.model_validate(alert).model_dump())
    return alert


@router.get("", response_model=list[AlertResponse])
def list_alerts(db: Session = Depends(get_db)):
    now = datetime.now(timezone.utc).replace(tzinfo=None)
    return (
        db.query(Alert)
        .filter((Alert.expires_at.is_(None)) | (Alert.expires_at > now))
        .order_by(Alert.created_at.desc())
        .all()
    )
