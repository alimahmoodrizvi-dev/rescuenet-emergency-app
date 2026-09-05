from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import Hospital, Shelter
from app.schemas import HospitalResponse, ShelterResponse

router = APIRouter(prefix="/api", tags=["places"])


@router.get("/shelters", response_model=list[ShelterResponse])
def list_shelters(db: Session = Depends(get_db)):
    return db.query(Shelter).all()


@router.get("/hospitals", response_model=list[HospitalResponse])
def list_hospitals(db: Session = Depends(get_db)):
    return db.query(Hospital).all()
