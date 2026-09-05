"""
Live push channel for the Command Center (Part 10: `/ws/incidents`). Kept intentionally
simple — one broadcast group, no per-client filtering — since filtering by incident
type/severity/area (Part 9) is expected to happen client-side against the full stream for
this prototype. A production build serving many concurrent command centers would want
server-side topic filtering to reduce bandwidth.
"""
import json
from typing import Any

from fastapi import WebSocket


class ConnectionManager:
    def __init__(self) -> None:
        self.active: list[WebSocket] = []

    async def connect(self, websocket: WebSocket) -> None:
        await websocket.accept()
        self.active.append(websocket)

    def disconnect(self, websocket: WebSocket) -> None:
        if websocket in self.active:
            self.active.remove(websocket)

    async def broadcast(self, event_type: str, data: dict[str, Any]) -> None:
        message = json.dumps({"event": event_type, "data": data}, default=str)
        stale: list[WebSocket] = []
        for connection in self.active:
            try:
                await connection.send_text(message)
            except Exception:
                stale.append(connection)
        for connection in stale:
            self.disconnect(connection)


manager = ConnectionManager()
