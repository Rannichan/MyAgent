from __future__ import annotations

import shutil
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


def save_npc(settings: Settings, npc_id: str, system_prompt: str, opening: str | None) -> NpcProfile:
    target = settings.npc_dir / npc_id
    legacy_file = settings.npc_dir / f"{npc_id}.md"
    if legacy_file.exists() and legacy_file.is_file():
        legacy_file.unlink(missing_ok=True)
    target.mkdir(parents=True, exist_ok=True)
    (target / "system.md").write_text(system_prompt.strip(), encoding="utf-8")

    opening_path = target / "opening.md"
    opening_text = (opening or "").strip()
    if opening_text:
        opening_path.write_text(opening_text, encoding="utf-8")
    else:
        opening_path.unlink(missing_ok=True)

    return NpcProfile(id=npc_id, name=npc_id, system_prompt=system_prompt.strip(), opening=opening_text or None)


def rename_npc(settings: Settings, old_id: str, new_id: str) -> None:
    source = settings.npc_dir / old_id
    source_legacy = settings.npc_dir / f"{old_id}.md"
    target = settings.npc_dir / new_id
    target_legacy = settings.npc_dir / f"{new_id}.md"
    if target.exists() or target_legacy.exists():
        raise FileExistsError(new_id)
    if source.exists():
        source.rename(target)
        return
    if source_legacy.exists():
        source_legacy.rename(target)
        return
    if not source.exists():
        raise FileNotFoundError(old_id)


def delete_npc(settings: Settings, npc_id: str) -> None:
    target = settings.npc_dir / npc_id
    legacy_file = settings.npc_dir / f"{npc_id}.md"
    if not target.exists():
        if legacy_file.exists():
            legacy_file.unlink(missing_ok=True)
            return
        raise FileNotFoundError(npc_id)
    if target.is_dir():
        shutil.rmtree(target)
        return
    target.unlink(missing_ok=True)


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
