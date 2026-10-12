# Minima — Research Notes (Phone Agent, not just chat)

**Updated:** 2026-10-12 01:55 Asia/Shanghai (CST)  
**Scope:** Feasibility + concrete libraries/links for turning Minima into a world-class on-device Android Agent.  
**Product stance:** Original minimal UI; OpenAI-compatible BYOK; user-granted tools; no 1:1 UI clone of any commercial app.

---

## 0) Android SDK / toolchain status (this box)

| Item | Path / value | Status |
|------|----------------|--------|
| Android SDK root | `/home/box/tools/android-sdk` | **Installed** |
| Platform | `platforms;android-36` | Present |
| Build-Tools | `build-tools;36.0.0` | Present |
| Platform-Tools | `37.0.1` (`adb` 1.0.41) | Present |
| cmdline-tools | `cmdline-tools/latest` (sdkmanager available) | Present |
| Licenses | `/home/box/tools/android-sdk/licenses` | Accepted |
| JDK | OpenJDK **21.0.12.1** | Present |
| Gradle | **8.13** @ `/home/box/tools/gradle-8.13` | Daemon running (app build in flight) |
| Env helper | `~/.android-sdk-env.sh` (sourced from `~/.bashrc`) | Present |

**Decision:** Overnight builds use `compileSdk/targetSdk = 36`, `minSdk = 26`. No emulator required for unit tests; device/emulator for APK smoke later.

```bash
export ANDROID_HOME=/home/box/tools/android-sdk
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
sdkmanager --list_installed
```

---

## 1) OpenAI-compatible tool calling + local agent loop (Kotlin)

### Wire format (must implement)

- Endpoint: `POST {baseUrl}/v1/chat/completions`
- Request fields: `model`, `messages`, `tools` (JSON Schema), `tool_choice`, `stream: true|false`
- Streaming: SSE `data:` lines; accumulate `choices[].delta.tool_calls[i]` by `index` (id / name / arguments fragments)
- Terminal `finish_reason`: `"tool_calls"` → execute tools; `"stop"` → done
- Append assistant message with `tool_calls`, then `role: "tool"` results with `tool_call_id`, loop

Docs:
- [Function calling](https://developers.openai.com/api/docs/guides/function-calling)
- [Streaming responses](https://developers.openai.com/api/docs/guides/streaming-responses)
- [Chat Completions streaming events](https://developers.openai.com/api/reference/resources/chat/subresources/completions/streaming-events/)

### Recommended Minima architecture

```
User turn
  → AgentLoop (maxIterations, wallClockMs, cancel)
      → ChatApiClient.stream(messages, tools)
      → ToolCallAggregator (SSE fragments → complete calls)
      → ToolRegistry.dispatch(name, argsJson)  // permission-gated
      → append tool results → repeat until stop | budget exhausted
  → persist messages (Room) + emit UI events (Flow)
```

**Decision notes**
- Keep the loop **in-app** (Kotlin coroutines). Do not rely on server-side agent unless user opts into a gateway.
- Cap: `maxIterations` (e.g. 8 chat / 40 agent) + wall-clock (e.g. 120s) + per-tool timeout.
- Parallel tool calls: honor `parallel_tool_calls` when provider supports; otherwise sequential.
- Persist intermediate tool cards in Room so process death mid-run can show “interrupted”.

### Libraries to study / optionally adopt

| Library | Link | Use |
|---------|------|-----|
| **Kompletions** | https://github.com/s1mar/Kompletions | Lightweight Kotlin OpenAI-compatible client: Flow SSE, tool round-trip, multi-provider |
| **Ailux** | https://github.com/kynnchuan/ailux | Android LLM access layer; `ToolCallAggregator` for SSE FC fragments (OpenAI + Anthropic) |
| **OFH** | https://github.com/NaustudentX18/ofh | Pure-Kotlin Android agent harness: AgentLoop + ToolRegistry + SKILL.md |
| **android-agent-harness** | https://github.com/susyimes/android-agent-harness | Bounded run kernel, Phone Use state machine, OpenAI-compatible live mode |
| **Poci** | https://github.com/rdh073/poci | Full on-device harness (Compose, Room, OkHttp, subagents, schedules) — architecture reference |

**Minima choice (overnight):** Implement our own thin client on existing OkHttp + kotlinx.serialization (already in `core-network`). Steal patterns from Kompletions/Ailux for aggregator + loop; do **not** vendor a whole harness yet.

### Suggested Gradle (when enabling tools)

```kotlin
// already have: okhttp, okhttp-sse, kotlinx-serialization-json, coroutines
// optional later:
implementation("io.ktor:ktor-client-okhttp:3.0.3") // only if adopting MCP kotlin-sdk transports
```

---

## 2) MCP on mobile — can we run / connect MCP servers?

### Spec transports (current)

Official transports ([MCP Transports 2025-06-18](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports)):

1. **stdio** — client spawns subprocess; JSON-RPC on stdin/stdout (newline-delimited)
2. **Streamable HTTP** — single HTTP endpoint POST/GET; optional SSE stream (replaces deprecated HTTP+SSE)

Security for HTTP: validate `Origin`, bind localhost when local, authenticate.

### Android realities

| Transport | Feasible on Android? | Notes |
|-----------|----------------------|-------|
| **stdio** | **Poor as primary** | Spawning arbitrary Node/Python MCP servers needs a Linux userland (Termux/proot) or bundled binaries. Android SELinux + no-exec on many paths + Play policy against downloading executables. Possible in **sideload / F-Droid** builds with app-private `exec` of bundled tools, not Play-friendly. |
| **Streamable HTTP (remote)** | **Yes — preferred** | User pastes `https://host/mcp` (or LAN). Use official Kotlin client + Ktor SSE. |
| **Streamable HTTP (on-device server)** | **Yes for debug / power users** | Bind `127.0.0.1`; expose via `adb reverse` for desktop clients. See android-mcp-sdk (debug-only). |
| **Legacy HTTP+SSE** | Compat only | Deprecated; keep fallback for old servers. |

### Concrete stacks

| Project | Link | Role |
|---------|------|------|
| **MCP Kotlin SDK** | https://github.com/modelcontextprotocol/kotlin-sdk | Official KMP client/server; `StreamableHttpClientTransport`, stdio, SSE, WebSocket |
| Docs / API | https://kotlin.sdk.modelcontextprotocol.io/ | Client API reference |
| **android-mcp-sdk** | https://kaeawc.github.io/android-mcp-sdk/getting-started/ | Debug-only on-device MCP **server** (minSdk 29); ADB port forward — **not for production Play** |
| **android-mcp** (Shizuku bridge) | https://github.com/shuao-pro/android-mcp | Host-side MCP exposing Android via Shizuku; Streamable HTTP `:9000/mcp` — learn bridge patterns |
| Flutter MCP client | https://github.com/app-appplayer/mcp_client | Cross-platform client (stdio/SSE/Streamable HTTP + OAuth) — protocol patterns only |

### Feasible Minima architecture

```
Minima AgentLoop
  ├─ BuiltinToolRegistry (files, notify, web_fetch, …)  // first-party
  └─ McpToolBridge
        ├─ RemoteMcpSession (Streamable HTTP)  // user-configured URLs
        └─ (optional) LocalMcpViaTermuxIntent / debug embedded server
```

**Decisions**
1. **v1:** MCP **client** over Streamable HTTP to remote/LAN servers. Map `tools/list` → OpenAI `tools[]`; `tools/call` → tool results.
2. **Defer stdio** until sideload channel + explicit user opt-in; never download unsigned executables at runtime for Play builds.
3. **Do not** ship android-mcp-sdk in release; fine as debugImplementation for dogfooding.
4. Gradle sketch:

```kotlin
implementation("io.modelcontextprotocol:kotlin-sdk-client:<latest>")
implementation("io.ktor:ktor-client-okhttp:<ktor>")
implementation("io.ktor:ktor-client-sse:<ktor>")  // if required by transport
```

Check Maven Central badge on the kotlin-sdk README for current version before pinning.

---

## 3) Scoped storage — public Downloads / Documents without root

### Goal

Agent can read/write **user-chosen public areas** (Downloads, Documents, custom folders) **without root**, with clear consent.

### API matrix

| API | What it unlocks | Permission | Play risk |
|-----|-----------------|------------|-----------|
| **SAF `ACTION_OPEN_DOCUMENT_TREE`** + `takePersistableUriPermission` | Whole tree R/W under chosen folder | None (user picker) | **Preferred** |
| **SAF `ACTION_OPEN_DOCUMENT` / `CREATE_DOCUMENT`** | Single file | None | Preferred for one-shots |
| **MediaStore.Downloads / Files** | App-owned downloads; limited visibility of others’ files | Often none for own files | Fine |
| **`MANAGE_EXTERNAL_STORAGE`** (“All files access”) | Broad shared storage + path access | Special access settings | **High** — only for file-manager core; Play review |

Official docs:
- [Access documents (SAF)](https://developer.android.com/training/data-storage/shared/documents-files)
- [Document provider guide](https://developer.android.com/guide/topics/providers/document-provider)
- [MediaStore shared media](https://developer.android.com/training/data-storage/shared/media)
- [Manage all files](https://developer.android.com/training/data-storage/manage-all-files)

### Android 11+ caveats

- `OPEN_DOCUMENT_TREE` cannot grant some roots (e.g. broad `/Download` restrictions vary by OEM/API); user may need to pick a **subfolder**.
- Persist URI permissions across reboots; store tree URI string in EncryptedSharedPreferences / DataStore.
- Prefer `DocumentFile` / `ContentResolver` + `DocumentsContract` over raw `java.io.File` paths.

### Minima file tool design

```
tools:
  list_dir / read_file / write_file / mkdir / search_files
policy:
  roots = [ app_files_dir (always), user_persisted_trees… ]
  deny = Android/data of other apps, /proc, etc.
```

**Decision:** **SAF-first**. Offer first-run card: “Grant Documents / Downloads folder for Agent”. Optional advanced toggle for All-files access **only** on sideload builds, with scary copy. Never require All-files for MVP.

---

## 4) Custom skills as MD files + tool manifests on device

### Target format (align with Agent Skills / Claude Skills)

Industry pattern ([anthropics/skills](https://github.com/anthropics/skills), [Agent Skills overview](https://docs.anthropic.com/en/docs/agents-and-tools/agent-skills/overview)):

```
Minima/skills/<skill-name>/
  SKILL.md          # YAML frontmatter + instructions (required)
  tools.json        # optional: extra tool schemas or allow-list
  scripts/          # optional: referenced helpers (not auto-exec on Play)
  references/       # optional docs
  assets/
```

`SKILL.md` frontmatter (minimal):

```yaml
---
name: meeting-notes
description: Turn rough notes into structured minutes. Use when user pastes meeting text.
allowed-tools: [read_file, write_file]   # optional allow-list
---
# Instructions
…
```

Discovery roots (user-configurable):
1. App-private: `context.filesDir/skills/`
2. User tree: `Documents/Minima/skills/` (via SAF)
3. Optional import ZIP / folder picker

**Runtime:** At session start, load **name+description** for all skills into system prompt (or a `list_skills` tool). Load full body only when skill selected / matched (progressive disclosure — same idea as Claude Code).

**OFH precedent:** loads `SKILL.md` from on-device config — https://github.com/NaustudentX18/ofh

**Decision:** Ship 2–3 bundled example skills (markdown only). User drops MD into granted folder. `tools.json` can register **declarative** HTTP tools (OpenAPI-ish) later; no arbitrary script exec in Play build.

---

## 5) Notifications / foreground service for long agent runs

### Requirements

- User leaves chat → agent continues tool loop
- Clear ongoing notification: progress, Stop action
- Survive Doze reasonably; respect Android 14+ FGS types

### Approach

| Layer | Choice | Why |
|-------|--------|-----|
| Default | **WorkManager** `CoroutineWorker` + `setForeground()` | Survives process death better; system manages FGS | 
| Interactive long run | Dedicated **Foreground Service** `specialUse` or `dataSync` | User-started agent turn from UI |
| Short async | Coroutines in ViewModel / `applicationScope` | Fine while app visible |

Docs:
- [Long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- [FGS types required (API 34+)](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [Launch FGS](https://developer.android.com/develop/background-work/services/fgs/launch)
- [FGS timeouts (API 35+)](https://developer.android.com/develop/background-work/services/fgs/timeout) — `dataSync` / `mediaProcessing` limited (~6h/24h)

**Manifest sketch**

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<service
    android:name=".agent.AgentForegroundService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="User-initiated on-device AI agent tool loop" />
</service>
```

**Notification UX:** title = session name; text = current tool / “Thinking…”; actions = Stop; tap → open chat. Update every tool boundary.

**Decision:** MVP uses app-scope coroutine + notification only when run > N seconds; v1 adds FGS `specialUse` + Stop. Prefer WorkManager for scheduled / reboot-survivable jobs (v2).

---

## 6) Sandbox vs broader file access — UX

### Principle

**Default tight; expand by explicit grants.** Agent never surprises the user with All-files or SMS.

### Permission gates (product)

| Tier | Access | How user enables |
|------|--------|------------------|
| **Sandbox** | App internal files, cache, clipboard (opt-in) | Always on |
| **Workspace** | One or more SAF trees (Documents/Minima, Downloads/…) | Folder picker + persist |
| **Network tools** | `web_fetch` / search | Toggle per-tool + optional domain allow-list |
| **Device sensors** | location, camera, notifications post | Standard runtime permissions, per-tool |
| **Dangerous** | shell, SMS, call log, All-files | Hidden behind “Power user” + typed confirm; sideload builds only for shell |

### Approval UX patterns (from harnesses)

- First use of a tool → bottom sheet: Allow once / Always for this skill / Deny
- High-risk tools (write outside sandbox, delete, shell) → always confirm unless user set “trusted mode” with PIN
- Show tool cards in transcript (name, args summary, result truncated)

**Decision:** Implement `ToolPolicy` interface early even if only sandbox tools exist — avoids retrofit pain.

---

## 7) Optional Termux-like local shell

### Reality check

- Full Linux userland on Android ≈ **Termux** (proot or app-private prefix) — https://github.com/termux/termux-app
- Play policy: [Device & Network Abuse](https://support.google.com/googleplay/android-developer/answer/9888379) restricts downloading executable code; Termux Play builds are constrained / discouraged vs F-Droid
- Apps like **AIOPE** vendor terminal + proot Alpine — heavy, GPL considerations, large APK

### Safer options for Minima

| Option | Play-safe? | Capability |
|--------|------------|------------|
| No shell | Yes | MVP |
| `ProcessBuilder` on **bundged** busybox in `nativeLibraryDir` (execbit) | Gray | Tiny command set; still review-sensitive |
| Intent to **Termux** / `RUN_COMMAND` (if permitted) | Depends | User installs Termux separately |
| Remote MCP shell on user’s PC/NAS | Yes | Best of both worlds |
| Shizuku-powered elevated ops | Sideload | Power users |

**Decision:** **No embedded Termux in overnight MVP.** Document “connect remote MCP with shell tools” + future “Termux companion” intent. If shell ever ships, **F-Droid / GitHub APK flavor** with clear labeling — not Play primary track.

---

## 8) Open-source Android agent / AI apps to learn from (architecture only)

Study patterns; do **not** copy UI pixels, trademarks, or proprietary prompts.

| Project | Link | Steal these ideas |
|---------|------|-------------------|
| **OFH** | https://github.com/NaustudentX18/ofh | Pure Kotlin AgentLoop, ToolRegistry, SKILL.md, provider presets, iteration/time budgets |
| **Poci** | https://github.com/rdh073/poci | Compose+Room harness, subagents, schedules, provider-agnostic wire layer |
| **android-agent-harness** | https://github.com/susyimes/android-agent-harness | Bounded budgets, Phone Use observe→one-action→observe, approval hooks |
| **Ailux** | https://github.com/kynnchuan/ailux | Production SSE/FC parsing, ToolCallAggregator, retry/auth |
| **Kompletions** | https://github.com/s1mar/Kompletions | Minimal OpenAI-compatible Kotlin client API |
| **AIOPE** | https://github.com/XNet-NGO/AIOPE | Upper bound of Android agent features (terminal, MCP, RAG) — scope caution |
| **Nova Agent** | https://github.com/lekhanpro/nova-agent | Termux:API tool surface catalog (what phone tools users expect) |
| **Termux-AI / Orion** | https://github.com/opsonusdh/Termux-AI | Supervisor→Worker→Critic, state.json crash recovery |
| **AnyCoding** | https://github.com/gurudin/anycoding | Phone as remote control for desktop agents (complementary mode) |
| **MCP kotlin-sdk** | https://github.com/modelcontextprotocol/kotlin-sdk | Canonical MCP client/server on JVM |

---

## 9) Suggested Minima Gradle deps (incremental)

Already in `gradle/libs.versions.toml`: OkHttp(+SSE), Room, DataStore, security-crypto, Hilt, Compose BOM, Navigation, serialization.

**Add when building Agent (v1):**

```toml
work = "2.9.1"
mcp = "<pin from Maven Central>"
ktor = "3.0.3"
markdown = "0.7.3"   # e.g. multiplatform-markdown-renderer or commonmark
```

```kotlin
implementation(libs.androidx.work.runtime.ktx)
implementation("io.modelcontextprotocol:kotlin-sdk-client:${libs.versions.mcp.get()}")
implementation("io.ktor:ktor-client-okhttp:${libs.versions.ktor.get()}")
// Markwon or multiplatform-markdown-renderer for chat MD
```

**Debug-only:**

```kotlin
debugImplementation("dev.jasonpearson:mcp-android-sdk:1.0.0") // on-device MCP server for dogfood
```

---

## 10) Risks & non-goals (overnight)

| Risk | Mitigation |
|------|------------|
| Play rejection (All-files / shell / downloaded code) | SAF-only + no shell in Play flavor |
| Agent loop runaway cost | Hard budgets + Stop + estimate tokens in UI |
| Prompt injection via files/MCP | Treat tool outputs as untrusted; confirm writes |
| OEM battery kills | FGS notification + user education |
| Scope creep (AIOPE-sized) | Stick to ROADMAP phases |

**Non-goals overnight:** Pixel-perfect clone of any commercial chat UI; root; full proot Linux; Play-listed All-files file manager.

---

## 11) Self-check prompts (every 3h)

1. Does current UI meet “极简 / industrial” bar?
2. Are necessary Agent features present for this phase — or still chat-only?
3. Point-to-point tests green?
4. Known bugs listed with owners (us)?
5. Could we hand this to a stranger as a beta?
6. What would delight the user that we can add without clutter?
