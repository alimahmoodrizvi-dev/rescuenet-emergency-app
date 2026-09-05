import { useEffect, useRef, useState } from "react";
import { websocketUrl } from "../api/client";
import type { WsMessage } from "../api/types";

export type ConnectionState = "connecting" | "open" | "closed";

/**
 * Connects to /ws/incidents (Part 10/23) and calls onMessage for every event the backend
 * broadcasts (incident_created, incident_updated, resource_assigned, alert_created).
 * Reconnects with a short fixed backoff on drop — a command center screen left open for
 * hours during a real disaster shouldn't need a manual refresh to keep receiving updates.
 */
export function useIncidentSocket(onMessage: (msg: WsMessage) => void) {
  const [state, setState] = useState<ConnectionState>("connecting");
  const onMessageRef = useRef(onMessage);
  useEffect(() => {
    onMessageRef.current = onMessage;
  }, [onMessage]);

  useEffect(() => {
    let socket: WebSocket | null = null;
    let reconnectTimer: number | undefined;
    let cancelled = false;

    function connect() {
      setState("connecting");
      socket = new WebSocket(websocketUrl());

      socket.onopen = () => setState("open");
      socket.onclose = () => {
        setState("closed");
        if (!cancelled) reconnectTimer = window.setTimeout(connect, 3000);
      };
      socket.onerror = () => socket?.close();
      socket.onmessage = (event) => {
        try {
          const parsed = JSON.parse(event.data) as WsMessage;
          onMessageRef.current(parsed);
        } catch {
          // Non-JSON frame — ignore rather than crash the dashboard.
        }
      };
    }

    connect();
    return () => {
      cancelled = true;
      window.clearTimeout(reconnectTimer);
      socket?.close();
    };
  }, []);

  return state;
}
