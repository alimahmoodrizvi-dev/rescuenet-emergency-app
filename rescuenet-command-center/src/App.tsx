import { useCallback, useEffect, useState } from "react";
import { HashRouter, Navigate, Route, Routes } from "react-router-dom";
import { api } from "./api/client";
import { useAuth } from "./hooks/useAuth";
import { useIncidentSocket } from "./hooks/useIncidentSocket";
import { Sidebar } from "./components/Sidebar";
import { LoginPage } from "./pages/LoginPage";
import { DashboardPage } from "./pages/DashboardPage";
import { MapPage } from "./pages/MapPage";
import { IncidentsPage } from "./pages/IncidentsPage";
import { ClustersPage } from "./pages/ClustersPage";
import { ResourcesPage } from "./pages/ResourcesPage";
import { AlertsPage } from "./pages/AlertsPage";
import type { IncidentResponse, WsMessage } from "./api/types";

export default function App() {
  const auth = useAuth();

  if (!auth.isAuthenticated) {
    return <LoginPage onLogin={auth.login} />;
  }

  return <AuthenticatedApp role={auth.role} onLogout={auth.logout} />;
}

function AuthenticatedApp({ role, onLogout }: { role: string | null; onLogout: () => void }) {
  const [incidents, setIncidents] = useState<IncidentResponse[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);

  // Initial snapshot via REST, then live deltas via WebSocket (Part 10/23) — the socket
  // never replays history, so the REST call is not optional.
  useEffect(() => {
    api
      .listIncidents()
      .then(setIncidents)
      .catch(() => setLoadError("Couldn't load incidents from the backend. Check the backend URL and that it's running."));
  }, []);

  const handleWsMessage = useCallback((msg: WsMessage) => {
    if (msg.event === "incident_created") {
      const incident = msg.data as IncidentResponse;
      if (!incident.id) return; // sync_batch broadcasts a lighter payload without a fetched row
      setIncidents((prev) => (prev.some((i) => i.id === incident.id) ? prev : [incident, ...prev]));
    } else if (msg.event === "incident_updated") {
      const incident = msg.data as IncidentResponse;
      setIncidents((prev) => prev.map((i) => (i.id === incident.id ? incident : i)));
    }
  }, []);

  const connState = useIncidentSocket(handleWsMessage);

  function handleIncidentUpdated(updated: IncidentResponse) {
    setIncidents((prev) => prev.map((i) => (i.id === updated.id ? updated : i)));
  }

  return (
    <HashRouter>
      <div className="app-shell">
        <Sidebar connState={connState} role={role} onLogout={onLogout} />
        <main className="main-content">
          {loadError && <p className="error-text">{loadError}</p>}
          <Routes>
            <Route path="/" element={<DashboardPage incidents={incidents} />} />
            <Route path="/map" element={<MapPage incidents={incidents} />} />
            <Route
              path="/incidents"
              element={<IncidentsPage incidents={incidents} onIncidentUpdated={handleIncidentUpdated} />}
            />
            <Route path="/clusters" element={<ClustersPage />} />
            <Route path="/resources" element={<ResourcesPage />} />
            <Route path="/alerts" element={<AlertsPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
      </div>
    </HashRouter>
  );
}
