from __future__ import annotations

from datetime import datetime
from typing import Any, Literal, Optional

from pydantic import BaseModel, Field


class Attachment(BaseModel):
    id: str
    name: str
    mime_type: str
    url: str
    kind: Literal["image", "video", "file"]


class TokenUsage(BaseModel):
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0


class ChatMessage(BaseModel):
    id: str
    role: Literal["system", "user", "assistant", "tool"]
    content: str
    reasoning_content: str = ""
    tool_calls: list[dict[str, Any]] = Field(default_factory=list)
    latency_ms: Optional[int] = None
    usage: Optional[TokenUsage] = None
    created_at: datetime = Field(default_factory=datetime.utcnow)
    attachments: list[Attachment] = Field(default_factory=list)


class Conversation(BaseModel):
    id: str
    title: str
    mode: Literal["normal", "npc", "agent"] = "agent"
    npc_id: Optional[str] = None
    agent_id: Optional[str] = None
    messages: list[ChatMessage] = Field(default_factory=list)
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)


class ConversationCreate(BaseModel):
    title: str = "新会话"
    mode: Literal["normal", "npc", "agent"] = "agent"
    npc_id: Optional[str] = None
    agent_id: Optional[str] = None


class ConversationUpdate(BaseModel):
    title: Optional[str] = None
    mode: Optional[Literal["normal", "npc", "agent"]] = None
    npc_id: Optional[str] = None
    agent_id: Optional[str] = None
    messages: Optional[list[ChatMessage]] = None


class SamplingSettings(BaseModel):
    temperature: Optional[float] = Field(default=None, ge=0, le=2)
    top_p: Optional[float] = Field(default=None, ge=0, le=1)
    max_tokens: Optional[int] = Field(default=None, ge=1, le=131072)
    presence_penalty: Optional[float] = Field(default=None, ge=-2, le=2)
    frequency_penalty: Optional[float] = Field(default=None, ge=-2, le=2)


class ChatRequest(BaseModel):
    conversation_id: Optional[str] = None
    mode: Literal["normal", "npc", "agent"] = "agent"
    npc_id: Optional[str] = None
    agent_id: Optional[str] = None
    message: str
    attachments: list[Attachment] = Field(default_factory=list)
    stream: bool = True
    thinking_enabled: bool = False
    tools_enabled: bool = False
    sampling: SamplingSettings = Field(default_factory=SamplingSettings)
    model: Optional[str] = None


class ChatResponse(BaseModel):
    conversation: Conversation
    assistant_message: ChatMessage
    raw_tool_calls: list[dict[str, Any]] = Field(default_factory=list)
    latency_ms: Optional[int] = None
    usage: Optional[TokenUsage] = None


class NpcProfile(BaseModel):
    id: str
    name: str
    system_prompt: str
    opening: Optional[str] = None


class NpcCreate(BaseModel):
    id: str = Field(min_length=1, max_length=64)
    system_prompt: str = Field(min_length=1)
    opening: Optional[str] = None


class NpcUpdate(BaseModel):
    id: Optional[str] = Field(default=None, min_length=1, max_length=64)
    system_prompt: str = Field(min_length=1)
    opening: Optional[str] = None


class AgentProfile(BaseModel):
    id: str
    name: str
    agent: str = ""
    identity: str = ""
    memory: str = ""
    soul: str = ""
    system_prompt: str


class AgentCreate(BaseModel):
    id: str = Field(min_length=1, max_length=64)
    agent: str = ""
    identity: str = ""
    memory: str = ""
    soul: str = ""


class AgentUpdate(BaseModel):
    id: Optional[str] = Field(default=None, min_length=1, max_length=64)
    agent: str = ""
    identity: str = ""
    memory: str = ""
    soul: str = ""


class UserConfig(BaseModel):
    content: str = ""


class LlmConfig(BaseModel):
    provider: str = "vllm"
    model: str = "local-model"
    vllm_base_url: str = "http://127.0.0.1:8000/v1"
    vllm_api_key: str = "EMPTY"
    llamacpp_base_url: str = "http://127.0.0.1:8080/v1"
    llamacpp_api_key: str = "EMPTY"
