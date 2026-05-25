from __future__ import annotations

import json
import mimetypes
import re
import time
from datetime import datetime
from pathlib import Path
from uuid import uuid4

import uvicorn
from fastapi import Depends, FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from fastapi.staticfiles import StaticFiles

from .config import Settings, get_settings
from .llm_client import LlmClient, build_openai_messages, build_payload
from .prompt_loader import (
    build_system_prompt,
    delete_agent,
    delete_npc,
    load_agent,
    load_agents,
    load_agent_prompt,
    load_npcs,
    rename_agent,
    rename_npc,
    save_agent,
    save_npc,
)
from .schemas import (
    AgentCreate,
    AgentUpdate,
    Attachment,
    ChatMessage,
    ChatRequest,
    ChatResponse,
    ConversationCreate,
    ConversationUpdate,
    NpcCreate,
    NpcUpdate,
    TokenUsage,
)
from .storage import ConversationStore, new_id

THINK_PATTERN = re.compile(r"<think>(.*?)</think>", re.DOTALL | re.IGNORECASE)


def split_thinking_tags(text: str) -> tuple[str, str]:
    reasoning_parts = [match.group(1).strip() for match in THINK_PATTERN.finditer(text) if match.group(1).strip()]
    content = THINK_PATTERN.sub("", text).strip()
    return content, "\n\n".join(reasoning_parts)


def validate_npc_id(value: str) -> str:
    npc_id = value.strip()
    if not npc_id:
        raise HTTPException(status_code=400, detail="NPC id is required")
    if npc_id in {".", ".."} or "/" in npc_id or "\\" in npc_id:
        raise HTTPException(status_code=400, detail="Invalid NPC id")
    return npc_id


def validate_agent_id(value: str) -> str:
    agent_id = value.strip()
    if not agent_id:
        raise HTTPException(status_code=400, detail="Agent id is required")
    if agent_id in {".", ".."} or "/" in agent_id or "\\" in agent_id:
        raise HTTPException(status_code=400, detail="Invalid Agent id")
    return agent_id

app = FastAPI(title="MyAgent API")
settings = get_settings()

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.mount("/uploads", StaticFiles(directory=settings.uploads_dir), name="uploads")


def get_store(settings: Settings = Depends(get_settings)) -> ConversationStore:
    return ConversationStore(settings)


@app.get("/api/config")
def read_config(settings: Settings = Depends(get_settings)) -> dict:
    return {
        "provider": settings.model_provider,
        "model": settings.model_name,
        "base_url": settings.active_base_url,
        "defaults": {
            "temperature": settings.default_temperature,
            "top_p": settings.default_top_p,
            "max_tokens": settings.default_max_tokens,
            "stream": settings.default_stream,
            "thinking": settings.default_thinking,
            "tools": settings.default_tools,
        },
    }


@app.get("/api/npcs")
def read_npcs(settings: Settings = Depends(get_settings)) -> list[dict]:
    return [profile.model_dump() for profile in load_npcs(settings)]


@app.post("/api/npcs")
def create_npc(payload: NpcCreate, settings: Settings = Depends(get_settings)) -> dict:
    npc_id = validate_npc_id(payload.id)
    target = settings.npc_dir / npc_id
    if target.exists():
        raise HTTPException(status_code=409, detail="NPC already exists")
    profile = save_npc(settings, npc_id, payload.system_prompt, payload.opening)
    return profile.model_dump(mode="json")


@app.put("/api/npcs/{npc_id}")
def update_npc(npc_id: str, payload: NpcUpdate, settings: Settings = Depends(get_settings)) -> dict:
    current_id = validate_npc_id(npc_id)
    next_id = validate_npc_id(payload.id) if payload.id is not None else current_id
    if next_id != current_id:
        try:
            rename_npc(settings, current_id, next_id)
        except FileNotFoundError as exc:
            raise HTTPException(status_code=404, detail="NPC not found") from exc
        except FileExistsError as exc:
            raise HTTPException(status_code=409, detail="NPC already exists") from exc
    profile_id = next_id
    source = settings.npc_dir / profile_id
    legacy_source = settings.npc_dir / f"{profile_id}.md"
    if not source.exists() and not legacy_source.exists():
        raise HTTPException(status_code=404, detail="NPC not found")
    profile = save_npc(settings, profile_id, payload.system_prompt, payload.opening)
    return profile.model_dump(mode="json")


@app.delete("/api/npcs/{npc_id}")
def remove_npc(npc_id: str, settings: Settings = Depends(get_settings)) -> dict:
    try:
        delete_npc(settings, validate_npc_id(npc_id))
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail="NPC not found") from exc
    return {"ok": True}


@app.get("/api/models")
async def list_models(settings: Settings = Depends(get_settings)) -> list[dict]:
    client = LlmClient(settings)
    try:
        return await client.list_models()
    except Exception:
        return [{"id": settings.model_name, "object": "model"}]


@app.get("/api/agent-profile")
def read_agent_profile(agent_id: str | None = None, settings: Settings = Depends(get_settings)) -> dict:
    if agent_id:
        profile = load_agent(settings, validate_agent_id(agent_id))
        if profile:
            return profile.model_dump(mode="json")
    return {"id": None, "name": "default", "system_prompt": load_agent_prompt(settings)}


@app.get("/api/agents")
def read_agents(settings: Settings = Depends(get_settings)) -> list[dict]:
    return [profile.model_dump(mode="json") for profile in load_agents(settings)]


@app.post("/api/agents")
def create_agent(payload: AgentCreate, settings: Settings = Depends(get_settings)) -> dict:
    agent_id = validate_agent_id(payload.id)
    target = settings.agent_dir / "profiles" / agent_id
    legacy_target = settings.agent_dir / "profiles" / f"{agent_id}.md"
    if target.exists() or legacy_target.exists():
        raise HTTPException(status_code=409, detail="Agent already exists")
    profile = save_agent(settings, agent_id, payload.system_prompt)
    return profile.model_dump(mode="json")


@app.put("/api/agents/{agent_id}")
def update_agent(agent_id: str, payload: AgentUpdate, settings: Settings = Depends(get_settings)) -> dict:
    current_id = validate_agent_id(agent_id)
    next_id = validate_agent_id(payload.id) if payload.id is not None else current_id
    if next_id != current_id:
        try:
            rename_agent(settings, current_id, next_id)
        except FileNotFoundError as exc:
            raise HTTPException(status_code=404, detail="Agent not found") from exc
        except FileExistsError as exc:
            raise HTTPException(status_code=409, detail="Agent already exists") from exc
    profile_id = next_id
    source = settings.agent_dir / "profiles" / profile_id
    legacy_source = settings.agent_dir / "profiles" / f"{profile_id}.md"
    if not source.exists() and not legacy_source.exists():
        raise HTTPException(status_code=404, detail="Agent not found")
    profile = save_agent(settings, profile_id, payload.system_prompt)
    return profile.model_dump(mode="json")


@app.delete("/api/agents/{agent_id}")
def remove_agent(agent_id: str, settings: Settings = Depends(get_settings)) -> dict:
    try:
        delete_agent(settings, validate_agent_id(agent_id))
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail="Agent not found") from exc
    return {"ok": True}


@app.get("/api/conversations")
def list_conversations(store: ConversationStore = Depends(get_store)) -> list[dict]:
    return [conversation.model_dump(mode="json") for conversation in store.list()]


@app.post("/api/conversations")
def create_conversation(payload: ConversationCreate, store: ConversationStore = Depends(get_store)) -> dict:
    return store.create(payload).model_dump(mode="json")


@app.get("/api/conversations/{conversation_id}")
def get_conversation(conversation_id: str, store: ConversationStore = Depends(get_store)) -> dict:
    try:
        return store.get(conversation_id).model_dump(mode="json")
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail="Conversation not found") from exc


@app.put("/api/conversations/{conversation_id}")
def update_conversation(conversation_id: str, payload: ConversationUpdate, store: ConversationStore = Depends(get_store)) -> dict:
    try:
        return store.update(conversation_id, payload).model_dump(mode="json")
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail="Conversation not found") from exc


@app.delete("/api/conversations/{conversation_id}")
def delete_conversation(conversation_id: str, store: ConversationStore = Depends(get_store)) -> dict:
    store.delete(conversation_id)
    return {"ok": True}


@app.post("/api/uploads")
async def upload_files(files: list[UploadFile] = File(...), settings: Settings = Depends(get_settings)) -> list[dict]:
    attachments: list[Attachment] = []
    for file in files:
        suffix = Path(file.filename or "upload").suffix
        file_id = uuid4().hex
        target = settings.uploads_dir / f"{file_id}{suffix}"
        content = await file.read()
        target.write_bytes(content)
        mime_type = file.content_type or mimetypes.guess_type(target.name)[0] or "application/octet-stream"
        kind = "image" if mime_type.startswith("image/") else "video" if mime_type.startswith("video/") else "file"
        attachments.append(
            Attachment(
                id=file_id,
                name=file.filename or target.name,
                mime_type=mime_type,
                url=f"{settings.app_public_url.rstrip('/')}/uploads/{target.name}",
                kind=kind,
            )
        )
    return [attachment.model_dump() for attachment in attachments]


def _prepare_chat(payload: ChatRequest, settings: Settings, store: ConversationStore):
    if payload.conversation_id:
        conversation = store.get(payload.conversation_id)
        conversation.mode = payload.mode
        conversation.npc_id = payload.npc_id
        conversation.agent_id = payload.agent_id
    else:
        conversation = store.create(ConversationCreate(mode=payload.mode, npc_id=payload.npc_id, agent_id=payload.agent_id))

    user_message = ChatMessage(id=new_id(), role="user", content=payload.message, attachments=payload.attachments)
    store.append(conversation, user_message)
    system_prompt = build_system_prompt(settings, payload.mode, payload.npc_id, payload.agent_id, payload.thinking_enabled)
    messages = build_openai_messages(system_prompt, conversation.messages)
    request_payload = build_payload(
        settings=settings,
        messages=messages,
        sampling=payload.sampling,
        stream=payload.stream,
        thinking_enabled=payload.thinking_enabled,
        tools_enabled=payload.tools_enabled,
        model=payload.model,
    )
    return conversation, request_payload


@app.post("/api/chat")
async def chat(payload: ChatRequest, settings: Settings = Depends(get_settings), store: ConversationStore = Depends(get_store)):
    try:
        conversation, request_payload = _prepare_chat(payload, settings, store)
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail="Conversation not found") from exc

    client = LlmClient(settings)
    if payload.stream:
        async def events():
            content_parts: list[str] = []
            reasoning_parts: list[str] = []
            tool_calls: list[dict] = []
            in_thinking_block = False
            usage_data: dict | None = None
            start_time = time.time()
            try:
                async for chunk in client.stream(request_payload):
                    if chunk.get("done"):
                        break
                    if chunk.get("usage"):
                        usage_data = chunk["usage"]
                    delta = chunk.get("choices", [{}])[0].get("delta", {})
                    reasoning_delta = delta.get("reasoning_content") or delta.get("reasoning") or ""
                    if reasoning_delta:
                        reasoning_parts.append(reasoning_delta)
                        yield f"data: {json.dumps({'type': 'reasoning', 'content': reasoning_delta}, ensure_ascii=False)}\n\n"
                    if delta.get("content"):
                        token = delta["content"]
                        while token:
                            if in_thinking_block:
                                end = token.lower().find("</think>")
                                if end >= 0:
                                    reasoning_text = token[:end]
                                    if reasoning_text:
                                        reasoning_parts.append(reasoning_text)
                                        yield f"data: {json.dumps({'type': 'reasoning', 'content': reasoning_text}, ensure_ascii=False)}\n\n"
                                    token = token[end + len("</think>"):]
                                    in_thinking_block = False
                                else:
                                    reasoning_parts.append(token)
                                    yield f"data: {json.dumps({'type': 'reasoning', 'content': token}, ensure_ascii=False)}\n\n"
                                    token = ""
                            else:
                                start = token.lower().find("<think>")
                                if start >= 0:
                                    content_text = token[:start]
                                    if content_text:
                                        content_parts.append(content_text)
                                        yield f"data: {json.dumps({'type': 'token', 'content': content_text}, ensure_ascii=False)}\n\n"
                                    token = token[start + len("<think>"):]
                                    in_thinking_block = True
                                else:
                                    content_parts.append(token)
                                    yield f"data: {json.dumps({'type': 'token', 'content': token}, ensure_ascii=False)}\n\n"
                                    token = ""
                    if delta.get("tool_calls"):
                        tool_calls.extend(delta["tool_calls"])
                        yield f"data: {json.dumps({'type': 'tool_call', 'tool_calls': delta['tool_calls']}, ensure_ascii=False)}\n\n"
                assistant_message = ChatMessage(
                    id=new_id(),
                    role="assistant",
                    content="".join(content_parts).strip(),
                    reasoning_content="".join(reasoning_parts).strip(),
                    tool_calls=tool_calls,
                    latency_ms=int((time.time() - start_time) * 1000),
                    usage=TokenUsage(**usage_data) if usage_data else None,
                )
                store.append(conversation, assistant_message)
                latency_ms = assistant_message.latency_ms
                usage = assistant_message.usage
                yield f"data: {json.dumps({'type': 'done', 'conversation': conversation.model_dump(mode='json'), 'assistant_message': assistant_message.model_dump(mode='json'), 'raw_tool_calls': tool_calls, 'latency_ms': latency_ms, 'usage': usage.model_dump() if usage else None}, ensure_ascii=False)}\n\n"
            except Exception as exc:
                yield f"data: {json.dumps({'type': 'error', 'message': str(exc)}, ensure_ascii=False)}\n\n"

        return StreamingResponse(events(), media_type="text/event-stream")

    t0 = time.time()
    response = await client.complete(request_payload)
    latency_ms = int((time.time() - t0) * 1000)
    choice = response.get("choices", [{}])[0]
    message = choice.get("message", {})
    raw_usage = response.get("usage")
    content, tag_reasoning = split_thinking_tags(message.get("content") or "")
    reasoning_content = message.get("reasoning_content") or message.get("reasoning") or tag_reasoning
    assistant_message = ChatMessage(
        id=new_id(),
        role="assistant",
        content=content,
        reasoning_content=reasoning_content,
        tool_calls=message.get("tool_calls") or [],
        latency_ms=latency_ms,
        usage=TokenUsage(**raw_usage) if raw_usage else None,
        created_at=datetime.utcnow(),
    )
    store.append(conversation, assistant_message)
    usage = assistant_message.usage
    return ChatResponse(
        conversation=conversation,
        assistant_message=assistant_message,
        raw_tool_calls=message.get("tool_calls") or [],
        latency_ms=latency_ms,
        usage=usage,
    ).model_dump(mode="json")


if __name__ == "__main__":
    uvicorn.run("app.main:app", host=settings.app_host, port=settings.app_port, reload=True)
