import { useEffect, useState } from "react";
import { api } from "../api/client";
import { AlertSeverityBadge, DemoTag } from "../components/Badges";
import type { AlertResponse, AlertSeverity, AlertType } from "../api/types";

const TYPES: AlertType[] = [
  "FLOOD_WARNING", "EARTHQUAKE_WARNING", "FIRE_WARNING", "EVACUATION_ORDER",
  "ROAD_CLOSURE", "SHELTER_OPENING", "MISSING_PERSON",
];
const SEVERITIES: AlertSeverity[] = ["CRITICAL", "WARNING", "INFORMATION"];

export function AlertsPage() {
  const [alerts, setAlerts] = useState<AlertResponse[]>([]);
  const [type, setType] = useState<AlertType>("FLOOD_WARNING");
  const [severity, setSeverity] = useState<AlertSeverity>("WARNING");
  const [message, setMessage] = useState("");
  const [isDemo, setIsDemo] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function refresh() {
    api.listAlerts().then(setAlerts).catch(() => {});
  }
  useEffect(refresh, []);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.createAlert(type, severity, message, isDemo);
      setMessage("");
      refresh();
    } catch {
      setError("Couldn't create alert — you may need Rescue Operator or Admin role.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>Alerts</h1>
      </div>

      <div className="two-col">
        <div className="card" style={{ padding: 0, overflow: "hidden" }}>
          <table>
            <thead>
              <tr>
                <th>Type</th>
                <th>Severity</th>
                <th>Message</th>
                <th></th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {alerts.map((a) => (
                <tr key={a.id}>
                  <td>{a.type.replace("_", " ")}</td>
                  <td>
                    <AlertSeverityBadge severity={a.severity} />
                  </td>
                  <td>{a.message}</td>
                  <td>
                    <DemoTag isDemo={a.is_demo} />
                  </td>
                  <td>{new Date(a.created_at + "Z").toLocaleTimeString()}</td>
                </tr>
              ))}
              {alerts.length === 0 && (
                <tr>
                  <td colSpan={5} style={{ textAlign: "center", color: "var(--text-dim)", padding: 24 }}>
                    No active alerts.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <form className="card" onSubmit={submit}>
          <h3 style={{ marginTop: 0 }}>Broadcast an alert</h3>
          <p style={{ fontSize: 12, color: "var(--text-dim)" }}>
            There is no path in this system to mark an alert as an official government
            warning — that integration is future work (Part 17). Every alert created here is
            either explicitly a demo/test alert or a RescueNet-originated advisory, and is
            labeled as such to every citizen who sees it.
          </p>

          <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Type</label>
          <select value={type} onChange={(e) => setType(e.target.value as AlertType)} style={{ width: "100%", marginBottom: 10 }}>
            {TYPES.map((t) => (
              <option key={t} value={t}>
                {t.replace("_", " ")}
              </option>
            ))}
          </select>

          <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Severity</label>
          <select value={severity} onChange={(e) => setSeverity(e.target.value as AlertSeverity)} style={{ width: "100%", marginBottom: 10 }}>
            {SEVERITIES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>

          <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Message</label>
          <textarea
            rows={3}
            required
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            style={{ width: "100%", marginBottom: 10 }}
          />

          <label style={{ fontSize: 13, display: "flex", alignItems: "center", gap: 6, marginBottom: 12 }}>
            <input type="checkbox" checked={isDemo} onChange={(e) => setIsDemo(e.target.checked)} />
            Mark as demo/test alert
          </label>

          <button type="submit" className="btn btn-primary" disabled={busy} style={{ width: "100%" }}>
            {busy ? "Sending…" : "Broadcast"}
          </button>
          {error && <p className="error-text">{error}</p>}
        </form>
      </div>
    </div>
  );
}
