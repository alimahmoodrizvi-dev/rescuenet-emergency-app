from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.auth import require_roles
from app.database import get_db
from app.models import HazardType, User, UserRole
from app.schemas import HazardTypeCreateRequest, HazardTypeResponse, HazardTypeUpdateRequest
from app.websocket import manager

router = APIRouter(prefix="/api/hazard-types", tags=["hazard-types"])


@router.get("", response_model=list[HazardTypeResponse])
def list_hazard_types(db: Session = Depends(get_db)):
    return db.query(HazardType).order_by(HazardType.name).all()


@router.post("", response_model=HazardTypeResponse, status_code=status.HTTP_201_CREATED)
async def create_hazard_type(
    req: HazardTypeCreateRequest,
    current_user: User = Depends(require_roles(UserRole.RESCUE_OPERATOR, UserRole.COMMAND_CENTER_ADMIN)),
    db: Session = Depends(get_db),
):
    hazard_type = HazardType(name=req.name.strip(), color=req.color)
    db.add(hazard_type)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail="A hazard type with this name already exists")
    db.refresh(hazard_type)

    await manager.broadcast("hazard_type_created", HazardTypeResponse.model_validate(hazard_type).model_dump())
    return hazard_type


@router.patch("/{hazard_type_id}", response_model=HazardTypeResponse)
async def update_hazard_type(
    hazard_type_id: str,
    req: HazardTypeUpdateRequest,
    current_user: User = Depends(require_roles(UserRole.RESCUE_OPERATOR, UserRole.COMMAND_CENTER_ADMIN)),
    db: Session = Depends(get_db),
):
    hazard_type = db.get(HazardType, hazard_type_id)
    if hazard_type is None:
        raise HTTPException(status_code=404, detail="Hazard type not found")

    for field, value in req.model_dump(exclude_unset=True).items():
        setattr(hazard_type, field, value.strip() if field == "name" and value else value)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail="A hazard type with this name already exists")
    db.refresh(hazard_type)

    await manager.broadcast("hazard_type_updated", HazardTypeResponse.model_validate(hazard_type).model_dump())
    return hazard_type


@router.delete("/{hazard_type_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_hazard_type(
    hazard_type_id: str,
    current_user: User = Depends(require_roles(UserRole.RESCUE_OPERATOR, UserRole.COMMAND_CENTER_ADMIN)),
    db: Session = Depends(get_db),
):
    """Removing a hazard type only affects the picker list going forward — any existing
    Alert rows keep their (now catalog-less) type string and simply fall back to a default
    color on the map/badges instead of disappearing or erroring."""
    hazard_type = db.get(HazardType, hazard_type_id)
    if hazard_type is None:
        raise HTTPException(status_code=404, detail="Hazard type not found")
    db.delete(hazard_type)
    db.commit()
    await manager.broadcast("hazard_type_deleted", {"id": hazard_type_id})
