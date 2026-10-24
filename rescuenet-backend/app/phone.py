"""Phone-number cleaning shared by every endpoint that accepts one.

The rule that matters most: a bad phone number must NEVER cause an emergency report to be
rejected. Anything we can't make sense of is simply dropped (None), and the report goes through.
"""
from __future__ import annotations

import re
from typing import Any, Optional

_ALLOWED = re.compile(r"^\+?[0-9]+$")


def normalize_phone(raw: Any) -> Optional[str]:
    """'+92 300-123 4567' -> '+923001234567'. Returns None for anything that isn't a plausible
    phone number (7-15 digits, optional leading '+'), including None, blanks and text."""
    if raw is None or not isinstance(raw, str):
        return None
    cleaned = re.sub(r"[\s\-\.\(\)]", "", raw.strip())
    if not _ALLOWED.match(cleaned):
        return None
    digits = cleaned.lstrip("+")
    if not 7 <= len(digits) <= 15:
        return None
    return cleaned


def lenient_float(value: Any) -> Optional[float]:
    """Numbers pass through; anything else (e.g. the text 'unknown' older app builds send when
    they have no GPS fix) becomes None instead of failing validation of the whole report."""
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    return None
