from __future__ import annotations

from pathlib import Path

from .config import Settings
from .schemas import NpcProfile


def _read_text(path: Path) -> str:
    if not path.exists() or not path.is_file():
        return ""
    return path.read_text(encoding="utf-8").strip()


def load_npcs(settings: Settings) -> list[NpcProfile]:
    if not settings.npc_dir.exists():
        return []

    profiles: list[NpcProfile] = []
    for path in sorted(settings.npc_dir.iterdir()):
        if path.is_dir():
            system_prompt = _read_text(path / "system.md") or _read_text(path / "prompt.md")
            opening = _read_text(path / "opening.md") or None
            if system_prompt:
                profiles.append(NpcProfile(id=path.name, name=path.name, system_prompt=system_prompt, opening=opening))
        elif path.suffix.lower() == ".md":
            profiles.append(NpcProfile(id=path.stem, name=path.stem, system_prompt=_read_text(path)))
    return profiles


def load_npc(settings: Settings, npc_id: str | None) -> NpcProfile | None:
    if not npc_id:
        return None
    return next((profile for profile in load_npcs(settings) if profile.id == npc_id), None)


def load_agent_prompt(settings: Settings) -> str:
    if not settings.agent_dir.exists():
        return ""

    preferred = ["soul.md", "identity.md", "agent.md", "memory.md"]
    parts: list[str] = []
    seen: set[Path] = set()

    for filename in preferred:
        path = settings.agent_dir / filename
        text = _read_text(path)
        if text:
            parts.append(f"# {path.stem}\n{text}")
            seen.add(path.resolve())

    for path in sorted(settings.agent_dir.glob("*.md")):
        if path.resolve() in seen:
            continue
        text = _read_text(path)
        if text:
            parts.append(f"# {path.stem}\n{text}")

    return "\n\n".join(parts)


def build_system_prompt(settings: Settings, mode: str, npc_id: str | None, thinking_enabled: bool) -> str | None:
    parts: list[str] = []
    if mode == "npc":
        npc = load_npc(settings, npc_id)
        if npc:
            parts.append(npc.system_prompt)
    elif mode == "agent":
        agent_prompt = load_agent_prompt(settings)
        if agent_prompt:
            parts.append(agent_prompt)

    if thinking_enabled:
        parts.append("请在需要时进行严谨思考，但最终回答要清晰、可执行。")
    else:
        parts.append("不要输出隐藏推理过程；直接给出简洁可靠的最终回答。")

    return "\n\n".join(parts).strip() or None
