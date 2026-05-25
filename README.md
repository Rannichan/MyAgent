# MyAgent

一个本地前后端分离 Agent 工具，支持接入本地 vLLM 或 llama.cpp 的 OpenAI 风格接口，提供流式对话、思考模式开关、工具调用开关、采样参数、图片/视频上传、会话管理、NPC 模式和 Agent 模式。

## 目录

```text
MyAgent/
  backend/        FastAPI API 服务
  frontend/       Vite + React 前端
  npc/           NPC 人设目录
  agent/          Agent prompt 片段目录
  .env.example    模型与服务配置模板
```

## 启动

1. 准备配置：

```bash
cp .env.example .env
```

按你的本地模型服务修改 `.env`：

- vLLM: `MODEL_PROVIDER=vllm`，设置 `VLLM_BASE_URL`
- llama.cpp: `MODEL_PROVIDER=llamacpp`，设置 `LLAMACPP_BASE_URL`
- 模型名通过前端模型选择器从 `/v1/models` 列表中选择

2. 启动后端：

```bash
cd backend
conda create -n myagent python=3.11 -y
conda activate myagent
pip install -r requirements.txt
python -m app.main
```

3. 启动前端：

```bash
cd frontend
npm install
npm run dev
```

打开 `http://127.0.0.1:5173`。

## Android 应用（Capacitor）

已在 `frontend/android/` 提供 Android 工程，直接复用当前前端页面与业务逻辑。

1. 构建前端资源（可选指定后端地址）：

```bash
cd frontend
# 按实际后端地址设置，例如 http://192.168.1.10:8765
VITE_API_BASE_URL=http://127.0.0.1:8765 npm run build
```

2. 同步到 Android 工程：

```bash
npx cap sync android
```

3. 使用 Android Studio 打开并部署：

- 打开 `frontend/android/`
- 连接手机（开启开发者模式与 USB 调试）或启动模拟器
- 运行 `app` 模块安装到设备

> 如果后端不在同一地址，请在构建时更新 `VITE_API_BASE_URL` 后重新 `npm run build && npx cap sync android`。

## NPC 模式

在 `npc/` 下每个子目录代表一个角色：

```text
npc/example/
  system.md      角色人设，作为 system prompt
  opening.md     可选开场白
```

前端会自动读取角色列表并允许自由选择。
同时支持在前端顶部「NPC 管理」中新增、编辑、删除并保存 NPC（保存为 `npc/<id>/system.md` 与可选 `opening.md`）。

## Agent 模式

`agent/` 目录中的 `soul.md`、`agent.md`、`identity.md`、`memory.md` 会按文件名顺序拼接为默认 system prompt。你可以继续添加 `.md` 文件，后端会一起读取。

同时支持在前端顶部「Agent 管理」中新增、编辑、删除并保存 Agent 配置（保存为 `agent/profiles/<id>/system.md`、`agent.md`、`identity.md`、`memory.md`、`soul.md`），并在 Agent 模式下选择不同 Agent。

## 图片与视频

上传文件会保存到 `backend/data/uploads/`，图片会以 OpenAI vision 消息格式传给模型；视频会作为附件 URL 和提示文本传入。具体能否理解视频取决于本地模型和服务端是否支持多模态视频。

## 工具调用

当前实现提供工具调用开关和请求参数通道。开启时会向 OpenAI 兼容接口发送示例工具定义，方便后续扩展真实本地工具执行器。
