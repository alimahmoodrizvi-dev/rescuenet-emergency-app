"""
RescueNet backend configuration.

Per Part 20/36 of the architecture: no secrets are ever hard-coded here. Every credential
(database URL, JWT signing secret, AI provider key) is read from the environment, with safe
local-dev defaults that make it obvious they must be overridden before any real deployment.
"""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env", env_file_encoding="utf-8", extra="ignore", env_prefix="RESCUENET_"
    )

    # Defaults to a local SQLite file so the backend runs with zero setup for a hackathon
    # demo. Part 12 recommends PostgreSQL (+ PostGIS) for a real deployment — set
    # DATABASE_URL to a postgres:// DSN (see docker-compose.yml) to use it instead.
    database_url: str = "sqlite:///./rescuenet.db"

    # MUST be overridden via env var in any real deployment. This default is intentionally
    # obviously insecure so it can't be mistaken for a real secret.
    jwt_secret: str = "CHANGE_ME_INSECURE_DEV_SECRET"
    jwt_algorithm: str = "HS256"
    jwt_expire_minutes: int = 60 * 24  # 24h — fine for a prototype; shorten + add refresh for production

    # AI provider abstraction (Part 8). Empty by default -> AIProvider falls back to the
    # deterministic MockProvider rather than silently failing or fabricating a "connected"
    # status. Set ANTHROPIC_API_KEY (or another provider's key) to enable a real model.
    anthropic_api_key: str = ""

    # CORS — the Command Center web app's origin(s) in production; "*" is fine for local dev only.
    cors_allow_origins: str = "*"


settings = Settings()
