import { useEffect, useState } from "react";
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from "recharts";
import { api } from "../api/client";
import type { DashboardStatistics, IncidentResponse } from "../api/types";

export function DashboardPage({ incidents }: { incidents: IncidentResponse[] }) {
  const [stats, setStats] = useState<DashboardStatistics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .dashboardStatistics()
      .then(setStats)
      .catch(() => setError("Couldn't load statistics from the backend."))
      .finally(() => setLoading(false));
  }, [incidents.length]); // refetch whenever a new incident arrives over the socket

  const severityCounts = ["CRITICAL", "HIGH", "MODERATE", "RESOLVED"].map((sev) => ({
    severity: sev,
    count: incidents.filter((i) => (i.severity ?? "UNANALYZED") === sev).length,
  }));

  const typeCounts = Object.entries(
    incidents.reduce<Record<string, number>>((acc, i) => {
      acc[i.type] = (acc[i.type] ?? 0) + 1;
      return acc;
    }, {})
  ).map(([type, count]) => ({ type, count }));

  return (
    <div>
      <div className="page-header">
        <h1>Dashboard</h1>
      </div>

      {error && <p className="error-text">{error}</p>}

      <div className="grid stat-grid">
        <StatCard label="Total Incidents" value={stats?.total_incidents ?? (loading ? "…" : incidents.length)} />
        <StatCard label="Critical" value={stats?.critical_incidents ?? "…"} accent="var(--critical)" />
        <StatCard label="Resolved" value={stats?.resolved_incidents ?? "…"} accent="var(--safe)" />
        <StatCard label="Active Resources" value={stats?.active_resources ?? "…"} />
        <StatCard
          label="Avg. AI Confidence"
          value={stats?.average_ai_confidence != null ? `${stats.average_ai_confidence}%` : "—"}
        />
        <StatCard label="Mesh Messages Received" value={stats?.network_messages_received ?? "…"} />
      </div>

      <div className="two-col">
        <div className="card">
          <h3 style={{ marginTop: 0 }}>Incidents by severity</h3>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={severityCounts}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="severity" fontSize={12} />
              <YAxis allowDecimals={false} fontSize={12} />
              <Tooltip />
              <Bar dataKey="count" fill="#0b1f3a" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="card">
          <h3 style={{ marginTop: 0 }}>Incidents by type</h3>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={typeCounts} layout="vertical" margin={{ left: 20 }}>
              <CartesianGrid strokeDasharray="3 3" horizontal={false} />
              <XAxis type="number" allowDecimals={false} fontSize={12} />
              <YAxis dataKey="type" type="category" fontSize={11} width={110} />
              <Tooltip />
              <Bar dataKey="count" fill="#0fa3a3" radius={[0, 4, 4, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}

function StatCard({ label, value, accent }: { label: string; value: string | number; accent?: string }) {
  return (
    <div className="stat-card">
      <div className="label">{label}</div>
      <div className="value" style={accent ? { color: accent } : undefined}>
        {value}
      </div>
    </div>
  );
}
