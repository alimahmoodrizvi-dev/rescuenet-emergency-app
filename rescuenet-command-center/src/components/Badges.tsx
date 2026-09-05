import type { AlertSeverity, IncidentStatus, ResourceStatus, Severity } from "../api/types";

export function SeverityBadge({ severity }: { severity: Severity | null }) {
  if (!severity) return <span className="badge badge-unknown">UNANALYZED</span>;
  const cls = `badge badge-${severity.toLowerCase()}`;
  return <span className={cls}>{severity}</span>;
}

export function AlertSeverityBadge({ severity }: { severity: AlertSeverity }) {
  const map: Record<AlertSeverity, string> = {
    CRITICAL: "badge-critical",
    WARNING: "badge-high",
    INFORMATION: "badge-moderate",
  };
  return <span className={`badge ${map[severity]}`}>{severity}</span>;
}

export function ResourceStatusBadge({ status }: { status: ResourceStatus }) {
  const map: Record<ResourceStatus, string> = {
    AVAILABLE: "badge-available",
    BUSY: "badge-moderate",
    UNAVAILABLE: "badge-unknown",
  };
  return <span className={`badge ${map[status]}`}>{status}</span>;
}

export function IncidentStatusBadge({ status }: { status: IncidentStatus }) {
  const map: Record<IncidentStatus, string> = {
    OPEN: "badge-critical",
    ACKNOWLEDGED: "badge-high",
    RESOURCE_ASSIGNED: "badge-moderate",
    RESOLVED: "badge-resolved",
    CLOSED: "badge-unknown",
  };
  return <span className={`badge ${map[status]}`}>{status.replace("_", " ")}</span>;
}

export function DemoTag({ isDemo }: { isDemo: boolean }) {
  if (!isDemo) return null;
  return <span className="badge badge-demo">DEMO</span>;
}
