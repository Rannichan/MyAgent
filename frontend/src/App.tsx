import { FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import { Bot, Brain, ImagePlus, MessageSquarePlus, Pencil, Save, Send, Settings, Trash2, UserRound, Wrench } from 'lucide-react';
import { api } from './api';
import type { Attachment, ChatMessage, Conversation, Mode, NpcProfile, RuntimeConfig } from './types';

type Sampling = {
  temperature: number;
  top_p: number;
  max_tokens: number;
};

const emptySampling: Sampling = { temperature: 0.7, top_p: 0.9, max_tokens: 2048 };
const modes: Mode[] = ['agent', 'npc'];

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
        <p>{item.content || '正在生成...'}</p>
      </section>
    </div>
  );
}

export default function App() {
  const [config, setConfig] = useState<RuntimeConfig | null>(null);
  const [npcs, setNpcs] = useState<NpcProfile[]>([]);
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
  const bottomRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    Promise.all([api.config(), api.npcs(), api.conversations()]).then(([runtime, profiles, items]) => {
      setConfig(runtime);
      setNpcs(profiles);
      setConversations(items);
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

  async function onUpload(files: FileList | null) {
    if (!files?.length) return;
    const uploaded = await api.upload(files);
    setAttachments((items) => [...items, ...uploaded]);
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

    const activeMode = normalizeMode(mode);
    const localUser = makeMessage('user', message, attachments);
    const localAssistant = makeMessage('assistant', '');
    setActive({ ...conversation, mode: activeMode, npc_id: activeMode === 'npc' ? npcId : null, messages: [...conversation.messages.map(withMessageDefaults), localUser, localAssistant] });

    const body = {
      conversation_id: conversation.id,
      mode: activeMode,
      npc_id: activeMode === 'npc' ? npcId : null,
      message,
      attachments,
      stream,
      thinking_enabled: thinking,
      tools_enabled: tools,
      sampling
    };
    setMessage('');
    setAttachments([]);

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
      }
      await refreshConversations(conversation.id);
    } catch (error) {
      appendToAssistantPart(localAssistant.id, 'content', `\n请求失败：${String(error)}`);
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
        if (data.type === 'done') selectConversation(data.conversation);
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

  async function saveEditedMessage(messageId: string) {
    if (!active) return;
    const updated = { ...active, messages: active.messages.map((message) => (message.id === messageId ? { ...withMessageDefaults(message), content: draftContent } : withMessageDefaults(message))) };
    const saved = await api.updateConversation(updated);
    selectConversation(saved);
    setConversations((items) => items.map((item) => (item.id === saved.id ? saved : item)));
    setEditingId(null);
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <Bot size={24} />
          <div>
            <strong>MyAgent</strong>
            <span>{config ? `${config.provider} · ${config.model}` : '加载中'}</span>
          </div>
        </div>
        <button className="primary-button" onClick={() => newConversation()}>
          <MessageSquarePlus size={18} /> 新建会话
        </button>
        <div className="conversation-list">
          {conversations.map((conversation) => (
            <button key={conversation.id} className={conversation.id === active?.id ? 'conversation active' : 'conversation'} onClick={() => selectConversation(conversation)}>
              <span>{conversation.title}</span>
              <small>{normalizeMode(conversation.mode) === 'agent' ? 'Agent' : 'NPC'}</small>
            </button>
          ))}
        </div>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div className="mode-tabs">
            {modes.map((item) => (
              <button key={item} className={mode === item ? 'selected' : ''} onClick={() => setMode(item)}>{item === 'agent' ? 'Agent' : 'NPC'}</button>
            ))}
          </div>
          <select value={npcId} disabled={mode !== 'npc'} onChange={(event) => setNpcId(event.target.value)}>
            <option value="">选择 NPC</option>
            {npcs.map((npc) => <option key={npc.id} value={npc.id}>{npc.name}</option>)}
          </select>
          {active && <button className="icon-button danger" title="删除会话" onClick={() => removeConversation(active.id)}><Trash2 size={18} /></button>}
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
                    <strong>{item.role === 'user' ? '你' : '助手'}</strong>
                    <button className="tiny-button" title="编辑正文" onClick={() => { setEditingId(item.id); setDraftContent(item.content); }}><Pencil size={14} /></button>
                  </div>
                  {editingId === item.id ? (
                    <div className="editor-inline">
                      <textarea value={draftContent} onChange={(event) => setDraftContent(event.target.value)} />
                      <button className="tiny-button" title="保存" onClick={() => saveEditedMessage(item.id)}><Save size={14} /></button>
                    </div>
                  ) : (
                    <MessageParts message={item} />
                  )}
                  {item.attachments.length > 0 && <div className="attachments">{item.attachments.map((file) => <a key={file.id} href={file.url} target="_blank">{file.name}</a>)}</div>}
                </div>
              </article>
            );
          })}
          <div ref={bottomRef} />
        </section>

        <section className="settings-bar">
          <div className="settings-title"><Settings size={16} /> 参数</div>
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
      </section>
    </main>
  );
}