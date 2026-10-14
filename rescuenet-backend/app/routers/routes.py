from typing import Literal, Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app import external, places_service, safe_route
from app.auth import get_current_user
from app.database import get_db
from app.models import User
from app.schemas import (
    HazardZoneResponse, RouteDestinationResponse, RouteHazardRef, RouteOptionResponse,
    RouteStepResponse, SafeRouteResponse,
)

router = APIRouter(prefix="/api/routes", tags=["routes"])

CAVEAT = (
    "Route follows OpenStreetMap roads and avoids only the hazard zones recorded in RescueNet. "
    "It cannot know about closures, flooding or damage nobody has reported — conditions change, "
    "so treat this as guidance, not a guarantee."
)


def _option(cand: safe_route.Candidate, origin_inside) -> RouteOptionResponse:
    return RouteOptionResponse(
        distance_m=round(cand.distance_m, 1),
        duration_s=round(cand.duration_s, 1),
        geometry=safe_route.geometry_json(cand),
        steps=[RouteStepResponse(**s) for s in cand.steps],
        exposure_m=round(cand.exposure_m, 1),
        risk="CLEAR" if safe_route.is_clear(cand) else "HAZARD",
        label=safe_route.risk_label(cand, origin_inside),
        crossed_hazards=[RouteHazardRef(id=z.id, type=z.type, message=z.message) for z in cand.crossed],
    )


@router.get("/safe", response_model=SafeRouteResponse)
async def safe_route_endpoint(
    from_lat: float = Query(ge=-90, le=90),
    from_lng: float = Query(ge=-180, le=180),
    to_lat: Optional[float] = Query(default=None, ge=-90, le=90),
    to_lng: Optional[float] = Query(default=None, ge=-180, le=180),
    # Display-only: lets the app show the real name of the place the user picked.
    to_name: Optional[str] = Query(default=None, max_length=120),
    to_category: Optional[str] = Query(default=None, max_length=40),
    mode: Literal["driving", "walking"] = "driving",
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """A road route from the caller to a safe area, avoiding recorded hazard zones. If no
    destination is given, the nearest available shelter/assembly point (or hospital) is used."""
    origin = (from_lat, from_lng)

    if (to_lat is None) != (to_lng is None):
        raise HTTPException(status_code=422, detail="Provide both to_lat and to_lng, or neither")

    if to_lat is not None and to_lng is not None:
        dest = (to_lat, to_lng)
        destination = RouteDestinationResponse(
            name=to_name or "Selected destination", category=to_category or "Destination",
            latitude=to_lat, longitude=to_lng, chosen_automatically=False,
        )
    else:
        place = await places_service.pick_safe_area(db, origin)
        if place is None:
            raise HTTPException(status_code=404, detail="No safe area found within 40 km of your location")
        dest = (place.latitude, place.longitude)
        destination = RouteDestinationResponse(
            name=place.name, category=place.category,
            latitude=place.latitude, longitude=place.longitude, chosen_automatically=True,
        )

    try:
        recommended, alternatives, zones, origin_inside = await safe_route.compute(db, origin, dest, mode)
    except external.NoRouteError:
        raise HTTPException(status_code=404, detail="No route found between your location and that destination")
    except external.RoutingError:
        raise HTTPException(status_code=502, detail="The routing service is unavailable right now. Try again shortly.")

    crossed_ids = {z.id for z in recommended.crossed}
    return SafeRouteResponse(
        mode=mode,
        origin_lat=from_lat,
        origin_lng=from_lng,
        destination=destination,
        recommended=_option(recommended, origin_inside),
        alternatives=[_option(a, origin_inside) for a in alternatives],
        hazard_zones=[
            HazardZoneResponse(
                id=z.id, type=z.type, message=z.message, latitude=z.lat, longitude=z.lng,
                radius_m=z.radius_m, color=z.color, crossed=z.id in crossed_ids,
            )
            for z in safe_route.zone_distance_filter(zones, origin, crossed_ids)
        ],
        caveat=CAVEAT,
    )
