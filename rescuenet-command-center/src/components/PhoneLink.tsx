import type { CSSProperties } from "react";

// The backend only ever stores numbers shaped like this, but the dashboard checks again before
// putting one into a tel: link, so a malformed value can never become a different kind of link.
const SAFE_PHONE = /^\+?[0-9]{7,15}$/;

export function isCallable(phone: string | null | undefined): phone is string {
  return !!phone && SAFE_PHONE.test(phone);
}

/** Backend timestamps are UTC without a zone marker, and websocket ones use a space, not a "T". */
export function formatUtcTime(value: string): string {
  return new Date(value.replace(" ", "T") + "Z").toLocaleTimeString();
}

export function PhoneLink({ phone }: { phone: string | null | undefined }) {
  if (!isCallable(phone)) {
    return <span style={{ color: "var(--text-dim)" }}>No phone number shared</span>;
  }
  return <a href={`tel:${phone}`}>{phone}</a>;
}

const callStyle: CSSProperties = { textDecoration: "none", display: "inline-block" };

/** A one-tap call button (opens the default calling app, e.g. Teams/Phone Link/softphone). */
export function CallButton({ phone, small }: { phone: string | null | undefined; small?: boolean }) {
  if (!isCallable(phone)) return null;
  return (
    <a className="btn btn-primary" href={`tel:${phone}`} style={{ ...callStyle, fontSize: small ? 12 : undefined }}>
      Call {phone}
    </a>
  );
}

/** Shown wherever an incident's reporter has since marked themselves safe. */
export function ReporterSafeBadge({ at }: { at?: string | null }) {
  return (
    <span
      title={at ? `Reporter marked themselves safe at ${formatUtcTime(at)}` : "Reporter marked themselves safe"}
      style={{
        display: "inline-block", marginLeft: 6, padding: "1px 8px", borderRadius: 999, fontSize: 11,
        fontWeight: 700, background: "#dff3e4", color: "#1d7a3a", border: "1px solid #9fd5ae",
      }}
    >
      ✓ SAFE
    </span>
  );
}
