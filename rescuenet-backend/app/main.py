from contextlib import asynccontextmanager

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.database import Base, SessionLocal, engine
from app.models import Hospital, Resource, ResourceStatus, ResourceType, Shelter
from app.routers import ai, alerts, auth, dashboard, incidents, places, resources, safety, sync
from app.websocket import manager

@asynccontextmanager
async def lifespan(app: FastAPI):
    Base.metadata.create_all(bind=engine)
    _seed_demo_reference_data()
    yield


app = FastAPI(
    title="RescueNet API",
    description=(
        "Backend for RescueNet — an offline-first emergency communication and coordination "
        "platform. Implements the Part 10 API surface from the Phase 1 architecture doc. "
        "See README.md for how demo/simulated data is labeled throughout."
    ),
    version="0.5.0-phase5-backend",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_allow_origins.split(","),
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(incidents.router)
app.include_router(safety.router)
app.include_router(resources.router)
app.include_router(places.router)
app.include_router(ai.router)
app.include_router(alerts.router)
app.include_router(sync.router)
app.include_router(dashboard.router)


@app.get("/")
def root():
    return {"service": "RescueNet API", "status": "ok"}


@app.get("/health")
def health():
    return {"status": "healthy"}


@app.websocket("/ws/incidents")
async def ws_incidents(websocket: WebSocket):
    """Part 10/23 — live push channel for the Command Center. On connect, no history is
    replayed; clients call GET /api/incidents first for the current snapshot, then rely on
    this socket for live deltas (created/updated incidents, resource assignments, alerts)."""
    await manager.connect(websocket)
    try:
        while True:
            # This endpoint is push-only from the server's side; we still need to await
            # something so a client disconnect is detected promptly.
            await websocket.receive_text()
    except WebSocketDisconnect:
        manager.disconnect(websocket)


def _seed_demo_reference_data() -> None:
    """Seeds a small set of Karachi-area shelters/hospitals/resources so the Command Center
    and the mobile app's Nearby Help / Safe Route screens have something real to query
    against out of the box. This is reference/location data (not fabricated incidents or
    alerts) — still worth flagging plainly per Part 36: coordinates are illustrative, not
    verified real-world facility data, and should be replaced with a real GIS/facility feed
    before any real deployment."""
    db = SessionLocal()
    try:
        if db.query(Hospital).count() > 0:
            return  # already seeded

        db.add_all([
            Hospital(name="Civil Hospital Karachi", latitude=24.8615, longitude=67.0099,
                      capabilities="Trauma,ICU,Emergency", contact="+92-21-99215740"),
            Hospital(name="Jinnah Postgraduate Medical Centre", latitude=24.8564, longitude=67.0378,
                      capabilities="Trauma,ICU,Burns", contact="+92-21-99201300"),
        ])
        db.add_all([
            Shelter(name="Government Shelter #3 (Saddar)", latitude=24.8546, longitude=67.0207, capacity=300, current_occupancy=0),
            Shelter(name="Community Center Shelter (Korangi)", latitude=24.8321, longitude=67.1206, capacity=150, current_occupancy=0),
        ])
        db.add_all([
            Resource(name="Ambulance 12", type=ResourceType.AMBULANCE, status=ResourceStatus.AVAILABLE,
                      latitude=24.8600, longitude=67.0150, capacity=2, capabilities="Basic life support"),
            Resource(name="Rescue Team 3", type=ResourceType.RESCUE_TEAM, status=ResourceStatus.AVAILABLE,
                      latitude=24.8700, longitude=67.0350, capacity=6, capabilities="Flood rescue,Medical support"),
            Resource(name="Rescue Team 4", type=ResourceType.RESCUE_TEAM, status=ResourceStatus.AVAILABLE,
                      latitude=24.8400, longitude=67.0600, capacity=6, capabilities="Flood rescue"),
            Resource(name="Fire Truck 2", type=ResourceType.FIRE_TRUCK, status=ResourceStatus.AVAILABLE,
                      latitude=24.8550, longitude=67.0500, capacity=4, capabilities="Fire suppression"),
        ])
        db.commit()
    finally:
        db.close()
