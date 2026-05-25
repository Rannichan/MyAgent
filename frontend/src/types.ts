export type Mode = 'npc' | 'agent';

export interface Attachment {
  id: string;
  name: string;
  mime_type: string;
  url: string;
  kind: 'image' | 'video' | 'file';
}

export interface ChatMessage {
  id: string;
  role: 'system' | 'user' | 'assistant' | 'tool';
  content: string;
  created_at: string;
  attachments: Attachment[];
  reasoning_content: string;
  tool_calls: unknown[];
  latency_ms?: number | null;
  usage?: TokenUsage | null;
}

export interface Conversation {
  id: string;
  title: string;
  mode: Mode | 'normal';
  npc_id?: string | null;
  agent_id?: string | null;
  messages: ChatMessage[];
  created_at: string;
  updated_at: string;
}

export interface NpcProfile {
  id: string;
  name: string;
  system_prompt: string;
  opening?: string | null;
}

export interface NpcDraft {
  id: string;
  system_prompt: string;
  opening?: string | null;
}

export interface AgentProfile {
  id: string;
  name: string;
  system: string;
  agent: string;
  identity: string;
  memory: string;
  soul: string;
  system_prompt: string;
}

export interface AgentDraft {
  id: string;
  system: string;
  agent: string;
  identity: string;
  memory: string;
  soul: string;
}

export interface TokenUsage {
  prompt_tokens: number;
  completion_tokens: number;
  total_tokens: number;
}

export interface ModelInfo {
  id: string;
  object?: string;
}

export interface RuntimeConfig {
  provider: string;
  model: string;
  base_url: string;
  defaults: {
    temperature: number;
    top_p: number;
    max_tokens: number;
    stream: boolean;
    thinking: boolean;
    tools: boolean;
  };
}
