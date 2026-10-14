"""Finds help near a point by combining two sources:

 1. RescueNet's own shelters and hospitals (entered by coordinators — authoritative, and carry
    live details like shelter occupancy).
 2. OpenStreetMap (hospitals, clinics, police, fire stations, assembly points) — so the list is
    real wherever the user actually is, not just where coordinators have entered data.

Results are always sorted nearest-first by real distance from the caller's coordinates.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

from sqlalchemy.orm import Session

from app import external
from app.geo import LatLng, haversine_m
from app.models import Hospital, Shelter

# Anything within this distance of a RescueNet entry of the same category is treated as the
# same place, so a hospital present in both sources isn't listed twice.
_DUPLICATE_RADIUS_M = 150

_OSM_AMENITY_CATEGORY = {
    "hospital": "Hospital",
    "clinic": "Clinic",
    "police": "Police",
    "fire_station": "Fire",
}
_DEFAULT_NAME = {
    "Hospital": "Hospital", "Clinic": "Clinic", "Police": "Police station",
    "Fire": "Fire station", "Assembly Point": "Emergency assembly point", "Shelter": "Shelter",
}
# Categories that make sense as a "safe area" to head for (used by Safe Route).
SAFE_AREA_CATEGORIES = ("Shelter", "Assembly Point")


@dataclass
class Place:
    id: str
    name: str
    category: str
    latitude: float
    longitude: float
    distance_m: float
    source: str  # "RescueNet" | "OpenStreetMap"
    phone: Optional[str] = None
    detail: Optional[str] = None
    available: bool = True  # False for e.g. a full shelter — never picked as a safe destination


def _osm_place(element: dict, origin: LatLng) -> Optional[Place]:
    tags = element.get("tags", {})
    if "lat" in element and "lon" in element:
        lat, lng = element["lat"], element["lon"]
    elif "center" in element:
        lat, lng = element["center"]["lat"], element["center"]["lon"]
    else:
        return None

    if tags.get("emergency") == "assembly_point":
        category = "Assembly Point"
    elif tags.get("amenity") == "social_facility" and tags.get("social_facility") == "shelter":
        category = "Shelter"
    else:
        category = _OSM_AMENITY_CATEGORY.get(tags.get("amenity", ""))
    if category is None:
        return None

    name = tags.get("name") or tags.get("name:en") or _DEFAULT_NAME[category]
    return Place(
        id=f"osm-{element.get('type', 'node')}-{element.get('id')}",
        name=name, category=category, latitude=lat, longitude=lng,
        distance_m=haversine_m(origin, (lat, lng)), source="OpenStreetMap",
        phone=tags.get("phone") or tags.get("contact:phone"),
    )


def _rescuenet_places(db: Session, origin: LatLng, radius_m: float) -> list[Place]:
    places: list[Place] = []
    for s in db.query(Shelter).all():
        dist = haversine_m(origin, (s.latitude, s.longitude))
        if dist > radius_m:
            continue
        capacity, occupied = s.capacity or 0, s.current_occupancy or 0
        is_open = (s.status or "OPEN").upper() == "OPEN"
        full = capacity > 0 and occupied >= capacity
        detail = f"{occupied}/{capacity} occupied" if capacity > 0 else "Capacity not recorded"
        if not is_open:
            detail += " · currently closed"
        elif full:
            detail += " · full"
        places.append(Place(
            id=f"shelter-{s.id}", name=s.name, category="Shelter", latitude=s.latitude,
            longitude=s.longitude, distance_m=dist, source="RescueNet", detail=detail,
            available=is_open and not full,
        ))
    for h in db.query(Hospital).all():
        dist = haversine_m(origin, (h.latitude, h.longitude))
        if dist > radius_m:
            continue
        places.append(Place(
            id=f"hospital-{h.id}", name=h.name, category="Hospital", latitude=h.latitude,
            longitude=h.longitude, distance_m=dist, source="RescueNet", phone=h.contact,
            detail=h.capabilities or None,
        ))
    return places


async def find_nearby(db: Session, origin: LatLng, radius_m: int) -> tuple[list[Place], bool]:
    """Nearby places within `radius_m`, nearest first, plus whether OpenStreetMap answered."""
    own = _rescuenet_places(db, origin, radius_m)
    elements = await external.fetch_osm_places(origin[0], origin[1], radius_m)
    osm_ok = elements is not None

    merged = list(own)
    for element in elements or []:
        place = _osm_place(element, origin)
        if place is None or place.distance_m > radius_m:
            continue
        duplicate = any(
            o.category == place.category
            and haversine_m((o.latitude, o.longitude), (place.latitude, place.longitude)) <= _DUPLICATE_RADIUS_M
            for o in own
        )
        if not duplicate:
            merged.append(place)

    merged.sort(key=lambda p: p.distance_m)
    return merged, osm_ok


async def pick_safe_area(db: Session, origin: LatLng) -> Optional[Place]:
    """Nearest available shelter / assembly point; if there is none, the nearest hospital.
    Widens the search in steps so a user in a sparsely-mapped area still gets an answer."""
    for radius_m in (5_000, 15_000, 40_000):
        places, _ = await find_nearby(db, origin, radius_m)
        for place in places:
            if place.category in SAFE_AREA_CATEGORIES and place.available:
                return place
        for place in places:
            if place.category == "Hospital":
                return place
    return None
