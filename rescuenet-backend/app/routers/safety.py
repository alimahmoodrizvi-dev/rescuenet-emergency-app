from fastapi import APIRouter, Depends
from sqlalchemy import func
from sqlalchemy.orm import Session

from app import incident_view, safety_service
from app.auth import get_current_user, require_roles
from app.database import get_db
from app.models import EmergencyContact, SafetyStatusUpdate, User
from app.schemas import FamilyMemberStatusResponse, SafetyCheckinResponse, SafetyStatusRequest

router = APIRouter(prefix="/api", tags=["safety"])


@router.post("/safety-status", status_code=201)
async def post_safety_status(
    req: SafetyStatusRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Part 8 — "I'm Safe" / status updates are append-only rows, not a mutated field, so the
    family board can show history and so a late-arriving offline update (synced after a more
    recent online one) doesn't clobber a newer status — see get_family_status's ordering.
    Also makes the person visible as safe to Command Center staff (see safety_service)."""
    outcome = safety_service.apply_status(db, current_user, req)
    db.commit()
    await safety_service.broadcast_outcome(db, outcome)
    return {"accepted": True}


@router.get("/safety/checkins", response_model=list[SafetyCheckinResponse])
def list_safety_checkins(
    current_user: User = Depends(require_roles(*incident_view.STAFF_ROLES)),
    db: Session = Depends(get_db),
):
    """Command Center only: everyone who has reported a status, latest first, with the phone
    number they chose to share so staff can reach them."""
    return safety_service.list_checkins(db)


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
