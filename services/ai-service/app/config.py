from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    qdrant_url: str = "http://localhost:6333"
    qdrant_collection: str = "products"
    vector_size: int = 64
    model_version: str = "rules-hybrid-v1"
    half_life_hours: float = 72.0
    model_config = SettingsConfigDict(env_prefix="REENGAGE_AI_")


settings = Settings()
