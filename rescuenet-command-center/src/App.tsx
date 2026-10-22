import { useCallback, useEffect, useState } from "react";
import { HashRouter, Navigate, Route, Routes } from "react-router-dom";
import { api } from "./api/client";
import { useAuth } from "./hooks/useAuth";
import { useIncidentSocket } from "./hooks/useIncidentSocket";
import { Sidebar } from "./components/Sidebar";
import { HelpAlertModal } from "./components/HelpAlertModal";
import { SafetyToasts } from "./components/SafetyToasts";
import { LoginPage } from "./pages/LoginPage";
import { DashboardPage } from "./pages/DashboardPage";
import { MapPage } from "./pages/MapPage";
import { IncidentsPage } from "./pages/IncidentsPage";
import { ClustersPage } from "./pages/ClustersPage";
import { ResourcesPage } from "./pages/ResourcesPage";
import { AlertsPage } from "./pages/AlertsPage";
import { SafetyPage } from "./pages/SafetyPage";
import type { IncidentResponse, SafetyCheckin, WsMessage } from "./api/types";

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
  // New help requests that arrived live and haven't been dismissed/acknowledged yet.
  const [alertQueue, setAlertQueue] = useState<IncidentResponse[]>([]);
  // People who used the app's "I'm safe" button (latest status each), and live notices about it.
  const [checkins, setCheckins] = useState<SafetyCheckin[]>([]);
  const [toasts, setToasts] = useState<SafetyCheckin[]>([]);

  // Initial snapshot via REST, then live deltas via WebSocket (Part 10/23) — the socket
  // never replays history, so the REST call is not optional.
  useEffect(() => {
    api
      .listIncidents()
      .then(setIncidents)
      .catch(() => setLoadError("Couldn't load incidents from the backend. Check the backend URL and that it's running."));
    api.listSafetyCheckins().then(setCheckins).catch(() => {});
  }, []);

  const handleWsMessage = useCallback((msg: WsMessage) => {
    if (msg.event === "incident_created") {
      const incident = msg.data as IncidentResponse;
      if (!incident.id) return; // sync_batch broadcasts a lighter payload without a fetched row
      setIncidents((prev) => (prev.some((i) => i.id === incident.id) ? prev : [incident, ...prev]));
      setAlertQueue((prev) => (prev.some((i) => i.id === incident.id) ? prev : [...prev, incident]));
    } else if (msg.event === "incident_updated") {
      const incident = msg.data as IncidentResponse;
      setIncidents((prev) => prev.map((i) => (i.id === incident.id ? incident : i)));
    } else if (msg.event === "safety_status") {
      const checkin = msg.data as SafetyCheckin;
      setCheckins((prev) => [checkin, ...prev.filter((c) => c.user_id !== checkin.user_id)]);
      setToasts((prev) => [...prev.filter((t) => t.user_id !== checkin.user_id), checkin]);
    } else if (msg.event === "incident_deleted") {
      const { id } = msg.data as { id: string };
      setIncidents((prev) => prev.filter((i) => i.id !== id));
      setAlertQueue((prev) => prev.filter((i) => i.id !== id));
    }
  }, []);

  const connState = useIncidentSocket(handleWsMessage);

  function handleIncidentUpdated(updated: IncidentResponse) {
    setIncidents((prev) => prev.map((i) => (i.id === updated.id ? updated : i)));
  }

  function handleIncidentDeleted(id: string) {
    setIncidents((prev) => prev.filter((i) => i.id !== id));
    setAlertQueue((prev) => prev.filter((i) => i.id !== id));
  }

  function dismissAlert(id: string) {
    setAlertQueue((prev) => prev.filter((i) => i.id !== id));
  }

  function dismissToast(userId: string) {
    setToasts((prev) => prev.filter((t) => t.user_id !== userId));
  }

  return (
    <HashRouter>
      <div className="app-shell">
        <HelpAlertModal queue={alertQueue} onDismiss={dismissAlert} onAcknowledged={handleIncidentUpdated} />
        <SafetyToasts toasts={toasts} onDismiss={dismissToast} />
        <Sidebar connState={connState} role={role} onLogout={onLogout} />
        <main className="main-content">
          {loadError && <p className="error-text">{loadError}</p>}
          <Routes>
            <Route path="/" element={<DashboardPage incidents={incidents} />} />
            <Route path="/map" element={<MapPage incidents={incidents} checkins={checkins} onIncidentDeleted={handleIncidentDeleted} onIncidentUpdated={handleIncidentUpdated} />} />
            <Route
              path="/incidents"
              element={<IncidentsPage incidents={incidents} onIncidentUpdated={handleIncidentUpdated} onIncidentDeleted={handleIncidentDeleted} />}
            />
            <Route path="/safety" element={<SafetyPage checkins={checkins} />} />
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
