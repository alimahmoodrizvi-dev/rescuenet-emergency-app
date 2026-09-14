// Field names deliberately mirror rescuenet-backend/app/schemas.py exactly so no
// translation layer is needed between the two projects.

export type IncidentType =
  | "MEDICAL" | "FIRE" | "FLOOD" | "EARTHQUAKE" | "ACCIDENT" | "TRAPPED"
  | "MISSING_PERSON" | "BUILDING_COLLAPSE" | "SECURITY" | "OTHER";

export type InjuryLevel = "NONE" | "MINOR" | "SERIOUS" | "CRITICAL" | "UNKNOWN";
export type Severity = "CRITICAL" | "HIGH" | "MODERATE" | "RESOLVED";
export type IncidentStatus = "OPEN" | "ACKNOWLEDGED" | "RESOURCE_ASSIGNED" | "RESOLVED" | "CLOSED";
export type ResourceType = "AMBULANCE" | "RESCUE_TEAM" | "FIRE_TRUCK" | "BOAT" | "MEDICAL_TEAM" | "SHELTER" | "VOLUNTEER";
export type ResourceStatus = "AVAILABLE" | "BUSY" | "UNAVAILABLE";
export type AlertType =
  | "FLOOD_WARNING" | "EARTHQUAKE_WARNING" | "FIRE_WARNING" | "EVACUATION_ORDER"
  | "ROAD_CLOSURE" | "SHELTER_OPENING" | "MISSING_PERSON";
export type AlertSeverity = "CRITICAL" | "WARNING" | "INFORMATION";
export type UserRole = "CITIZEN" | "VOLUNTEER" | "RESCUE_OPERATOR" | "COMMAND_CENTER_ADMIN";

export interface IncidentResponse {
  id: string;
  event_uuid: string;
  type: IncidentType;
  severity: Severity | null;
  people_count: number;
  injury_level: InjuryLevel;
  needs: string;
  description: string;
  latitude: number | null;
  longitude: number | null;
  status: IncidentStatus;
  source: string;
  created_at: string;
  updated_at: string;
}

export interface ResourceResponse {
  id: string;
  name: string;
  type: ResourceType;
  status: ResourceStatus;
  latitude: number | null;
  longitude: number | null;
  capacity: number;
  capabilities: string;
}

export interface ShelterResponse {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  capacity: number;
  current_occupancy: number;
  status: string;
}

export interface HospitalResponse {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  capabilities: string;
  contact: string | null;
}

export interface AlertResponse {
  id: string;
  type: AlertType;
  severity: AlertSeverity;
  message: string;
  is_demo: boolean;
  area_geojson: string | null;
  created_at: string;
  expires_at: string | null;
}

// Parsed shape of AlertResponse.area_geojson — a simple circle, not a full GeoJSON polygon.
export interface AffectedAreaCircle {
  lat: number;
  lng: number;
  radius_km: number;
}

export interface DashboardStatistics {
  total_incidents: number;
  critical_incidents: number;
  resolved_incidents: number;
  active_resources: number;
  average_ai_confidence: number | null;
  network_messages_received: number;
}

export interface ClusterResult {
  incident_count: number;
  incident_ids: string[];
  likely_type: string;
  estimated_people_affected: number;
  center_latitude: number;
  center_longitude: number;
}

export interface ClusterResponse {
  clusters: ClusterResult[];
  radius_km: number;
}

export interface ResourceRecommendation {
  recommendation: {
    resource_id: string;
    name: string;
    type: ResourceType;
    distance_km: number;
    eta_minutes: number;
    capabilities: string;
  } | null;
  reason: string;
  is_simulated_eta?: boolean;
}

export interface TokenResponse {
  access_token: string;
  token_type: string;
  role: UserRole;
}

export interface WsMessage<T = unknown> {
  event: string;
  data: T;
}
