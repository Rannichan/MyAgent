import type { Attachment, Conversation, Mode, ModelInfo, NpcDraft, NpcProfile, RuntimeConfig } from './types';

export const api = {
  async config(): Promise<RuntimeConfig> {
    return getJson('/api/config');
  },
  async npcs(): Promise<NpcProfile[]> {
    return getJson('/api/npcs');
  },
  async models(): Promise<ModelInfo[]> {
    return getJson('/api/models');
  },
  async createNpc(payload: NpcDraft): Promise<NpcProfile> {
    return postJson('/api/npcs', payload);
  },
  async updateNpc(npcId: string, payload: NpcDraft): Promise<NpcProfile> {
    return putJson(`/api/npcs/${npcId}`, payload);
  },
  async deleteNpc(npcId: string): Promise<void> {
    await fetch(`/api/npcs/${npcId}`, { method: 'DELETE' });
  },
  async conversations(): Promise<Conversation[]> {
    return getJson('/api/conversations');
  },
  async createConversation(mode: Mode, npcId?: string | null): Promise<Conversation> {
    return postJson('/api/conversations', { title: '新会话', mode, npc_id: npcId });
  },
  async updateConversation(conversation: Conversation): Promise<Conversation> {
    return putJson(`/api/conversations/${conversation.id}`, conversation);
  },
  async deleteConversation(id: string): Promise<void> {
    await fetch(`/api/conversations/${id}`, { method: 'DELETE' });
  },
  async upload(files: FileList): Promise<Attachment[]> {
    const form = new FormData();
    Array.from(files).forEach((file) => form.append('files', file));
    const response = await fetch('/api/uploads', { method: 'POST', body: form });
    if (!response.ok) throw new Error(await response.text());
    return response.json();
  }
};

async function getJson<T>(url: string): Promise<T> {
  const response = await fetch(url);
  if (!response.ok) throw new Error(await response.text());
  return response.json();
}

async function postJson<T>(url: string, body: unknown): Promise<T> {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  if (!response.ok) throw new Error(await response.text());
  return response.json();
}

async function putJson<T>(url: string, body: unknown): Promise<T> {
  const response = await fetch(url, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  if (!response.ok) throw new Error(await response.text());
  return response.json();
}
