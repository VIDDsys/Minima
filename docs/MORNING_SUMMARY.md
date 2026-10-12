# Minima 早间交付摘要（v2.33.0）

面向 VIDDsys · Asia/Shanghai · **晨间接手定稿**

## 5分钟上手
1. **装包**：`adb install -r minima-v2.33.0-agent-debug.apk`（或拷 release）
2. 打开 → 首启引导（无 Key 时）：三步清单 → **Open Settings** 或 Skip
3. Settings → **PROVIDER**：Preset / Base URL / Model / Temperature / Max tokens / Top-p / API Key
4. Settings → **AGENT**：Max steps / Wall clock / Tool timeout
5. 新建会话；回形针附图；Agent 可用 **focus_task**（可选 max_steps / max_seconds）
6. 技能：read_skill / search_memory / HistoryWindow；focus_task 结果含嵌套 tools 摘要

## 包
| 类型 | 文件 | 说明 |
|------|------|------|
| Debug（推荐侧载） | `minima-v2.33.0-agent-debug.apk` | ~20MB，符号完整，minimaDebug 签名 |
| Release | `minima-v2.33.0-agent-release.apk` | ~2.5MB，R8，同为 minimaDebug 侧载签名 |

```bash
source ~/.android-sdk-env.sh
cd /workspace/minima-chat
adb install -r minima-v2.33.0-agent-debug.apk
```

## v2.33.0 相对 v2.32.0（focus_task Agent 打磨）
- **可选预算**：`max_steps`（1–4）、`max_seconds`（5–60）只收紧、不扩张父预算
- **嵌套工具 transcript**：结果头后附 `tools:` 列表（ok/err + 脱敏 peek）
- `JsonSchema.integer`；versionCode **46**；MCP clientInfo → **2.33.0**

## 仍缺（诚实）
- 真机浸泡（无 KVM）、完整浏览器 OAuth/PKCE、更深子 Agent UX（子工具卡进主 UI）、本机自动化、Play 上传签名策略

## 验证
- `./scripts/verify.sh`：**PASS**（**496** 项 debug 单测）
- 无模拟器 / 无 `connectedAndroidTest`

## 真机请按
1. `docs/FIRST_RUN_CN.md`
2. `docs/CHECKLIST.md`
3. 让模型调用 focus_task（可带 max_steps=2）完成小任务，确认工具卡结果含 `tools:` 摘要

---

# Minima 早间交付摘要（v2.32.0）

面向 VIDDsys · Asia/Shanghai · **晨间接手定稿**

## 5分钟上手
1. **装包**：`adb install -r minima-v2.32.0-agent-debug.apk`（或拷 release）
2. 打开 → 首启引导（无 Key 时）：三步清单 → **Open Settings** 或 Skip
3. Settings → **PROVIDER**：Preset / Base URL / Model / Temperature / Max tokens / Top-p / API Key
4. Settings → **AGENT**：Max steps / Wall clock / Tool timeout
5. 新建会话；回形针附图；Agent 可用 **focus_task** 做短预算子任务
6. 技能：read_skill / search_memory / HistoryWindow

## 包
| 类型 | 文件 | 说明 |
|------|------|------|
| Debug（推荐侧载） | `minima-v2.32.0-agent-debug.apk` | ~20MB，符号完整，minimaDebug 签名 |
| Release | `minima-v2.32.0-agent-release.apk` | ~2.5MB，R8，同为 minimaDebug 侧载签名 |

```bash
source ~/.android-sdk-env.sh
cd /workspace/minima-chat
adb install -r minima-v2.32.0-agent-debug.apk
```

## v2.32.0 相对 v2.31.0（focus_task 子 Agent）
- **focus_task** 内置工具：主 Agent 可把单一任务交给嵌套 AgentLoop（≤4 步 / ≤60s，受父墙钟与 tool timeout 约束）
- 禁递归 / 禁并发嵌套；子运行工具集不含 focus_task
- `FocusRunContext` + `FocusTaskLogic`；MockWebServer / 纯逻辑单测
- 版本 **2.32.0** / versionCode **45**；MCP clientInfo → 2.32.0

## 仍缺（诚实）
- 真机浸泡（无 KVM）、完整浏览器 OAuth/PKCE、更深子 Agent UX、本机自动化、Play 上传签名策略

## 验证
- `./scripts/verify.sh`：**PASS**（**492** 项 debug 单测；含 focus_task settle 探针 +6，版本仍 **2.32.0**）
- Settle：nesting / budget / no-recursion **通过** → **不发 2.32.1**；晨间请装 **v2.32.0**
- 无模拟器 / 无 `connectedAndroidTest`

## 真机请按
1. `docs/FIRST_RUN_CN.md`
2. `docs/CHECKLIST.md`
3. 让模型调用 focus_task 完成一个小调研/文件任务，确认工具卡与摘要

---

# Minima 早间交付摘要（v2.31.0）

面向 VIDDsys · Asia/Shanghai · **晨间接手定稿**

## 5分钟上手
1. **装包**：`adb install -r minima-v2.31.0-agent-debug.apk`（或拷 release）
2. 打开 → 首启引导（无 Key 时）：三步清单 → **Open Settings** 或 Skip
3. Settings → **PROVIDER**：Preset / Base URL / Model / **Temperature** / **Max tokens** / **Top-p** / API Key；可 Refresh models、Check endpoint
4. Settings → **AGENT**：Max steps / Wall clock / Tool timeout
5. 新建会话；空聊有安静 starter chips；回形针附图
6. Agent：技能索引 → **read_skill**；search_memory、HistoryWindow

## 包
| 类型 | 文件 | 说明 |
|------|------|------|
| Debug（推荐侧载） | `minima-v2.31.0-agent-debug.apk` | ~20MB，符号完整，minimaDebug 签名 |
| Release | `minima-v2.31.0-agent-release.apk` | ~2.5MB，R8，同为 minimaDebug 侧载签名 |

```bash
source ~/.android-sdk-env.sh
cd /workspace/minima-chat
adb install -r minima-v2.31.0-agent-debug.apk
```

## v2.31.0 相对 v2.30.0（Top-p + 小数输入修复）
- **Top-p**：Settings PROVIDER 0–1（空白=省略）；请求写入 `top_p`；备份导入导出；Agent / 非 Agent 路径共用
- **小数草稿修复**：Temperature / Top-p 输入过滤器改为 `filterDecimalDraft`，保留地区 IME 的逗号小数（`0,7` → 0.7），对齐 v2.29 解析层承诺
- 版本 **2.31.0** / versionCode **44**；MCP clientInfo → 2.31.0

## 仍缺（诚实）
- 真机浸泡（无 KVM）、完整浏览器 OAuth/PKCE、子 Agent、本机自动化、Play 上传签名策略

## 验证
- `./scripts/verify.sh`：**PASS**（**473** 项 debug 单测）
- 无模拟器 / 无 `connectedAndroidTest`

## 真机请按
1. `docs/FIRST_RUN_CN.md`
2. `docs/CHECKLIST.md`
3. Settings → PROVIDER：试填 Top-p `0,9`（或 `0.9`），保存后再清空确认可省略

---

# Minima 早间交付摘要（v2.30.0）

面向 VIDDsys · Asia/Shanghai · **晨间接手定稿**

## 5分钟上手
1. **装包**：`adb install -r minima-v2.30.0-agent-debug.apk`（或拷 release）
2. 打开 → 首启引导（无 Key 时）：三步清单 → **Open Settings** 或 Skip
3. Settings → **PROVIDER**：Preset / Base URL / Model / **Temperature** / **Max tokens** / API Key；可 Refresh models、Check endpoint
4. Settings → **AGENT**：Max steps / **Wall clock (s)** / **Tool timeout (s)**（可拉长长跑）
5. 新建会话；空聊有安静 starter chips；回形针附图
6. Agent：技能索引 → **read_skill**；search_memory、HistoryWindow（长会话裁剪更安全）

## 包
| 类型 | 文件 | 说明 |
|------|------|------|
| Debug（推荐侧载） | `minima-v2.30.0-agent-debug.apk` | ~20MB，符号完整，minimaDebug 签名 |
| Release | `minima-v2.30.0-agent-release.apk` | ~2.5MB，R8，同为 minimaDebug 侧载签名 |

```bash
source ~/.android-sdk-env.sh
cd /workspace/minima-chat
adb install -r minima-v2.30.0-agent-debug.apk
```

## v2.30.0 相对 v2.29.0（Agent 时间预算可配）
- **Wall clock (seconds)**：整轮 Agent 墙钟 30–900（默认 180）；此前写死 180s
- **Tool timeout (seconds)**：单工具 5–300，且不超过墙钟（默认 60）；此前写死 60s
- `AgentTimingParams` 钳制 + 备份导入导出；`ChatViewModel` → `AgentBudget`
- 版本 **2.30.0** / versionCode **43**；MCP clientInfo → 2.30.0

## 仍缺（诚实）
- 真机浸泡（无 KVM）、完整浏览器 OAuth/PKCE、子 Agent、本机自动化、Play 上传签名策略

## 验证
- `./scripts/verify.sh`：**PASS**（**469** 项 debug 单测）
- 无模拟器 / 无 `connectedAndroidTest`

## 真机请按
1. `docs/FIRST_RUN_CN.md`
2. `docs/CHECKLIST.md`
3. 首启：试把 Wall clock 调到 60、Tool timeout 30，确认长工具会按时截止；再调回 180/60

---

# Minima 早间交付摘要（v2.29.0）

面向 VIDDsys · Asia/Shanghai · **晨间接手定稿**

## 5分钟上手
1. **装包**：`adb install -r minima-v2.29.0-agent-debug.apk`（或拷 release）
2. 打开 → 首启引导（无 Key 时）：三步清单 → **Open Settings** 或 Skip
3. Settings → **PROVIDER**：Preset / Base URL / Model / **Temperature** / **Max tokens** / API Key；可 Refresh models、Check endpoint
4. 新建会话；空聊有安静 starter chips；回形针附图
5. Agent：技能索引 → **read_skill**；search_memory、HistoryWindow（长会话裁剪更安全）

## 包
| 类型 | 文件 | 说明 |
|------|------|------|
| Debug（推荐侧载） | `minima-v2.29.0-agent-debug.apk` | ~20MB，符号完整，minimaDebug 签名 |
| Release | `minima-v2.29.0-agent-release.apk` | ~2.5MB，R8，同为 minimaDebug 侧载签名 |

```bash
source ~/.android-sdk-env.sh
cd /workspace/minima-chat
adb install -r minima-v2.29.0-agent-debug.apk
```

## v2.29.0 相对 v2.28.0（采样参数硬化）
- **请求边界钳制**：`MultimodalContent.encodeRequestBody` 对 temperature / max_tokens 再跑 `SamplingParams.clamp*`，坏配置（NaN、Inf、负数、Int.MAX）不会漏进 JSON
- **IME 小数逗号**：`0,7` → 0.7（KeyboardType.Decimal 在部分地区输入逗号）
- **小数 / 科学计数 max_tokens**：`4096.0`、`2e3` 可解析；`SamplingParams` 迁到 `:core:network` 供编码层共用
- 版本 **2.29.0** / versionCode **42**；MCP clientInfo → 2.29.0

## 仍缺（诚实）
- 真机浸泡（无 KVM）、完整浏览器 OAuth/PKCE、子 Agent、本机自动化、Play 上传签名策略

## 验证
- `./scripts/verify.sh`：**PASS**（**463** 项 debug 单测）
- 无模拟器 / 无 `connectedAndroidTest`

## 真机请按
1. `docs/FIRST_RUN_CN.md`
2. `docs/CHECKLIST.md`
3. 首启：Temperature 试 `0,7` 与 blank；Max tokens 试 `4096.0` / 小值截断

---

# Minima 早间交付摘要（v2.28.0）

面向 VIDDsys · Asia/Shanghai · **晨间接手定稿**

## 5分钟上手
1. **装包**：`adb install -r minima-v2.28.0-agent-debug.apk`（或拷 release）
2. 打开 → 首启引导（无 Key 时）：三步清单 → **Open Settings** 或 Skip
3. Settings → **PROVIDER**：Preset / Base URL / Model / **Temperature** / **Max tokens** / API Key；可 Refresh models、Check endpoint
4. 新建会话；空聊有安静 starter chips；回形针附图
5. Agent：技能索引 → **read_skill**；search_memory、HistoryWindow（长会话裁剪更安全）

## 包
| 类型 | 文件 | 说明 |
|------|------|------|
| Debug（推荐侧载） | `minima-v2.28.0-agent-debug.apk` | ~20MB，符号完整，minimaDebug 签名 |
| Release | `minima-v2.28.0-agent-release.apk` | ~2.5MB，R8，同为 minimaDebug 侧载签名 |

```bash
source ~/.android-sdk-env.sh
cd /workspace/minima-chat
adb install -r minima-v2.28.0-agent-debug.apk
```

## v2.28.0 相对 v2.27.0（采样参数）
- **Temperature / Max tokens**：Settings → PROVIDER；空白 = 不发字段（走供应商默认）；温度 0–2，max_tokens 1–200000
- 请求体经 `ChatProviderConfig` → `MultimodalContent.encodeRequestBody` 写入；Agent / 非 Agent 路径共用
- 备份导入导出带上这两项；`SamplingParams` 纯函数钳制 + 单测
- 版本 **2.28.0** / versionCode **41**；MCP clientInfo → 2.28.0

## 仍缺（诚实）
- 真机浸泡（无 KVM）、完整浏览器 OAuth/PKCE、子 Agent、本机自动化、Play 上传签名策略

## 验证
- `./scripts/verify.sh`：**PASS**（**459** 项 debug 单测）
- 无模拟器 / 无 `connectedAndroidTest`

## 真机请按
1. `docs/FIRST_RUN_CN.md`
2. `docs/CHECKLIST.md`
3. 首启：配好 Key → 试 Temperature=0.2 与 blank 对比；试 Max tokens 小值截断

---

# Minima 早间交付摘要（v2.27.0）

面向 VIDDsys · Asia/Shanghai · **晨间接手定稿**

## 5分钟上手
1. **装包**：`adb install -r minima-v2.27.0-agent-debug.apk`（或拷 release）
2. 打开 → 首启引导（无 Key 时）：三步清单 → **Open Settings** 或 Skip
3. Settings → **PROVIDER**：Preset（含 **Ollama**）/ Base URL / Model / API Key；可 Refresh models、Check endpoint
4. 新建会话；空聊有安静 starter chips；回形针附图
5. Agent：技能索引 → **read_skill**；search_memory、HistoryWindow（长会话裁剪更安全）

## 包
| 类型 | 文件 | 说明 |
|------|------|------|
| Debug（推荐侧载） | `minima-v2.27.0-agent-debug.apk` | ~20MB，符号完整，minimaDebug 签名 |
| Release | `minima-v2.27.0-agent-release.apk` | ~2.5MB，R8，同为 minimaDebug 侧载签名 |

```bash
source ~/.android-sdk-env.sh
cd /workspace/minima-chat
adb install -r minima-v2.27.0-agent-debug.apk
```

## v2.27.0 相对 v2.26.0（深度审查硬伤）
- **HistoryWindow**：压爆预算时不再 `removeAt` 拆开 `assistant(tool_calls)`+`tool`；改为截断 stub，避免 OpenAI 回放 400；新增 `toolIntegrityOk`
- **ImageAttachPrep / ImageAttachLogic.resolveAttachmentFile**：附图回放路径必须落在 `filesDir/attachments/`（canonical），拒绝 `..` / 绝对路径逃逸（防把别的文件当图发给模型）
- 版本 **2.27.0** / versionCode **40**；MCP clientInfo → 2.27.0
- Settle check（本轮）：工具完整性 + 附件 confine **PASS**；维持 2.27.0

## 仍缺（诚实）
- 真机浸泡（无 KVM）、完整浏览器 OAuth/PKCE、子 Agent、本机自动化、Play 上传签名策略

## 验证
- `./scripts/verify.sh`：**PASS**（**448** 项 debug 单测；含 settle 探针 +6，版本仍 **2.27.0**）
- Settle：**无回归** — HistoryWindow `toolIntegrityOk` 多预算对抗 + 附件 symlink/绝对路径/前缀兄弟目录/NUL；**不发 2.27.1**
- 无模拟器 / 无 `connectedAndroidTest`

## 真机请按
1. `docs/FIRST_RUN_CN.md`
2. `docs/CHECKLIST.md`
3. 首启：无 Key → 引导；配好 Key → New chat → 试长 Agent 多工具后再发一轮（HistoryWindow）；试 Edit last 附图仍在

---

# Minima 早间交付摘要（v2.26.0）

面向 VIDDsys · Asia/Shanghai · **晨间接手定稿**

## 5分钟上手
1. **装包**：`adb install -r minima-v2.26.0-agent-debug.apk`（或拷 release）
2. 打开 → 首启引导（无 Key 时）：三步清单 → **Open Settings** 或 Skip
3. Settings → **PROVIDER**：Preset（含 **Ollama**）/ Base URL / Model / API Key；可 Refresh models、Check endpoint
4. 新建会话；空聊有安静 starter chips；回形针附图
5. Agent：技能索引 → **read_skill**（名称已做路径安全校验）；search_memory、HistoryWindow

## 包
| 类型 | 文件 | 说明 |
|------|------|------|
| Debug（推荐侧载） | `minima-v2.26.0-agent-debug.apk` | ~20MB，符号完整，minimaDebug 签名 |
| Release | `minima-v2.26.0-agent-release.apk` | ~2.5MB，R8，同为 minimaDebug 侧载签名 |

```bash
source ~/.android-sdk-env.sh
cd /workspace/minima-chat
adb install -r minima-v2.26.0-agent-debug.apk
```

## v2.26.0 相对 v2.25.0（read_skill 加固 + cleartext 审查）
- **SkillNameSafety**：拒绝路径穿越 / 绝对路径 / 控制字符；stem 仅 `[A-Za-z0-9._-]`
- **SkillsLoader**：`PathSafety.resolveSafe` + 流式 capped 读（`MAX_SKILL_BYTES`）+ 二进制嗅探跳过 + `MAX_SKILLS`
- **CleartextPolicy**：与 NSC 对齐；非 loopback 的 `http://` 在发信 / Check endpoint / Refresh models 直接拒
- NSC 增加 **`::1`**；`usesCleartextTraffic=false` 保持
- 版本 **2.26.0** / versionCode **39**；MCP clientInfo → 2.26.0

## 仍缺（诚实）
- 真机浸泡（无 KVM）、完整浏览器 OAuth/PKCE、子 Agent、本机自动化、Play 上传签名策略

## 验证
- `./scripts/verify.sh`：**PASS**（**439** 项 debug 单测；较 v2.25.0 +17）
- 无模拟器 / 无 `connectedAndroidTest`

## 真机请按
1. `docs/FIRST_RUN_CN.md`
2. `docs/CHECKLIST.md`（含 read_skill 安全名；Ollama cleartext；试 `http://evil` 应被拒）
3. 首启：无 Key → 引导；配好 Key → New chat → 试「按 summarize-doc 技能总结这段」

---

# Minima 早间交付摘要（v2.25.0）

面向 VIDDsys · Asia/Shanghai · **晨间接手定稿**

## 5分钟上手
1. **装包**：`adb install -r minima-v2.25.0-agent-debug.apk`（或拷 release）
2. 打开 → 首启引导（无 Key 时）：三步清单 → **Open Settings** 或 Skip
3. Settings → **PROVIDER**：Preset（含 **Ollama**）/ Base URL / Model / API Key；可 Refresh models、Check endpoint
4. 新建会话；空聊有安静 starter chips；回形针附图
5. Agent 开着时：技能索引只列名称 → 调 **read_skill** 拉完整说明；另有 search_memory、HistoryWindow 裁剪

## 包
| 类型 | 文件 | 说明 |
|------|------|------|
| Debug（推荐侧载） | `minima-v2.25.0-agent-debug.apk` | ~20MB，符号完整，minimaDebug 签名 |
| Release | `minima-v2.25.0-agent-release.apk` | ~2.5MB，R8，同为 minimaDebug 侧载签名 |

```bash
source ~/.android-sdk-env.sh
cd /workspace/minima-chat
adb install -r minima-v2.25.0-agent-debug.apk
```

## v2.25.0 相对 v2.24.0（read_skill / frontmatter / Ollama）
- **SkillFrontmatter**：解析 SKILL.md 的 `---` YAML（name / description / always_apply）
- **read_skill** 内置工具：按名称懒加载技能正文（修「索引有、正文到不了模型」）
- 系统提示技能索引明确要求先 `read_skill`，不再暗示正文已在上下文
- **Ollama** 预设：`http://127.0.0.1:11434/v1` + `llama3.2`；仅 localhost / 127.0.0.1 / 10.0.2.2 允许 cleartext
- 版本 **2.25.0** / versionCode **38**；MCP clientInfo → 2.25.0

## 仍缺（诚实）
- 真机浸泡（无 KVM）、完整浏览器 OAuth/PKCE、子 Agent、本机自动化、Play 上传签名策略

## 验证
- `./scripts/verify.sh`：**PASS**（**422** 项 debug 单测；较 v2.24.0 +10）
- 无模拟器 / 无 `connectedAndroidTest`

## 真机请按
1. `docs/FIRST_RUN_CN.md`
2. `docs/CHECKLIST.md`（含 read_skill；Ollama 本机）
3. 首启：无 Key → 引导；配好 Key → New chat → 试「按 summarize-doc 技能总结这段」

---

# Minima 早间交付摘要（v2.24.0）

面向 VIDDsys · Asia/Shanghai · **晨间接手定稿**

## 5分钟上手
1. **装包**：`adb install -r minima-v2.24.0-agent-debug.apk`（或拷 release）
2. 打开 → 首启引导（无 Key 时）：三步清单 → **Open Settings** 或 Skip
3. Settings → **PROVIDER**：Preset / Base URL / Model / API Key；可 Refresh models、Check endpoint
4. 新建会话；空聊有安静 starter chips；回形针附图
5. Agent 开着时可用 **search_memory**；长会话会自动裁进上下文窗口（不拆工具回合）；Share 导出 Markdown

## 包
| 类型 | 文件 | 说明 |
|------|------|------|
| Debug（推荐侧载） | `minima-v2.24.0-agent-debug.apk` | ~20MB，符号完整，minimaDebug 签名 |
| Release | `minima-v2.24.0-agent-release.apk` | ~2.5MB，R8，同为 minimaDebug 侧载签名 |

```bash
source ~/.android-sdk-env.sh
cd /workspace/minima-chat
adb install -r minima-v2.24.0-agent-debug.apk
```

## v2.24.0 相对 v2.23.0（HistoryWindow）
- 新 **`HistoryWindow`**：按 ~100k 字符预算裁剪发给模型的历史；**绝不拆开** `assistant(tool_calls)` + 对应 `tool` 结果
- 更早用户附图的 base64 会先剥掉（默认只保留最近 1 条带图用户消息），避免 vision 撑爆请求
- Agent / 非 Agent 发信路径都已接入；超大 system（AGENT.md 等）会软截断
- 版本 **2.24.0** / versionCode **37**；MCP clientInfo → 2.24.0

## 仍缺（诚实）
- 真机浸泡（无 KVM）、完整浏览器 OAuth/PKCE、子 Agent、本机自动化、Play 上传签名策略

## 验证
- `./scripts/verify.sh`：**PASS**（**412** 项 debug 单测；较 v2.23.0 +7）
- 无模拟器 / 无 `connectedAndroidTest`

## 真机请按
1. `docs/FIRST_RUN_CN.md`
2. `docs/CHECKLIST.md`（含长会话裁剪；search_memory）
3. 首启：无 Key → 引导；配好 Key → New chat → chips

---

# Minima 早间交付摘要（v2.23.0）

面向 VIDDsys · Asia/Shanghai · **晨间接手定稿**

## 5分钟上手
1. **装包**：`adb install -r minima-v2.23.0-agent-debug.apk`（或拷 release）
2. 打开 → 首启引导（无 Key 时）：三步清单 → **Open Settings** 或 Skip
3. Settings → **PROVIDER**：Preset / Base URL / Model / API Key；可 Refresh models、Check endpoint
4. 新建会话；空聊有安静 starter chips（含 “Search my notes for: ”）；回形针附图
5. Agent 开着时可用 **search_memory** 搜 AGENT.md / 沙箱笔记 /（可选）SAF；工具卡可展开；Share 导出 Markdown

## 包
| 类型 | 文件 | 说明 |
|------|------|------|
| Debug（推荐侧载） | `minima-v2.23.0-agent-debug.apk` | ~20MB，符号完整，minimaDebug 签名 |
| Release | `minima-v2.23.0-agent-release.apk` | ~2.5MB，R8，同为 minimaDebug 侧载签名 |

```bash
source ~/.android-sdk-env.sh
cd /workspace/minima-chat
adb install -r minima-v2.23.0-agent-debug.apk
```

## v2.23.0 相对 v2.22.0（search_memory harden）
- **路径逃逸**：File 树 walk 每层 `PathSafety.isUnderRoot`（防 symlink 穿出沙箱）；`PathSafety.sanitizeRelative` 拒绝 `..` / 中间 `.`；SAF path 同样 sanitize
- **二进制跳过**：扩展名过滤 + 前 512B NUL/控制字符嗅探；伪装成 `.md` 的二进制不进结果
- **体积上限**：单文件流式最多 96KB（不信任 `length()`）；整次扫描总预算 2MB；行长截断 4KB；输出仍 24KB
- 版本 **2.23.0** / versionCode **36**；MCP clientInfo → 2.23.0

## 仍缺（诚实）
- 真机浸泡（无 KVM）、完整浏览器 OAuth/PKCE、子 Agent、本机自动化、Play 上传签名策略

## 验证
- `./scripts/verify.sh`：**PASS**（约 **405** 项 debug 单测；较 v2.22.0 +9）
- 无模拟器 / 无 `connectedAndroidTest`

## 真机请按
1. `docs/FIRST_RUN_CN.md`
2. `docs/CHECKLIST.md`（含 search_memory；可试恶意 path / 大文件）
3. 首启：无 Key → 引导；配好 Key → New chat → chips

---

# Minima 早间交付摘要（v2.22.0）

面向 VIDDsys · Asia/Shanghai · **晨间接手定稿**

## 5分钟上手
1. **装包**：`adb install -r minima-v2.22.0-agent-debug.apk`（或拷 release）
2. 打开 → 首启引导（无 Key 时）：三步清单 → **Open Settings** 或 Skip
3. Settings → **PROVIDER**：Preset / Base URL / Model / API Key；可 Refresh models、Check endpoint
4. 新建会话；空聊有安静 starter chips（含 “Search my notes for: ”）；回形针附图
5. Agent 开着时可用 **search_memory** 搜 AGENT.md / 沙箱笔记 /（可选）SAF；工具卡可展开；Share 导出 Markdown

## 包
| 类型 | 文件 | 说明 |
|------|------|------|
| Debug（推荐侧载） | `minima-v2.22.0-agent-debug.apk` | ~20MB，符号完整，minimaDebug 签名 |
| Release | `minima-v2.22.0-agent-release.apk` | ~2.5MB，R8，同为 minimaDebug 侧载签名 |

```bash
source ~/.android-sdk-env.sh
cd /workspace/minima-chat
adb install -r minima-v2.22.0-agent-debug.apk
```

## v2.22.0 相对 v2.21.0（memory search）
- 新内置工具 **`search_memory`**：在 AGENT.md、沙箱 `minima_workspace`、以及 Sandbox only OFF 时的 SAF 工作区做大小写不敏感子串搜索，返回 `path:line: snippet`，有扫描上限与结果上限
- `MemorySearch` 纯逻辑可单测；reserved 名防 `tools.json` 抢占；空态 chip / FIRST_RUN / Settings 工具副标题已对齐
- 版本 **2.22.0** / versionCode **35**；MCP clientInfo → 2.22.0

## 仍缺（诚实）
- 真机浸泡（无 KVM）、完整浏览器 OAuth/PKCE、子 Agent、本机自动化、Play 上传签名策略

## 验证
- `./scripts/verify.sh`：**PASS**（约 **396** 项 debug 单测；较 v2.21.0 +10）
- 无模拟器 / 无 `connectedAndroidTest`

## 真机请按
1. `docs/FIRST_RUN_CN.md`
2. `docs/CHECKLIST.md`（含 search_memory 一项）
3. 首启：无 Key → 引导；配好 Key → New chat → chips

---

# Minima 早间交付摘要（v2.21.0）

面向 VIDDsys · Asia/Shanghai · **晨间接手定稿**

## 5分钟上手
1. **装包**：`adb install -r minima-v2.21.0-agent-debug.apk`（或拷 release）
2. 打开 → 首启引导（无 Key 时）：三步清单 → **Open Settings** 或 Skip
3. Settings → **PROVIDER**：Preset / Base URL / Model / API Key；可 Refresh models、Check endpoint
4. 新建会话；空聊有安静 starter chips（点一下填入输入框）；回形针附图
5. Agent 开着时工具卡可展开（展示已脱敏）；Share 导出 Markdown；细节见 FIRST_RUN_CN / MCP / CHECKLIST

## 包
| 类型 | 文件 | 说明 |
|------|------|------|
| Debug（推荐侧载） | `minima-v2.21.0-agent-debug.apk` | ~20MB，符号完整，minimaDebug 签名 |
| Release | `minima-v2.21.0-agent-release.apk` | ~2.5MB，R8，同为 minimaDebug 侧载签名 |

```bash
source ~/.android-sdk-env.sh
cd /workspace/minima-chat
adb install -r minima-v2.21.0-agent-debug.apk
```

## v2.21.0 相对 v2.20.0（首启 / 空态 / 设置密度）
- **Onboarding**：编号三步清单 + Skip 提示；语义 `Onboarding`
- **Hydrate splash**：DataStore 未就绪时显示安静 “Minima” 字标（超时仍进主流程）
- **Sessions 空态**：文案更清晰；主操作 Open Settings + 次操作 **New chat**
- **Chat 空态**：Almost ready / Say hello；有 Key 时 3 个安静 suggestion chips 填入草稿（`ChatEmptyTips`）
- **EmptyState**：支持次按钮 + suggestion chips；默认 fillMaxSize，可传入有界 modifier
- **Settings 密度**：APPEARANCE 合并原 DISPLAY（Usage estimate 进同一组）；组间距收紧；筛选无结果空态 + Clear filter
- **Settings 筛选修复**：`SettingsQueryMatcher` 双向匹配（如 “base”“sand” 能命中）；单测覆盖
- 版本 **2.21.0** / versionCode **34**；MCP clientInfo → 2.21.0

## 仍缺（诚实）
- 真机浸泡（本轮无 KVM）、完整浏览器 OAuth/PKCE、子 Agent、本机自动化、Play 上传签名策略、更深 DNS pin 连接

## 验证
- `./scripts/verify.sh`：**PASS**（**386** 项 debug 单测）
- 无模拟器 / 无 `connectedAndroidTest`

## 真机请按
1. `docs/FIRST_RUN_CN.md`
2. `docs/CHECKLIST.md`
3. 首启：无 Key → 引导；Skip 后会话空态 → Settings；配好 Key → New chat → chips

---

# Minima 早间交付摘要（v2.20.0）

面向 VIDDsys · Asia/Shanghai

## 5分钟上手
1. **装包**：`adb install -r minima-v2.20.0-agent-debug.apk`（或拷 release）
2. 打开 → Settings：选 preset 或 Custom → 粘贴 API Key
3. 新建会话；回形针附图；Agent 开着时工具卡可点开看结果（展示已脱敏）
4. **HTTP 技能工具**：内置 `http-tools-example`；自建 `skills/<pack>/tools.json` 后 Reload skills（主机锁定 / 禁私网 / **运行时 DNS 校验** / 禁重定向）
5. **Share**：顶栏分享 → 导出 Markdown（SecretRedact）；详细见 FIRST_RUN_CN / MCP / CHECKLIST

## 包
| 类型 | 文件 | 说明 |
|------|------|------|
| Debug（推荐侧载） | `minima-v2.20.0-agent-debug.apk` | ~20MB，符号完整，minimaDebug 签名 |
| Release | `minima-v2.20.0-agent-release.apk` | ~2.5MB，R8，同为 minimaDebug 侧载签名 |

v2.20.0：DNS rebinding 防护（SafeDns + 预检）。详见仓库历史 STATUS。
