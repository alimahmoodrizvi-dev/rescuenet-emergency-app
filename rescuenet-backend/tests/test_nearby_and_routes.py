"""Nearby Help + Safe Route. The two public services (OpenStreetMap, OSRM) are replaced with
fakes, so these tests check RescueNet's own logic: ordering, de-duplication, hazard avoidance,
detours, and graceful failure — not the third-party services themselves."""
import asyncio
import json
import uuid
from datetime import datetime, timedelta, timezone

import httpx
import pytest

from app import external
from app.database import SessionLocal
from app.geo import decimate, haversine_m, metres_inside_circle, offset_point, path_length_m
from app.models import Alert, AlertSeverity, HazardType, Hospital, Shelter

ORIGIN = (24.8607, 67.0300)
DEST = (24.8607, 67.0800)  # ~5 km due east of ORIGIN


def _token(client) -> dict:
    resp = client.post("/api/auth/device-register", json={"device_uuid": str(uuid.uuid4())})
    assert resp.status_code == 200
    return {"Authorization": f"Bearer {resp.json()['access_token']}"}


def _naive_now():
    return datetime.now(timezone.utc).replace(tzinfo=None)


@pytest.fixture(autouse=True)
def clean_tables(client):
    """Each test starts with no shelters/hospitals/alerts so results are fully predictable."""
    def wipe():
        db = SessionLocal()
        for model in (Alert, Shelter, Hospital, HazardType):
            db.query(model).delete()
        db.commit()
        db.close()
    wipe()
    yield
    wipe()
    # Put back the demo shelters/hospitals/hazard types the app seeds at startup, so tests in
    # other files that rely on that seed data aren't affected by this file running first.
    from app.main import _seed_default_hazard_types, _seed_demo_reference_data
    _seed_demo_reference_data()
    _seed_default_hazard_types()


def _add(*rows):
    db = SessionLocal()
    db.add_all(rows)
    db.commit()
    db.close()


def _zone_alert(lat, lng, radius_km, type_="FIRE_WARNING", expires_at=None, area=None):
    return Alert(
        type=type_, severity=AlertSeverity.CRITICAL, message=f"{type_} test zone", is_demo=True,
        area_geojson=area if area is not None else json.dumps({"lat": lat, "lng": lng, "radius_km": radius_km}),
        expires_at=expires_at,
    )


def _interpolate(points, per_segment=60):
    path = []
    for a, b in zip(points, points[1:]):
        for i in range(per_segment):
            t = i / per_segment
            path.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
    path.append(points[-1])
    return path


class FakeRouter:
    """Stands in for OSRM: straight lines between waypoints, 10 m/s. Optionally also offers a
    second 'alternative' route that bows through `alt_via` when alternatives are requested."""

    def __init__(self, alt_via=None, fail=None):
        self.alt_via, self.fail, self.calls = alt_via, fail, []

    def _route(self, points):
        path = _interpolate(points)
        dist = path_length_m(path)
        return {
            "path": path, "distance_m": dist, "duration_s": dist / 10.0,
            "steps": [
                {"instruction": "Head out on Main Road", "distance_m": dist, "duration_s": dist / 10.0},
                {"instruction": "You have arrived at your destination", "distance_m": 0.0, "duration_s": 0.0},
            ],
        }

    async def __call__(self, mode, waypoints, alternatives):
        self.calls.append({"mode": mode, "waypoints": list(waypoints), "alternatives": alternatives})
        if self.fail:
            raise self.fail
        routes = [self._route(waypoints)]
        if alternatives and self.alt_via:
            routes.append(self._route([waypoints[0], self.alt_via, waypoints[-1]]))
        return routes


def _use(monkeypatch, router=None, osm="unset"):
    if router is not None:
        monkeypatch.setattr(external, "fetch_routes", router)
    if osm != "unset":
        async def fake_osm(lat, lng, radius_m):
            return osm
        monkeypatch.setattr(external, "fetch_osm_places", fake_osm)


def _route(client, headers, **params):
    base = {"from_lat": ORIGIN[0], "from_lng": ORIGIN[1], "to_lat": DEST[0], "to_lng": DEST[1]}
    base.update(params)
    return client.get("/api/routes/safe", params={k: v for k, v in base.items() if v is not None}, headers=headers)


# ----------------------------------------------------------------------------- geometry

def test_haversine_one_degree_of_latitude():
    assert haversine_m((0, 0), (1, 0)) == pytest.approx(111_195, rel=0.002)


def test_metres_inside_circle_straight_through_centre():
    path = _interpolate([(24.8607, 67.040), (24.8607, 67.070)])
    inside = metres_inside_circle(path, (24.8607, 67.055), 1000)
    assert inside == pytest.approx(2000, rel=0.05)
    assert metres_inside_circle(path, (24.95, 67.055), 1000) == 0


def test_offset_point_round_trips_distance():
    p = offset_point(ORIGIN, 90, 2500)
    assert haversine_m(ORIGIN, p) == pytest.approx(2500, rel=0.001)


def test_decimate_keeps_both_ends_and_limit():
    path = [(i * 0.0001, 0.0) for i in range(5000)]
    out = decimate(path, 100)
    assert len(out) == 100 and out[0] == path[0] and out[-1] == path[-1]
    assert decimate(path[:50], 100) == path[:50]


# ----------------------------------------------------------------------------- nearby

def _osm_node(id_, lat, lng, **tags):
    return {"type": "node", "id": id_, "lat": lat, "lon": lng, "tags": tags}


def test_nearby_requires_auth(client):
    assert client.get("/api/places/nearby", params={"lat": 1, "lng": 1}).status_code == 401


def test_nearby_is_sorted_by_real_distance_and_merges_sources(client, monkeypatch):
    _add(
        Shelter(name="Community Hall Shelter", latitude=24.8650, longitude=67.0300, capacity=100,
                current_occupancy=40, status="OPEN"),
        Shelter(name="Far Away Shelter", latitude=33.7, longitude=73.0, capacity=50, current_occupancy=0, status="OPEN"),
        Hospital(name="Civil Hospital", latitude=24.8700, longitude=67.0300, capabilities="Trauma", contact="021-111"),
    )
    elements = [
        _osm_node(1, 24.8615, 67.0300, amenity="police", name="Saddar Police Station"),
        # Same hospital as the RescueNet entry (~20 m apart) — must not be listed twice.
        _osm_node(2, 24.8701, 67.0301, amenity="hospital", name="Civil Hospital Karachi"),
        {"type": "way", "id": 3, "center": {"lat": 24.8680, "lon": 67.0300},
         "tags": {"amenity": "fire_station"}},
        _osm_node(4, 24.8620, 67.0310, emergency="assembly_point", name="Park Assembly Point"),
        _osm_node(5, 24.8610, 67.0300, amenity="bench"),         # not a help category — ignored
        _osm_node(6, 25.5, 67.5, amenity="hospital", name="Too Far Hospital"),  # outside radius
    ]
    _use(monkeypatch, osm=elements)

    resp = client.get("/api/places/nearby", params={"lat": ORIGIN[0], "lng": ORIGIN[1], "radius_km": 10},
                      headers=_token(client))
    assert resp.status_code == 200
    body = resp.json()
    assert body["osm_available"] is True

    names = [p["name"] for p in body["places"]]
    assert names == ["Saddar Police Station", "Park Assembly Point", "Community Hall Shelter",
                     "Fire station", "Civil Hospital"]
    dists = [p["distance_km"] for p in body["places"]]
    assert dists == sorted(dists)
    assert "Far Away Shelter" not in names and "Too Far Hospital" not in names

    by_name = {p["name"]: p for p in body["places"]}
    assert by_name["Community Hall Shelter"]["detail"] == "40/100 occupied"
    assert by_name["Community Hall Shelter"]["source"] == "RescueNet"
    assert by_name["Civil Hospital"]["phone"] == "021-111"
    assert by_name["Saddar Police Station"]["category"] == "Police"
    assert by_name["Fire station"]["category"] == "Fire"  # unnamed OSM place gets a sensible default


def test_nearby_still_works_when_openstreetmap_is_down(client, monkeypatch):
    _add(Shelter(name="Community Hall Shelter", latitude=24.8650, longitude=67.0300, capacity=0,
                 current_occupancy=0, status="OPEN"))
    _use(monkeypatch, osm=None)
    body = client.get("/api/places/nearby", params={"lat": ORIGIN[0], "lng": ORIGIN[1]},
                      headers=_token(client)).json()
    assert body["osm_available"] is False
    assert [p["name"] for p in body["places"]] == ["Community Hall Shelter"]
    assert body["places"][0]["detail"] == "Capacity not recorded"


def test_nearby_validates_inputs(client):
    h = _token(client)
    assert client.get("/api/places/nearby", params={"lat": 123, "lng": 0}, headers=h).status_code == 422
    assert client.get("/api/places/nearby", params={"lat": 0, "lng": 0, "radius_km": 500}, headers=h).status_code == 422


# ----------------------------------------------------------------------------- safe route

def test_route_without_hazards_returns_clear_route_with_geometry_and_steps(client, monkeypatch):
    router = FakeRouter()
    _use(monkeypatch, router=router)
    resp = _route(client, _token(client))
    assert resp.status_code == 200
    body = resp.json()
    rec = body["recommended"]
    assert rec["risk"] == "CLEAR" and rec["exposure_m"] == 0
    assert rec["label"] == "No known hazard on this route"
    assert rec["geometry"][0] == pytest.approx(list(ORIGIN), abs=1e-4)
    assert rec["geometry"][-1] == pytest.approx(list(DEST), abs=1e-4)
    assert rec["distance_m"] == pytest.approx(5_045, rel=0.02)  # 0.05 deg of longitude at this latitude
    assert [s["instruction"] for s in rec["steps"]][-1] == "You have arrived at your destination"
    assert body["destination"]["chosen_automatically"] is False
    assert body["mode"] == "driving" and "guidance, not a guarantee" in body["caveat"]
    assert router.calls[0]["waypoints"] == [ORIGIN, DEST]


def test_explicit_destination_keeps_the_name_the_app_gave_it(client, monkeypatch):
    _use(monkeypatch, router=FakeRouter())
    body = _route(client, _token(client), to_name="Civil Hospital", to_category="Hospital").json()
    assert body["destination"]["name"] == "Civil Hospital"
    assert body["destination"]["category"] == "Hospital"
    assert body["destination"]["chosen_automatically"] is False


def test_route_prefers_the_alternative_that_avoids_a_hazard(client, monkeypatch):
    _add(_zone_alert(24.8607, 67.055, 0.8))  # sits right on the direct line
    router = FakeRouter(alt_via=(24.9000, 67.055))  # bows ~4.4 km north of the hazard
    _use(monkeypatch, router=router)

    body = _route(client, _token(client)).json()
    rec = body["recommended"]
    assert rec["risk"] == "CLEAR"
    assert max(p[0] for p in rec["geometry"]) > 24.89  # it really is the northern route
    assert [z["crossed"] for z in body["hazard_zones"]] == [False]
    # The faster, hazard-crossing direct route is still offered, honestly labelled.
    assert body["alternatives"][0]["risk"] == "HAZARD"
    assert "Passes through 1 hazard zone" in body["alternatives"][0]["label"]
    assert len(router.calls) == 1  # a clear alternative existed, so no detour was needed


def test_route_asks_for_a_detour_when_every_offered_route_crosses_the_hazard(client, monkeypatch):
    _add(_zone_alert(24.8607, 67.055, 0.8))
    router = FakeRouter()  # offers only the direct route, which crosses the hazard
    _use(monkeypatch, router=router)

    body = _route(client, _token(client)).json()
    assert any(len(c["waypoints"]) == 3 for c in router.calls), "a via-waypoint detour should be requested"
    rec = body["recommended"]
    assert rec["risk"] == "CLEAR"
    assert max(abs(p[0] - ORIGIN[0]) for p in rec["geometry"]) > 0.01  # went around, not through
    assert rec["duration_s"] > 5_045 / 10  # and the detour costs some extra time


def test_route_through_an_unavoidable_hazard_is_flagged_not_hidden(client, monkeypatch):
    _add(_zone_alert(DEST[0], DEST[1], 1.5))  # the destination itself is inside the hazard
    _use(monkeypatch, router=FakeRouter())
    rec = _route(client, _token(client)).json()["recommended"]
    assert rec["risk"] == "HAZARD" and rec["exposure_m"] > 500
    assert "Passes through 1 hazard zone" in rec["label"]


def test_route_when_user_is_already_inside_a_hazard_says_so(client, monkeypatch):
    _add(_zone_alert(ORIGIN[0], ORIGIN[1], 1.0))
    _use(monkeypatch, router=FakeRouter())
    rec = _route(client, _token(client)).json()["recommended"]
    assert "you appear to be inside a hazard zone" in rec["label"]


def test_route_ignores_expired_non_hazard_and_unreadable_zones(client, monkeypatch):
    _add(
        _zone_alert(24.8607, 67.055, 0.8, expires_at=_naive_now() - timedelta(hours=1)),   # expired
        _zone_alert(24.8607, 67.055, 0.8, type_="SHELTER_OPENING"),                          # not a hazard
        _zone_alert(0, 0, 0, area="this is not json"),                                       # garbage
        _zone_alert(0, 0, 0, area=json.dumps({"lat": "x"})),                                 # incomplete
    )
    _use(monkeypatch, router=FakeRouter())
    body = _route(client, _token(client)).json()
    assert body["recommended"]["risk"] == "CLEAR" and body["hazard_zones"] == []


def test_custom_hazard_types_are_treated_as_hazards_and_use_their_colour(client, monkeypatch):
    _add(HazardType(name="Bomb blast", color="#222222"), _zone_alert(24.8607, 67.055, 0.8, type_="Bomb blast"))
    _use(monkeypatch, router=FakeRouter(alt_via=(24.9, 67.055)))
    body = _route(client, _token(client)).json()
    assert body["hazard_zones"][0]["type"] == "Bomb blast" and body["hazard_zones"][0]["color"] == "#222222"
    assert body["alternatives"][0]["crossed_hazards"][0]["type"] == "Bomb blast"


def test_route_picks_nearest_open_shelter_and_skips_full_or_closed_ones(client, monkeypatch):
    _add(
        Shelter(name="Full Shelter", latitude=24.8620, longitude=67.0300, capacity=50, current_occupancy=50, status="OPEN"),
        Shelter(name="Closed Shelter", latitude=24.8630, longitude=67.0300, capacity=50, current_occupancy=0, status="CLOSED"),
        Shelter(name="Open Shelter", latitude=24.8800, longitude=67.0300, capacity=50, current_occupancy=10, status="OPEN"),
    )
    _use(monkeypatch, router=FakeRouter(), osm=[])
    body = _route(client, _token(client), to_lat=None, to_lng=None).json()
    assert body["destination"]["name"] == "Open Shelter"
    assert body["destination"]["chosen_automatically"] is True


def test_route_falls_back_to_a_hospital_when_there_is_no_shelter(client, monkeypatch):
    _add(Hospital(name="Civil Hospital", latitude=24.8700, longitude=67.0300))
    _use(monkeypatch, router=FakeRouter(), osm=[])
    body = _route(client, _token(client), to_lat=None, to_lng=None).json()
    assert body["destination"]["name"] == "Civil Hospital" and body["destination"]["category"] == "Hospital"


def test_route_with_no_safe_area_anywhere_is_a_clean_404(client, monkeypatch):
    _use(monkeypatch, router=FakeRouter(), osm=[])
    resp = _route(client, _token(client), to_lat=None, to_lng=None)
    assert resp.status_code == 404 and "No safe area" in resp.json()["detail"]


def test_route_failure_modes(client, monkeypatch):
    h = _token(client)
    _use(monkeypatch, router=FakeRouter(fail=external.RoutingError("down")))
    assert _route(client, h).status_code == 502
    _use(monkeypatch, router=FakeRouter(fail=external.NoRouteError("none")))
    assert _route(client, h).status_code == 404
    assert _route(client, h, to_lat=24.9, to_lng=None).status_code == 422   # only half a destination
    assert _route(client, h, mode="flying").status_code == 422
    assert client.get("/api/routes/safe", params={"from_lat": 1, "from_lng": 1}).status_code == 401


def test_walking_mode_is_passed_through(client, monkeypatch):
    router = FakeRouter()
    _use(monkeypatch, router=router)
    assert _route(client, _token(client), mode="walking").json()["mode"] == "walking"
    assert router.calls[0]["mode"] == "walking"


def test_a_failed_detour_attempt_does_not_break_the_route(client, monkeypatch):
    _add(_zone_alert(24.8607, 67.055, 0.8))
    base = FakeRouter()

    async def flaky(mode, waypoints, alternatives):
        if len(waypoints) == 3:
            raise external.RoutingError("detour service hiccup")
        return await base(mode, waypoints, alternatives)

    _use(monkeypatch, router=flaky)
    resp = _route(client, _token(client))
    assert resp.status_code == 200 and resp.json()["recommended"]["risk"] == "HAZARD"


# ----------------------------------------------------------------------------- external clients

_REAL_ASYNC_CLIENT = httpx.AsyncClient  # captured once, so repeated mocking never stacks wrappers


def _mock_httpx(monkeypatch, handler):
    def factory(*args, **kwargs):
        kwargs["transport"] = httpx.MockTransport(handler)
        return _REAL_ASYNC_CLIENT(*args, **kwargs)

    monkeypatch.setattr(external.httpx, "AsyncClient", factory)


OSRM_OK = {
    "code": "Ok",
    "routes": [{
        "distance": 5200.5, "duration": 610.2,
        "geometry": {"type": "LineString", "coordinates": [[67.03, 24.86], [67.04, 24.87], [67.05, 24.88]]},
        "legs": [{"steps": [
            {"maneuver": {"type": "depart", "modifier": "right"}, "name": "Main Road", "distance": 300, "duration": 40},
            {"maneuver": {"type": "turn", "modifier": "slight left"}, "name": "Shahrah-e-Faisal", "distance": 4000, "duration": 400},
            {"maneuver": {"type": "arrive", "modifier": "right"}, "name": "", "distance": 0, "duration": 0},
        ]}],
    }],
}


def test_osrm_client_builds_lon_lat_url_and_parses_route(monkeypatch):
    seen = {}

    def handler(request: httpx.Request):
        seen["url"] = str(request.url)
        return httpx.Response(200, json=OSRM_OK)

    _mock_httpx(monkeypatch, handler)
    routes = asyncio.run(external.fetch_routes("driving", [(24.86, 67.03), (24.88, 67.05)], alternatives=True))
    assert "/67.030000,24.860000;67.050000,24.880000" in seen["url"]  # OSRM wants lon,lat
    assert "alternatives=true" in seen["url"] and "geometries=geojson" in seen["url"]
    assert routes[0]["path"][0] == (24.86, 67.03)  # we convert back to (lat, lng)
    assert routes[0]["distance_m"] == 5200.5
    assert [s["instruction"] for s in routes[0]["steps"]] == [
        "Head out on Main Road", "Turn slight left onto Shahrah-e-Faisal", "You have arrived at your destination",
    ]


def test_osrm_client_uses_the_walking_server_for_walking(monkeypatch):
    seen = {}
    _mock_httpx(monkeypatch, lambda r: (seen.update(url=str(r.url)), httpx.Response(200, json=OSRM_OK))[1])
    asyncio.run(external.fetch_routes("walking", [(24.86, 67.03), (24.88, 67.05)], alternatives=False))
    assert "routed-foot" in seen["url"] and "alternatives=false" in seen["url"]


def test_osrm_client_error_handling(monkeypatch):
    wp = [(24.86, 67.03), (24.88, 67.05)]
    _mock_httpx(monkeypatch, lambda r: httpx.Response(200, json={"code": "NoRoute"}))
    with pytest.raises(external.NoRouteError):
        asyncio.run(external.fetch_routes("driving", wp, True))
    _mock_httpx(monkeypatch, lambda r: httpx.Response(200, json={"code": "InvalidQuery", "message": "bad"}))
    with pytest.raises(external.RoutingError):
        asyncio.run(external.fetch_routes("driving", wp, True))
    _mock_httpx(monkeypatch, lambda r: httpx.Response(502, text="<html>bad gateway</html>"))
    with pytest.raises(external.RoutingError):
        asyncio.run(external.fetch_routes("driving", wp, True))


def test_overpass_client_queries_caches_and_degrades(monkeypatch):
    external._osm_cache.clear()
    hits = []

    def handler(request: httpx.Request):
        hits.append(request.content.decode())
        return httpx.Response(200, json={"elements": [{"type": "node", "id": 1, "lat": 1.0, "lon": 2.0, "tags": {}}]})

    _mock_httpx(monkeypatch, handler)
    first = asyncio.run(external.fetch_osm_places(24.8607, 67.03, 10_000))
    second = asyncio.run(external.fetch_osm_places(24.8611, 67.0304, 10_000))  # same ~1 km cell
    assert first == second and len(hits) == 1, "second lookup in the same area must come from cache"
    assert "around%3A10000%2C24.8607%2C67.03" in hits[0] or "around:10000,24.8607,67.03" in hits[0]
    assert "hospital" in hits[0] and "assembly_point" in hits[0]

    external._osm_cache.clear()
    _mock_httpx(monkeypatch, lambda r: httpx.Response(504, text="timeout"))
    assert asyncio.run(external.fetch_osm_places(10.0, 10.0, 5_000)) is None  # None, never an exception
