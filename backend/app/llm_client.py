from __future__ import annotations

import json
from collections.abc import AsyncIterator
from typing import Any

import httpx

from .config import Settings
from .schemas import Attachment, ChatMessage, SamplingSettings, TokenUsage


def _message_content(text: str, attachments: list[Attachment]) -> Any:
    if not attachments:
        return text

    content: list[dict[str, Any]] = [{"type": "text", "text": text}]
    video_lines: list[str] = []
    for attachment in attachments:
        if attachment.kind == "image":
            content.append({"type": "image_url", "image_url": {"url": attachment.url}})
        elif attachment.kind == "video":
            video_lines.append(f"视频附件: {attachment.name} ({attachment.url})")
        else:
            video_lines.append(f"文件附件: {attachment.name} ({attachment.url})")
    if video_lines:
        content[0]["text"] = text + "\n\n" + "\n".join(video_lines)
    return content


def build_openai_messages(system_prompt: str | None, history: list[ChatMessage]) -> list[dict[str, Any]]:
    messages: list[dict[str, Any]] = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})

    for message in history:
        if message.role == "system":
            continue
        messages.append(
            {
                "role": message.role,
                "content": _message_content(message.content, message.attachments),
            }
        )
    return messages


def build_payload(
    settings: Settings,
    messages: list[dict[str, Any]],
    sampling: SamplingSettings,
    stream: bool,
    thinking_enabled: bool,
    tools_enabled: bool,
    model: str | None = None,
) -> dict[str, Any]:
    selected_model = (model or "").strip() or settings.model_name.strip()
    if not selected_model:
        raise ValueError("No model selected. Please choose a model from /api/models.")
    payload: dict[str, Any] = {
        "model": selected_model,
        "messages": messages,
        "stream": stream,
        "temperature": sampling.temperature if sampling.temperature is not None else settings.default_temperature,
        "top_p": sampling.top_p if sampling.top_p is not None else settings.default_top_p,
        "max_tokens": sampling.max_tokens if sampling.max_tokens is not None else settings.default_max_tokens,
    }
    if sampling.presence_penalty is not None:
        payload["presence_penalty"] = sampling.presence_penalty
    if sampling.frequency_penalty is not None:
        payload["frequency_penalty"] = sampling.frequency_penalty

    if stream:
        payload["stream_options"] = {"include_usage": True}

    if settings.model_provider == "vllm":
        payload["chat_template_kwargs"] = {"enable_thinking": thinking_enabled}

    if tools_enabled:
        payload["tools"] = [
            {
                "type": "function",
                "function": {
                    "name": "local_note",
                    "description": "记录一个本地待办或事实。当前版本只把工具调用返回给前端，不执行副作用。",
                    "parameters": {
                        "type": "object",
                        "properties": {"text": {"type": "string"}},
                        "required": ["text"],
                    },
                },
            }
        ]
        payload["tool_choice"] = "auto"
    return payload


class LlmClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    @property
    def endpoint(self) -> str:
        return f"{self.settings.active_base_url.rstrip('/')}/chat/completions"

    @property
    def headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self.settings.active_api_key}", "Content-Type": "application/json"}

    async def complete(self, payload: dict[str, Any]) -> dict[str, Any]:
        async with httpx.AsyncClient(timeout=None) as client:
            response = await client.post(self.endpoint, headers=self.headers, json=payload)
            response.raise_for_status()
            return response.json()

    async def list_models(self) -> list[dict[str, Any]]:
        url = f"{self.settings.active_base_url.rstrip('/')}/models"
        async with httpx.AsyncClient(timeout=10) as client:
            response = await client.get(url, headers=self.headers)
            response.raise_for_status()
            data = response.json()
            return data.get("data", [])

    async def stream(self, payload: dict[str, Any]) -> AsyncIterator[dict[str, Any]]:
        async with httpx.AsyncClient(timeout=None) as client:
            async with client.stream("POST", self.endpoint, headers=self.headers, json=payload) as response:
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    data = line.removeprefix("data:").strip()
                    if data == "[DONE]":
                        yield {"done": True}
                        return
                    try:
                        yield json.loads(data)
                    except json.JSONDecodeError:
                        continue
