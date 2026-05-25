import { FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Bot, Brain, Check, ChevronDown, Copy, Download, ImagePlus, MessageSquarePlus, Moon, Pencil, Plus, Save, Send, Settings, Sun, Trash2, UserRound, Wrench, X } from 'lucide-react';
import { marked } from 'marked';
import { api } from './api';
import type { AgentDraft, AgentProfile, Attachment, ChatMessage, Conversation, Mode, ModelInfo, NpcDraft, NpcProfile, RuntimeConfig, TokenUsage } from './types';

type Sampling = {
  temperature: number;
  top_p: number;
  max_tokens: number;
};
type ThemeMode = 'light' | 'dark';

const emptySampling: Sampling = { temperature: 0.7, top_p: 0.9, max_tokens: 2048 };
const modes: Mode[] = ['agent', 'npc'];
const emptyNpcDraft: NpcDraft = { id: '', system_prompt: '', opening: '' };
const emptyAgentDraft: AgentDraft = { id: '', system_prompt: '' };

function safeFilename(name: string) {
  return name.replace(/[\\/:*?"<>|]/g, '_').slice(0, 60) || 'conversation';
}

function renderMarkdown(content: string): string {
  const escaped = content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
  return marked.parse(escaped, { gfm: true, breaks: true }) as string;
}

function fillRoundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number, fill: string) {
  const cr = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + cr, y);
  ctx.lineTo(x + w - cr, y);
  ctx.quadraticCurveTo(x + w, y, x + w, y + cr);
  ctx.lineTo(x + w, y + h - cr);
  ctx.quadraticCurveTo(x + w, y + h, x + w - cr, y + h);
  ctx.lineTo(x + cr, y + h);
  ctx.quadraticCurveTo(x, y + h, x, y + h - cr);
  ctx.lineTo(x, y + cr);
  ctx.quadraticCurveTo(x, y, x + cr, y);
  ctx.closePath();
  ctx.fillStyle = fill;
  ctx.fill();
}

async function conversationToImageBlob(conversation: Conversation, assistantName: string, isDark: boolean): Promise<Blob> {
  const scale = 2;
  const logicalWidth = 860;
  const W = logicalWidth * scale;
  const pad = 40 * scale;
  const msgGap = 20 * scale;
  const avatarSz = 40 * scale;
  const avatarR = 12 * scale;
  const avatarGap = 12 * scale;
  const bPad = 14 * scale;
  const bRadius = 16 * scale;
  const bubbleMaxW = W - pad * 2 - avatarSz - avatarGap;
  const fs = 15 * scale;
  const lh = Math.round(fs * 1.65);
  const nameFs = 13 * scale;
  const nameLh = nameFs + 8 * scale;
  const titleFs = 20 * scale;
  const subFs = 13 * scale;

  const clr = isDark
    ? {
        bg: '#141218',
        surfaceContainer: '#211f26',
        onSurface: '#e6e1e5',
        onSurfaceVar: '#cac4d0',
        userBubble: 'rgba(99,59,72,0.55)',
        asstBubble: '#2b2930',
        userAvatarBg: '#633b48',
        asstAvatarBg: '#4d3d75',
        userNameClr: '#ffb3c1',
        asstNameClr: '#cfbcff',
        outline: '#49454f',
      }
    : {
        bg: '#ece6f0',
        surfaceContainer: '#f3edf7',
        onSurface: '#1c1b1f',
        onSurfaceVar: '#49454f',
        userBubble: 'rgba(255,216,228,0.65)',
        asstBubble: '#fdfbff',
        userAvatarBg: '#ffd8e4',
        asstAvatarBg: '#e9ddff',
        userNameClr: '#8b2d47',
        asstNameClr: '#4d3d75',
        outline: '#cac4d0',
      };

  // Measure pass
  const draft = document.createElement('canvas');
  draft.width = W;
  draft.height = 100;
  const dCtx = draft.getContext('2d')!;
  dCtx.font = `${fs}px "Segoe UI", Roboto, sans-serif`;

  type Block = { isUser: boolean; name: string; lines: string[]; bubbleH: number };
  const blocks: Block[] = [];

  const visibleMessages = conversation.messages.filter(
    (m) => withMessageDefaults(m).role !== 'system' && withMessageDefaults(m).role !== 'tool'
  );

  for (const msg of visibleMessages) {
    const item = withMessageDefaults(msg);
    const isUser = item.role === 'user';
    const name = isUser ? '你' : assistantName;
    const parts: string[] = [];
    if (item.reasoning_content.trim()) parts.push(`[思考]\n${item.reasoning_content.trim()}`);
    if (item.tool_calls.length > 0) parts.push(`[工具调用]\n${JSON.stringify(item.tool_calls, null, 2)}`);
    parts.push(item.content || '（空）');
    if (item.attachments.length > 0) {
      const names = item.attachments
        .map((f) => `${f.kind === 'image' ? '图片' : f.kind === 'video' ? '视频' : '文件'}: ${f.name}`)
        .join('\n');
      parts.push(`[附件]\n${names}`);
    }
    dCtx.font = `${fs}px "Segoe UI", Roboto, sans-serif`;
    const lines = wrapTextLines(dCtx, parts.join('\n\n'), bubbleMaxW - bPad * 2);
    const bubbleH = Math.max(avatarSz, nameLh + lines.length * lh + bPad * 2);
    blocks.push({ isUser, name, lines, bubbleH });
  }

  const headerH = pad + titleFs + 8 * scale + subFs + 28 * scale;
  const totalH = headerH + blocks.reduce((sum, b) => sum + b.bubbleH + msgGap, 0) + pad;

  const canvas = document.createElement('canvas');
  canvas.width = W;
  canvas.height = Math.max(600 * scale, totalH);
  const ctx = canvas.getContext('2d')!;

  // Background
  ctx.fillStyle = clr.bg;
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  // Header
  let y = pad;
  ctx.font = `700 ${titleFs}px "Segoe UI", Roboto, sans-serif`;
  ctx.fillStyle = clr.onSurface;
  ctx.fillText(conversation.title, pad, y + titleFs);
  y += titleFs + 8 * scale;
  ctx.font = `${subFs}px "Segoe UI", Roboto, sans-serif`;
  ctx.fillStyle = clr.onSurfaceVar;
  ctx.fillText(
    `${normalizeMode(conversation.mode) === 'npc' ? 'NPC' : 'Agent'} · ${assistantName} · ${visibleMessages.length} 条消息`,
    pad,
    y + subFs
  );
  y += subFs + 28 * scale;

  // Messages
  for (const block of blocks) {
    const avatarX = block.isUser ? W - pad - avatarSz : pad;
    const bubbleX = block.isUser ? W - pad - avatarSz - avatarGap - bubbleMaxW : pad + avatarSz + avatarGap;

    // Avatar circle
    fillRoundRect(ctx, avatarX, y, avatarSz, avatarSz, avatarR, block.isUser ? clr.userAvatarBg : clr.asstAvatarBg);

    // Avatar letter
    ctx.font = `600 ${Math.round(avatarSz * 0.42)}px "Segoe UI", Roboto, sans-serif`;
    ctx.fillStyle = isDark ? '#ffffff' : '#ffffff';
    ctx.textAlign = 'center';
    ctx.fillText(block.name.charAt(0), avatarX + avatarSz / 2, y + avatarSz / 2 + Math.round(avatarSz * 0.15));
    ctx.textAlign = 'left';

    // Bubble
    fillRoundRect(ctx, bubbleX, y, bubbleMaxW, block.bubbleH, bRadius, block.isUser ? clr.userBubble : clr.asstBubble);

    // Name
    const textX = bubbleX + bPad;
    let textY = y + bPad;
    ctx.font = `600 ${nameFs}px "Segoe UI", Roboto, sans-serif`;
    ctx.fillStyle = block.isUser ? clr.userNameClr : clr.asstNameClr;
    ctx.fillText(block.name, textX, textY + nameFs);
    textY += nameLh;

    // Content lines
    ctx.font = `${fs}px "Segoe UI", Roboto, sans-serif`;
    ctx.fillStyle = clr.onSurface;
    for (const line of block.lines) {
      textY += lh;
      ctx.fillText(line || ' ', textX, textY);
    }

    y += block.bubbleH + msgGap;
  }

  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob);
      else reject(new Error('图片导出失败'));
    }, 'image/png');
  });
}
function wrapTextLines(context: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
  const lines: string[] = [];
  for (const paragraph of text.split('\n')) {
    if (!paragraph) {
      lines.push('');
      continue;
    }
    let current = '';
    for (const char of paragraph) {
      const next = `${current}${char}`;
      if (context.measureText(next).width > maxWidth && current) {
        lines.push(current);
        current = char;
      } else {
        current = next;
      }
    }
    lines.push(current);
  }
  return lines;
}

function resolveInitialTheme(): ThemeMode {
  if (typeof window === 'undefined') return 'light';
  const stored = window.localStorage.getItem('theme-mode');
  if (stored === 'light' || stored === 'dark') return stored;
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function normalizeMode(value: Conversation['mode']): Mode {
  return value === 'npc' ? 'npc' : 'agent';
}

function withMessageDefaults(message: ChatMessage): ChatMessage {
  return {
    ...message,
    reasoning_content: message.reasoning_content ?? '',
    tool_calls: message.tool_calls ?? [],
    attachments: message.attachments ?? [],
    latency_ms: message.latency_ms ?? null,
    usage: message.usage ?? null,
  };
}

function makeMessage(role: ChatMessage['role'], content: string, attachments: Attachment[] = []): ChatMessage {
  return {
    id: crypto.randomUUID(),
    role,
    content,
    reasoning_content: '',
    tool_calls: [],
    attachments,
    created_at: new Date().toISOString()
  };
}

function CollapsiblePart({ title, preview, collapsed, onToggle }: {
  title: ReactNode;
  preview: string;
  collapsed: boolean;
  onToggle: () => void;
}) {
  return (
    <div className="part-title">
      <span className="part-title-label">{title}</span>
      {collapsed && <span className="part-preview">{preview.slice(0, 80)}{preview.length > 80 ? '…' : ''}</span>}
      <button className="part-toggle" type="button" onClick={onToggle}>
        <ChevronDown size={14} className={collapsed ? 'chevron-collapsed' : ''} />
      </button>
    </div>
  );
}

function MessageParts({ message, autoCollapseDetails = false }: { message: ChatMessage; autoCollapseDetails?: boolean }) {
  const item = withMessageDefaults(message);
  const [reasoningCollapsed, setReasoningCollapsed] = useState(autoCollapseDetails);
  const [toolsCollapsed, setToolsCollapsed] = useState(autoCollapseDetails);

  useEffect(() => {
    if (autoCollapseDetails) {
      setReasoningCollapsed(true);
      setToolsCollapsed(true);
    }
  }, [autoCollapseDetails]);

  if (item.role === 'user') {
    return <p className="body-text">{item.content}</p>;
  }

  return (
    <div className="message-parts">
      {item.reasoning_content && (
        <section className="part reasoning-part">
          <CollapsiblePart
            title={<><Brain size={15} /> 思考</>}
            preview={item.reasoning_content}
            collapsed={reasoningCollapsed}
            onToggle={() => setReasoningCollapsed((v) => !v)}
          />
          <div className={`part-collapsible${reasoningCollapsed ? ' part-collapsible-hidden' : ''}`}>
            <div className="part-collapsible-inner"><p>{item.reasoning_content}</p></div>
          </div>
        </section>
      )}
      {item.tool_calls.length > 0 && (
        <section className="part tool-part">
          <CollapsiblePart
            title={<><Wrench size={15} /> 工具调用</>}
            preview={JSON.stringify(item.tool_calls)}
            collapsed={toolsCollapsed}
            onToggle={() => setToolsCollapsed((v) => !v)}
          />
          <div className={`part-collapsible${toolsCollapsed ? ' part-collapsible-hidden' : ''}`}>
            <div className="part-collapsible-inner"><pre>{JSON.stringify(item.tool_calls, null, 2)}</pre></div>
          </div>
        </section>
      )}
      <section className="part answer-part">
        <div className="part-title"><Bot size={15} /> 正文</div>
        <div className="markdown-content" dangerouslySetInnerHTML={{ __html: renderMarkdown(item.content || '正在生成...') }} />
      </section>
    </div>
  );
}

export default function App() {
  const [config, setConfig] = useState<RuntimeConfig | null>(null);
  const [npcs, setNpcs] = useState<NpcProfile[]>([]);
  const [agents, setAgents] = useState<AgentProfile[]>([]);
  const [modelList, setModelList] = useState<ModelInfo[]>([]);
  const [selectedModel, setSelectedModel] = useState<string>('');
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [active, setActive] = useState<Conversation | null>(null);
  const [mode, setMode] = useState<Mode>('agent');
  const [npcId, setNpcId] = useState<string>('');
  const [agentId, setAgentId] = useState<string>('');
  const [message, setMessage] = useState('');
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [sampling, setSampling] = useState<Sampling>(emptySampling);
  const [stream, setStream] = useState(true);
  const [thinking, setThinking] = useState(false);
  const [tools, setTools] = useState(false);
  const [busy, setBusy] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draftContent, setDraftContent] = useState('');
  const [theme, setTheme] = useState<ThemeMode>(resolveInitialTheme);
  const [latencyMap, setLatencyMap] = useState<Record<string, number>>({});
  const [usageMap, setUsageMap] = useState<Record<string, TokenUsage>>({});
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; id: string } | null>(null);
  const [profileContextMenu, setProfileContextMenu] = useState<{ x: number; y: number; id: string; kind: 'npc' | 'agent' } | null>(null);
  const [copiedMessageId, setCopiedMessageId] = useState<string | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [settingsTab, setSettingsTab] = useState<'npc' | 'agent'>('npc');
  const [npcEditingId, setNpcEditingId] = useState<string | null>(null);
  const [npcDraft, setNpcDraft] = useState<NpcDraft>(emptyNpcDraft);
  const [npcSaving, setNpcSaving] = useState(false);
  const [npcError, setNpcError] = useState<string>('');
  const [agentEditingId, setAgentEditingId] = useState<string | null>(null);
  const [agentDraft, setAgentDraft] = useState<AgentDraft>(emptyAgentDraft);
  const [agentSaving, setAgentSaving] = useState(false);
  const [agentError, setAgentError] = useState<string>('');
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const latestAssistantMessageId = useMemo(
    () => [...(active?.messages ?? [])].reverse().find((item) => item.role === 'assistant')?.id ?? null,
    [active?.messages]
  );

  useEffect(() => {
    Promise.all([api.config(), api.npcs(), api.agents(), api.conversations(), api.models()]).then(([runtime, profiles, agentProfiles, items, models]) => {
      setConfig(runtime);
      applyNpcList(profiles, profiles[0]?.id ?? null);
      applyAgentList(agentProfiles, agentProfiles[0]?.id ?? null);
      setConversations(items);
      setModelList(models);
      setSelectedModel(runtime.model);
      setSampling({
        temperature: runtime.defaults.temperature,
        top_p: runtime.defaults.top_p,
        max_tokens: runtime.defaults.max_tokens
      });
      setStream(runtime.defaults.stream);
      setThinking(runtime.defaults.thinking);
      setTools(runtime.defaults.tools);
      if (items[0]) selectConversation(items[0]);
    });
  }, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [active, busy]);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    window.localStorage.setItem('theme-mode', theme);
  }, [theme]);


  const activeNpc = useMemo(() => npcs.find((npc) => npc.id === npcId), [npcId, npcs]);
  const activeAgent = useMemo(() => agents.find((agent) => agent.id === agentId), [agentId, agents]);

  function moveProfileToBottom<T extends { id: string }>(items: T[], id?: string | null): T[] {
    if (!id) return items;
    const index = items.findIndex((item) => item.id === id);
    if (index < 0 || index === items.length - 1) return items;
    return [...items.slice(0, index), ...items.slice(index + 1), items[index]];
  }

  function applyNpcList(next: NpcProfile[], preferredId?: string | null, appendPreferredToBottom = false) {
    const ordered = appendPreferredToBottom ? moveProfileToBottom(next, preferredId) : next;
    setNpcs(ordered);
    if (ordered.length === 0) {
      setNpcId('');
      return;
    }
    const candidate = preferredId ?? npcId;
    if (candidate && ordered.some((npc) => npc.id === candidate)) {
      setNpcId(candidate);
      return;
    }
    setNpcId(ordered[0].id);
  }

  async function refreshNpcs(preferredId?: string | null, appendPreferredToBottom = false) {
    const items = await api.npcs();
    applyNpcList(items, preferredId, appendPreferredToBottom);
  }

  function applyAgentList(next: AgentProfile[], preferredId?: string | null, appendPreferredToBottom = false) {
    const ordered = appendPreferredToBottom ? moveProfileToBottom(next, preferredId) : next;
    setAgents(ordered);
    if (ordered.length === 0) {
      setAgentId('');
      return;
    }
    const candidate = preferredId ?? agentId;
    if (candidate && ordered.some((agent) => agent.id === candidate)) {
      setAgentId(candidate);
      return;
    }
    setAgentId(ordered[0].id);
  }

  async function refreshAgents(preferredId?: string | null, appendPreferredToBottom = false) {
    const items = await api.agents();
    applyAgentList(items, preferredId, appendPreferredToBottom);
  }

  function openSettings(tab: 'npc' | 'agent') {
    setSettingsTab(tab);
    if (tab === 'npc') {
      const initial = npcs.find((item) => item.id === npcId) ?? npcs[0] ?? null;
      if (initial) {
        setNpcEditingId(initial.id);
        setNpcDraft({ id: initial.id, system_prompt: initial.system_prompt, opening: initial.opening ?? '' });
      } else {
        setNpcEditingId(null);
        setNpcDraft(emptyNpcDraft);
      }
      setNpcError('');
    } else {
      const initial = agents.find((item) => item.id === agentId) ?? agents[0] ?? null;
      if (initial) {
        setAgentEditingId(initial.id);
        setAgentDraft({ id: initial.id, system_prompt: initial.system_prompt });
      } else {
        setAgentEditingId(null);
        setAgentDraft(emptyAgentDraft);
      }
      setAgentError('');
    }
    setProfileContextMenu(null);
    setSettingsOpen(true);
  }

  function selectNpcForEdit(profile: NpcProfile) {
    setNpcEditingId(profile.id);
    setNpcDraft({
      id: profile.id,
      system_prompt: profile.system_prompt,
      opening: profile.opening ?? ''
    });
    setNpcError('');
  }

  function startNewNpc() {
    setNpcEditingId(null);
    setNpcDraft(emptyNpcDraft);
    setNpcError('');
  }

  function selectAgentForEdit(profile: AgentProfile) {
    setAgentEditingId(profile.id);
    setAgentDraft({
      id: profile.id,
      system_prompt: profile.system_prompt
    });
    setAgentError('');
  }

  function startNewAgent() {
    setAgentEditingId(null);
    setAgentDraft(emptyAgentDraft);
    setAgentError('');
  }

  function selectConversation(conversation: Conversation) {
    setActive({ ...conversation, messages: conversation.messages.map(withMessageDefaults) });
    setMode(normalizeMode(conversation.mode));
    setNpcId(conversation.npc_id ?? '');
    setAgentId(conversation.agent_id ?? '');
  }

  async function refreshConversations(nextActiveId?: string) {
    const items = await api.conversations();
    setConversations(items);
    const nextActive = items.find((item) => item.id === nextActiveId) ?? (active ? items.find((item) => item.id === active.id) : null);
    if (nextActive) selectConversation(nextActive);
  }

  async function newConversation(nextMode = mode, nextNpcId = npcId, nextAgentId = agentId) {
    const conversation = await api.createConversation(
      nextMode,
      nextMode === 'npc' ? nextNpcId : null,
      nextMode === 'agent' ? nextAgentId : null
    );
    setConversations((items) => [conversation, ...items]);
    selectConversation(conversation);
  }

  function findRecentConversationForRole(nextMode: Mode, nextRoleId: string): Conversation | undefined {
    return conversations.find((conversation) => {
      if (normalizeMode(conversation.mode) !== nextMode) return false;
      if (nextMode === 'npc') return (conversation.npc_id ?? '') === nextRoleId;
      return (conversation.agent_id ?? '') === nextRoleId;
    });
  }

  async function switchToRoleConversation(nextMode: Mode, nextRoleId: string) {
    const recent = findRecentConversationForRole(nextMode, nextRoleId);
    if (recent) {
      selectConversation(recent);
      return;
    }
    if (nextMode === 'npc') {
      await newConversation('npc', nextRoleId, agentId);
      return;
    }
    await newConversation('agent', npcId, nextRoleId);
  }

  async function switchMode(nextMode: Mode) {
    if (nextMode === mode) return;
    setMode(nextMode);
    const nextRoleId = nextMode === 'npc' ? npcId : agentId;
    await switchToRoleConversation(nextMode, nextRoleId);
  }

  async function onRoleChange(nextRoleId: string) {
    if (mode === 'npc') {
      setNpcId(nextRoleId);
      await switchToRoleConversation('npc', nextRoleId);
      return;
    }
    setAgentId(nextRoleId);
    await switchToRoleConversation('agent', nextRoleId);
  }

  async function removeConversation(id: string) {
    await api.deleteConversation(id);
    const remaining = conversations.filter((item) => item.id !== id);
    setConversations(remaining);
    if (active?.id === id) {
      const next = remaining[0];
      if (next) selectConversation(next);
      else setActive(null);
    }
  }

  async function copyAssistantMessage(item: ChatMessage) {
    if (!item.content.trim()) return;
    await navigator.clipboard.writeText(item.content);
    setCopiedMessageId(item.id);
    window.setTimeout(() => setCopiedMessageId((current) => (current === item.id ? null : current)), 1600);
  }

  function getConversation(id: string): Conversation | undefined {
    if (active?.id === id) return active;
    return conversations.find((conversation) => conversation.id === id);
  }

  function downloadConversationJson(id: string) {
    const conversation = getConversation(id);
    if (!conversation) return;
    const blob = new Blob([JSON.stringify(conversation, null, 2)], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${safeFilename(conversation.title)}.json`;
    link.click();
    URL.revokeObjectURL(url);
  }

  async function downloadConversationImage(id: string) {
    const conversation = getConversation(id);
    if (!conversation) return;
    const npcName = npcs.find((npc) => npc.id === conversation.npc_id)?.name;
    const agentName = agents.find((agent) => agent.id === conversation.agent_id)?.name;
    const assistantName = normalizeMode(conversation.mode) === 'npc' ? (npcName || 'NPC') : (agentName || 'Agent');
    const blob = await conversationToImageBlob(conversation, assistantName, theme === 'dark');
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${safeFilename(conversation.title)}.png`;
    link.click();
    URL.revokeObjectURL(url);
  }

  async function saveNpcDraft() {
    const nextId = npcDraft.id.trim();
    const prompt = npcDraft.system_prompt.trim();
    if (!nextId) {
      setNpcError('请填写 NPC 标识');
      return;
    }
    if (!prompt) {
      setNpcError('请填写 system prompt');
      return;
    }

    setNpcSaving(true);
    setNpcError('');
    try {
      if (npcEditingId) {
        await api.updateNpc(npcEditingId, {
          id: nextId,
          system_prompt: prompt,
          opening: npcDraft.opening?.trim() || null
        });
      } else {
        await api.createNpc({
          id: nextId,
          system_prompt: prompt,
          opening: npcDraft.opening?.trim() || null
        });
      }
      await refreshNpcs(nextId, !npcEditingId);
      setNpcEditingId(nextId);
      setNpcDraft((current) => ({ ...current, id: nextId, system_prompt: prompt, opening: current.opening?.trim() || '' }));
    } catch (error) {
      setNpcError(`保存失败：${String(error)}`);
    } finally {
      setNpcSaving(false);
    }
  }

  async function removeNpcDraft(id: string) {
    setNpcSaving(true);
    setNpcError('');
    try {
      await api.deleteNpc(id);
      const remaining = npcs.filter((npc) => npc.id !== id);
      const nextId = remaining[0]?.id ?? '';
      applyNpcList(remaining, nextId);
      if (!nextId) {
        setNpcEditingId(null);
        setNpcDraft(emptyNpcDraft);
      } else {
        const profile = remaining[0];
        setNpcEditingId(profile.id);
        setNpcDraft({
          id: profile.id,
          system_prompt: profile.system_prompt,
          opening: profile.opening ?? ''
        });
      }
      if (mode === 'npc' && id === npcId) {
        await newConversation('npc');
      }
    } catch (error) {
      setNpcError(`删除失败：${String(error)}`);
    } finally {
      setNpcSaving(false);
    }
  }

  async function saveAgentDraft() {
    const nextId = agentDraft.id.trim();
    const prompt = agentDraft.system_prompt.trim();
    if (!nextId) {
      setAgentError('请填写 Agent 标识');
      return;
    }
    if (!prompt) {
      setAgentError('请填写 system prompt');
      return;
    }

    setAgentSaving(true);
    setAgentError('');
    try {
      if (agentEditingId) {
        await api.updateAgent(agentEditingId, {
          id: nextId,
          system_prompt: prompt
        });
      } else {
        await api.createAgent({
          id: nextId,
          system_prompt: prompt
        });
      }
      await refreshAgents(nextId, !agentEditingId);
      setAgentEditingId(nextId);
      setAgentDraft({ id: nextId, system_prompt: prompt });
    } catch (error) {
      setAgentError(`保存失败：${String(error)}`);
    } finally {
      setAgentSaving(false);
    }
  }

  async function removeAgentDraft(id: string) {
    setAgentSaving(true);
    setAgentError('');
    try {
      await api.deleteAgent(id);
      const remaining = agents.filter((agent) => agent.id !== id);
      const nextId = remaining[0]?.id ?? '';
      applyAgentList(remaining, nextId);
      if (!nextId) {
        setAgentEditingId(null);
        setAgentDraft(emptyAgentDraft);
      } else {
        const profile = remaining[0];
        setAgentEditingId(profile.id);
        setAgentDraft({
          id: profile.id,
          system_prompt: profile.system_prompt
        });
      }
      if (mode === 'agent' && id === agentId) {
        await newConversation('agent');
      }
    } catch (error) {
      setAgentError(`删除失败：${String(error)}`);
    } finally {
      setAgentSaving(false);
    }
  }

  async function onUpload(files: FileList | null) {
    if (!files?.length) return;
    const uploaded = await api.upload(files);
    setAttachments((items) => [...items, ...uploaded]);
  }

  async function sendContent(content: string, atts: Attachment[], conversationToUse: Conversation) {
    const activeMode = normalizeMode(conversationToUse.mode === 'normal' ? mode : conversationToUse.mode as Mode);
    const npc = activeMode === 'npc' ? npcId : null;
    const agent = activeMode === 'agent' ? agentId : null;
    const localUser = makeMessage('user', content, atts);
    const localAssistant = makeMessage('assistant', '');
    setActive({
      ...conversationToUse,
      mode: activeMode,
      npc_id: npc,
      agent_id: agent,
      messages: [...conversationToUse.messages.map(withMessageDefaults), localUser, localAssistant]
    });

    const body = {
      conversation_id: conversationToUse.id,
      mode: activeMode,
      npc_id: npc,
      agent_id: agent,
      message: content,
      attachments: atts,
      stream,
      thinking_enabled: thinking,
      tools_enabled: tools,
      sampling,
      model: selectedModel || undefined,
    };

    try {
      if (stream) {
        await streamChat(body, localAssistant.id);
      } else {
        const response = await fetch('/api/chat', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        });
        if (!response.ok) throw new Error(await response.text());
        const data = await response.json();
        selectConversation(data.conversation);
        if (data.latency_ms != null && data.assistant_message?.id) {
          setLatencyMap((prev) => ({ ...prev, [data.assistant_message.id]: data.latency_ms }));
        }
        if (data.usage != null && data.assistant_message?.id) {
          setUsageMap((prev) => ({ ...prev, [data.assistant_message.id]: data.usage }));
        }
      }
      await refreshConversations(conversationToUse.id);
    } catch (error) {
      appendToAssistantPart(localAssistant.id, 'content', `\n请求失败：${String(error)}`);
    }
  }

  async function sendMessage(event: FormEvent) {
    event.preventDefault();
    if (!message.trim() || busy) return;
    setBusy(true);

    let conversation = active;
    if (!conversation) {
      conversation = await api.createConversation(mode, mode === 'npc' ? npcId : null, mode === 'agent' ? agentId : null);
      setConversations((items) => [conversation!, ...items]);
    }

    const content = message;
    const atts = attachments;
    setMessage('');
    setAttachments([]);

    try {
      await sendContent(content, atts, conversation);
    } finally {
      setBusy(false);
    }
  }

  async function resendEditedMessage(messageId: string) {
    if (!active || busy) return;
    const messageIndex = active.messages.findIndex((m) => m.id === messageId);
    if (messageIndex === -1) return;

    setBusy(true);
    setEditingId(null);
    const editedContent = draftContent;

    try {
      const truncated = { ...active, messages: active.messages.slice(0, messageIndex) };
      const saved = await api.updateConversation(truncated);
      await sendContent(editedContent, [], saved);
    } finally {
      setBusy(false);
    }
  }

  async function streamChat(body: unknown, assistantId: string) {
    const response = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    if (!response.ok || !response.body) throw new Error(await response.text());

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const events = buffer.split('\n\n');
      buffer = events.pop() ?? '';
      for (const event of events) {
        const line = event.split('\n').find((item) => item.startsWith('data: '));
        if (!line) continue;
        const data = JSON.parse(line.slice(6));
        if (data.type === 'token') appendToAssistantPart(assistantId, 'content', data.content);
        if (data.type === 'reasoning') appendToAssistantPart(assistantId, 'reasoning_content', data.content);
        if (data.type === 'tool_call') appendToolCalls(assistantId, data.tool_calls ?? []);
        if (data.type === 'done') {
          selectConversation(data.conversation);
          if (data.latency_ms != null && data.assistant_message?.id) {
            setLatencyMap((prev) => ({ ...prev, [data.assistant_message.id]: data.latency_ms }));
          }
          if (data.usage != null && data.assistant_message?.id) {
            setUsageMap((prev) => ({ ...prev, [data.assistant_message.id]: data.usage }));
          }
        }
        if (data.type === 'error') appendToAssistantPart(assistantId, 'content', `\n${data.message}`);
      }
    }
  }

  function appendToAssistantPart(id: string, field: 'content' | 'reasoning_content', token: string) {
    setActive((current) => {
      if (!current) return current;
      return {
        ...current,
        messages: current.messages.map((message) => {
          const item = withMessageDefaults(message);
          return item.id === id ? { ...item, [field]: `${item[field]}${token}` } : item;
        })
      };
    });
  }

  function appendToolCalls(id: string, toolCalls: unknown[]) {
    setActive((current) => {
      if (!current) return current;
      return {
        ...current,
        messages: current.messages.map((message) => {
          const item = withMessageDefaults(message);
          return item.id === id ? { ...item, tool_calls: [...item.tool_calls, ...toolCalls] } : item;
        })
      };
    });
  }

  function assistantDisplayName(): string {
    if (active?.mode === 'npc') return activeNpc?.name ?? 'NPC';
    return activeAgent?.name ?? 'Agent';
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <Bot size={24} />
          <div>
            <strong>MyAgent</strong>
            <span>{config ? `${config.provider} · ${selectedModel || config.model}` : '加载中'}</span>
          </div>
        </div>
        <button className="primary-button" onClick={() => newConversation()}>
          <MessageSquarePlus size={18} /> 新建会话
        </button>
        <div className="conversation-list">
          {conversations.map((conversation) => {
            const convMode = normalizeMode(conversation.mode);
            const roleName = convMode === 'npc'
              ? (npcs.find((n) => n.id === conversation.npc_id)?.name ?? null)
              : (agents.find((a) => a.id === conversation.agent_id)?.name ?? null);
            return (
              <button
                key={conversation.id}
                className={conversation.id === active?.id ? 'conversation active' : 'conversation'}
                onClick={() => selectConversation(conversation)}
                onContextMenu={(e) => {
                  e.preventDefault();
                  setContextMenu({ x: e.clientX, y: e.clientY, id: conversation.id });
                  setProfileContextMenu(null);
                }}
              >
                <div className="conv-info">
                  <span>{conversation.title}</span>
                  {roleName && <em className="conv-role">{roleName}</em>}
                </div>
                <small>{convMode === 'agent' ? 'Agent' : 'NPC'}</small>
              </button>
            );
          })}
        </div>
      </aside>

      {contextMenu && (
        <>
          <div className="context-menu-backdrop" onClick={() => setContextMenu(null)} />
          <div
            className="context-menu"
            style={{ left: contextMenu.x, top: contextMenu.y }}
          >
            <button
              className="context-menu-item"
              onClick={() => { void downloadConversationJson(contextMenu.id); setContextMenu(null); }}
            >
              <Download size={14} /> 下载 JSON
            </button>
            <button
              className="context-menu-item"
              onClick={() => { void downloadConversationImage(contextMenu.id); setContextMenu(null); }}
            >
              <Download size={14} /> 下载图片
            </button>
            <button
              className="context-menu-item danger"
              onClick={() => { removeConversation(contextMenu.id); setContextMenu(null); }}
            >
              <Trash2 size={14} /> 删除会话
            </button>
          </div>
        </>
      )}

      {settingsOpen && (
        <div className="npc-editor-overlay" onClick={() => {
          setSettingsOpen(false);
          setProfileContextMenu(null);
        }}>
          <div className="npc-editor" onClick={(event) => event.stopPropagation()}>
            <div className="npc-editor-head">
              <strong>设置</strong>
              <button className="tiny-button" type="button" onClick={() => { setSettingsOpen(false); setProfileContextMenu(null); }}><X size={14} /></button>
            </div>
            <div className="settings-tabs">
              <button type="button" className={settingsTab === 'npc' ? 'selected' : ''} onClick={() => { setSettingsTab('npc'); setProfileContextMenu(null); }}>NPC 管理</button>
              <button type="button" className={settingsTab === 'agent' ? 'selected' : ''} onClick={() => { setSettingsTab('agent'); setProfileContextMenu(null); }}>Agent 管理</button>
            </div>
            {settingsTab === 'npc' && (
              <div className="npc-editor-body">
                <aside className="npc-list">
                  <div className="npc-list-scroll">
                    {npcs.map((profile) => (
                      <button
                        type="button"
                        key={profile.id}
                        className={npcEditingId === profile.id ? 'npc-item active' : 'npc-item'}
                        onClick={() => selectNpcForEdit(profile)}
                        onContextMenu={(event) => {
                          event.preventDefault();
                          setProfileContextMenu({ x: event.clientX, y: event.clientY, id: profile.id, kind: 'npc' });
                        }}
                      >
                        {profile.name}
                      </button>
                    ))}
                  </div>
                  <div className="npc-list-footer">
                    <button className="npc-item npc-item-add" type="button" onClick={startNewNpc}>
                      <Plus size={14} /> 新建
                    </button>
                  </div>
                </aside>
                <section className="npc-form">
                  <div className="npc-form-fields">
                    <label>
                      NPC 标识
                      <input
                        value={npcDraft.id}
                        onChange={(event) => setNpcDraft((draft) => ({ ...draft, id: event.target.value }))}
                        placeholder="例如 assistant"
                      />
                    </label>
                    <label>
                      System Prompt
                      <textarea
                        value={npcDraft.system_prompt}
                        onChange={(event) => setNpcDraft((draft) => ({ ...draft, system_prompt: event.target.value }))}
                        rows={12}
                      />
                    </label>
                    <label>
                      Opening（可选）
                      <textarea
                        value={npcDraft.opening ?? ''}
                        onChange={(event) => setNpcDraft((draft) => ({ ...draft, opening: event.target.value }))}
                        rows={6}
                      />
                    </label>
                    {npcError && <div className="npc-error">{npcError}</div>}
                  </div>
                  <div className="npc-actions">
                    <button className="tiny-action" type="button" onClick={() => void saveNpcDraft()} disabled={npcSaving}>
                      <Save size={14} /> 保存
                    </button>
                  </div>
                </section>
              </div>
            )}
            {settingsTab === 'agent' && (
              <div className="npc-editor-body">
                <aside className="npc-list">
                  <div className="npc-list-scroll">
                    {agents.map((profile) => (
                      <button
                        type="button"
                        key={profile.id}
                        className={agentEditingId === profile.id ? 'npc-item active' : 'npc-item'}
                        onClick={() => selectAgentForEdit(profile)}
                        onContextMenu={(event) => {
                          event.preventDefault();
                          setProfileContextMenu({ x: event.clientX, y: event.clientY, id: profile.id, kind: 'agent' });
                        }}
                      >
                        {profile.name}
                      </button>
                    ))}
                  </div>
                  <div className="npc-list-footer">
                    <button className="npc-item npc-item-add" type="button" onClick={startNewAgent}>
                      <Plus size={14} /> 新建
                    </button>
                  </div>
                </aside>
                <section className="npc-form">
                  <div className="npc-form-fields">
                    <label>
                      Agent 标识
                      <input
                        value={agentDraft.id}
                        onChange={(event) => setAgentDraft((draft) => ({ ...draft, id: event.target.value }))}
                        placeholder="例如 planner"
                      />
                    </label>
                    <label>
                      System Prompt
                      <textarea
                        value={agentDraft.system_prompt}
                        onChange={(event) => setAgentDraft((draft) => ({ ...draft, system_prompt: event.target.value }))}
                        rows={14}
                      />
                    </label>
                    {agentError && <div className="npc-error">{agentError}</div>}
                  </div>
                  <div className="npc-actions">
                    <button className="tiny-action" type="button" onClick={() => void saveAgentDraft()} disabled={agentSaving}>
                      <Save size={14} /> 保存
                    </button>
                  </div>
                </section>
              </div>
            )}
          </div>
        </div>
      )}

      {profileContextMenu && (
        <>
          <div className="context-menu-backdrop" onClick={() => setProfileContextMenu(null)} />
          <div
            className="context-menu"
            style={{ left: profileContextMenu.x, top: profileContextMenu.y }}
          >
            <button
              className="context-menu-item danger"
              onClick={() => {
                if (profileContextMenu.kind === 'npc') {
                  void removeNpcDraft(profileContextMenu.id);
                } else {
                  void removeAgentDraft(profileContextMenu.id);
                }
                setProfileContextMenu(null);
              }}
            >
              <Trash2 size={14} /> 删除
            </button>
          </div>
        </>
      )}

      <section className="workspace">
        <header className="topbar">
          <div className="mode-tabs">
            {modes.map((item) => (
              <button key={item} className={mode === item ? 'selected' : ''} onClick={() => void switchMode(item)}>{item === 'agent' ? 'Agent' : 'NPC'}</button>
            ))}
          </div>
          <select
            value={mode === 'npc' ? npcId : agentId}
            disabled={mode === 'npc' ? npcs.length === 0 : agents.length === 0}
            onChange={(event) => { void onRoleChange(event.target.value); }}
            className={(mode === 'npc' ? npcId : agentId) ? 'has-value' : ''}
          >
            <option value="">{mode === 'npc' ? '选择 NPC' : '选择 Agent'}</option>
            {(mode === 'npc' ? npcs : agents).map((profile) => <option key={profile.id} value={profile.id}>{profile.name}</option>)}
          </select>
          <button className="icon-button topbar-action-start" type="button" title="管理设置" onClick={() => openSettings(mode)}>
            <Settings size={18} />
          </button>
          <button className="icon-button" type="button" title={theme === 'dark' ? '切换浅色模式' : '切换深色模式'} onClick={() => setTheme((current) => (current === 'dark' ? 'light' : 'dark'))}>
            {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
          </button>
        </header>

        <section className="chat-panel">
          {!active && <div className="empty-state">选择或新建一个会话</div>}
          {activeNpc?.opening && active?.messages.length === 0 && <div className="opening">{activeNpc.opening}</div>}
          {active?.messages.map((message) => {
            const item = withMessageDefaults(message);
            const messageLatency = item.latency_ms ?? latencyMap[item.id];
            const messageUsage = item.usage ?? usageMap[item.id];
            return (
              <article key={item.id} className={`message ${item.role}`}>
                <div className="avatar">{item.role === 'user' ? <UserRound size={18} /> : <Bot size={18} />}</div>
                <div className="bubble">
                  <div className="message-actions">
                    <strong>{item.role === 'user' ? '你' : assistantDisplayName()}</strong>
                    {item.role === 'user' && (
                      <button className="tiny-button" title="编辑并重新发送" onClick={() => { setEditingId(item.id); setDraftContent(item.content); }}><Pencil size={14} /></button>
                    )}
                    {item.role === 'assistant' && (
                      <div className="message-meta">
                        {messageLatency != null && (
                          <span className="latency">{messageLatency} ms</span>
                        )}
                        {messageUsage != null && (
                          <span className="latency">↑{messageUsage.prompt_tokens} ↓{messageUsage.completion_tokens}</span>
                        )}
                        <button className="tiny-button" title="复制回复" onClick={() => void copyAssistantMessage(item)}>
                          {copiedMessageId === item.id ? <Check size={14} /> : <Copy size={14} />}
                        </button>
                      </div>
                    )}
                  </div>
                  {editingId === item.id ? (
                    <div className="editor-inline">
                      <textarea value={draftContent} onChange={(event) => setDraftContent(event.target.value)} />
                      <button className="tiny-button" title="发送" onClick={() => resendEditedMessage(item.id)}><Send size={14} /></button>
                    </div>
                  ) : (
                    <MessageParts
                      message={item}
                      autoCollapseDetails={item.role === 'assistant' && item.id !== latestAssistantMessageId}
                    />
                  )}
                  {item.attachments.length > 0 && (
                    <div className="attachments">
                      {item.attachments.map((file) => (
                        file.kind === 'image' ? (
                          <a key={file.id} className="attachment-thumb" href={file.url} target="_blank" rel="noreferrer">
                            <img src={file.url} alt={file.name} loading="lazy" />
                            <span>{file.name}</span>
                          </a>
                        ) : (
                          <a key={file.id} href={file.url} target="_blank" rel="noreferrer">{file.name}</a>
                        )
                      ))}
                    </div>
                  )}
                </div>
              </article>
            );
          })}
          <div ref={bottomRef} />
        </section>

        <div className="bottom-dock">
          <section className="settings-bar">
            <div className="settings-title"><Settings size={16} /> 参数</div>
            <label>模型
              <select
                value={selectedModel}
                onChange={(e) => setSelectedModel(e.target.value)}
                style={{ height: 34, fontSize: 13 }}
                className={selectedModel ? 'has-value' : ''}
              >
                {modelList.length === 0 && <option value={selectedModel}>{selectedModel}</option>}
                {modelList.map((m) => <option key={m.id} value={m.id}>{m.id}</option>)}
              </select>
            </label>
            <label>温度 <input type="number" min="0" max="2" step="0.1" value={sampling.temperature} onChange={(event) => setSampling({ ...sampling, temperature: Number(event.target.value) })} /></label>
            <label>Top P <input type="number" min="0" max="1" step="0.05" value={sampling.top_p} onChange={(event) => setSampling({ ...sampling, top_p: Number(event.target.value) })} /></label>
            <label>Max Tokens <input type="number" min="1" value={sampling.max_tokens} onChange={(event) => setSampling({ ...sampling, max_tokens: Number(event.target.value) })} /></label>
            <label className="switch"><input type="checkbox" checked={stream} onChange={(event) => setStream(event.target.checked)} /> 流式</label>
            <label className="switch"><input type="checkbox" checked={thinking} onChange={(event) => setThinking(event.target.checked)} /> 思考</label>
            <label className="switch"><input type="checkbox" checked={tools} onChange={(event) => setTools(event.target.checked)} /> 工具</label>
          </section>

          <form className="composer" onSubmit={sendMessage}>
            <label className="upload-button" title="上传图片或视频">
              <ImagePlus size={20} />
              <input type="file" accept="image/*,video/*" multiple onChange={(event) => onUpload(event.target.files)} />
            </label>
            <textarea rows={1} value={message} placeholder="输入消息..." onChange={(event) => setMessage(event.target.value)} onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                event.currentTarget.form?.requestSubmit();
              }
            }} />
            <button className="send-button" disabled={busy || !message.trim()} title="发送"><Send size={20} /></button>
            {attachments.length > 0 && <div className="pending-files">{attachments.map((file) => <span key={file.id}>{file.name}</span>)}</div>}
          </form>
        </div>
      </section>
    </main>
  );
}