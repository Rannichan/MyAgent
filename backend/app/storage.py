from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from uuid import uuid4

from .config import Settings
from .schemas import ChatMessage, Conversation, ConversationCreate, ConversationUpdate


def new_id() -> str:
    return uuid4().hex


class ConversationStore:
    def __init__(self, settings: Settings):
        self.directory = settings.conversations_dir
        self.directory.mkdir(parents=True, exist_ok=True)

    def _path(self, conversation_id: str) -> Path:
        return self.directory / f"{conversation_id}.json"

    def list(self) -> list[Conversation]:
        conversations: list[Conversation] = []
        for path in sorted(self.directory.glob("*.json"), key=lambda item: item.stat().st_mtime, reverse=True):
            conversations.append(Conversation.model_validate_json(path.read_text(encoding="utf-8")))
        return conversations

    def get(self, conversation_id: str) -> Conversation:
        path = self._path(conversation_id)
        if not path.exists():
            raise FileNotFoundError(conversation_id)
        return Conversation.model_validate_json(path.read_text(encoding="utf-8"))

    def create(self, payload: ConversationCreate) -> Conversation:
        conversation = Conversation(id=new_id(), title=payload.title, mode=payload.mode, npc_id=payload.npc_id)
        self.save(conversation)
        return conversation

    def update(self, conversation_id: str, payload: ConversationUpdate) -> Conversation:
        conversation = self.get(conversation_id)
        if payload.title is not None:
            conversation.title = payload.title
        if payload.mode is not None:
            conversation.mode = payload.mode
        if payload.npc_id is not None:
            conversation.npc_id = payload.npc_id
        if payload.messages is not None:
            conversation.messages = payload.messages
        conversation.updated_at = datetime.utcnow()
        self.save(conversation)
        return conversation

    def append(self, conversation: Conversation, message: ChatMessage) -> Conversation:
        conversation.messages.append(message)
        conversation.updated_at = datetime.utcnow()
        if conversation.title == "新会话" and message.role == "user" and message.content.strip():
            conversation.title = message.content.strip()[:32]
        self.save(conversation)
        return conversation

    def save(self, conversation: Conversation) -> None:
        self._path(conversation.id).write_text(
            json.dumps(conversation.model_dump(mode="json"), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

    def delete(self, conversation_id: str) -> None:
        self._path(conversation_id).unlink(missing_ok=True)
