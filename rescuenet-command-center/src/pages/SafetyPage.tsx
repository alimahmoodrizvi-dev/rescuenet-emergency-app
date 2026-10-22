import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CallButton, PhoneLink, formatUtcTime } from "../components/PhoneLink";
import type { CheckinStatus, SafetyCheckin } from "../api/types";

const STATUS_STYLE: Record<CheckinStatus, { label: string; bg: string; fg: string }> = {
  SAFE: { label: "SAFE", bg: "#dff3e4", fg: "#1d7a3a" },
  NEEDS_HELP: { label: "NEEDS HELP", bg: "#fde4e1", fg: "#b3261e" },
  UNKNOWN: { label: "UNKNOWN", bg: "#eceff3", fg: "#5b6577" },
};

function StatusPill({ status }: { status: CheckinStatus }) {
  const s = STATUS_STYLE[status] ?? STATUS_STYLE.UNKNOWN;
  return (
    <span style={{ background: s.bg, color: s.fg, borderRadius: 999, padding: "2px 10px", fontSize: 12, fontWeight: 700 }}>
      {s.label}
    </span>
  );
}

// People who used the mobile app's "I'm safe" button, newest first, with the phone number they
// chose to share so staff can reach them.
export function SafetyPage({ checkins }: { checkins: SafetyCheckin[] }) {
  const [filter, setFilter] = useState<"ALL" | CheckinStatus>("ALL");
  const navigate = useNavigate();

  const shown = useMemo(
    () => (filter === "ALL" ? checkins : checkins.filter((c) => c.status === filter)),
    [checkins, filter]
  );
  const count = (s: CheckinStatus) => checkins.filter((c) => c.status === s).length;

  return (
    <div>
      <div className="page-header">
        <h1>Safe check-ins</h1>
      </div>

      <div className="grid stat-grid">
        <div className="stat-card">
          <div className="label">Marked safe</div>
          <div className="value" style={{ color: "var(--safe)" }}>{count("SAFE")}</div>
        </div>
        <div className="stat-card">
          <div className="label">Need help</div>
          <div className="value" style={{ color: "var(--critical)" }}>{count("NEEDS_HELP")}</div>
        </div>
        <div className="stat-card">
          <div className="label">Safe, with an open incident</div>
          <div className="value">{checkins.filter((c) => c.status === "SAFE" && c.open_incident_id).length}</div>
        </div>
      </div>

      <div style={{ display: "flex", gap: 8, margin: "12px 0" }}>
        {(["ALL", "SAFE", "NEEDS_HELP"] as const).map((f) => (
          <button key={f} className={filter === f ? "btn btn-primary" : "btn"} onClick={() => setFilter(f)}>
            {f === "ALL" ? "Everyone" : STATUS_STYLE[f].label}
          </button>
        ))}
      </div>

      <div className="card" style={{ padding: 0, overflow: "hidden" }}>
        <table>
          <thead>
            <tr>
              <th>Status</th>
              <th>Person</th>
              <th>Phone</th>
              <th>Updated</th>
              <th>Open incident</th>
              <th>Location</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {shown.map((c) => (
              <tr key={c.user_id}>
                <td><StatusPill status={c.status} /></td>
                <td>{c.label}</td>
                <td><PhoneLink phone={c.phone} /></td>
                <td>{formatUtcTime(c.updated_at)}</td>
                <td>{c.open_incident_type ? c.open_incident_type.replace(/_/g, " ") : "—"}</td>
                <td>
                  {c.latitude != null && c.longitude != null ? (
                    <button
                      className="btn"
                      style={{ fontSize: 12 }}
                      onClick={() => navigate("/map", { state: { focusLat: c.latitude, focusLng: c.longitude } })}
                    >
                      Show on map
                    </button>
                  ) : (
                    <span style={{ color: "var(--text-dim)" }}>not shared</span>
                  )}
                </td>
                <td><CallButton phone={c.phone} small /></td>
              </tr>
            ))}
            {shown.length === 0 && (
              <tr>
                <td colSpan={7} style={{ textAlign: "center", color: "var(--text-dim)", padding: 24 }}>
                  No check-ins yet. They appear here the moment someone taps "I'm safe" in the app.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
