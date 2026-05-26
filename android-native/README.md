# MyAgent Native Android (Single APK)

该目录是独立的原生 Android 客户端实现，不依赖 WebView/Capacitor，也不要求部署本项目的 Python 后端。

## 目标
- 纯原生 Android UI（Jetpack Compose + Material 3）
- 前后端能力内聚到 App 内本地用例层
- 仅在调用 LLM 时访问外部 OpenAI-compatible 服务
- 与现有 Web 版保持业务语义一致（模式、消息、流式事件、提示词拼接规则等）

## 架构
- `ui/`：Compose 页面（会话聊天 + 设置管理）
- `domain/`：业务规则（System Prompt 组装）
- `data/local/`：本地持久化（Room + 私有目录文件）
- `data/remote/`：LLM 流式网络调用与事件解析

## 本地持久化对齐
- 会话：Room `conversations` 表
- NPC：`filesDir/myagent/npc/<id>/system.md|opening.md`
- Agent：`filesDir/myagent/agent/profiles/<id>/*.md`
- 用户设定：`filesDir/myagent/agent/user.md`
- LLM 配置：`filesDir/myagent/config/llm.json`

## 运行方式
1. 用 Android Studio 打开 `/tmp/workspace/Rannichan/MyAgent/android-native`
2. 同步 Gradle
3. 运行 `app` 模块

> 说明：当前仓库未包含 Android SDK/NDK 环境，因此在此沙箱中未执行 APK 构建。

## 无后端部署模式
- App 启动后即可本地浏览历史会话与配置
- 仅发送消息时调用外部 LLM endpoint
- 不需要启动本仓库 `backend/` 服务

## 与 Web 版语义对齐关键点
- Agent system prompt 顺序：`agent -> identity -> user -> soul -> memory`
- 流式事件类型：`token / reasoning / tool_call / done / error`
- 支持 usage-only chunk（`choices` 为空）并安全跳过索引
- 会话完成后在本地状态落盘，不依赖额外会话 refetch
