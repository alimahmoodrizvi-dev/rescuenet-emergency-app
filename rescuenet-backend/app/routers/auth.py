from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.auth import create_access_token, verify_password
from app.database import get_db
from app.models import Device, User, UserRole
from app.schemas import DeviceRegisterRequest, OrgLoginRequest, TokenResponse

router = APIRouter(prefix="/api/auth", tags=["auth"])


@router.post("/device-register", response_model=TokenResponse)
def device_register(req: DeviceRegisterRequest, db: Session = Depends(get_db)):
    """Anonymous citizen auth (Part 19/24) — the device_uuid is the same one generated and
    stored locally by the Android app's UserRepository. First call for a given device_uuid
    creates both a Device row and a bare CITIZEN User row; subsequent calls just refresh the
    token and last_seen_at."""
    device = db.query(Device).filter(Device.device_uuid == req.device_uuid).first()

    if device is None:
        user = User(role=UserRole.CITIZEN)
        db.add(user)
        db.flush()  # populate user.id before referencing it
        device = Device(
            device_uuid=req.device_uuid, user_id=user.id,
            os_version=req.os_version, app_version=req.app_version,
        )
        db.add(device)
    else:
        device.os_version = req.os_version or device.os_version
        device.app_version = req.app_version or device.app_version

    db.commit()
    db.refresh(device)
    user = db.get(User, device.user_id)

    token = create_access_token(user.id, user.role)
    return TokenResponse(access_token=token, role=user.role)


@router.post("/org-login", response_model=TokenResponse)
def org_login(req: OrgLoginRequest, db: Session = Depends(get_db)):
    """Real credential auth for Volunteer / Rescue Operator / Command Center Admin (Part 24).
    There is no public self-signup endpoint for these roles — provisioning organizational
    accounts (setting `hashed_password` + `role`) is an admin/ops task, intentionally not
    exposed here."""
    user = db.query(User).filter(User.name == req.username).first()
    if user is None or user.hashed_password is None or not verify_password(req.password, user.hashed_password):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials")

    token = create_access_token(user.id, user.role)
    return TokenResponse(access_token=token, role=user.role)
