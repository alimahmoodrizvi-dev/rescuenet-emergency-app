"""Pydantic schemas for the Part 10 API surface."""
from datetime import datetime
from typing import Optional

from pydantic import BaseModel, ConfigDict, Field

from app.models import (
    AlertSeverity, IncidentStatus, IncidentType, InjuryLevel, ResourceStatus,
    ResourceType, SafetyStatus, Severity, UserRole,
)


class ORMBase(BaseModel):
    model_config = ConfigDict(from_attributes=True)


# ---------------------------------------------------------------------------
# Auth
# ---------------------------------------------------------------------------

class DeviceRegisterRequest(BaseModel):
    device_uuid: str
    os_version: Optional[str] = None
    app_version: Optional[str] = None


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    role: UserRole


class OrgLoginRequest(BaseModel):
    """For Volunteer/Operator/Admin roles only — plain citizens use device auth (Part 19/24)."""
    username: str
    password: str


# ---------------------------------------------------------------------------
# Incidents
# ---------------------------------------------------------------------------

class IncidentCreateRequest(BaseModel):
    event_uuid: str
    type: IncidentType
    people_count: int = Field(ge=1, default=1)
    injury_level: InjuryLevel = InjuryLevel.UNKNOWN
    needs: list[str] = Field(default_factory=list)
    description: str = ""
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    location_accuracy_m: Optional[float] = None
    source: str = "app"


class IncidentUpdateRequest(BaseModel):
    update_text: Optional[str] = None
    status_change: Optional[IncidentStatus] = None


class IncidentResponse(ORMBase):
    id: str
    event_uuid: str
    type: IncidentType
    severity: Optional[Severity]
    people_count: int
    injury_level: InjuryLevel
    needs: str
    description: str
    latitude: Optional[float]
    longitude: Optional[float]
    status: IncidentStatus
    source: str
    created_at: datetime
    updated_at: datetime


# ---------------------------------------------------------------------------
# Safety status / family
# ---------------------------------------------------------------------------

class SafetyStatusRequest(BaseModel):
    status: SafetyStatus
    latitude: Optional[float] = None
    longitude: Optional[float] = None


class FamilyMemberStatusResponse(BaseModel):
    user_id: str
    name: Optional[str]
    status: SafetyStatus
    updated_at: datetime


# ---------------------------------------------------------------------------
# Resources
# ---------------------------------------------------------------------------

class ResourceResponse(ORMBase):
    id: str
    name: str
    type: ResourceType
    status: ResourceStatus
    latitude: Optional[float]
    longitude: Optional[float]
    capacity: int
    capabilities: str


class ResourceAssignRequest(BaseModel):
    incident_id: str
    eta_minutes: Optional[int] = None


class ResourceCreateRequest(BaseModel):
    name: str
    type: ResourceType
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    capacity: int = 1
    capabilities: str = ""


class ResourceUpdateRequest(BaseModel):
    """All fields optional — only the ones the caller sends get changed."""
    name: Optional[str] = None
    type: Optional[ResourceType] = None
    status: Optional[ResourceStatus] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    capacity: Optional[int] = None
    capabilities: Optional[str] = None


class ResourceAssignmentResponse(ORMBase):
    id: str
    resource_id: str
    incident_id: str
    eta_minutes: Optional[int]
    status: str
    created_at: datetime


# ---------------------------------------------------------------------------
# Shelters / Hospitals
# ---------------------------------------------------------------------------

class ShelterResponse(ORMBase):
    id: str
    name: str
    latitude: float
    longitude: float
    capacity: int
    current_occupancy: int
    status: str


class HospitalResponse(ORMBase):
    id: str
    name: str
    latitude: float
    longitude: float
    capabilities: str
    contact: Optional[str]


# ---------------------------------------------------------------------------
# AI
# ---------------------------------------------------------------------------

class AIAnalyzeIncidentRequest(BaseModel):
    """Raw text (typed or transcribed) to structure — mirrors Part 6's example flow."""
    raw_text: str
    language_hint: Optional[str] = None  # "en" | "ur"


class AIAnalyzeIncidentResponse(BaseModel):
    incident_type: IncidentType
    severity: Severity
    people_count: Optional[int]
    injuries_present: Optional[bool]
    required_resources: list[str]
    confidence: int = Field(ge=0, le=100)
    model_provider: str
    is_simulated: bool
    caveat: str = "AI-assisted analysis, not a medical or emergency diagnosis. Verify before acting."


# ---------------------------------------------------------------------------
# Alerts
# ---------------------------------------------------------------------------

class AlertCreateRequest(BaseModel):
    type: str
    severity: AlertSeverity
    message: str
    is_demo: bool = True
    expires_at: Optional[datetime] = None
    # Simple JSON string of {"lat": float, "lng": float, "radius_km": float} describing the
    # affected area as a circle. Kept as a string (not a nested model) since the existing
    # `Alert.area_geojson` column is free-text — this is the simplest shape that fits without
    # a migration, and can be swapped for a real GeoJSON polygon later without a schema change.
    area_geojson: Optional[str] = None


class AlertResponse(ORMBase):
    id: str
    type: str
    severity: AlertSeverity
    message: str
    is_demo: bool
    area_geojson: Optional[str]
    created_at: datetime
    expires_at: Optional[datetime]


# ---------------------------------------------------------------------------
# Hazard types (user-manageable catalog for the affected-area / alert "type" field)
# ---------------------------------------------------------------------------

class HazardTypeResponse(ORMBase):
    id: str
    name: str
    color: str
    created_at: datetime


class HazardTypeCreateRequest(BaseModel):
    name: str
    color: str = "#8a93a6"


class HazardTypeUpdateRequest(BaseModel):
    name: Optional[str] = None
    color: Optional[str] = None


# ---------------------------------------------------------------------------
# Sync (offline mesh hand-off, Part 21/23)
# ---------------------------------------------------------------------------

class SyncBatchItem(BaseModel):
    entity_type: str  # "incident" | "safety_status"
    payload: dict


class SyncBatchRequest(BaseModel):
    device_uuid: str
    items: list[SyncBatchItem]


class SyncBatchResult(BaseModel):
    accepted: int
    duplicates: int
    rejected: int


# ---------------------------------------------------------------------------
# Dashboard
# ---------------------------------------------------------------------------

class DashboardStatistics(BaseModel):
    total_incidents: int
    critical_incidents: int
    resolved_incidents: int
    active_resources: int
    average_ai_confidence: Optional[float]
    network_messages_received: int
