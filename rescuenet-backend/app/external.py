"""The only module that talks to outside services (OpenStreetMap data + OSRM road routing).

Keeping every network call here means the rest of the backend is plain, testable logic and the
tests can swap these two functions for fakes. Both services are free community infrastructure
with fair-use limits, so: identify ourselves with a User-Agent, cache what we can, keep requests
small, and never let a failure here take down an endpoint — callers get None / RoutingError and
decide how to degrade.
"""
from __future__ import annotations

import logging
import time
from typing import Optional

import httpx

from app.config import settings
from app.geo import LatLng

logger = logging.getLogger("rescuenet.external")

USER_AGENT = "RescueNet/1.0 (emergency coordination app; contact via project repository)"

# --- OpenStreetMap places (Overpass API) ------------------------------------------------------

_OSM_CACHE_TTL_S = 600  # places don't move; 10 minutes keeps us far under Overpass fair-use limits
_OSM_CACHE_MAX = 200
_osm_cache: dict[tuple[float, float, int], tuple[float, list[dict]]] = {}


def _overpass_query(lat: float, lng: float, radius_m: int) -> str:
    around = f"(around:{radius_m},{lat},{lng})"
    # Pharmacies are deliberately excluded: in a dense city there are hundreds, and they'd crowd
    # the hospitals/police/fire stations out of a nearest-first list.
    return (
        "[out:json][timeout:20];("
        f'node{around}[amenity~"^(hospital|clinic|police|fire_station)$"];'
        f'way{around}[amenity~"^(hospital|clinic|police|fire_station)$"];'
        f"node{around}[emergency=assembly_point];"
        f'node{around}[amenity=social_facility]["social_facility"="shelter"];'
        f'way{around}[amenity=social_facility]["social_facility"="shelter"];'
        ");out center 400;"
    )


async def fetch_osm_places(lat: float, lng: float, radius_m: int) -> Optional[list[dict]]:
    """Raw OpenStreetMap elements around a point, or None if the service is unavailable."""
    key = (round(lat, 2), round(lng, 2), radius_m)
    cached = _osm_cache.get(key)
    if cached and time.monotonic() - cached[0] < _OSM_CACHE_TTL_S:
        return cached[1]

    try:
        async with httpx.AsyncClient(timeout=settings.external_timeout_seconds) as client:
            resp = await client.post(
                settings.overpass_url,
                data={"data": _overpass_query(lat, lng, radius_m)},
                headers={"User-Agent": USER_AGENT},
            )
            resp.raise_for_status()
            elements = resp.json().get("elements", [])
    except Exception as exc:  # network error, timeout, 429/504 from Overpass, bad JSON...
        logger.warning("Overpass lookup failed: %s", exc)
        return None

    if len(_osm_cache) >= _OSM_CACHE_MAX:
        _osm_cache.pop(next(iter(_osm_cache)))  # evict the oldest entry
    _osm_cache[key] = (time.monotonic(), elements)
    return elements


# --- Road routing (OSRM) ----------------------------------------------------------------------

class RoutingError(Exception):
    """The routing service was unreachable or returned something unusable."""


class NoRouteError(RoutingError):
    """The routing service worked but there is no road/path between the points."""


def _describe_step(maneuver_type: str, modifier: str, name: str) -> str:
    road = f" onto {name}" if name else ""
    on_road = f" on {name}" if name else ""
    if maneuver_type == "depart":
        return f"Head out{on_road}"
    if maneuver_type == "arrive":
        return "You have arrived at your destination"
    if maneuver_type == "turn":
        return f"Turn {modifier}{road}" if modifier else f"Continue{road}"
    if maneuver_type in ("new name", "continue"):
        return f"Continue{on_road}" if modifier in ("", "straight") else f"Bear {modifier}{road}"
    if maneuver_type == "merge":
        return f"Merge {modifier}{road}".replace("  ", " ")
    if maneuver_type == "on ramp":
        return f"Take the ramp {modifier}{road}".replace("  ", " ")
    if maneuver_type == "off ramp":
        return f"Take the exit {modifier}{road}".replace("  ", " ")
    if maneuver_type == "fork":
        return f"Keep {modifier} at the fork{road}".replace("  ", " ")
    if maneuver_type == "end of road":
        return f"At the end of the road, turn {modifier}{road}".replace("  ", " ")
    if maneuver_type in ("roundabout", "rotary", "roundabout turn"):
        return f"At the roundabout, take the exit{road}"
    if maneuver_type in ("exit roundabout", "exit rotary"):
        return f"Exit the roundabout{road}"
    return f"Continue{road}"


def _parse_osrm_route(route: dict) -> dict:
    path: list[LatLng] = [(lat, lng) for lng, lat in route["geometry"]["coordinates"]]
    steps: list[dict] = []
    for leg in route.get("legs", []):
        for step in leg.get("steps", []):
            maneuver = step.get("maneuver", {})
            steps.append({
                "instruction": _describe_step(
                    maneuver.get("type", ""), maneuver.get("modifier", ""), step.get("name", "")
                ),
                "distance_m": float(step.get("distance", 0.0)),
                "duration_s": float(step.get("duration", 0.0)),
            })
    return {
        "path": path,
        "distance_m": float(route.get("distance", 0.0)),
        "duration_s": float(route.get("duration", 0.0)),
        "steps": steps,
    }


async def fetch_routes(mode: str, waypoints: list[LatLng], alternatives: bool) -> list[dict]:
    """Routes through the given (lat, lng) waypoints, best first. Raises RoutingError/NoRouteError."""
    base = settings.routing_walking_url if mode == "walking" else settings.routing_driving_url
    coords = ";".join(f"{lng:.6f},{lat:.6f}" for lat, lng in waypoints)  # OSRM wants lon,lat
    params = {
        "overview": "full",
        "geometries": "geojson",
        "steps": "true",
        "alternatives": "true" if alternatives else "false",
    }
    try:
        async with httpx.AsyncClient(timeout=settings.external_timeout_seconds) as client:
            resp = await client.get(f"{base}/{coords}", params=params, headers={"User-Agent": USER_AGENT})
        body = resp.json()
    except Exception as exc:
        logger.warning("Routing request failed: %s", exc)
        raise RoutingError("Routing service unavailable") from exc

    code = body.get("code")
    if code == "NoRoute":
        raise NoRouteError("No route found between these points")
    if code != "Ok" or not body.get("routes"):
        logger.warning("Routing service returned %s: %s", code, body.get("message"))
        raise RoutingError(f"Routing service error: {code}")
    return [_parse_osrm_route(r) for r in body["routes"]]
