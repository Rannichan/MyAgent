from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from dotenv import load_dotenv
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

ROOT_DIR = Path(__file__).resolve().parents[2]
load_dotenv(ROOT_DIR / ".env")


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=ROOT_DIR / ".env", extra="ignore")

    app_host: str = "127.0.0.1"
    app_port: int = 8765
    app_public_url: str = "http://127.0.0.1:8765"
    cors_origins: str = "http://localhost:5173,http://127.0.0.1:5173,http://localhost,capacitor://localhost"

    model_provider: str = Field(default="vllm", pattern="^(vllm|llamacpp)$")
    model_name: str = ""

    vllm_base_url: str = "http://127.0.0.1:8000/v1"
    vllm_api_key: str = "EMPTY"
    llamacpp_base_url: str = "http://127.0.0.1:8080/v1"
    llamacpp_api_key: str = "EMPTY"

    default_temperature: float = 0.7
    default_top_p: float = 0.9
    default_max_tokens: int = 2048
    default_stream: bool = True
    default_thinking: bool = False
    default_tools: bool = False

    @property
    def root_dir(self) -> Path:
        return ROOT_DIR

    @property
    def conversations_dir(self) -> Path:
        return ROOT_DIR / "backend" / "data" / "conversations"

    @property
    def uploads_dir(self) -> Path:
        return ROOT_DIR / "backend" / "data" / "uploads"

    @property
    def npc_dir(self) -> Path:
        return ROOT_DIR / "npc"

    @property
    def agent_dir(self) -> Path:
        return ROOT_DIR / "agent"

    @property
    def active_base_url(self) -> str:
        return self.vllm_base_url if self.model_provider == "vllm" else self.llamacpp_base_url

    @property
    def active_api_key(self) -> str:
        return self.vllm_api_key if self.model_provider == "vllm" else self.llamacpp_api_key

    @property
    def cors_origin_list(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    settings.conversations_dir.mkdir(parents=True, exist_ok=True)
    settings.uploads_dir.mkdir(parents=True, exist_ok=True)
    return settings
