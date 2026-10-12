# Minima 真机侧载浸泡清单（v2.31.0）

给 VIDDsys · 2026-10-12 Asia/Shanghai · 约 15–25 分钟

包：`minima-v2.31.0-agent-debug.apk`（推荐）或 release ~2.5MB  
说明：[`FIRST_RUN_CN.md`](./FIRST_RUN_CN.md) · [`MORNING_SUMMARY.md`](./MORNING_SUMMARY.md)

```bash
adb install -r minima-v2.31.0-agent-debug.apk
```

勾一项打 `[x]`；失败记下步骤与截图。

## A. 安装与首启
- [ ] 安装成功，图标 **Minima**，About 显示 **2.31.0**
- [ ] 聊天输入框左侧回形针可选图；图+文字 / 仅图均可发送（vision 模型）
- [ ] 发送后 composer 附件 chip 清空；Preparing… 时可点 × 取消
- [ ] 对带图用户消息：**Regenerate** 仍带图；**Edit** 恢复 chip + 可再发
- [ ] 无 Key 时出现引导；**Skip** 能进会话列表，不卡死
- [ ] Settings → PROVIDER：填 Base URL / Model / API Key；Key 保存后不明文回显
- [ ] **Check endpoint**：可达时有成功提示；故意错 URL 有失败提示（不崩溃）
- [ ] **Refresh models**：有 Key 时拉列表；点 chip 写入 Model；错误提示无明文 Key
- [ ] **Temperature / Max tokens / Top-p**：可填保存；Top-p 试 `0.9` 与 `0,9`；清空后省略该字段

## B. 普通聊天（Agent OFF）
- [ ] 新建会话 → 发一句 → 流式出现；停网再发有可读错误
- [ ] **Copy** 气泡文本 OK；**Share** 顶栏导出 Markdown 文件（含工具；无 API Key）
- [ ] 最后一条用户消息 **Edit** → 改完重发成功
- [ ] **Regenerate** 助手回复；**Delete** 气泡后列表一致
- [ ] 会话列表：重命名 / 置顶 / 归档 / 搜索 / 删除

## C. Agent + 沙箱
- [ ] Agent ON + Sandbox only ON → 让模型调 `now`，工具行出现并可完成
- [ ] 超长会话仍可发送（旧轮次被静默裁进上下文窗口，工具回合不拆开）
- [ ] 让 Agent 用 `search_memory / read_skill` 搜一个刚写入的关键词，能返回 path:line
- [ ] `search_memory` path 试 `../` 应被拒绝；大文件 / 二进制伪装不应拖垮或泄露
- [ ] 让写 `notes/smoke.md`（sandbox_write）→ 再读回内容正确
- [ ] 跑工具时点 **Stop**：工具变 _(Stopped)_，可立刻再发新消息且**不误杀**新工具行
- [ ] Step 指示（n/max）在 Agent 运行时可见
- [ ] Settings → AGENT：改 Wall clock / Tool timeout 后长跑会按新预算截止（可试较小值）

## D. 公共文件（SAF）
- [ ] Pick folder（如 Documents/Minima）→ 关 Sandbox only
- [ ] 首次 `list_dir`/`read_file`：Allow once / Allow all here / Deny for chat 行为符合预期
- [ ] Deny 后同会话同工具不再弹（粘性）；离开会话再进可重新询问
- [ ] 放 `AGENT.md` + `skills/*.md` → Reload skills → 预览可读；Agent 回合带上偏好

## E. MCP / 通知 / 数据（有条件再测）
- [ ] 粘贴 MCP JSON → Refresh/health；坏 URL 有错误且不崩（结果重启后仍可见更好）
- [ ] 长 Agent 跑时有前台通知；点通知进**当前会话**；通知 Stop 与聊天 Stop 一致
- [ ] Export sessions → 清数据或重装后 Import；pin/archive 保留、无 Key 泄露
- [ ] Export settings（默认无 Key）再 Import；可选 Export+key 有警告

## F. 稳定性快速回归（Stop 竞态）
- [ ] Agent 多步工具中途 **Stop** → 等 1s → 马上再发：旧工具停、新跑正常
- [ ] Stop → 立刻 Regenerate：无崩溃、无「幽灵 Running」
- [ ] 旋转屏幕 / 切后台再回：会话与设置仍在

## 通过标准
全部 A–C + F 为绿 → **可日常试用**。D/E 按你是否用 SAF/MCP/通知勾选。  
构建箱无 KVM，本清单就是真机验收入口。

- [ ] Agent: list skills → call **read_skill** for summarize-doc / daily-notes; body loads
- [ ] Preset **Ollama** fills http://127.0.0.1:11434/v1 (cleartext localhost only)
- [ ] Base URL `http://example.com` 被拒（CleartextPolicy / Check endpoint 提示用 HTTPS）
- [ ] Agent 调 read_skill 正常；路径式名称（如 `../x`）工具返回 Unsafe
