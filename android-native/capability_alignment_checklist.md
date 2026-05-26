# Android 能力对齐清单（基线：当前 backend API + frontend 功能）

## 1. NPC / Agent 管理
- [x] NPC 新增/编辑/删除（本地目录文件落盘）
- [x] Agent 新增/编辑/删除（本地目录文件落盘）
- [x] Agent Prompt 拼接顺序固定：agent→identity→user→soul→memory

### 验收标准
- 目录结构和字段语义与现有实现保持一致
- 保存后重启 App 仍可恢复

## 2. 会话管理
- [x] 新建会话（Agent/NPC 模式）
- [x] 会话列表与选中切换
- [x] 本地持久化（Room）

### 验收标准
- 新建/切换/重启恢复行为与 Web 版一致
- 首条用户消息可作为默认标题来源

## 3. 流式聊天
- [x] 本地实现 LLM SSE 流解析
- [x] 事件语义对齐：token/reasoning/tool_call/done/error
- [x] usage-only chunk 防护（choices 为空时不索引）

### 验收标准
- UI 可逐 token 增量更新
- done 后消息落盘，usage/latency 可见

## 4. 附件上传
- [ ] 本地文件选择器接入（当前保留消息结构，UI待补齐）

### 验收标准
- 图片/视频/文件附件可进入消息并传递给 LLM 请求

## 5. LLM 配置
- [x] Provider/Model/Base URL/API Key 本地保存
- [x] 替换 .env 写入为应用私有配置文件

### 验收标准
- 重启后配置可恢复
- 可在不启动 backend 的情况下直接请求 LLM

## 6. 原生 UI（MD3）
- [x] Compose + Material3 基础框架
- [x] 会话与设置页面
- [ ] 抽屉式侧栏（当前为基础布局，后续增强）
- [ ] 设计细节审查（深浅色、可访问性、间距）

### 验收标准
- 无 WebView/Capacitor
- 关键控件均为原生 MD3 组件

## 7. 质量收口
- [ ] 行为等价回归（与 Web 关键流程逐项比对）
- [ ] 长会话、流中断恢复、大附件稳定性测试
- [ ] MD3 视觉与可访问性审查
