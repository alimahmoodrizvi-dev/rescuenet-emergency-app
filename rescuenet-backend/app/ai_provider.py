"""
AI abstraction layer (Part 8). `AIProvider` is the interface every concrete implementation
follows; `get_ai_provider()` picks one based on configuration so the rest of the backend
never hard-codes a provider. This mirrors the Android app's own `AIProvider` interface
described in the Phase 1 architecture doc — text structuring here is the server-side
counterpart to that client-side abstraction, used when the app is online and defers analysis
to the backend rather than doing it fully offline.

Per Part 26 (AI safety): every result carries a confidence score and an explicit
`is_simulated` flag, and callers must not present AI output as certain fact — see the
`caveat` field on AIAnalyzeIncidentResponse.
"""
from __future__ import annotations

import json
import re
from abc import ABC, abstractmethod
from typing import Optional

import httpx

from app.config import settings
from app.models import IncidentType, Severity


class AIProvider(ABC):
    name: str

    @abstractmethod
    async def analyze_incident_text(self, raw_text: str, language_hint: Optional[str]) -> dict:
        """Returns a dict matching AIAnalyzeIncidentResponse's fields (minus model_provider/
        is_simulated, which the caller fills in from `self.name` / provider type)."""
        ...


class MockProvider(AIProvider):
    """Deterministic keyword-based extraction — no external call, always available, and
    exactly what the backend falls back to when no AI_PROVIDER key is configured (Part 8:
    "do NOT hard-code the application around a single AI provider" — this is the safe
    default, not a placeholder pretending to be a real model)."""

    name = "mock"

    _TYPE_KEYWORDS: dict[IncidentType, list[str]] = {
        IncidentType.FLOOD: ["flood", "pani", "paani", "water", "seelab"],
        IncidentType.FIRE: ["fire", "aag", "jal raha", "burning"],
        IncidentType.EARTHQUAKE: ["earthquake", "zalzala", "zilzala", "shaking"],
        IncidentType.MEDICAL: ["injured", "zakhmi", "bimar", "heart attack", "unconscious", "behosh"],
        IncidentType.TRAPPED: ["trapped", "phans", "phansa", "stuck"],
        IncidentType.BUILDING_COLLAPSE: ["collapse", "gir gaya", "building fell", "rubble"],
        IncidentType.ACCIDENT: ["accident", "hadsa", "crash"],
        IncidentType.MISSING_PERSON: ["missing", "gum", "lost person"],
        IncidentType.SECURITY: ["security", "attack", "threat"],
    }

    _INJURY_KEYWORDS = ["injured", "zakhmi", "hurt", "bleeding", "critical", "unconscious", "behosh"]

    async def analyze_incident_text(self, raw_text: str, language_hint: Optional[str]) -> dict:
        text_lower = raw_text.lower()

        incident_type = IncidentType.OTHER
        for itype, keywords in self._TYPE_KEYWORDS.items():
            if any(kw in text_lower for kw in keywords):
                incident_type = itype
                break

        people_match = re.search(r"(\d+)\s*(people|log|persons|afraad)", text_lower)
        people_count = int(people_match.group(1)) if people_match else None

        injuries_present = any(kw in text_lower for kw in self._INJURY_KEYWORDS)

        if injuries_present and ("critical" in text_lower or "behosh" in text_lower or "unconscious" in text_lower):
            severity = Severity.CRITICAL
        elif injuries_present or (people_count and people_count >= 5):
            severity = Severity.HIGH
        else:
            severity = Severity.MODERATE

        required_resources: list[str] = []
        if incident_type == IncidentType.FLOOD:
            required_resources += ["RESCUE_TEAM", "EVACUATION"]
        if incident_type == IncidentType.FIRE:
            required_resources += ["FIRE_SERVICE", "EVACUATION"]
        if injuries_present:
            required_resources += ["AMBULANCE", "MEDICAL_ASSISTANCE"]
        required_resources = list(dict.fromkeys(required_resources))  # de-dup, preserve order

        # Confidence reflects how much the text actually let us extract — an empty/vague
        # message should never be reported as high-confidence (Part 6/26).
        confidence = 40
        if incident_type != IncidentType.OTHER:
            confidence += 25
        if people_count is not None:
            confidence += 15
        if injuries_present:
            confidence += 10
        confidence = min(confidence, 90)  # mock provider never claims near-certainty

        return {
            "incident_type": incident_type,
            "severity": severity,
            "people_count": people_count,
            "injuries_present": injuries_present,
            "required_resources": required_resources,
            "confidence": confidence,
        }


class AnthropicProvider(AIProvider):
    """Real cloud LLM provider. Only constructed when RESCUENET_ANTHROPIC_API_KEY is set —
    never hard-coded, per Part 20/36. Constrains the model to strict JSON output matching the
    same schema MockProvider returns, so callers don't need to know which provider answered."""

    name = "anthropic"

    def __init__(self, api_key: str) -> None:
        self._api_key = api_key

    async def analyze_incident_text(self, raw_text: str, language_hint: Optional[str]) -> dict:
        system_prompt = (
            "You extract structured emergency-report data from a citizen's message, which may be "
            "in English, Urdu, or a mix. Respond with ONLY a JSON object, no other text, matching "
            "exactly this shape: "
            '{"incident_type": one of ["MEDICAL","FIRE","FLOOD","EARTHQUAKE","ACCIDENT","TRAPPED",'
            '"MISSING_PERSON","BUILDING_COLLAPSE","SECURITY","OTHER"], '
            '"severity": one of ["CRITICAL","HIGH","MODERATE"], '
            '"people_count": integer or null if not stated, '
            '"injuries_present": true/false/null if unclear, '
            '"required_resources": array from ["AMBULANCE","RESCUE_TEAM","FIRE_SERVICE","FOOD",'
            '"WATER","SHELTER","EVACUATION","MEDICAL_ASSISTANCE"], '
            '"confidence": integer 0-100 reflecting how much the text actually supports these '
            "fields — do not default to a high number. Never invent facts not implied by the text; "
            "use null where genuinely unclear."
        )
        async with httpx.AsyncClient(timeout=20.0) as client:
            response = await client.post(
                "https://api.anthropic.com/v1/messages",
                headers={
                    "x-api-key": self._api_key,
                    "anthropic-version": "2023-06-01",
                    "content-type": "application/json",
                },
                json={
                    "model": "claude-sonnet-4-6",
                    "max_tokens": 500,
                    "system": system_prompt,
                    "messages": [{"role": "user", "content": raw_text}],
                },
            )
            response.raise_for_status()
            data = response.json()
            text = "".join(block.get("text", "") for block in data.get("content", []) if block.get("type") == "text")
            parsed = json.loads(text)
            return {
                "incident_type": IncidentType(parsed["incident_type"]),
                "severity": Severity(parsed["severity"]),
                "people_count": parsed.get("people_count"),
                "injuries_present": parsed.get("injuries_present"),
                "required_resources": parsed.get("required_resources", []),
                "confidence": int(parsed.get("confidence", 50)),
            }


def get_ai_provider() -> AIProvider:
    if settings.anthropic_api_key:
        return AnthropicProvider(settings.anthropic_api_key)
    return MockProvider()
