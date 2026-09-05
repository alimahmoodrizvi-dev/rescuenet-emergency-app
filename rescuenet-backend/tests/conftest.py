"""
Shared test setup. RESCUENET_DATABASE_URL must be set, and app.main imported, before any
other test module imports app.main — Settings() and the SQLAlchemy engine are both
module-level singletons, so importing app.main more than once with different env vars
(which happened when each test file set its own) silently reuses the first engine created,
leading to confusing cross-file database state. Centralizing here fixes that: exactly one
TestClient, one engine, one temp SQLite file for the whole test session.
"""
import os

os.environ["RESCUENET_DATABASE_URL"] = "sqlite:///./test_rescuenet_shared.db"

import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture(scope="session")
def client():
    with TestClient(app) as c:
        yield c


@pytest.fixture(autouse=True, scope="session")
def cleanup_db_after_session():
    yield
    # Dispose the SQLAlchemy engine's connection pool before deleting the file — on Windows,
    # SQLite keeps an OS-level file lock open for as long as any connection exists, and
    # os.remove() on a still-locked file raises PermissionError (WinError 32). Linux doesn't
    # enforce this the same way, which is why this only surfaced on Windows. Disposing first
    # closes every pooled connection so the file is actually free to delete.
    from app.database import engine
    engine.dispose()

    if os.path.exists("test_rescuenet_shared.db"):
        try:
            os.remove("test_rescuenet_shared.db")
        except PermissionError:
            # Best-effort cleanup — a leftover test DB file is harmless (each session uses a
            # fresh one anyway) and must never fail the test run itself.
            pass
