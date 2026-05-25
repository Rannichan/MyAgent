import type { AgentDraft, AgentProfile, Attachment, Conversation, LlmConfig, Mode, ModelInfo, NpcDraft, NpcProfile, RuntimeConfig, UserConfig } from './types';

declare global {
  interface Window {
    __MYAGENT_API_BASE__?: string;
  }
}

const runtimeApiBase =
  typeof window !== 'undefined' && typeof window.__MYAGENT_API_BASE__ === 'string'
    ? window.__MYAGENT_API_BASE__
    : '';
const envApiBase = typeof import.meta !== 'undefined' && import.meta.env?.VITE_API_BASE_URL ? import.meta.env.VITE_API_BASE_URL : '';
const apiBase = (runtimeApiBase || envApiBase).trim().replace(/\/+$/, '');

export function apiUrl(path: string): string {
  if (!path.startsWith('/')) return path;
  return apiBase ? `${apiBase}${path}` : path;
}

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
  async agents(): Promise<AgentProfile[]> {
    return getJson('/api/agents');
  },
  async createAgent(payload: AgentDraft): Promise<AgentProfile> {
    return postJson('/api/agents', payload);
  },
  async updateAgent(agentId: string, payload: AgentDraft): Promise<AgentProfile> {
    return putJson(`/api/agents/${agentId}`, payload);
  },
  async deleteAgent(agentId: string): Promise<void> {
    await fetch(apiUrl(`/api/agents/${agentId}`), { method: 'DELETE' });
  },
  async createNpc(payload: NpcDraft): Promise<NpcProfile> {
    return postJson('/api/npcs', payload);
  },
  async updateNpc(npcId: string, payload: NpcDraft): Promise<NpcProfile> {
    return putJson(`/api/npcs/${npcId}`, payload);
  },
  async deleteNpc(npcId: string): Promise<void> {
    await fetch(apiUrl(`/api/npcs/${npcId}`), { method: 'DELETE' });
  },
  async user(): Promise<UserConfig> {
    return getJson('/api/user');
  },
  async saveUser(content: string): Promise<UserConfig> {
    return putJson('/api/user', { content });
  },
  async getLlmConfig(): Promise<LlmConfig> {
    return getJson('/api/llm-config');
  },
  async saveLlmConfig(config: LlmConfig): Promise<LlmConfig> {
    return putJson('/api/llm-config', config);
  },
  async conversations(): Promise<Conversation[]> {
    return getJson('/api/conversations');
  },
  async createConversation(mode: Mode, npcId?: string | null, agentId?: string | null): Promise<Conversation> {
    return postJson('/api/conversations', { title: '新会话', mode, npc_id: npcId, agent_id: agentId });
  },
  async updateConversation(conversation: Conversation): Promise<Conversation> {
    return putJson(`/api/conversations/${conversation.id}`, conversation);
  },
  async deleteConversation(id: string): Promise<void> {
    await fetch(apiUrl(`/api/conversations/${id}`), { method: 'DELETE' });
  },
  async upload(files: FileList): Promise<Attachment[]> {
    const form = new FormData();
    Array.from(files).forEach((file) => form.append('files', file));
    const response = await fetch(apiUrl('/api/uploads'), { method: 'POST', body: form });
    if (!response.ok) throw new Error(await response.text());
    return response.json();
  }
};

async function getJson<T>(url: string): Promise<T> {
  const response = await fetch(apiUrl(url));
  if (!response.ok) throw new Error(await response.text());
  return response.json();
}

async function postJson<T>(url: string, body: unknown): Promise<T> {
  const response = await fetch(apiUrl(url), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  if (!response.ok) throw new Error(await response.text());
  return response.json();
}

async function putJson<T>(url: string, body: unknown): Promise<T> {
  const response = await fetch(apiUrl(url), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  if (!response.ok) throw new Error(await response.text());
  return response.json();
}
