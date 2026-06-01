from __future__ import annotations

import shutil
from pathlib import Path

from .config import Settings
from .schemas import AgentProfile, NpcProfile

AGENT_PROMPT_FILENAMES = ("agent.md", "identity.md", "soul.md", "memory.md")
DEFAULT_NPC_CONTEXT_TURNS = 10


def _read_text(path: Path) -> str:
    if not path.exists() or not path.is_file():
        return ""
    return path.read_text(encoding="utf-8").strip()


def _read_npc_context_turns(path: Path) -> int:
    config_path = path / "settings.json"
    if not config_path.exists() or not config_path.is_file():
        return DEFAULT_NPC_CONTEXT_TURNS
    try:
        import json

        data = json.loads(config_path.read_text(encoding="utf-8"))
        value = int(data.get("context_turns", DEFAULT_NPC_CONTEXT_TURNS))
        if value < 1:
            return DEFAULT_NPC_CONTEXT_TURNS
        return min(value, 200)
    except Exception:
        return DEFAULT_NPC_CONTEXT_TURNS


def load_npcs(settings: Settings) -> list[NpcProfile]:
    if not settings.npc_dir.exists():
        return []

    profiles: list[NpcProfile] = []
    for path in sorted(settings.npc_dir.iterdir()):
        if path.is_dir():
            system_prompt = _read_text(path / "system.md") or _read_text(path / "prompt.md")
            opening = _read_text(path / "opening.md") or None
            if system_prompt:
                profiles.append(
                    NpcProfile(
                        id=path.name,
                        name=path.name,
                        system_prompt=system_prompt,
                        opening=opening,
                        context_turns=_read_npc_context_turns(path),
                    )
                )
        elif path.suffix.lower() == ".md":
            profiles.append(
                NpcProfile(
                    id=path.stem,
                    name=path.stem,
                    system_prompt=_read_text(path),
                    context_turns=DEFAULT_NPC_CONTEXT_TURNS,
                )
            )
    return profiles


def load_npc(settings: Settings, npc_id: str | None) -> NpcProfile | None:
    if not npc_id:
        return None
    return next((profile for profile in load_npcs(settings) if profile.id == npc_id), None)


def save_npc(settings: Settings, npc_id: str, system_prompt: str, opening: str | None, context_turns: int | None = None) -> NpcProfile:
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

    context_turns_value = context_turns if context_turns is not None else DEFAULT_NPC_CONTEXT_TURNS
    context_turns_value = max(1, min(int(context_turns_value), 200))
    import json

    (target / "settings.json").write_text(
        json.dumps({"context_turns": context_turns_value}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    return NpcProfile(
        id=npc_id,
        name=npc_id,
        system_prompt=system_prompt.strip(),
        opening=opening_text or None,
        context_turns=context_turns_value,
    )


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


def _agent_profiles_dir(settings: Settings) -> Path:
    return settings.agent_dir / "profiles"


def _normalize_prompt_text(text: str | None) -> str:
    return (text or "").strip()


def _compose_agent_prompt(parts: dict[str, str], user_text: str = "") -> str:
    sections: list[str] = []
    order = ["agent", "identity", "user", "soul", "memory"]
    texts: dict[str, str] = {**parts}
    if user_text:
        texts["user"] = user_text
    for key in order:
        text = _normalize_prompt_text(texts.get(key))
        if text:
            sections.append(f"# {key}\n{text}")
    return "\n\n".join(sections)


def _load_agent_prompt_parts(directory: Path) -> dict[str, str]:
    return {
        Path(filename).stem: _read_text(directory / filename)
        for filename in AGENT_PROMPT_FILENAMES
    }


def load_user_prompt(settings: Settings) -> str:
    return _read_text(settings.agent_dir / "user.md")


def save_user_prompt(settings: Settings, text: str) -> str:
    settings.agent_dir.mkdir(parents=True, exist_ok=True)
    (settings.agent_dir / "user.md").write_text(text.strip(), encoding="utf-8")
    return text.strip()


def load_agents(settings: Settings) -> list[AgentProfile]:
    directory = _agent_profiles_dir(settings)
    if not directory.exists():
        return []

    user_text = load_user_prompt(settings)
    profiles: list[AgentProfile] = []
    for path in sorted(directory.iterdir()):
        if path.is_dir():
            parts = _load_agent_prompt_parts(path)
            system_prompt = _compose_agent_prompt(parts, user_text)
            profiles.append(
                AgentProfile(
                    id=path.name,
                    name=path.name,
                    agent=parts["agent"],
                    identity=parts["identity"],
                    memory=parts["memory"],
                    soul=parts["soul"],
                    system_prompt=system_prompt,
                )
            )
        elif path.suffix.lower() == ".md":
            text = _read_text(path)
            if text:
                profiles.append(
                    AgentProfile(
                        id=path.stem,
                        name=path.stem,
                        agent=text,
                        identity="",
                        memory="",
                        soul="",
                        system_prompt=_compose_agent_prompt({"agent": text}, user_text),
                    )
                )
    return profiles


def load_agent(settings: Settings, agent_id: str | None) -> AgentProfile | None:
    if not agent_id:
        return None
    return next((profile for profile in load_agents(settings) if profile.id == agent_id), None)


def save_agent(
    settings: Settings,
    agent_id: str,
    agent: str = "",
    identity: str = "",
    memory: str = "",
    soul: str = "",
) -> AgentProfile:
    directory = _agent_profiles_dir(settings)
    target = directory / agent_id
    legacy_file = directory / f"{agent_id}.md"
    if legacy_file.exists() and legacy_file.is_file():
        legacy_file.unlink(missing_ok=True)
    target.mkdir(parents=True, exist_ok=True)
    prompt_parts = {
        "agent": _normalize_prompt_text(agent),
        "identity": _normalize_prompt_text(identity),
        "memory": _normalize_prompt_text(memory),
        "soul": _normalize_prompt_text(soul),
    }
    for filename in AGENT_PROMPT_FILENAMES:
        stem = Path(filename).stem
        (target / filename).write_text(prompt_parts[stem], encoding="utf-8")
    user_text = load_user_prompt(settings)
    return AgentProfile(
        id=agent_id,
        name=agent_id,
        agent=prompt_parts["agent"],
        identity=prompt_parts["identity"],
        memory=prompt_parts["memory"],
        soul=prompt_parts["soul"],
        system_prompt=_compose_agent_prompt(prompt_parts, user_text),
    )


def rename_agent(settings: Settings, old_id: str, new_id: str) -> None:
    directory = _agent_profiles_dir(settings)
    source = directory / old_id
    source_legacy = directory / f"{old_id}.md"
    target = directory / new_id
    target_legacy = directory / f"{new_id}.md"
    if target.exists() or target_legacy.exists():
        raise FileExistsError(new_id)
    if source.exists():
        source.rename(target)
        return
    if source_legacy.exists():
        source_legacy.rename(target)
        return
    raise FileNotFoundError(old_id)


def delete_agent(settings: Settings, agent_id: str) -> None:
    directory = _agent_profiles_dir(settings)
    target = directory / agent_id
    legacy_file = directory / f"{agent_id}.md"
    if not target.exists():
        if legacy_file.exists():
            legacy_file.unlink(missing_ok=True)
            return
        raise FileNotFoundError(agent_id)
    if target.is_dir():
        shutil.rmtree(target)
        return
    target.unlink(missing_ok=True)


def load_agent_prompt(settings: Settings) -> str:
    if not settings.agent_dir.exists():
        return ""

    preferred = ["agent.md", "identity.md", "user.md", "soul.md", "memory.md"]
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


def build_system_prompt(
    settings: Settings,
    mode: str,
    npc_id: str | None,
    agent_id: str | None,
    thinking_enabled: bool,
) -> str | None:
    parts: list[str] = []
    if mode == "npc":
        npc = load_npc(settings, npc_id)
        if npc:
            parts.append(npc.system_prompt)
    elif mode == "agent":
        agent_profile = load_agent(settings, agent_id)
        agent_prompt = agent_profile.system_prompt if agent_profile else load_agent_prompt(settings)
        if agent_prompt:
            parts.append(agent_prompt)

    if thinking_enabled:
        parts.append("请在需要时进行严谨思考，但最终回答要清晰、可执行。")
    else:
        parts.append("不要输出隐藏推理过程；直接给出简洁可靠的最终回答。")

    return "\n\n".join(parts).strip() or None
