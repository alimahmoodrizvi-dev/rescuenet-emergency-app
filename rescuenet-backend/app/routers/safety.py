from fastapi import APIRouter, Depends
from sqlalchemy import func
from sqlalchemy.orm import Session

from app.auth import get_current_user
from app.database import get_db
from app.models import EmergencyContact, SafetyStatusUpdate, User
from app.schemas import FamilyMemberStatusResponse, SafetyStatusRequest

router = APIRouter(prefix="/api", tags=["safety"])


@router.post("/safety-status", status_code=201)
def post_safety_status(
    req: SafetyStatusRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Part 8 — "I'm Safe" / status updates are append-only rows, not a mutated field, so the
    family board can show history and so a late-arriving offline update (synced after a more
    recent online one) doesn't clobber a newer status — see get_family_status's ordering."""
    update = SafetyStatusUpdate(
        user_id=current_user.id, status=req.status,
        latitude=req.latitude, longitude=req.longitude,
    )
    db.add(update)
    db.commit()
    return {"accepted": True}


@router.get("/family/{user_id}", response_model=list[FamilyMemberStatusResponse])
def get_family_status(user_id: str, db: Session = Depends(get_db)):
    """Returns each trusted contact's *latest* status row. Grouping by user and taking
    MAX(created_at) rather than trusting insertion order matters once messages can arrive
    out of order via mesh relay + delayed sync (Part 21)."""
    contact_ids = [
        row.contact_user_id
        for row in db.query(EmergencyContact).filter(EmergencyContact.user_id == user_id).all()
    ]
    if not contact_ids:
        return []

    latest_per_user = (
        db.query(
            SafetyStatusUpdate.user_id,
            func.max(SafetyStatusUpdate.created_at).label("latest_at"),
        )
        .filter(SafetyStatusUpdate.user_id.in_(contact_ids))
        .group_by(SafetyStatusUpdate.user_id)
        .subquery()
    )

    rows = (
        db.query(SafetyStatusUpdate, User)
        .join(latest_per_user, (SafetyStatusUpdate.user_id == latest_per_user.c.user_id) &
              (SafetyStatusUpdate.created_at == latest_per_user.c.latest_at))
        .join(User, User.id == SafetyStatusUpdate.user_id)
        .all()
    )

    return [
        FamilyMemberStatusResponse(user_id=u.id, name=u.name, status=s.status, updated_at=s.created_at)
        for s, u in rows
    ]
