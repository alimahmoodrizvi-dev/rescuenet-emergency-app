import { useState } from "react";
import { api } from "../api/client";
import type { ClusterResponse } from "../api/types";

export function ClustersPage() {
  const [radius, setRadius] = useState(2.0);
  const [result, setResult] = useState<ClusterResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function runClustering() {
    setLoading(true);
    setError(null);
    try {
      setResult(await api.clusterIncidents(radius));
    } catch {
      setError("Clustering request failed.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>AI Incident Clusters</h1>
      </div>

      <div className="card" style={{ marginBottom: 20 }}>
        <p style={{ fontSize: 13, color: "var(--text-dim)", marginTop: 0 }}>
          Groups open incidents within a radius of each other and flags likely-related clusters
          (Part 12) — for example, many separate flood reports in the same neighborhood.
          This is a naive proximity pass, not a full spatial index: fine for a demo-scale
          dataset, explicitly not validated at city scale. Treat every result below as an{" "}
          <strong>AI recommendation to review</strong>, not an automatic dispatch.
        </p>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <label style={{ fontSize: 13 }}>
            Radius (km):{" "}
            <input
              type="number"
              min={0.1}
              step={0.1}
              value={radius}
              onChange={(e) => setRadius(Number(e.target.value))}
              style={{ width: 70 }}
            />
          </label>
          <button className="btn btn-primary" onClick={runClustering} disabled={loading}>
            {loading ? "Analyzing…" : "Run clustering"}
          </button>
        </div>
        {error && <p className="error-text">{error}</p>}
      </div>

      {result && (
        <div className="grid" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))" }}>
          {result.clusters.length === 0 && (
            <p style={{ color: "var(--text-dim)" }}>No clusters found within {result.radius_km} km — incidents are spread out.</p>
          )}
          {result.clusters.map((c, idx) => (
            <div className="card" key={idx}>
              <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 8 }}>
                <span style={{ fontSize: 18 }}>⚠️</span>
                <strong>Cluster detected</strong>
              </div>
              <p style={{ fontSize: 13, margin: "4px 0" }}>
                <strong>{c.incident_count}</strong> emergency reports within {result.radius_km} km.
              </p>
              <p style={{ fontSize: 13, margin: "4px 0" }}>
                Likely incident: <strong>{c.likely_type.replace("_", " ")}</strong>
              </p>
              <p style={{ fontSize: 13, margin: "4px 0" }}>
                Estimated affected people: <strong>{c.estimated_people_affected}</strong>
              </p>
              <p style={{ fontSize: 12, color: "var(--text-dim)", margin: "8px 0 0 0" }}>
                Center: {c.center_latitude.toFixed(4)}, {c.center_longitude.toFixed(4)}
              </p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
