"""Small, dependency-free geometry helpers for Nearby Help and Safe Route.

Everything here works on plain (lat, lng) tuples in degrees and distances in metres. The
distances involved (a city, at most a few tens of km) are small enough that the haversine
formula and straight-segment sampling are accurate to well within what a phone GPS gives.
"""
from __future__ import annotations

import math

EARTH_RADIUS_M = 6_371_000.0

LatLng = tuple[float, float]


def haversine_m(a: LatLng, b: LatLng) -> float:
    lat1, lng1, lat2, lng2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    dlat, dlng = lat2 - lat1, lng2 - lng1
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlng / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(min(1.0, math.sqrt(h)))


def bearing_deg(a: LatLng, b: LatLng) -> float:
    lat1, lat2 = math.radians(a[0]), math.radians(b[0])
    dlng = math.radians(b[1] - a[1])
    y = math.sin(dlng) * math.cos(lat2)
    x = math.cos(lat1) * math.sin(lat2) - math.sin(lat1) * math.cos(lat2) * math.cos(dlng)
    return (math.degrees(math.atan2(y, x)) + 360) % 360


def offset_point(origin: LatLng, bearing: float, distance_m: float) -> LatLng:
    """The point `distance_m` away from `origin` along `bearing` (degrees clockwise from north)."""
    lat1, lng1 = math.radians(origin[0]), math.radians(origin[1])
    brg = math.radians(bearing)
    ang = distance_m / EARTH_RADIUS_M
    lat2 = math.asin(math.sin(lat1) * math.cos(ang) + math.cos(lat1) * math.sin(ang) * math.cos(brg))
    lng2 = lng1 + math.atan2(
        math.sin(brg) * math.sin(ang) * math.cos(lat1),
        math.cos(ang) - math.sin(lat1) * math.sin(lat2),
    )
    return math.degrees(lat2), (math.degrees(lng2) + 540) % 360 - 180


def path_length_m(path: list[LatLng]) -> float:
    return sum(haversine_m(path[i], path[i + 1]) for i in range(len(path) - 1))


def metres_inside_circle(path: list[LatLng], center: LatLng, radius_m: float, step_m: float = 25.0) -> float:
    """Approximate length of `path` that lies inside a circle, by sampling each segment's
    sub-pieces at their midpoints. `step_m` is the sampling resolution — 25 m is far finer
    than any hazard zone an operator would draw by hand (radius slider starts at 100 m)."""
    inside = 0.0
    for i in range(len(path) - 1):
        a, b = path[i], path[i + 1]
        seg = haversine_m(a, b)
        if seg == 0:
            continue
        pieces = max(1, math.ceil(seg / step_m))
        for k in range(pieces):
            t = (k + 0.5) / pieces
            mid = (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
            if haversine_m(mid, center) <= radius_m:
                inside += seg / pieces
    return inside


def decimate(path: list[LatLng], max_points: int) -> list[LatLng]:
    """Evenly thin a very long polyline down to at most `max_points`, always keeping both ends,
    so the JSON sent to the phone stays small without visibly changing the line."""
    if len(path) <= max_points:
        return path
    step = (len(path) - 1) / (max_points - 1)
    picked = [path[round(i * step)] for i in range(max_points - 1)]
    picked.append(path[-1])
    return picked
