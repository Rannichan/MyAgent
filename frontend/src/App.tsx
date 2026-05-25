import { FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import { Bot, Brain, Check, Copy, Download, ImagePlus, MessageSquarePlus, Moon, Pencil, Send, Settings, Sun, Trash2, UserRound, Wrench } from 'lucide-react';
import { marked } from 'marked';
import { api } from './api';
import type { Attachment, ChatMessage, Conversation, Mode, ModelInfo, NpcProfile, RuntimeConfig } from './types';

type Sampling = {
  temperature: number;
  top_p: number;
  max_tokens: number;
};
type ThemeMode = 'light' | 'dark';

const emptySampling: Sampling = { temperature: 0.7, top_p: 0.9, max_tokens: 2048 };
const modes: Mode[] = ['agent', 'npc'];

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

async function conversationToImageBlob(conversation: Conversation, assistantName: string): Promise<Blob> {
  const width = 1200;
  const padding = 52;
  const contentWidth = width - padding * 2;
  const lineHeight = 34;
  const roleGap = 18;
  const bottomPadding = 60;

  const draft = document.createElement('canvas');
  const draftCtx = draft.getContext('2d');
  if (!draftCtx) throw new Error('无法生成图片');
  draftCtx.font = '24px "Segoe UI", sans-serif';

  let totalLines = 3;
  const blocks = conversation.messages.map((message) => {
    const role = message.role === 'user' ? '你' : assistantName;
    const parts: string[] = [];
    if (message.reasoning_content.trim()) parts.push(`思考: ${message.reasoning_content.trim()}`);
    if (message.tool_calls.length > 0) parts.push(`工具调用: ${JSON.stringify(message.tool_calls)}`);
    parts.push(message.content || '（空）');
    if (message.attachments.length > 0) {
      const names = message.attachments.map((file) => `${file.kind === 'image' ? '图片' : file.kind === 'video' ? '视频' : '文件'}:${file.name}`).join('，');
      parts.push(`附件: ${names}`);
    }
    const lines = wrapTextLines(draftCtx, parts.join('\n'), contentWidth - 8);
    totalLines += lines.length + 1;
    return { role, lines, isUser: message.role === 'user' };
  });

  const height = Math.max(720, padding * 2 + totalLines * lineHeight + blocks.length * roleGap + bottomPadding);
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('无法生成图片');

  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, width, height);
  ctx.fillStyle = '#111827';
  ctx.font = '700 36px "Segoe UI", sans-serif';
  ctx.fillText(conversation.title, padding, padding);
  ctx.font = '500 20px "Segoe UI", sans-serif';
  ctx.fillStyle = '#6b7280';
  ctx.fillText(`模式: ${conversation.mode === 'npc' ? 'NPC' : 'Agent'} · 消息数: ${conversation.messages.length}`, padding, padding + 42);

  let y = padding + 90;
  blocks.forEach((block) => {
    ctx.font = '600 24px "Segoe UI", sans-serif';
    ctx.fillStyle = block.isUser ? '#0f766e' : '#4338ca';
    ctx.fillText(block.role, padding, y);
    y += roleGap;
    ctx.font = '400 24px "Segoe UI", sans-serif';
    ctx.fillStyle = '#111827';
    block.lines.forEach((line) => {
      ctx.fillText(line || ' ', padding, y);
      y += lineHeight;
    });
    y += 10;
  });

  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob);
      else reject(new Error('图片导出失败'));
    }, 'image/png');
  });
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
    attachments: message.attachments ?? []
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

function MessageParts({ message }: { message: ChatMessage }) {
  const item = withMessageDefaults(message);

  if (item.role === 'user') {
    return <p className="body-text">{item.content}</p>;
  }

  return (
    <div className="message-parts">
      {item.reasoning_content && (
        <section className="part reasoning-part">
          <div className="part-title"><Brain size={15} /> 思考</div>
          <p>{item.reasoning_content}</p>
        </section>
      )}
      {item.tool_calls.length > 0 && (
        <section className="part tool-part">
          <div className="part-title"><Wrench size={15} /> 工具调用</div>
          <pre>{JSON.stringify(item.tool_calls, null, 2)}</pre>
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
  const [modelList, setModelList] = useState<ModelInfo[]>([]);
  const [selectedModel, setSelectedModel] = useState<string>('');
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [active, setActive] = useState<Conversation | null>(null);
  const [mode, setMode] = useState<Mode>('agent');
  const [npcId, setNpcId] = useState<string>('');
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
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; id: string } | null>(null);
  const [copiedMessageId, setCopiedMessageId] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    Promise.all([api.config(), api.npcs(), api.conversations(), api.models()]).then(([runtime, profiles, items, models]) => {
      setConfig(runtime);
      setNpcs(profiles);
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

  useEffect(() => {
    if (!contextMenu) return;
    const handler = () => setContextMenu(null);
    window.addEventListener('click', handler);
    return () => window.removeEventListener('click', handler);
  }, [contextMenu]);

  const activeNpc = useMemo(() => npcs.find((npc) => npc.id === npcId), [npcId, npcs]);

  function selectConversation(conversation: Conversation) {
    setActive({ ...conversation, messages: conversation.messages.map(withMessageDefaults) });
    setMode(normalizeMode(conversation.mode));
    setNpcId(conversation.npc_id ?? '');
  }

  async function refreshConversations(nextActiveId?: string) {
    const items = await api.conversations();
    setConversations(items);
    const nextActive = items.find((item) => item.id === nextActiveId) ?? (active ? items.find((item) => item.id === active.id) : null);
    if (nextActive) selectConversation(nextActive);
  }

  async function newConversation(nextMode = mode) {
    const conversation = await api.createConversation(nextMode, nextMode === 'npc' ? npcId : null);
    setConversations((items) => [conversation, ...items]);
    selectConversation(conversation);
  }

  async function switchMode(nextMode: Mode) {
    if (nextMode === mode) return;
    setMode(nextMode);
    await newConversation(nextMode);
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
    const assistantName = normalizeMode(conversation.mode) === 'npc' ? (npcName || 'NPC') : 'Agent';
    const blob = await conversationToImageBlob(conversation, assistantName);
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${safeFilename(conversation.title)}.png`;
    link.click();
    URL.revokeObjectURL(url);
  }

  async function onUpload(files: FileList | null) {
    if (!files?.length) return;
    const uploaded = await api.upload(files);
    setAttachments((items) => [...items, ...uploaded]);
  }

  async function sendContent(content: string, atts: Attachment[], conversationToUse: Conversation) {
    const activeMode = normalizeMode(conversationToUse.mode === 'normal' ? mode : conversationToUse.mode as Mode);
    const npc = activeMode === 'npc' ? npcId : null;
    const localUser = makeMessage('user', content, atts);
    const localAssistant = makeMessage('assistant', '');
    setActive({ ...conversationToUse, mode: activeMode, npc_id: npc, messages: [...conversationToUse.messages.map(withMessageDefaults), localUser, localAssistant] });

    const body = {
      conversation_id: conversationToUse.id,
      mode: activeMode,
      npc_id: npc,
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
      conversation = await api.createConversation(mode, mode === 'npc' ? npcId : null);
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
    return 'Agent';
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
          {conversations.map((conversation) => (
            <button
              key={conversation.id}
              className={conversation.id === active?.id ? 'conversation active' : 'conversation'}
              onClick={() => selectConversation(conversation)}
              onContextMenu={(e) => {
                e.preventDefault();
                setContextMenu({ x: e.clientX, y: e.clientY, id: conversation.id });
              }}
            >
              <span>{conversation.title}</span>
              <small>{normalizeMode(conversation.mode) === 'agent' ? 'Agent' : 'NPC'}</small>
            </button>
          ))}
        </div>
      </aside>

      {contextMenu && (
        <div
          className="context-menu"
          style={{ left: contextMenu.x, top: contextMenu.y }}
          onClick={(e) => e.stopPropagation()}
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
      )}

      <section className="workspace">
        <header className="topbar">
          <div className="mode-tabs">
            {modes.map((item) => (
              <button key={item} className={mode === item ? 'selected' : ''} onClick={() => void switchMode(item)}>{item === 'agent' ? 'Agent' : 'NPC'}</button>
            ))}
          </div>
          <select value={npcId} disabled={mode !== 'npc'} onChange={(event) => setNpcId(event.target.value)}>
            <option value="">选择 NPC</option>
            {npcs.map((npc) => <option key={npc.id} value={npc.id}>{npc.name}</option>)}
          </select>
          <button className="icon-button" type="button" title={theme === 'dark' ? '切换浅色模式' : '切换深色模式'} onClick={() => setTheme((current) => (current === 'dark' ? 'light' : 'dark'))}>
            {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
          </button>
        </header>

        <section className="chat-panel">
          {!active && <div className="empty-state">选择或新建一个会话</div>}
          {activeNpc?.opening && active?.messages.length === 0 && <div className="opening">{activeNpc.opening}</div>}
          {active?.messages.map((message) => {
            const item = withMessageDefaults(message);
            return (
              <article key={item.id} className={`message ${item.role}`}>
                <div className="avatar">{item.role === 'user' ? <UserRound size={18} /> : <Bot size={18} />}</div>
                <div className="bubble">
                  <div className="message-actions">
                    <strong>{item.role === 'user' ? '你' : assistantDisplayName()}</strong>
                    {item.role === 'user' && (
                      <button className="tiny-button" title="编辑并重新发送" onClick={() => { setEditingId(item.id); setDraftContent(item.content); }}><Pencil size={14} /></button>
                    )}
                    {item.role === 'assistant' && latencyMap[item.id] != null && (
                      <span className="latency">{latencyMap[item.id]} ms</span>
                    )}
                    {item.role === 'assistant' && (
                      <button className="tiny-button" title="复制回复" onClick={() => void copyAssistantMessage(item)}>
                        {copiedMessageId === item.id ? <Check size={14} /> : <Copy size={14} />}
                      </button>
                    )}
                  </div>
                  {editingId === item.id ? (
                    <div className="editor-inline">
                      <textarea value={draftContent} onChange={(event) => setDraftContent(event.target.value)} />
                      <button className="tiny-button" title="发送" onClick={() => resendEditedMessage(item.id)}><Send size={14} /></button>
                    </div>
                  ) : (
                    <MessageParts message={item} />
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
            <textarea value={message} placeholder="输入消息..." onChange={(event) => setMessage(event.target.value)} onKeyDown={(event) => {
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