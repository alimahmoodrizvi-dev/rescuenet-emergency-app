"""
Authentication (Part 19/24). Two paths, deliberately asymmetric:

  - Citizens: anonymous device-bound auth. POST /api/auth/device-register with a
    locally-generated device_uuid (the same one Room stores — see UserRepository.kt on the
    Android side) returns a JWT scoped to role=CITIZEN. No phone number or password required,
    matching the Part 19 principle that core emergency features never require a real-name
    account.
  - Volunteer / Rescue Operator / Command Center Admin: real username+password auth, because
    these roles can assign resources, broadcast alerts, and see cross-citizen data — Part 24
    requires that to be gated behind actual credentials, not just possession of a phone.

Both paths end in the same kind of JWT so downstream endpoints don't need to know which path
was used — they just check `role`.
"""
from datetime import datetime, timedelta, timezone
from typing import Optional

import bcrypt
from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jose import JWTError, jwt
from sqlalchemy.orm import Session

from app.config import settings
from app.database import get_db
from app.models import User, UserRole

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/auth/org-login", auto_error=False)


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(plain: str, hashed: str) -> bool:
    return bcrypt.checkpw(plain.encode("utf-8"), hashed.encode("utf-8"))


def create_access_token(user_id: str, role: UserRole) -> str:
    expire = datetime.now(timezone.utc).replace(tzinfo=None) + timedelta(minutes=settings.jwt_expire_minutes)
    payload = {"sub": user_id, "role": role.value, "exp": expire}
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)


def decode_token(token: str) -> dict:
    try:
        return jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
    except JWTError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or expired token")


def get_current_user(
    token: Optional[str] = Depends(oauth2_scheme),
    db: Session = Depends(get_db),
) -> User:
    if token is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Not authenticated")
    payload = decode_token(token)
    user = db.get(User, payload.get("sub"))
    if user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User no longer exists")
    return user


def require_roles(*allowed: UserRole):
    """Dependency factory for role-gated endpoints (Part 24). Usage:
    `current_user: User = Depends(require_roles(UserRole.COMMAND_CENTER_ADMIN))`"""

    def _check(user: User = Depends(get_current_user)) -> User:
        if user.role not in allowed:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Requires one of roles: {[r.value for r in allowed]}",
            )
        return user

    return _check
