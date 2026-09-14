import { useEffect, useState } from "react";
import { CircleMarker, Circle, MapContainer, Marker, Popup, TileLayer, useMapEvents } from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { api, ApiError } from "../api/client";
import type {
  AffectedAreaCircle, AlertResponse, AlertSeverity, AlertType,
  HospitalResponse, IncidentResponse, ResourceResponse, ResourceType, ShelterResponse, Severity,
} from "../api/types";

// Leaflet's default marker icons reference image files that Vite doesn't bundle
// automatically — inline SVG data-URIs avoid the classic "broken marker" issue entirely.
function coloredIcon(color: string) {
  return L.divIcon({
    className: "",
    html: `<div style="width:14px;height:14px;border-radius:50%;background:${color};border:2px solid white;box-shadow:0 0 0 1px rgba(0,0,0,0.2)"></div>`,
    iconSize: [14, 14],
    iconAnchor: [7, 7],
  });
}

const severityColor: Record<string, string> = {
  CRITICAL: "#e1341e",
  HIGH: "#f08a24",
  MODERATE: "#f2c230",
  RESOLVED: "#2fa84f",
};

// Color per hazard type for affected-area circles — grouped by what the hazard physically is.
const hazardColor: Record<AlertType, string> = {
  FIRE_WARNING: "#d8261c",
  FLOOD_WARNING: "#1c7fd6",
  EARTHQUAKE_WARNING: "#8a5a2b",
  EVACUATION_ORDER: "#8a2be2",
  ROAD_CLOSURE: "#5f5e5a",
  SHELTER_OPENING: "#2fa84f",
  MISSING_PERSON: "#d4537e",
};

const RESOURCE_TYPES: ResourceType[] = [
  "AMBULANCE", "RESCUE_TEAM", "FIRE_TRUCK", "BOAT", "MEDICAL_TEAM", "SHELTER", "VOLUNTEER",
];
const HAZARD_TYPES: AlertType[] = [
  "FIRE_WARNING", "FLOOD_WARNING", "EARTHQUAKE_WARNING", "EVACUATION_ORDER",
  "ROAD_CLOSURE", "SHELTER_OPENING", "MISSING_PERSON",
];
const HAZARD_SEVERITIES: AlertSeverity[] = ["CRITICAL", "WARNING", "INFORMATION"];

const KARACHI_CENTER: [number, number] = [24.8607, 67.0300];

type Mode = "view" | "add-resource" | "add-area";

function parseArea(areaGeojson: string | null): AffectedAreaCircle | null {
  if (!areaGeojson) return null;
  try {
    const parsed = JSON.parse(areaGeojson);
    if (typeof parsed.lat === "number" && typeof parsed.lng === "number" && typeof parsed.radius_km === "number") {
      return parsed as AffectedAreaCircle;
    }
  } catch {
    // not a circle we understand — skip it rather than crash the map
  }
  return null;
}

function ClickCapture({ active, onClick }: { active: boolean; onClick: (lat: number, lng: number) => void }) {
  useMapEvents({
    click(e) {
      if (active) onClick(e.latlng.lat, e.latlng.lng);
    },
  });
  return null;
}

export function MapPage({ incidents }: { incidents: IncidentResponse[] }) {
  const [resources, setResources] = useState<ResourceResponse[]>([]);
  const [shelters, setShelters] = useState<ShelterResponse[]>([]);
  const [hospitals, setHospitals] = useState<HospitalResponse[]>([]);
  const [alerts, setAlerts] = useState<AlertResponse[]>([]);

  const [mode, setMode] = useState<Mode>("view");
  const [pendingLatLng, setPendingLatLng] = useState<{ lat: number; lng: number } | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // add-resource form state
  const [resName, setResName] = useState("");
  const [resType, setResType] = useState<ResourceType>("AMBULANCE");
  const [resCapacity, setResCapacity] = useState(1);

  // add-area form state
  const [hazardType, setHazardType] = useState<AlertType>("FIRE_WARNING");
  const [hazardSeverity, setHazardSeverity] = useState<AlertSeverity>("WARNING");
  const [hazardRadiusKm, setHazardRadiusKm] = useState(1);
  const [hazardMessage, setHazardMessage] = useState("");
  const [hazardIsDemo, setHazardIsDemo] = useState(true);

  function refreshResources() {
    api.listResources().then(setResources).catch(() => {});
  }
  function refreshAlerts() {
    api.listAlerts().then(setAlerts).catch(() => {});
  }

  useEffect(() => {
    refreshResources();
    api.listShelters().then(setShelters).catch(() => {});
    api.listHospitals().then(setHospitals).catch(() => {});
    refreshAlerts();
  }, []);

  const located = incidents.filter((i) => i.latitude != null && i.longitude != null);

  function handleMapClick(lat: number, lng: number) {
    setFormError(null);
    setPendingLatLng({ lat, lng });
  }

  function cancelPending() {
    setPendingLatLng(null);
    setFormError(null);
    setResName("");
    setHazardMessage("");
  }

  async function submitResource() {
    if (!pendingLatLng) return;
    if (!resName.trim()) {
      setFormError("Enter a name first.");
      return;
    }
    setBusy(true);
    setFormError(null);
    try {
      await api.createResource(resName.trim(), resType, pendingLatLng.lat, pendingLatLng.lng, resCapacity, "");
      refreshResources();
      cancelPending();
      setMode("view");
    } catch (err) {
      setFormError(err instanceof ApiError && err.status === 403
        ? "You need Rescue Operator or Admin role to add resources."
        : "Couldn't add resource. Try again.");
    } finally {
      setBusy(false);
    }
  }

  async function submitArea() {
    if (!pendingLatLng) return;
    if (!hazardMessage.trim()) {
      setFormError("Enter a short description first.");
      return;
    }
    setBusy(true);
    setFormError(null);
    try {
      const area: AffectedAreaCircle = { lat: pendingLatLng.lat, lng: pendingLatLng.lng, radius_km: hazardRadiusKm };
      await api.createAlert(hazardType, hazardSeverity, hazardMessage.trim(), hazardIsDemo, JSON.stringify(area));
      refreshAlerts();
      cancelPending();
      setMode("view");
    } catch (err) {
      setFormError(err instanceof ApiError && err.status === 403
        ? "You need Rescue Operator or Admin role to mark an affected area."
        : "Couldn't save affected area. Try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>Live Map</h1>
      </div>

      <div className="card" style={{ padding: 8, marginBottom: 16 }}>
        <Legend />
      </div>

      <div className="card" style={{ padding: 12, marginBottom: 16, display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        <button
          className={mode === "view" ? "btn btn-primary" : "btn"}
          onClick={() => { setMode("view"); cancelPending(); }}
        >
          Browse
        </button>
        <button
          className={mode === "add-resource" ? "btn btn-primary" : "btn"}
          onClick={() => { setMode("add-resource"); cancelPending(); }}
        >
          Add resource
        </button>
        <button
          className={mode === "add-area" ? "btn btn-primary" : "btn"}
          onClick={() => { setMode("add-area"); cancelPending(); }}
        >
          Mark affected area
        </button>
        {mode !== "view" && (
          <span style={{ fontSize: 12, color: "var(--text-dim)" }}>
            Click anywhere on the map to place it.
          </span>
        )}
      </div>

      {pendingLatLng && mode === "add-resource" && (
        <div className="card" style={{ padding: 16, marginBottom: 16 }}>
          <h3 style={{ marginTop: 0 }}>New resource</h3>
          <p style={{ fontSize: 12, color: "var(--text-dim)" }}>
            Location: {pendingLatLng.lat.toFixed(4)}, {pendingLatLng.lng.toFixed(4)}
          </p>
          <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Name</label>
          <input
            value={resName}
            onChange={(e) => setResName(e.target.value)}
            placeholder="Ambulance 4"
            style={{ width: "100%", marginBottom: 10 }}
          />
          <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Type</label>
          <select value={resType} onChange={(e) => setResType(e.target.value as ResourceType)} style={{ width: "100%", marginBottom: 10 }}>
            {RESOURCE_TYPES.map((t) => (
              <option key={t} value={t}>{t.replace("_", " ")}</option>
            ))}
          </select>
          <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Capacity</label>
          <input
            type="number"
            min={1}
            value={resCapacity}
            onChange={(e) => setResCapacity(Math.max(1, Number(e.target.value)))}
            style={{ width: "100%", marginBottom: 10 }}
          />
          {formError && <p className="error-text">{formError}</p>}
          <div style={{ display: "flex", gap: 8 }}>
            <button className="btn btn-primary" disabled={busy} onClick={submitResource}>
              {busy ? "Saving…" : "Save resource"}
            </button>
            <button className="btn" onClick={cancelPending}>Cancel</button>
          </div>
        </div>
      )}

      {pendingLatLng && mode === "add-area" && (
        <div className="card" style={{ padding: 16, marginBottom: 16 }}>
          <h3 style={{ marginTop: 0 }}>New affected area</h3>
          <p style={{ fontSize: 12, color: "var(--text-dim)" }}>
            Center: {pendingLatLng.lat.toFixed(4)}, {pendingLatLng.lng.toFixed(4)}
          </p>
          <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Hazard type</label>
          <select value={hazardType} onChange={(e) => setHazardType(e.target.value as AlertType)} style={{ width: "100%", marginBottom: 10 }}>
            {HAZARD_TYPES.map((t) => (
              <option key={t} value={t}>{t.replace("_", " ")}</option>
            ))}
          </select>
          <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Severity</label>
          <select value={hazardSeverity} onChange={(e) => setHazardSeverity(e.target.value as AlertSeverity)} style={{ width: "100%", marginBottom: 10 }}>
            {HAZARD_SEVERITIES.map((s) => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
          <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>
            Radius: {hazardRadiusKm} km
          </label>
          <input
            type="range"
            min={0.1}
            max={20}
            step={0.1}
            value={hazardRadiusKm}
            onChange={(e) => setHazardRadiusKm(Number(e.target.value))}
            style={{ width: "100%", marginBottom: 10 }}
          />
          <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Description</label>
          <textarea
            rows={2}
            value={hazardMessage}
            onChange={(e) => setHazardMessage(e.target.value)}
            placeholder="Rising water level near riverside colony"
            style={{ width: "100%", marginBottom: 10 }}
          />
          <label style={{ fontSize: 13, display: "flex", alignItems: "center", gap: 6, marginBottom: 12 }}>
            <input type="checkbox" checked={hazardIsDemo} onChange={(e) => setHazardIsDemo(e.target.checked)} />
            Mark as demo/test
          </label>
          {formError && <p className="error-text">{formError}</p>}
          <div style={{ display: "flex", gap: 8 }}>
            <button className="btn btn-primary" disabled={busy} onClick={submitArea}>
              {busy ? "Saving…" : "Save affected area"}
            </button>
            <button className="btn" onClick={cancelPending}>Cancel</button>
          </div>
        </div>
      )}

      <MapContainer center={KARACHI_CENTER} zoom={12} scrollWheelZoom>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        <ClickCapture active={mode !== "view"} onClick={handleMapClick} />

        {alerts.map((a) => {
          const area = parseArea(a.area_geojson);
          if (!area) return null;
          const color = hazardColor[a.type] ?? "#8a93a6";
          return (
            <Circle
              key={a.id}
              center={[area.lat, area.lng]}
              radius={area.radius_km * 1000}
              pathOptions={{ color, fillColor: color, fillOpacity: 0.15, weight: 2 }}
            >
              <Popup>
                <strong>{a.type.replace("_", " ")}</strong> — {a.severity}
                <br />
                {a.message}
                <br />
                <span style={{ color: "#666" }}>{area.radius_km} km radius</span>
              </Popup>
            </Circle>
          );
        })}

        {pendingLatLng && mode === "add-area" && (
          <Circle
            center={[pendingLatLng.lat, pendingLatLng.lng]}
            radius={hazardRadiusKm * 1000}
            pathOptions={{ color: hazardColor[hazardType], fillColor: hazardColor[hazardType], fillOpacity: 0.2, weight: 2, dashArray: "6 4" }}
          />
        )}

        {pendingLatLng && mode === "add-resource" && (
          <Marker position={[pendingLatLng.lat, pendingLatLng.lng]} icon={coloredIcon("#0fa3a3")} />
        )}

        {located.map((i) => (
          <CircleMarker
            key={i.id}
            center={[i.latitude as number, i.longitude as number]}
            radius={9}
            pathOptions={{
              color: "white",
              weight: 2,
              fillColor: severityColor[i.severity ?? "MODERATE"] ?? "#8a93a6",
              fillOpacity: 0.9,
            }}
          >
            <Popup>
              <strong>{i.type.replace("_", " ")}</strong> — {i.severity ?? "unanalyzed"}
              <br />
              {i.people_count} people · {i.status.replace("_", " ")}
              <br />
              <span style={{ color: "#666" }}>{i.description}</span>
            </Popup>
          </CircleMarker>
        ))}

        {resources
          .filter((r) => r.latitude != null && r.longitude != null)
          .map((r) => (
            <Marker key={r.id} position={[r.latitude as number, r.longitude as number]} icon={coloredIcon("#0fa3a3")}>
              <Popup>
                <strong>{r.name}</strong>
                <br />
                {r.type.replace("_", " ")} · {r.status}
              </Popup>
            </Marker>
          ))}

        {shelters.map((s) => (
          <Marker key={s.id} position={[s.latitude, s.longitude]} icon={coloredIcon("#0b1f3a")}>
            <Popup>
              <strong>{s.name}</strong>
              <br />
              Shelter · {s.current_occupancy}/{s.capacity}
            </Popup>
          </Marker>
        ))}

        {hospitals.map((h) => (
          <Marker key={h.id} position={[h.latitude, h.longitude]} icon={coloredIcon("#8a2be2")}>
            <Popup>
              <strong>{h.name}</strong>
              <br />
              Hospital · {h.capabilities}
            </Popup>
          </Marker>
        ))}
      </MapContainer>
    </div>
  );
}

function Legend() {
  const items: { label: string; color: string; severity?: Severity }[] = [
    { label: "Critical", color: severityColor.CRITICAL },
    { label: "High", color: severityColor.HIGH },
    { label: "Moderate", color: severityColor.MODERATE },
    { label: "Resolved", color: severityColor.RESOLVED },
  ];
  return (
    <div style={{ display: "flex", gap: 16, fontSize: 12, flexWrap: "wrap", padding: "4px 8px" }}>
      {items.map((it) => (
        <span key={it.label} style={{ display: "flex", alignItems: "center", gap: 6 }}>
          <span style={{ width: 10, height: 10, borderRadius: "50%", background: it.color, display: "inline-block" }} />
          {it.label} incident
        </span>
      ))}
      <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
        <span style={{ width: 10, height: 10, borderRadius: "50%", background: "#0fa3a3", display: "inline-block" }} />
        Resource
      </span>
      <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
        <span style={{ width: 10, height: 10, borderRadius: "50%", background: "#0b1f3a", display: "inline-block" }} />
        Shelter
      </span>
      <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
        <span style={{ width: 10, height: 10, borderRadius: "50%", background: "#8a2be2", display: "inline-block" }} />
        Hospital
      </span>
      <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
        <span style={{ width: 10, height: 10, borderRadius: "50%", background: "#d8261c", display: "inline-block" }} />
        Affected area (color = hazard type)
      </span>
    </div>
  );
}
