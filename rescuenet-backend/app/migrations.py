"""Tiny additive schema migrations, run at startup.

`Base.metadata.create_all()` only creates tables that don't exist yet; it never adds a column
to a table that already does. So when a model gains a column, a deployed database (Render's
Postgres) keeps the old shape and inserts fail. This adds any missing columns — and only adds,
never drops or alters — so it's safe to run on every start, on SQLite and PostgreSQL alike.
"""
from __future__ import annotations

import logging
from typing import Optional

from sqlalchemy import inspect, text
from sqlalchemy.engine import Engine
from sqlalchemy.exc import OperationalError, ProgrammingError

logger = logging.getLogger("rescuenet.migrations")

# (table, column, column DDL). Append here when a model gains a nullable column.
ADDED_COLUMNS: list[tuple[str, str, str]] = [
    ("emergency_incidents", "reporter_phone", "VARCHAR"),
    ("users", "phone", "VARCHAR"),
]


def _column_state(engine: Engine, table: str, column: str) -> Optional[bool]:
    """None if the table doesn't exist yet, otherwise whether the column is there. Always asks
    the database afresh (a new Inspector each time) so it never reports a stale answer."""
    inspector = inspect(engine)
    if table not in inspector.get_table_names():
        return None
    return column in {c["name"] for c in inspector.get_columns(table)}


def ensure_columns(engine: Engine) -> list[str]:
    """Adds any missing columns from ADDED_COLUMNS. Returns descriptions of what it added."""
    added: list[str] = []
    for table, column, ddl in ADDED_COLUMNS:
        state = _column_state(engine, table, column)
        if state is None or state:
            continue  # table absent (create_all makes it, column included) or column already there
        try:
            with engine.begin() as conn:  # its own transaction: PostgreSQL aborts a transaction on error
                conn.execute(text(f"ALTER TABLE {table} ADD COLUMN {column} {ddl}"))
        except (OperationalError, ProgrammingError):
            # Several app processes can start at once, and another one may have added the column
            # between our check and our ALTER. If it's there now, that's exactly what we wanted.
            if _column_state(engine, table, column):
                continue
            raise
        added.append(f"{table}.{column}")
        logger.warning("Added missing column %s.%s", table, column)
    return added
