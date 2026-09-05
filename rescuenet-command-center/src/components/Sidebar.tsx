import { NavLink } from "react-router-dom";
import type { ConnectionState } from "../hooks/useIncidentSocket";

const links = [
  { to: "/", label: "Dashboard", icon: "\u25A4" },
  { to: "/map", label: "Live Map", icon: "\u25C9" },
  { to: "/incidents", label: "Incidents", icon: "\u2691" },
  { to: "/clusters", label: "AI Clusters", icon: "\u2728" },
  { to: "/resources", label: "Resources", icon: "\u2699" },
  { to: "/alerts", label: "Alerts", icon: "\u26A0" },
];

export function Sidebar({ connState, role, onLogout }: { connState: ConnectionState; role: string | null; onLogout: () => void }) {
  return (
    <nav className="sidebar">
      <div className="sidebar-brand">RescueNet<br /><span style={{ fontSize: 12, fontWeight: 400, opacity: 0.7 }}>Command Center</span></div>
      <div className="sidebar-nav">
        {links.map((l) => (
          <NavLink key={l.to} to={l.to} end={l.to === "/"} className={({ isActive }) => (isActive ? "active" : "")}>
            <span>{l.icon}</span> {l.label}
          </NavLink>
        ))}
      </div>
      <div className="sidebar-footer">
        <div style={{ marginBottom: 8 }}>
          <span className={`conn-dot conn-${connState}`} />
          {connState === "open" ? "Live" : connState === "connecting" ? "Connecting…" : "Disconnected"}
        </div>
        <div style={{ marginBottom: 8 }}>Role: {role ?? "—"}</div>
        <button className="btn" onClick={onLogout} style={{ width: "100%" }}>
          Log out
        </button>
      </div>
    </nav>
  );
}
