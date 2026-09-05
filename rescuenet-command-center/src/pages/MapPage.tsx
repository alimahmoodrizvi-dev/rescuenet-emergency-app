import { useEffect, useState } from "react";
import { CircleMarker, MapContainer, Marker, Popup, TileLayer } from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { api } from "../api/client";
import type { HospitalResponse, IncidentResponse, ResourceResponse, ShelterResponse, Severity } from "../api/types";

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

const KARACHI_CENTER: [number, number] = [24.8607, 67.0300];

export function MapPage({ incidents }: { incidents: IncidentResponse[] }) {
  const [resources, setResources] = useState<ResourceResponse[]>([]);
  const [shelters, setShelters] = useState<ShelterResponse[]>([]);
  const [hospitals, setHospitals] = useState<HospitalResponse[]>([]);

  useEffect(() => {
    api.listResources().then(setResources).catch(() => {});
    api.listShelters().then(setShelters).catch(() => {});
    api.listHospitals().then(setHospitals).catch(() => {});
  }, []);

  const located = incidents.filter((i) => i.latitude != null && i.longitude != null);

  return (
    <div>
      <div className="page-header">
        <h1>Live Map</h1>
      </div>
      <div className="card" style={{ padding: 8, marginBottom: 16 }}>
        <Legend />
      </div>
      <MapContainer center={KARACHI_CENTER} zoom={12} scrollWheelZoom>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

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
    </div>
  );
}
