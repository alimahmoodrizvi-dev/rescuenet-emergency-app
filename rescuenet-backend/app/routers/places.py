from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app import places_service
from app.auth import get_current_user
from app.database import get_db
from app.models import Hospital, Shelter, User
from app.schemas import HospitalResponse, NearbyPlaceResponse, NearbyPlacesResponse, ShelterResponse

router = APIRouter(prefix="/api", tags=["places"])


@router.get("/shelters", response_model=list[ShelterResponse])
def list_shelters(db: Session = Depends(get_db)):
    return db.query(Shelter).all()


@router.get("/hospitals", response_model=list[HospitalResponse])
def list_hospitals(db: Session = Depends(get_db)):
    return db.query(Hospital).all()


@router.get("/places/nearby", response_model=NearbyPlacesResponse)
async def nearby_places(
    lat: float = Query(ge=-90, le=90),
    lng: float = Query(ge=-180, le=180),
    radius_km: float = Query(default=10.0, gt=0, le=50),
    limit: int = Query(default=40, ge=1, le=100),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Help near a real location, nearest first: coordinator-entered shelters/hospitals plus
    OpenStreetMap hospitals, clinics, police, fire stations and assembly points."""
    places, osm_ok = await places_service.find_nearby(db, (lat, lng), int(radius_km * 1000))
    return NearbyPlacesResponse(
        origin_lat=lat, origin_lng=lng, radius_km=radius_km, osm_available=osm_ok,
        places=[
            NearbyPlaceResponse(
                id=p.id, name=p.name, category=p.category, latitude=p.latitude, longitude=p.longitude,
                distance_km=round(p.distance_m / 1000.0, 2), source=p.source, phone=p.phone, detail=p.detail,
            )
            for p in places[:limit]
        ],
    )
