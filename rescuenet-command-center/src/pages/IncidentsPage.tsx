import { useMemo, useState } from "react";
import { api } from "../api/client";
import { IncidentStatusBadge, SeverityBadge } from "../components/Badges";
import type { IncidentResponse, IncidentStatus, IncidentType, ResourceRecommendation } from "../api/types";

const TYPES: IncidentType[] = [
  "MEDICAL", "FIRE", "FLOOD", "EARTHQUAKE", "ACCIDENT", "TRAPPED",
  "MISSING_PERSON", "BUILDING_COLLAPSE", "SECURITY", "OTHER",
];
const STATUSES: IncidentStatus[] = ["OPEN", "ACKNOWLEDGED", "RESOURCE_ASSIGNED", "RESOLVED", "CLOSED"];

export function IncidentsPage({
  incidents,
  onIncidentUpdated,
}: {
  incidents: IncidentResponse[];
  onIncidentUpdated: (incident: IncidentResponse) => void;
}) {
  const [typeFilter, setTypeFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [selected, setSelected] = useState<IncidentResponse | null>(null);

  const filtered = useMemo(
    () =>
      incidents.filter(
        (i) => (!typeFilter || i.type === typeFilter) && (!statusFilter || i.status === statusFilter)
      ),
    [incidents, typeFilter, statusFilter]
  );

  return (
    <div>
      <div className="page-header">
        <h1>Incidents</h1>
      </div>

      <div className="filters">
        <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)}>
          <option value="">All types</option>
          {TYPES.map((t) => (
            <option key={t} value={t}>
              {t.replace("_", " ")}
            </option>
          ))}
        </select>
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">All statuses</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s.replace("_", " ")}
            </option>
          ))}
        </select>
        <span style={{ fontSize: 12, color: "var(--text-dim)", alignSelf: "center" }}>
          {filtered.length} of {incidents.length} shown
        </span>
      </div>

      <div className="two-col">
        <div className="card" style={{ padding: 0, overflow: "hidden" }}>
          <table>
            <thead>
              <tr>
                <th>Type</th>
                <th>Severity</th>
                <th>People</th>
                <th>Status</th>
                <th>Reported</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((i) => (
                <tr key={i.id} onClick={() => setSelected(i)} style={{ cursor: "pointer" }}>
                  <td>{i.type.replace("_", " ")}</td>
                  <td>
                    <SeverityBadge severity={i.severity} />
                  </td>
                  <td>{i.people_count}</td>
                  <td>
                    <IncidentStatusBadge status={i.status} />
                  </td>
                  <td>{new Date(i.created_at + "Z").toLocaleTimeString()}</td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={5} style={{ textAlign: "center", color: "var(--text-dim)", padding: 24 }}>
                    No incidents match these filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {selected ? (
          <IncidentDetail incident={selected} onUpdated={(updated) => { setSelected(updated); onIncidentUpdated(updated); }} />
        ) : (
          <div className="card" style={{ color: "var(--text-dim)", fontSize: 13 }}>
            Select an incident to see details, assign a resource, or update its status.
          </div>
        )}
      </div>
    </div>
  );
}

function IncidentDetail({
  incident,
  onUpdated,
}: {
  incident: IncidentResponse;
  onUpdated: (i: IncidentResponse) => void;
}) {
  const [updateText, setUpdateText] = useState("");
  const [statusChange, setStatusChange] = useState<IncidentStatus | "">("");
  const [recommendation, setRecommendation] = useState<ResourceRecommendation | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submitUpdate() {
    setBusy(true);
    setError(null);
    try {
      const updated = await api.updateIncident(incident.id, updateText || null, statusChange || null);
      onUpdated(updated);
      setUpdateText("");
      setStatusChange("");
    } catch {
      setError("Update failed — you may need Rescue Operator or Admin role.");
    } finally {
      setBusy(false);
    }
  }

  async function fetchRecommendation() {
    setBusy(true);
    setError(null);
    try {
      setRecommendation(await api.recommendResource(incident.id));
    } catch {
      setError("Couldn't get a recommendation — the incident may be missing a location.");
    } finally {
      setBusy(false);
    }
  }

  async function assignRecommended() {
    if (!recommendation?.recommendation) return;
    setBusy(true);
    try {
      await api.assignResource(recommendation.recommendation.resource_id, incident.id, recommendation.recommendation.eta_minutes);
      const refreshed = await api.getIncident(incident.id);
      onUpdated(refreshed);
    } catch {
      setError("Assignment failed — you may need Rescue Operator or Admin role.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "start" }}>
        <h3 style={{ marginTop: 0 }}>{incident.type.replace("_", " ")}</h3>
        <SeverityBadge severity={incident.severity} />
      </div>
      <p style={{ fontSize: 13, color: "var(--text-dim)" }}>{incident.description || "No description provided."}</p>
      <dl style={{ fontSize: 13 }}>
        <Row label="People affected" value={String(incident.people_count)} />
        <Row label="Injury level" value={incident.injury_level} />
        <Row label="Needs" value={incident.needs || "—"} />
        <Row label="Location" value={incident.latitude ? `${incident.latitude.toFixed(4)}, ${incident.longitude?.toFixed(4)}` : "Unknown"} />
        <Row label="Source" value={incident.source} />
      </dl>

      <hr style={{ border: "none", borderTop: "1px solid var(--border)", margin: "12px 0" }} />

      <h4 style={{ marginBottom: 6 }}>Smart resource match</h4>
      <button className="btn" onClick={fetchRecommendation} disabled={busy}>
        Get recommendation
      </button>
      {recommendation && (
        <div style={{ marginTop: 10, fontSize: 13 }}>
          {recommendation.recommendation ? (
            <>
              <div>
                <strong>{recommendation.recommendation.name}</strong> — {recommendation.recommendation.distance_km} km, ~
                {recommendation.recommendation.eta_minutes} min
                {recommendation.is_simulated_eta && (
                  <span style={{ color: "var(--text-dim)" }}> (straight-line distance, not real routing)</span>
                )}
              </div>
              <div style={{ color: "var(--text-dim)", marginTop: 4 }}>{recommendation.reason}</div>
              <button className="btn btn-primary" style={{ marginTop: 8 }} onClick={assignRecommended} disabled={busy}>
                Assign this resource
              </button>
            </>
          ) : (
            <div style={{ color: "var(--text-dim)" }}>{recommendation.reason}</div>
          )}
        </div>
      )}

      <hr style={{ border: "none", borderTop: "1px solid var(--border)", margin: "12px 0" }} />

      <h4 style={{ marginBottom: 6 }}>Add update</h4>
      <textarea
        rows={2}
        style={{ width: "100%", marginBottom: 8 }}
        placeholder="Status note for the incident log…"
        value={updateText}
        onChange={(e) => setUpdateText(e.target.value)}
      />
      <div style={{ display: "flex", gap: 8 }}>
        <select value={statusChange} onChange={(e) => setStatusChange(e.target.value as IncidentStatus | "")}>
          <option value="">No status change</option>
          {(["OPEN", "ACKNOWLEDGED", "RESOURCE_ASSIGNED", "RESOLVED", "CLOSED"] as IncidentStatus[]).map((s) => (
            <option key={s} value={s}>
              {s.replace("_", " ")}
            </option>
          ))}
        </select>
        <button className="btn btn-primary" onClick={submitUpdate} disabled={busy || (!updateText && !statusChange)}>
          Submit
        </button>
      </div>
      {error && <p className="error-text">{error}</p>}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", padding: "3px 0" }}>
      <dt style={{ color: "var(--text-dim)" }}>{label}</dt>
      <dd style={{ margin: 0, fontWeight: 500 }}>{value}</dd>
    </div>
  );
}
