import type {
  AlertResponse, ClusterResponse, DashboardStatistics, HospitalResponse,
  IncidentResponse, IncidentStatus, ResourceRecommendation, ResourceResponse,
  Severity, ShelterResponse, TokenResponse,
} from "./types";

const BASE_URL_KEY = "rescuenet_backend_base_url";
const TOKEN_KEY = "rescuenet_token";
const ROLE_KEY = "rescuenet_role";

export function getBaseUrl(): string {
  return localStorage.getItem(BASE_URL_KEY) || "http://localhost:8000";
}
export function setBaseUrl(url: string) {
  localStorage.setItem(BASE_URL_KEY, url.replace(/\/$/, ""));
}
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}
export function getRole(): string | null {
  return localStorage.getItem(ROLE_KEY);
}
export function setSession(token: string, role: string) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(ROLE_KEY, role);
}
export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(ROLE_KEY);
}

class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(init?.headers as Record<string, string> | undefined),
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const response = await fetch(`${getBaseUrl()}/${path.replace(/^\//, "")}`, { ...init, headers });
  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new ApiError(response.status, body || response.statusText);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const api = {
  orgLogin: (username: string, password: string) =>
    request<TokenResponse>("/api/auth/org-login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),

  listIncidents: (filters?: { type?: string; severity?: Severity; status?: IncidentStatus }) => {
    const params = new URLSearchParams();
    if (filters?.type) params.set("type", filters.type);
    if (filters?.severity) params.set("severity", filters.severity);
    if (filters?.status) params.set("status", filters.status);
    const qs = params.toString();
    return request<IncidentResponse[]>(`/api/incidents${qs ? `?${qs}` : ""}`);
  },

  getIncident: (id: string) => request<IncidentResponse>(`/api/incidents/${id}`),

  updateIncident: (id: string, updateText: string | null, statusChange: IncidentStatus | null) =>
    request<IncidentResponse>(`/api/incidents/${id}/updates`, {
      method: "POST",
      body: JSON.stringify({ update_text: updateText, status_change: statusChange }),
    }),

  listResources: () => request<ResourceResponse[]>("/api/resources"),

  assignResource: (resourceId: string, incidentId: string, etaMinutes?: number) =>
    request(`/api/resources/${resourceId}/assign`, {
      method: "POST",
      body: JSON.stringify({ incident_id: incidentId, eta_minutes: etaMinutes ?? null }),
    }),

  recommendResource: (incidentId: string) =>
    request<ResourceRecommendation>(`/api/resources/recommend/${incidentId}`),

  listShelters: () => request<ShelterResponse[]>("/api/shelters"),
  listHospitals: () => request<HospitalResponse[]>("/api/hospitals"),

  clusterIncidents: (radiusKm = 2.0) =>
    request<ClusterResponse>(`/api/ai/cluster?radius_km=${radiusKm}`, { method: "POST" }),

  listAlerts: () => request<AlertResponse[]>("/api/alerts"),

  createAlert: (type: string, severity: string, message: string, isDemo: boolean) =>
    request<AlertResponse>("/api/alerts", {
      method: "POST",
      body: JSON.stringify({ type, severity, message, is_demo: isDemo }),
    }),

  dashboardStatistics: () => request<DashboardStatistics>("/api/dashboard/statistics"),
};

export function websocketUrl(): string {
  const base = getBaseUrl().replace(/^http/, "ws");
  return `${base}/ws/incidents`;
}

export { ApiError };
