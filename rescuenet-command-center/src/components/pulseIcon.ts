import L from "leaflet";

// A blinking dot with an expanding ring, used for incidents that haven't been acknowledged
// yet (status OPEN). Styled by the .pulse-marker rules in index.css; the colour is passed in
// through a CSS variable so one set of rules covers every severity colour.
export function pulseIcon(color: string) {
  return L.divIcon({
    className: "pulse-marker",
    html: `<span class="pulse-ring" style="--c:${color}"></span><span class="pulse-dot" style="--c:${color}"></span>`,
    iconSize: [20, 20],
    iconAnchor: [10, 10],
  });
}

// Unacknowledged help requests are urgent even before AI has rated their severity.
export const UNRATED_PULSE_COLOR = "#e1341e";
