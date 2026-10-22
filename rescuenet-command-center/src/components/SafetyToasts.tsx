import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { CallButton, PhoneLink } from "./PhoneLink";
import type { SafetyCheckin } from "../api/types";

const AUTO_DISMISS_MS = 25_000;

function Toast({ checkin, onDismiss }: { checkin: SafetyCheckin; onDismiss: () => void }) {
  const navigate = useNavigate();
  useEffect(() => {
    const timer = window.setTimeout(onDismiss, AUTO_DISMISS_MS);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [checkin.user_id, checkin.updated_at]);

  const safe = checkin.status === "SAFE";
  return (
    <div className="toast" style={{ borderLeftColor: safe ? "#2fa84f" : "#e1341e" }} role="status">
      <div style={{ fontWeight: 600 }}>
        {safe ? "✓" : "⚠"} {checkin.label} marked themselves {safe ? "SAFE" : "as needing help"}
      </div>
      {checkin.open_incident_type && (
        <div style={{ fontSize: 12, color: "var(--text-dim)" }}>
          Has an open {checkin.open_incident_type.replace(/_/g, " ").toLowerCase()} incident
        </div>
      )}
      <div style={{ fontSize: 13, margin: "4px 0" }}>
        <PhoneLink phone={checkin.phone} />
      </div>
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
        <CallButton phone={checkin.phone} small />
        <button className="btn" style={{ fontSize: 12 }} onClick={() => { navigate("/safety"); onDismiss(); }}>
          View
        </button>
        <button className="btn" style={{ fontSize: 12 }} onClick={onDismiss}>Dismiss</button>
      </div>
    </div>
  );
}

// Small, non-blocking notices (the full-screen pop-up is reserved for new help requests).
export function SafetyToasts({ toasts, onDismiss }: { toasts: SafetyCheckin[]; onDismiss: (userId: string) => void }) {
  if (toasts.length === 0) return null;
  return (
    <div className="toast-stack" aria-live="polite">
      {toasts.map((t) => (
        <Toast key={`${t.user_id}-${t.updated_at}`} checkin={t} onDismiss={() => onDismiss(t.user_id)} />
      ))}
    </div>
  );
}
