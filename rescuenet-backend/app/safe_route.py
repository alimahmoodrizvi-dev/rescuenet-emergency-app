"""Safe Route: a real road route from the user to a safe area that avoids hazard zones.

How "safe" works: coordinators mark affected areas (fire, flood, ...) as circles on the Command
Center map. Those are stored as alerts. For every candidate road route we measure how many
metres of it run inside an active hazard circle ("exposure"), then recommend the route with
the least exposure (ties go to the faster one). If *every* route the routing service offers
passes through a hazard, we ask it for a couple of detours via waypoints placed beside the
worst hazard, and judge those the same way.

What this does NOT know: road closures, flooding, or damage that nobody has marked as an
affected area. The response says so — it is guidance, not a guarantee.
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime, timezone

from sqlalchemy.orm import Session

from app import external
from app.geo import (
    LatLng, bearing_deg, decimate, haversine_m, metres_inside_circle, offset_point,
)
from app.models import Alert, HazardType

# Alert types that mark an area as *dangerous to travel through*. Others (a shelter opening, a
# missing-person notice) have an area but aren't hazards to route around. Custom hazard types
# an operator adds are treated as hazards by default.
NON_HAZARD_TYPES = {"SHELTER_OPENING", "MISSING_PERSON"}

DEFAULT_ZONE_COLOR = "#8a93a6"
# Under this many metres inside a zone we call a route clear — GPS/road-snapping noise.
EXPOSURE_NOISE_M = 30.0
MAX_GEOMETRY_POINTS = 2500


@dataclass
class HazardZone:
    id: str
    type: str
    message: str
    lat: float
    lng: float
    radius_m: float
    color: str


@dataclass
class Candidate:
    path: list[LatLng]
    distance_m: float
    duration_s: float
    steps: list[dict]
    exposure_m: float = 0.0
    crossed: list[HazardZone] = None  # type: ignore[assignment]

    def __post_init__(self) -> None:
        if self.crossed is None:
            self.crossed = []


def load_active_hazard_zones(db: Session) -> list[HazardZone]:
    now = datetime.now(timezone.utc).replace(tzinfo=None)
    colors = {t.name: t.color for t in db.query(HazardType).all()}
    zones: list[HazardZone] = []
    alerts = (
        db.query(Alert)
        .filter(Alert.area_geojson.isnot(None))
        .filter((Alert.expires_at.is_(None)) | (Alert.expires_at > now))
        .all()
    )
    for alert in alerts:
        if alert.type in NON_HAZARD_TYPES:
            continue
        try:
            area = json.loads(alert.area_geojson)
            lat, lng, radius_km = float(area["lat"]), float(area["lng"]), float(area["radius_km"])
        except (ValueError, KeyError, TypeError):
            continue  # an area shape we don't understand — skip rather than fail the route
        zones.append(HazardZone(
            id=alert.id, type=alert.type, message=alert.message, lat=lat, lng=lng,
            radius_m=radius_km * 1000.0, color=colors.get(alert.type, DEFAULT_ZONE_COLOR),
        ))
    return zones


def _evaluate(raw: dict, zones: list[HazardZone]) -> Candidate:
    cand = Candidate(
        path=raw["path"], distance_m=raw["distance_m"], duration_s=raw["duration_s"], steps=raw["steps"],
    )
    for zone in zones:
        inside = metres_inside_circle(cand.path, (zone.lat, zone.lng), zone.radius_m)
        if inside > 0:
            cand.exposure_m += inside
            if inside >= EXPOSURE_NOISE_M:
                cand.crossed.append(zone)
    return cand


def is_clear(cand: Candidate) -> bool:
    return cand.exposure_m < EXPOSURE_NOISE_M


def _rank_key(cand: Candidate) -> tuple[float, float]:
    # Exposure is bucketed to the nearest 50 m so a few metres of difference can't outrank a
    # much faster route; within a bucket, faster wins.
    return (round(cand.exposure_m / 50.0), cand.duration_s)


def _detour_waypoints(origin: LatLng, dest: LatLng, worst: HazardZone) -> list[LatLng]:
    """Two points either side of the worst hazard, clear of its edge, to route around it."""
    heading = bearing_deg(origin, dest)
    clearance = worst.radius_m * 1.3 + 200.0
    return [
        offset_point((worst.lat, worst.lng), (heading + 90) % 360, clearance),
        offset_point((worst.lat, worst.lng), (heading - 90) % 360, clearance),
    ]


async def compute(
    db: Session, origin: LatLng, dest: LatLng, mode: str,
) -> tuple[Candidate, list[Candidate], list[HazardZone], list[HazardZone]]:
    """Returns (recommended, alternatives, zones, zones_the_origin_is_inside).
    Raises external.RoutingError / external.NoRouteError."""
    zones = load_active_hazard_zones(db)
    candidates = [_evaluate(r, zones) for r in await external.fetch_routes(mode, [origin, dest], alternatives=True)]

    best = min(candidates, key=_rank_key)
    if not is_clear(best) and zones:
        worst = max(best.crossed or zones, key=lambda z: metres_inside_circle(best.path, (z.lat, z.lng), z.radius_m))
        for waypoint in _detour_waypoints(origin, dest, worst):
            if any(haversine_m(waypoint, (z.lat, z.lng)) <= z.radius_m for z in zones):
                continue  # this side is inside another hazard — pointless to route through it
            try:
                raw_routes = await external.fetch_routes(mode, [origin, waypoint, dest], alternatives=False)
            except external.RoutingError:
                continue  # a failed detour attempt must never break the route we already have
            candidates.extend(_evaluate(r, zones) for r in raw_routes)

    # Ignore absurd detours (more than 3x the fastest option and half an hour longer).
    fastest = min(c.duration_s for c in candidates)
    sensible = [c for c in candidates if c.duration_s <= max(3 * fastest, fastest + 1800)]
    ranked = sorted(sensible, key=_rank_key)

    recommended = ranked[0]
    alternatives: list[Candidate] = []
    for cand in ranked[1:]:
        if len(alternatives) >= 2:
            break
        # Skip near-duplicates (same distance and time to the nearest few percent).
        if all(
            abs(cand.distance_m - o.distance_m) > 0.03 * o.distance_m
            or abs(cand.duration_s - o.duration_s) > 0.03 * o.duration_s
            for o in [recommended, *alternatives]
        ):
            alternatives.append(cand)

    origin_inside = [z for z in zones if haversine_m(origin, (z.lat, z.lng)) <= z.radius_m]
    return recommended, alternatives, zones, origin_inside


def geometry_json(cand: Candidate) -> list[list[float]]:
    return [[round(lat, 5), round(lng, 5)] for lat, lng in decimate(cand.path, MAX_GEOMETRY_POINTS)]


def risk_label(cand: Candidate, origin_inside: list[HazardZone]) -> str:
    if is_clear(cand):
        base = "No known hazard on this route"
    else:
        names = ", ".join(sorted({z.type.replace("_", " ").title() for z in cand.crossed})) or "a hazard zone"
        count = len(cand.crossed)
        base = f"Passes through {count} hazard zone{'s' if count != 1 else ''} ({names})"
    if origin_inside:
        base += " — you appear to be inside a hazard zone now, so leave it by the quickest way"
    return base


def zone_distance_filter(zones: list[HazardZone], origin: LatLng, crossed_ids: set[str], within_m: float = 40_000) -> list[HazardZone]:
    """Zones worth drawing: ones near the user or that the recommended route crosses."""
    keep = [z for z in zones if z.id in crossed_ids or haversine_m(origin, (z.lat, z.lng)) <= within_m + z.radius_m]
    return keep[:50]
