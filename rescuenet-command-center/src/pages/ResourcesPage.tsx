import { useEffect, useState } from "react";
import { api, ApiError } from "../api/client";
import { ResourceStatusBadge } from "../components/Badges";
import type { ResourceResponse, ResourceStatus, ResourceType } from "../api/types";

const RESOURCE_TYPES: ResourceType[] = [
  "AMBULANCE", "RESCUE_TEAM", "FIRE_TRUCK", "BOAT", "MEDICAL_TEAM", "SHELTER", "VOLUNTEER",
];
const RESOURCE_STATUSES: ResourceStatus[] = ["AVAILABLE", "BUSY", "UNAVAILABLE"];

export function ResourcesPage() {
  const [resources, setResources] = useState<ResourceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);

  function refresh() {
    setLoading(true);
    api
      .listResources()
      .then(setResources)
      .catch(() => setError("Couldn't load resources."))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);

  const byStatus = {
    AVAILABLE: resources.filter((r) => r.status === "AVAILABLE").length,
    BUSY: resources.filter((r) => r.status === "BUSY").length,
    UNAVAILABLE: resources.filter((r) => r.status === "UNAVAILABLE").length,
  };

  return (
    <div>
      <div className="page-header">
        <h1>Resources</h1>
        <div style={{ display: "flex", gap: 8 }}>
          <button className="btn" onClick={refresh}>Refresh</button>
          <button
            className={showAddForm ? "btn" : "btn btn-primary"}
            onClick={() => { setShowAddForm((v) => !v); setEditingId(null); }}
          >
            {showAddForm ? "Cancel" : "Add resource"}
          </button>
        </div>
      </div>

      <div className="grid stat-grid">
        <div className="stat-card">
          <div className="label">Available</div>
          <div className="value" style={{ color: "var(--safe)" }}>{byStatus.AVAILABLE}</div>
        </div>
        <div className="stat-card">
          <div className="label">Busy</div>
          <div className="value" style={{ color: "var(--moderate)" }}>{byStatus.BUSY}</div>
        </div>
        <div className="stat-card">
          <div className="label">Unavailable</div>
          <div className="value" style={{ color: "var(--unknown)" }}>{byStatus.UNAVAILABLE}</div>
        </div>
      </div>

      {showAddForm && (
        <AddResourceForm
          onCreated={() => { refresh(); setShowAddForm(false); }}
          onCancel={() => setShowAddForm(false)}
        />
      )}

      {error && <p className="error-text">{error}</p>}
      {loading ? (
        <p style={{ color: "var(--text-dim)" }}>Loading…</p>
      ) : (
        <div className="card" style={{ padding: 0, overflow: "hidden" }}>
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Type</th>
                <th>Status</th>
                <th>Capacity</th>
                <th>Capabilities</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {resources.map((r) =>
                editingId === r.id ? (
                  <EditResourceRow
                    key={r.id}
                    resource={r}
                    onSaved={() => { refresh(); setEditingId(null); }}
                    onCancel={() => setEditingId(null)}
                    onDeleted={() => { refresh(); setEditingId(null); }}
                  />
                ) : (
                  <tr key={r.id}>
                    <td>{r.name}</td>
                    <td>{r.type.replace("_", " ")}</td>
                    <td>
                      <ResourceStatusBadge status={r.status} />
                    </td>
                    <td>{r.capacity}</td>
                    <td style={{ color: "var(--text-dim)" }}>{r.capabilities || "—"}</td>
                    <td>
                      <button className="btn" style={{ fontSize: 12 }} onClick={() => { setEditingId(r.id); setShowAddForm(false); }}>
                        Edit
                      </button>
                    </td>
                  </tr>
                )
              )}
              {resources.length === 0 && (
                <tr>
                  <td colSpan={6} style={{ textAlign: "center", color: "var(--text-dim)", padding: 24 }}>
                    No resources yet — click "Add resource" above.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
      <p style={{ fontSize: 12, color: "var(--text-dim)", marginTop: 12 }}>
        Assign a resource to a specific incident from the Incidents page's "Smart resource
        match" panel, which shows the reasoning behind each recommendation. Resources can also
        be added or repositioned by clicking directly on the Live Map.
      </p>
    </div>
  );
}

function AddResourceForm({ onCreated, onCancel }: { onCreated: () => void; onCancel: () => void }) {
  const [name, setName] = useState("");
  const [type, setType] = useState<ResourceType>("AMBULANCE");
  const [capacity, setCapacity] = useState(1);
  const [capabilities, setCapabilities] = useState("");
  const [latitude, setLatitude] = useState("");
  const [longitude, setLongitude] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) {
      setError("Enter a name.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.createResource(
        name.trim(),
        type,
        latitude ? Number(latitude) : null,
        longitude ? Number(longitude) : null,
        capacity,
        capabilities.trim(),
      );
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError && err.status === 403
        ? "You need Rescue Operator or Admin role to add resources."
        : "Couldn't add resource. Try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card" onSubmit={submit} style={{ marginBottom: 16 }}>
      <h3 style={{ marginTop: 0 }}>New resource</h3>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
        <div>
          <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Name</label>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Ambulance 5" style={{ width: "100%" }} />
        </div>
        <div>
          <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Type</label>
          <select value={type} onChange={(e) => setType(e.target.value as ResourceType)} style={{ width: "100%" }}>
            {RESOURCE_TYPES.map((t) => (
              <option key={t} value={t}>{t.replace("_", " ")}</option>
            ))}
          </select>
        </div>
        <div>
          <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Capacity</label>
          <input
            type="number"
            min={1}
            value={capacity}
            onChange={(e) => setCapacity(Math.max(1, Number(e.target.value)))}
            style={{ width: "100%" }}
          />
        </div>
        <div>
          <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Capabilities (optional)</label>
          <input value={capabilities} onChange={(e) => setCapabilities(e.target.value)} placeholder="Flood rescue, medical" style={{ width: "100%" }} />
        </div>
        <div>
          <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Latitude (optional)</label>
          <input value={latitude} onChange={(e) => setLatitude(e.target.value)} placeholder="24.8607" style={{ width: "100%" }} />
        </div>
        <div>
          <label style={{ fontSize: 12, display: "block", marginBottom: 4 }}>Longitude (optional)</label>
          <input value={longitude} onChange={(e) => setLongitude(e.target.value)} placeholder="67.0300" style={{ width: "100%" }} />
        </div>
      </div>
      <p style={{ fontSize: 11, color: "var(--text-dim)", marginTop: 6 }}>
        Leave latitude/longitude blank if unknown yet — you can set them later by editing this
        resource, or place it precisely by clicking directly on the Live Map instead.
      </p>
      {error && <p className="error-text">{error}</p>}
      <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? "Saving…" : "Save resource"}
        </button>
        <button type="button" className="btn" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}

function EditResourceRow({
  resource, onSaved, onCancel, onDeleted,
}: { resource: ResourceResponse; onSaved: () => void; onCancel: () => void; onDeleted: () => void }) {
  const [name, setName] = useState(resource.name);
  const [type, setType] = useState<ResourceType>(resource.type);
  const [status, setStatus] = useState<ResourceStatus>(resource.status);
  const [capacity, setCapacity] = useState(resource.capacity);
  const [capabilities, setCapabilities] = useState(resource.capabilities);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    setBusy(true);
    setError(null);
    try {
      await api.updateResource(resource.id, {
        name: name.trim(), type, status, capacity, capabilities: capabilities.trim(),
      });
      onSaved();
    } catch {
      setError("Couldn't save — check your role/permissions.");
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!confirm(`Delete "${resource.name}"? This can't be undone.`)) return;
    setBusy(true);
    setError(null);
    try {
      await api.deleteResource(resource.id);
      onDeleted();
    } catch {
      setError("Couldn't delete — check your role/permissions.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <tr>
      <td><input value={name} onChange={(e) => setName(e.target.value)} style={{ width: "100%" }} /></td>
      <td>
        <select value={type} onChange={(e) => setType(e.target.value as ResourceType)} style={{ width: "100%" }}>
          {RESOURCE_TYPES.map((t) => (
            <option key={t} value={t}>{t.replace("_", " ")}</option>
          ))}
        </select>
      </td>
      <td>
        <select value={status} onChange={(e) => setStatus(e.target.value as ResourceStatus)} style={{ width: "100%" }}>
          {RESOURCE_STATUSES.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
      </td>
      <td>
        <input
          type="number"
          min={1}
          value={capacity}
          onChange={(e) => setCapacity(Math.max(1, Number(e.target.value)))}
          style={{ width: 70 }}
        />
      </td>
      <td><input value={capabilities} onChange={(e) => setCapabilities(e.target.value)} style={{ width: "100%" }} /></td>
      <td>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          <button className="btn btn-primary" style={{ fontSize: 12 }} disabled={busy} onClick={save}>Save</button>
          <button className="btn" style={{ fontSize: 12 }} onClick={onCancel}>Cancel</button>
          <button className="btn" style={{ fontSize: 12, color: "#d8261c" }} disabled={busy} onClick={remove}>Delete</button>
        </div>
        {error && <p className="error-text" style={{ fontSize: 11 }}>{error}</p>}
      </td>
    </tr>
  );
}
