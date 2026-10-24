"""
Live push channel for the Command Center (Part 10: `/ws/incidents`). Kept intentionally
simple — one broadcast group, no per-client filtering — since filtering by incident
type/severity/area (Part 9) is expected to happen client-side against the full stream for
this prototype. A production build serving many concurrent command centers would want
server-side topic filtering to reduce bandwidth.

Connections are either *staff* (authenticated Command Center users) or anonymous. Staff get
the full payload of an event; anonymous connections get the scrubbed `public_data` instead
(no phone numbers, no safe/unsafe status) or nothing at all for staff-only events.
"""
import json
from typing import Any, Optional

from fastapi import WebSocket


class ConnectionManager:
    def __init__(self) -> None:
        self.active: list[WebSocket] = []
        self._staff: set[int] = set()  # id() of the staff connections

    async def connect(self, websocket: WebSocket, is_staff: bool = False) -> None:
        await websocket.accept()
        self.active.append(websocket)
        if is_staff:
            self._staff.add(id(websocket))

    def disconnect(self, websocket: WebSocket) -> None:
        if websocket in self.active:
            self.active.remove(websocket)
        self._staff.discard(id(websocket))

    async def broadcast(
        self,
        event_type: str,
        data: dict[str, Any],
        *,
        public_data: Optional[dict[str, Any]] = None,
        staff_only: bool = False,
    ) -> None:
        """`data` goes to staff. Anonymous connections get `public_data` if given, otherwise
        the same `data` — unless `staff_only`, in which case they get nothing."""
        staff_message = json.dumps({"event": event_type, "data": data}, default=str)
        public_message = json.dumps(
            {"event": event_type, "data": public_data if public_data is not None else data}, default=str
        )
        stale: list[WebSocket] = []
        for connection in list(self.active):
            is_staff = id(connection) in self._staff
            if staff_only and not is_staff:
                continue
            try:
                await connection.send_text(staff_message if is_staff else public_message)
            except Exception:
                stale.append(connection)
        for connection in stale:
            self.disconnect(connection)


manager = ConnectionManager()
