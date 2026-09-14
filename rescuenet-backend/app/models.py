"""
ORM models implementing the Part 9 database schema from the Phase 1 architecture doc.
Enum values are kept in sync with the Android app's `data/model/Models.kt` so payloads
never need translation between the two.
"""
import enum
import uuid
from datetime import datetime, timezone

from sqlalchemy import (
    Boolean, Column, DateTime, Enum as SAEnum, Float, ForeignKey, Integer, String, Text,
)
from sqlalchemy.orm import relationship

from app.database import Base


def gen_uuid() -> str:
    return str(uuid.uuid4())


# ---------------------------------------------------------------------------
# Enums (mirrors Android's data/model/Models.kt — Part 9/26)
# ---------------------------------------------------------------------------

class UserRole(str, enum.Enum):
    CITIZEN = "CITIZEN"
    VOLUNTEER = "VOLUNTEER"
    RESCUE_OPERATOR = "RESCUE_OPERATOR"
    COMMAND_CENTER_ADMIN = "COMMAND_CENTER_ADMIN"


class IncidentType(str, enum.Enum):
    MEDICAL = "MEDICAL"
    FIRE = "FIRE"
    FLOOD = "FLOOD"
    EARTHQUAKE = "EARTHQUAKE"
    ACCIDENT = "ACCIDENT"
    TRAPPED = "TRAPPED"
    MISSING_PERSON = "MISSING_PERSON"
    BUILDING_COLLAPSE = "BUILDING_COLLAPSE"
    SECURITY = "SECURITY"
    OTHER = "OTHER"


class InjuryLevel(str, enum.Enum):
    NONE = "NONE"
    MINOR = "MINOR"
    SERIOUS = "SERIOUS"
    CRITICAL = "CRITICAL"
    UNKNOWN = "UNKNOWN"


class Severity(str, enum.Enum):
    CRITICAL = "CRITICAL"
    HIGH = "HIGH"
    MODERATE = "MODERATE"
    RESOLVED = "RESOLVED"


class IncidentStatus(str, enum.Enum):
    OPEN = "OPEN"
    ACKNOWLEDGED = "ACKNOWLEDGED"
    RESOURCE_ASSIGNED = "RESOURCE_ASSIGNED"
    RESOLVED = "RESOLVED"
    CLOSED = "CLOSED"


class SafetyStatus(str, enum.Enum):
    SAFE = "SAFE"
    NEEDS_HELP = "NEEDS_HELP"
    UNKNOWN = "UNKNOWN"


class ResourceType(str, enum.Enum):
    AMBULANCE = "AMBULANCE"
    RESCUE_TEAM = "RESCUE_TEAM"
    FIRE_TRUCK = "FIRE_TRUCK"
    BOAT = "BOAT"
    MEDICAL_TEAM = "MEDICAL_TEAM"
    SHELTER = "SHELTER"
    VOLUNTEER = "VOLUNTEER"


class ResourceStatus(str, enum.Enum):
    AVAILABLE = "AVAILABLE"
    BUSY = "BUSY"
    UNAVAILABLE = "UNAVAILABLE"


class AlertType(str, enum.Enum):
    """Kept only to seed the default hazard-type catalog at startup — Alert.type itself is
    now a free-text string (see HazardType below) so command center operators can add their
    own hazard categories (e.g. "Landslide") beyond this original built-in set."""
    FLOOD_WARNING = "FLOOD_WARNING"
    EARTHQUAKE_WARNING = "EARTHQUAKE_WARNING"
    FIRE_WARNING = "FIRE_WARNING"
    EVACUATION_ORDER = "EVACUATION_ORDER"
    ROAD_CLOSURE = "ROAD_CLOSURE"
    SHELTER_OPENING = "SHELTER_OPENING"
    MISSING_PERSON = "MISSING_PERSON"


class HazardType(Base):
    """A user-manageable catalog of hazard categories operators can pick from (or add to)
    when marking an affected area or broadcasting an alert. Deleting one here does not touch
    any Alert rows that already used its name — they just stop showing a matching color."""
    __tablename__ = "hazard_types"

    id = Column(String, primary_key=True, default=gen_uuid)
    name = Column(String, unique=True, nullable=False)
    color = Column(String, nullable=False, default="#8a93a6")  # hex, used for map circles/badges
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None))


class AlertSeverity(str, enum.Enum):
    CRITICAL = "CRITICAL"
    WARNING = "WARNING"
    INFORMATION = "INFORMATION"


# ---------------------------------------------------------------------------
# Core tables
# ---------------------------------------------------------------------------

class User(Base):
    """Per Part 19/25: `phone_hash` is a salted hash, never a raw phone number. Real-name
    accounts are only required for organizational roles (Volunteer/Operator/Admin) — plain
    citizens authenticate via their anonymous `device_uuid` (see Device below)."""
    __tablename__ = "users"

    id = Column(String, primary_key=True, default=gen_uuid)
    phone_hash = Column(String, nullable=True, index=True)
    name = Column(String, nullable=True)
    role = Column(SAEnum(UserRole), nullable=False, default=UserRole.CITIZEN)
    language_pref = Column(String, default="en")
    hashed_password = Column(String, nullable=True)  # only set for organizational roles
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None))
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None), onupdate=lambda: datetime.now(timezone.utc).replace(tzinfo=None))

    devices = relationship("Device", back_populates="user")
    incidents = relationship("EmergencyIncident", back_populates="reporter")


class Device(Base):
    __tablename__ = "devices"

    id = Column(String, primary_key=True, default=gen_uuid)
    device_uuid = Column(String, unique=True, nullable=False, index=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    last_seen_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None))
    os_version = Column(String, nullable=True)
    app_version = Column(String, nullable=True)

    user = relationship("User", back_populates="devices")


class EmergencyContact(Base):
    __tablename__ = "emergency_contacts"

    id = Column(String, primary_key=True, default=gen_uuid)
    user_id = Column(String, ForeignKey("users.id"), nullable=False, index=True)
    contact_user_id = Column(String, ForeignKey("users.id"), nullable=False, index=True)
    relationship_label = Column(String, nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None))


class EmergencyIncident(Base):
    __tablename__ = "emergency_incidents"

    id = Column(String, primary_key=True, default=gen_uuid)
    # De-dup key shared with the Android app's local eventUuid and mesh messageUuid — the
    # single most important field for Part 21's "never duplicate emergency incidents."
    event_uuid = Column(String, unique=True, nullable=False, index=True)
    reporter_id = Column(String, ForeignKey("users.id"), nullable=True)

    type = Column(SAEnum(IncidentType), nullable=False)
    severity = Column(SAEnum(Severity), nullable=True)  # set by AI analysis, may be null pre-analysis
    people_count = Column(Integer, default=1)
    injury_level = Column(SAEnum(InjuryLevel), default=InjuryLevel.UNKNOWN)
    needs = Column(String, default="")  # comma-separated ResourceNeed names
    description = Column(Text, default="")

    latitude = Column(Float, nullable=True)
    longitude = Column(Float, nullable=True)
    location_accuracy_m = Column(Float, nullable=True)

    status = Column(SAEnum(IncidentStatus), default=IncidentStatus.OPEN)
    source = Column(String, default="app")  # "app" | "relay" | "manual"

    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None), index=True)
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None), onupdate=lambda: datetime.now(timezone.utc).replace(tzinfo=None))

    reporter = relationship("User", back_populates="incidents")
    updates = relationship("IncidentUpdate", back_populates="incident", cascade="all, delete-orphan")
    assignments = relationship("ResourceAssignment", back_populates="incident", cascade="all, delete-orphan")
    ai_analysis = relationship("AIAnalysis", back_populates="incident", uselist=False, cascade="all, delete-orphan")


class IncidentUpdate(Base):
    __tablename__ = "incident_updates"

    id = Column(String, primary_key=True, default=gen_uuid)
    incident_id = Column(String, ForeignKey("emergency_incidents.id"), nullable=False, index=True)
    actor_id = Column(String, ForeignKey("users.id"), nullable=True)
    update_text = Column(Text, nullable=True)
    status_change = Column(SAEnum(IncidentStatus), nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None))

    incident = relationship("EmergencyIncident", back_populates="updates")


class LocationHistory(Base):
    """Family-safety-board location pings (Part 8) — deliberately separate from
    EmergencyIncidents.lat/lng, which is per-report, not a standing location trail."""
    __tablename__ = "locations"

    id = Column(String, primary_key=True, default=gen_uuid)
    user_id = Column(String, ForeignKey("users.id"), nullable=False, index=True)
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)
    accuracy_m = Column(Float, nullable=True)
    recorded_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None))


class SafetyStatusUpdate(Base):
    """One row per "I'm Safe" / status change — Part 8's family safety board is derived by
    taking the latest row per user, not by mutating a single status field, so the history is
    auditable."""
    __tablename__ = "safety_status_updates"

    id = Column(String, primary_key=True, default=gen_uuid)
    user_id = Column(String, ForeignKey("users.id"), nullable=False, index=True)
    status = Column(SAEnum(SafetyStatus), nullable=False)
    latitude = Column(Float, nullable=True)
    longitude = Column(Float, nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None), index=True)


class Resource(Base):
    __tablename__ = "resources"

    id = Column(String, primary_key=True, default=gen_uuid)
    name = Column(String, nullable=False)
    type = Column(SAEnum(ResourceType), nullable=False)
    status = Column(SAEnum(ResourceStatus), default=ResourceStatus.AVAILABLE)
    latitude = Column(Float, nullable=True)
    longitude = Column(Float, nullable=True)
    capacity = Column(Integer, default=1)
    capabilities = Column(String, default="")  # comma-separated free-text capability tags
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None), onupdate=lambda: datetime.now(timezone.utc).replace(tzinfo=None))

    assignments = relationship("ResourceAssignment", back_populates="resource")


class ResourceAssignment(Base):
    __tablename__ = "resource_assignments"

    id = Column(String, primary_key=True, default=gen_uuid)
    resource_id = Column(String, ForeignKey("resources.id"), nullable=False, index=True)
    incident_id = Column(String, ForeignKey("emergency_incidents.id"), nullable=False, index=True)
    assigned_by = Column(String, ForeignKey("users.id"), nullable=True)
    eta_minutes = Column(Integer, nullable=True)
    status = Column(String, default="ASSIGNED")  # ASSIGNED | EN_ROUTE | ON_SCENE | COMPLETED
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None))

    resource = relationship("Resource", back_populates="assignments")
    incident = relationship("EmergencyIncident", back_populates="assignments")


class Shelter(Base):
    __tablename__ = "shelters"

    id = Column(String, primary_key=True, default=gen_uuid)
    name = Column(String, nullable=False)
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)
    capacity = Column(Integer, default=0)
    current_occupancy = Column(Integer, default=0)
    status = Column(String, default="OPEN")
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None), onupdate=lambda: datetime.now(timezone.utc).replace(tzinfo=None))


class Hospital(Base):
    __tablename__ = "hospitals"

    id = Column(String, primary_key=True, default=gen_uuid)
    name = Column(String, nullable=False)
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)
    capabilities = Column(String, default="")
    contact = Column(String, nullable=True)
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None), onupdate=lambda: datetime.now(timezone.utc).replace(tzinfo=None))


class RescueTeam(Base):
    __tablename__ = "rescue_teams"

    id = Column(String, primary_key=True, default=gen_uuid)
    name = Column(String, nullable=False)
    type = Column(String, nullable=True)
    member_count = Column(Integer, default=0)
    status = Column(SAEnum(ResourceStatus), default=ResourceStatus.AVAILABLE)
    base_latitude = Column(Float, nullable=True)
    base_longitude = Column(Float, nullable=True)


class NetworkMessage(Base):
    """Server-side mirror of the mesh-relayed messages the Android app hands off via
    POST /api/sync/batch — kept for audit/debugging of the offline relay path (Part 7),
    distinct from EmergencyIncidents which is the canonical, deduplicated incident record."""
    __tablename__ = "network_messages"

    id = Column(String, primary_key=True, default=gen_uuid)
    message_uuid = Column(String, unique=True, nullable=False, index=True)
    origin_device_id = Column(String, nullable=True)
    hop_count = Column(Integer, default=0)
    received_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None))


class Alert(Base):
    __tablename__ = "alerts"

    id = Column(String, primary_key=True, default=gen_uuid)
    # Free-text hazard type name (e.g. "FIRE_WARNING" or a custom one like "Landslide") —
    # matched against HazardType.name for display color, but not foreign-keyed to it, so
    # deleting a hazard type from the catalog never breaks or orphans existing alerts.
    type = Column(String, nullable=False)
    severity = Column(SAEnum(AlertSeverity), nullable=False)
    message = Column(Text, nullable=False)
    area_geojson = Column(Text, nullable=True)
    # Part 15/36: simulated/demo alerts must never be indistinguishable from real ones.
    is_demo = Column(Boolean, nullable=False, default=True)
    created_by = Column(String, ForeignKey("users.id"), nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None))
    expires_at = Column(DateTime, nullable=True)


class AuditLog(Base):
    __tablename__ = "audit_logs"

    id = Column(String, primary_key=True, default=gen_uuid)
    actor_id = Column(String, ForeignKey("users.id"), nullable=True)
    action = Column(String, nullable=False)
    target_type = Column(String, nullable=True)
    target_id = Column(String, nullable=True)
    metadata_json = Column(Text, nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None), index=True)


class AIAnalysis(Base):
    __tablename__ = "ai_analysis"

    id = Column(String, primary_key=True, default=gen_uuid)
    incident_id = Column(String, ForeignKey("emergency_incidents.id"), unique=True, nullable=False)
    model_provider = Column(String, nullable=False)
    raw_output_json = Column(Text, nullable=False)
    confidence = Column(Integer, nullable=False)  # 0-100, Part 6/26
    is_simulated = Column(Boolean, nullable=False, default=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None))

    incident = relationship("EmergencyIncident", back_populates="ai_analysis")
