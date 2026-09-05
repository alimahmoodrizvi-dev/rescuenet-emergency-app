import { useEffect, useState } from "react";
import { api } from "../api/client";
import { ResourceStatusBadge } from "../components/Badges";
import type { ResourceResponse } from "../api/types";

export function ResourcesPage() {
  const [resources, setResources] = useState<ResourceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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
        <button className="btn" onClick={refresh}>
          Refresh
        </button>
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
              </tr>
            </thead>
            <tbody>
              {resources.map((r) => (
                <tr key={r.id}>
                  <td>{r.name}</td>
                  <td>{r.type.replace("_", " ")}</td>
                  <td>
                    <ResourceStatusBadge status={r.status} />
                  </td>
                  <td>{r.capacity}</td>
                  <td style={{ color: "var(--text-dim)" }}>{r.capabilities || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p style={{ fontSize: 12, color: "var(--text-dim)", marginTop: 12 }}>
        Assign a resource to a specific incident from the Incidents page's "Smart resource
        match" panel, which shows the reasoning behind each recommendation.
      </p>
    </div>
  );
}
