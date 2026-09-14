import { useEffect, useState } from "react";
import { api } from "../api/client";
import { AlertSeverityBadge, DemoTag } from "../components/Badges";
import type { AlertResponse, AlertSeverity, AlertType, HazardTypeResponse } from "../api/types";

const SEVERITIES: AlertSeverity[] = ["CRITICAL", "WARNING", "INFORMATION"];

export function AlertsPage() {
  const [alerts, setAlerts] = useState<AlertResponse[]>([]);
  const [hazardTypes, setHazardTypes] = useState<HazardTypeResponse[]>([]);
  const [type, setType] = useState<AlertType>("");
  const [severity, setSeverity] = useState<AlertSeverity>("WARNING");
  const [message, setMessage] = useState("");
  const [isDemo, setIsDemo] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function refreshAlerts() {
    api.listAlerts().then(setAlerts).catch(() => {});
  }
  function refreshHazardTypes() {
    api.listHazardTypes().then((types) => {
      setHazardTypes(types);
      // Keep the broadcast form's selection valid if the list changes underneath it.
      setType((current) => (types.some((t) => t.name === current) ? current : (types[0]?.name ?? "")));
    }).catch(() => {});
  }

  useEffect(() => {
    refreshAlerts();
    refreshHazardTypes();
  }, []);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!type) {
      setError("Add at least one hazard type below before broadcasting.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.createAlert(type, severity, message, isDemo);
      setMessage("");
      refreshAlerts();
    } catch {
      setError("Couldn't create alert — you may need Rescue Operator or Admin role.");
    } finally {
      setBusy(false);
    }
  }

  async function clearAlert(id: string) {
    if (!confirm("Delete this alert? This can't be undone.")) return;
    try {
      await api.clearAlert(id);
      setAlerts((prev) => prev.filter((a) => a.id !== id));
    } catch {
      setError("Couldn't delete alert — you may need Rescue Operator or Admin role.");
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
                <th></th>
              </tr>
            </thead>
            <tbody>
              {alerts.map((a) => (
                <tr key={a.id}>
                  <td>{a.type.replace(/_/g, " ")}</td>
                  <td>
                    <AlertSeverityBadge severity={a.severity} />
                  </td>
                  <td>{a.message}</td>
                  <td>
                    <DemoTag isDemo={a.is_demo} />
                  </td>
                  <td>{new Date(a.created_at + "Z").toLocaleTimeString()}</td>
                  <td>
                    <button className="btn" style={{ fontSize: 12, color: "#d8261c" }} onClick={() => clearAlert(a.id)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
              {alerts.length === 0 && (
                <tr>
                  <td colSpan={6} style={{ textAlign: "center", color: "var(--text-dim)", padding: 24 }}>
                    No active alerts.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <form className="card" onSubmit={submit}>
            <h3 style={{ marginTop: 0 }}>Broadcast an alert</h3>
            <p style={{ fontSize: 12, color: "var(--text-dim)" }}>
              There is no path in this system to mark an alert as an official government
              warning — that integration is future work (Part 17). Every alert created here is
              either explicitly a demo/test alert or a RescueNet-originated advisory, and is
              labeled as such to every citizen who sees it.
            </p>

            <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Type</label>
            <select value={type} onChange={(e) => setType(e.target.value)} style={{ width: "100%", marginBottom: 10 }}>
              {hazardTypes.length === 0 && <option value="">No hazard types yet — add one below</option>}
              {hazardTypes.map((t) => (
                <option key={t.id} value={t.name}>
                  {t.name.replace(/_/g, " ")}
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

          <HazardTypeManager hazardTypes={hazardTypes} onChanged={refreshHazardTypes} />
        </div>
      </div>
    </div>
  );
}

function HazardTypeManager({
  hazardTypes, onChanged,
}: { hazardTypes: HazardTypeResponse[]; onChanged: () => void }) {
  const [newName, setNewName] = useState("");
  const [newColor, setNewColor] = useState("#8a93a6");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const [editColor, setEditColor] = useState("#8a93a6");

  async function addType(e: React.FormEvent) {
    e.preventDefault();
    if (!newName.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await api.createHazardType(newName.trim(), newColor);
      setNewName("");
      onChanged();
    } catch {
      setError("Couldn't add hazard type — it may already exist, or you may need Rescue Operator or Admin role.");
    } finally {
      setBusy(false);
    }
  }

  function startEdit(t: HazardTypeResponse) {
    setEditingId(t.id);
    setEditName(t.name);
    setEditColor(t.color);
  }

  async function saveEdit(id: string) {
    setBusy(true);
    setError(null);
    try {
      await api.updateHazardType(id, { name: editName.trim(), color: editColor });
      setEditingId(null);
      onChanged();
    } catch {
      setError("Couldn't save changes.");
    } finally {
      setBusy(false);
    }
  }

  async function remove(t: HazardTypeResponse) {
    if (!confirm(`Delete hazard type "${t.name}"? Existing alerts using it are kept, but it won't be selectable anymore.`)) return;
    setBusy(true);
    setError(null);
    try {
      await api.deleteHazardType(t.id);
      onChanged();
    } catch {
      setError("Couldn't delete hazard type.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card">
      <h3 style={{ marginTop: 0 }}>Hazard types</h3>
      <p style={{ fontSize: 12, color: "var(--text-dim)" }}>
        These are the categories available when broadcasting an alert or marking an affected
        area on the Live Map. Add your own (e.g. "Landslide") alongside the built-in ones.
      </p>

      <div style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: 12 }}>
        {hazardTypes.map((t) =>
          editingId === t.id ? (
            <div key={t.id} style={{ display: "flex", gap: 6, alignItems: "center" }}>
              <input type="color" value={editColor} onChange={(e) => setEditColor(e.target.value)} style={{ width: 32, height: 28, padding: 0 }} />
              <input value={editName} onChange={(e) => setEditName(e.target.value)} style={{ flex: 1 }} />
              <button className="btn btn-primary" style={{ fontSize: 12 }} disabled={busy} onClick={() => saveEdit(t.id)}>Save</button>
              <button className="btn" style={{ fontSize: 12 }} onClick={() => setEditingId(null)}>Cancel</button>
            </div>
          ) : (
            <div key={t.id} style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <span style={{ width: 14, height: 14, borderRadius: "50%", background: t.color, display: "inline-block", flexShrink: 0 }} />
              <span style={{ flex: 1, fontSize: 13 }}>{t.name.replace(/_/g, " ")}</span>
              <button className="btn" style={{ fontSize: 12 }} onClick={() => startEdit(t)}>Edit</button>
              <button className="btn" style={{ fontSize: 12, color: "#d8261c" }} onClick={() => remove(t)}>Delete</button>
            </div>
          )
        )}
        {hazardTypes.length === 0 && (
          <p style={{ fontSize: 12, color: "var(--text-dim)" }}>No hazard types yet — add the first one below.</p>
        )}
      </div>

      <form onSubmit={addType} style={{ display: "flex", gap: 6, alignItems: "center" }}>
        <input type="color" value={newColor} onChange={(e) => setNewColor(e.target.value)} style={{ width: 32, height: 32, padding: 0 }} />
        <input
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          placeholder="e.g. Landslide"
          style={{ flex: 1 }}
        />
        <button type="submit" className="btn btn-primary" disabled={busy}>Add</button>
      </form>
      {error && <p className="error-text">{error}</p>}
    </div>
  );
}
