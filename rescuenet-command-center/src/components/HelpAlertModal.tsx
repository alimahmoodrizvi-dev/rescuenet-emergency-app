import { useState } from "react";
import { MapContainer, Marker, TileLayer } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { pulseIcon, UNRATED_PULSE_COLOR } from "./pulseIcon";
import type { IncidentResponse } from "../api/types";

// Pop-up shown the moment a new help request arrives from a phone. It stays until an operator
// deals with it, and if several arrive close together they queue up and are shown one by one.
export function HelpAlertModal({
  queue, onDismiss, onAcknowledged,
}: {
  queue: IncidentResponse[];
  onDismiss: (id: string) => void;
  onAcknowledged: (updated: IncidentResponse) => void;
}) {
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const incident = queue[0];
  if (!incident) return null;

  const hasLocation = incident.latitude != null && incident.longitude != null;
  const lat = incident.latitude as number;
  const lng = incident.longitude as number;

  function dismiss() {
    setError(null);
    onDismiss(incident.id);
  }

  function viewOnMap() {
    // The incident keeps blinking on the map until someone acknowledges it.
    navigate("/map", { state: { focusIncidentId: incident.id } });
    dismiss();
  }

  async function acknowledge() {
    setBusy(true);
    setError(null);
    try {
      const updated = await api.updateIncident(incident.id, "Acknowledged from the new-request alert", "ACKNOWLEDGED");
      onAcknowledged(updated);
      onDismiss(incident.id);
    } catch {
      setError("Couldn't acknowledge — you may need Rescue Operator or Admin role.");
    } finally {
      setBusy(false);
    }
  }

  const needs = incident.needs ? incident.needs.split(",").filter(Boolean).join(", ") : "";

  return (
    <div className="help-alert-backdrop" role="alertdialog" aria-modal="true" aria-label="New help request">
      <div className="help-alert">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
          <h2 style={{ margin: 0, color: "var(--critical)", fontSize: 20 }}>New help request</h2>
          {queue.length > 1 && (
            <span style={{ fontSize: 12, color: "var(--text-dim)" }}>1 of {queue.length} waiting</span>
          )}
        </div>
        <p style={{ margin: "4px 0 0", fontSize: 13, color: "var(--text-dim)" }}>
          {new Date(incident.created_at + "Z").toLocaleTimeString()} ·{" "}
          {incident.source === "relay" ? "relayed over the offline mesh" : "sent directly from the app"}
        </p>

        <h3 style={{ margin: "12px 0 2px" }}>{incident.type.replace(/_/g, " ")}</h3>
        <div style={{ fontSize: 13 }}>
          {incident.people_count} {incident.people_count === 1 ? "person" : "people"} affected
          {incident.injury_level && incident.injury_level !== "UNKNOWN" && <> · injury: {incident.injury_level.toLowerCase()}</>}
          {needs && <> · needs: {needs}</>}
        </div>
        {incident.description && (
          <p style={{ fontSize: 13, color: "var(--text-dim)", margin: "6px 0 0" }}>{incident.description}</p>
        )}

        {hasLocation ? (
          <>
            <MapContainer
              key={incident.id}
              center={[lat, lng]}
              zoom={15}
              dragging={false}
              scrollWheelZoom={false}
              doubleClickZoom={false}
              zoomControl={false}
            >
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              />
              <Marker position={[lat, lng]} icon={pulseIcon(UNRATED_PULSE_COLOR)} />
            </MapContainer>
            <div style={{ fontSize: 13, marginBottom: 12 }}>
              <strong>Location:</strong> {lat.toFixed(5)}, {lng.toFixed(5)} ·{" "}
              <a href={`https://www.google.com/maps?q=${lat},${lng}`} target="_blank" rel="noreferrer">
                Open in Google Maps
              </a>
            </div>
          </>
        ) : (
          <p style={{ fontSize: 13, margin: "12px 0", color: "var(--high)" }}>
            <strong>Location unavailable</strong> — the phone didn't send GPS coordinates with this request.
          </p>
        )}

        {error && <p className="error-text">{error}</p>}
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <button className="btn btn-primary" disabled={busy} onClick={acknowledge}>Acknowledge</button>
          {hasLocation && <button className="btn" onClick={viewOnMap}>View on live map</button>}
          <button className="btn" onClick={dismiss}>Dismiss</button>
        </div>
        <p style={{ fontSize: 11, color: "var(--text-dim)", margin: "10px 0 0" }}>
          Dismiss closes this window but the incident keeps blinking on the map until someone acknowledges it.
        </p>
      </div>
    </div>
  );
}
