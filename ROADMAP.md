**v2.39.0** Kill message/tool Material ripple (keep tool animateContentSize); vision GetContent + URI cache-copy + ImageDecoder + HistoryPatch; user purple SVG adaptive icon. versionCode 52. verify **517** PASS.
## v2.37.0 — deep-fix flash/composer/prompt/hang/MD (2026-10-12)
Task-snapshot flash kill (`setRecentsScreenshotEnabled(false)`), settle gate before deep-link, no launch all-files jump; composer nested circle; system prompt purged of sandbox lecture; SSE idle 45s + send/regenerate withTimeout; 全局 MD. versionCode 50. verify **506** PASS.

## Shipped: v2.35.0 (2026-10-12)
API key sticky (api_key_rev + keep drafts); minimal CN settings (Base URL/Model/API Key/About); sandbox UI/gating removed (max perms); public file tools + external fallback; cold-start nav flash fixed (fresh NavController). versionCode 48.

## Shipped: v2.34.0 (2026-10-12)

Settings auto-persist + API key dots + CN UI + no hydrate flash.

## Shipped: v2.33.0 (2026-10-12)
**focus_task Agent polish**: optional `max_steps` / `max_seconds` tighten nested budget
(never expand past parent); nested tools transcript in result (name ok/err + redacted peek);
JsonSchema.integer; FocusTaskLogic tests. versionCode 46.

## Shipped: v2.32.0 (2026-10-12)
**focus_task sub-agent**: nested AgentLoop with clamped budget (≤4 iters / ≤60s),
no recursion/concurrent nest, toolsOverride excludes focus_task; FocusRunContext;
FocusTaskLogic + MockWebServer tests. versionCode 45. verify **486** PASS.

## Shipped: v2.31.0 (2026-10-12)
**Top-p sampling** + locale decimal draft fix: Settings Top-p (0–1, blank=omit);
`SamplingParams.parseTopP` / `filterDecimalDraft` (Temperature & Top-p keep `,`);
encode + backup + ChatProviderConfig. versionCode 44. verify **473** PASS.

## Shipped: v2.30.0 (2026-10-12)
**Agent timing knobs**: Settings Wall clock (30–900s) + Tool timeout (5–300s, ≤ wall);
`AgentTimingParams`; ChatViewModel → AgentBudget; backup. versionCode 43. verify **469** PASS.

## Shipped: v2.29.0 (2026-10-12)
**Sampling harden**: encode-boundary clamp; comma decimals; decimal/scientific max_tokens;
`SamplingParams` in `:core:network`. versionCode 42. verify **463** PASS.

## Shipped: v2.28.0 (2026-10-12)

**Sampling knobs**: Settings PROVIDER Temperature (0–2, blank=omit) + Max tokens (1–200k, blank=omit);
`SamplingParams` clamps; ChatProviderConfig → MultimodalContent; backup/import; Agent + non-Agent.
versionCode 41. verify **459** PASS.

## Shipped: v2.27.0 (2026-10-12)

Settle (no bump): tool-integrity + attachment-confine probes PASS → **left at v2.27.0** (no 2.27.1).

**HistoryWindow tool-round integrity** under last-unit shrink (no orphan `tool` msgs) +
**attachment path confinement** (`ImageAttachLogic.resolveAttachmentFile` → attachments/ only).
versionCode 40. verify **442** PASS.

## Shipped: v2.26.0 (2026-10-12)

**read_skill / SkillsLoader harden** + **cleartext policy review**: SkillNameSafety (query/stem/path reject), stream-capped skill reads with binary sniff, MAX_SKILLS / MAX_SKILL_BYTES; CleartextPolicy mirrors NSC (localhost/127.0.0.1/::1/10.0.2.2 only) in ChatRequestBuilder / EndpointReachability / ModelsListClient; NSC adds ::1. versionCode 39.

## Shipped: v2.25.0 (2026-10-12)

**Skill lazy-load**: YAML-ish frontmatter parser (`SkillFrontmatter`), enriched `SkillDoc`,
system index points at **read_skill** (bodies no longer implied-but-missing). Ollama preset
+ network_security_config cleartext for localhost / 127.0.0.1 / 10.0.2.2 only. versionCode 38.

## Shipped: v2.24.0 (2026-10-12)

**HistoryWindow**: fit OpenAI-compatible history into a ~100k char budget without
breaking assistant(tool_calls)+tool integrity; strip older vision images; soft-trim
huge system blobs; omission system note when turns dropped. Wired Agent + non-Agent
send paths. Unit tests. versionCode 37.

## Shipped: v2.23.0 (2026-10-12)

Harden **search_memory**: symlink/path escape checks in File walk, PathSafety rejects `..`,
binary sniff (NUL/controls), capped stream reads (never trust length), per-file + total
byte budgets, max line length. SAF path sanitize + capped reads. Extra unit tests.
versionCode 36.

## Shipped: v2.22.0 (2026-10-12)

Builtin **search_memory**: case-insensitive scan of AGENT.md + sandbox workspace (+ SAF when
Sandbox only OFF). Ranked path:line snippets, file/result caps, MemorySearch unit tests.
versionCode 35.

## Shipped: v2.21.0 (2026-10-12)

First-open polish: onboarding checklist, empty-state chips, Settings density + filter fix (`SettingsQueryMatcher`), hydrate splash.

## Shipped: v2.20.0 (2026-10-12)

Runtime DNS rebinding guard for HTTP tools: injectable AddressLookup, SafeDns on
OkHttp, assertResolvedHostSafe before declarative + http_get. Blocks private /
link-local / CGNAT / ULA / IPv4-mapped resolved IPs even when hostname looks public.
versionCode 33.

## Shipped: v2.19.0 (2026-10-12)

HTTP tools SSRF harden: HostSafety (encoding tricks, IPv6 ULA/link-local, metadata,
CGNAT), no redirects on declarative + http_get, userinfo/@ rejected, header CRLF
blocked, assertSafeFinalUrl host lock. versionCode 32.

## Shipped: v2.18.0 (2026-10-12)

Declarative HTTP tools from skill-pack `tools.json` (fixed https host, template args,
32KB + SecretRedact). Settings inventory HTTP (skills). Example http-tools-example.
versionCode 31.

## Shipped: v2.17.0 (2026-10-12)

Agent quality: ToolResultLimiter (32KB) in AgentLoop + LlmHistoryMapper with truncation marker;
FriendlyHttp clearer HTTP/transport errors (provider message extract + redact). versionCode 30.

## Shipped: v2.16.0 (2026-10-12)

Share/transcript hardening: SecretRedact on all export fields, containsSecret gate, ShareCache TTL+cap, ToolCard display redact + expandKey, shareInFlight. verify 302 PASS.

## Shipped: v2.15.0 (2026-10-12)
- Share session as Markdown file (FileProvider); richer agent tool transcript
- ToolCard truncation UX (UnicodeSafe + tap-for-more up to 8k)
- versionCode 28; verify.sh PASS

## Shipped: v2.14.0 (2026-10-12)
- Vision regenerate/edit keep attachment; stream downscale OOM harden; composer clear/cancel + paperclip a11y
- versionCode 27; verify.sh PASS

## Shipped: v2.13.0 (2026-10-12)
MCP remote auth without browser OAuth: per-server `bearerToken` + custom headers editor;
`McpAuthHeaders` merges into Streamable HTTP (user-static wins over session / bearer).
Secret redaction on health errors. Settings ink-minimal. versionCode 26.

## Shipped: v2.12.0 (2026-10-12)
Vision image attach: paperclip → PickVisualMedia → compress/store → multimodal
image_url in chat/completions; Room v6 attachmentPath/Mime; MultimodalContent.
versionCode 25.

## Shipped: v2.11.0 (2026-10-12)
Provider usage token persistence: stream_options.include_usage, SSE/non-stream parse,
Room inputTokens/outputTokens, chat header billed display, agent multi-turn accumulate.
versionCode 24.

## Shipped: v2.10.0 (2026-10-12)
Settings **Refresh models**: OpenAI-compatible GET `{base}/models`, chip → Model field,
Bearer when key set, SecretRedact on errors, ModelsListParser tests. versionCode 24.

## Shipped: v2.9.0 (2026-10-12)
GFM pipe markdown tables in assistant bubbles (parse + ink-minimal render + tests).
versionCode 22.

## Shipped: v2.8.3 (2026-10-12)
MCP Streamable HTTP **session header persistence**: capture `Mcp-Session-Id` from
responses, reuse on later requests (user `headers` win); clear on re-initialize /
HTTP 401|404. `McpSessionStore` + unit/MockWebServer tests. versionCode 21.

## Shipped: v2.8.2 (2026-10-12)
Stop join+hold post-join ceiling; tool collect does not swallow CancellationException;
onCleared cancels stopCleanupJob before early-ceiling FF. versionCode 20.

## Shipped: v2.8.0 (2026-10-12)
Deep bug hunt: CancellationException propagation (Stop/timeout), session-scoped toolCallId,
unique synthetic tool ids, no runBlocking in onCleared, single Chat VM nav, systemPrompt in
non-agent send, empty-tools no duplicate fallback. versionCode 18.

## Shipped: v2.7.0 (2026-10-12)
Morning confidence: HistoryRoundtripTest (Entity→Mapper→LLM), ScheduledNoteLogic hardens missing sandbox,
StartDestinationResolver + hydrate timeout / local dismiss so Skip cannot stick, MORNING_SUMMARY「5分钟上手」.
versionCode 17.

## Shipped: v2.6.0 (2026-10-12)
- Stability: Compose NPE hygiene; FGS Stop == chat Stop via AgentRunController
- Settings Check endpoint; scripts/verify.sh; docs/MORNING_SUMMARY 2.5

## Shipped: v2.4.0 (2026-10-12)
- Cancel mid-step clears Running tools; empty tool results placeholder
- Regenerate + agentStep history verified; MCP last health persisted per server
- Targeted tests for regenerate/cancel/MCP codec

## Shipped: v2.3.0 (2026-10-12)
- Same-turn multi-iteration tool replay via persisted `agentStep` + LlmHistoryMapper groups
- Room v5; export/import agentStep; ToolCard `·N` hint
- Tests for 2-iteration same-turn replay

## Shipped: v2.2.0 (2026-10-12)
- Multi-turn LLM history replays tool cards as OpenAI tool_calls + tool role messages (`LlmHistoryMapper`)
- Fuller tool arg/result persistence for replay; unit tests for payload survival

## Shipped: v2.1.0 (2026-10-12)
- Deny sticky + AgentLoop MockWebServer tests + permission UX

# Minima — Roadmap (Chat → Phone Agent)

**Updated:** 2026-10-12 ~03:30 Asia/Shanghai (CST)  
**North star:** Quiet, original, industrial-grade **on-device Agent** — OpenAI-compatible BYOK, user-granted tools, custom skills (MD), remote MCP — not a feature landfill.  
**Companion:** [`RESEARCH.md`](./RESEARCH.md) (libraries, links, decisions).

---

## Product principles

1. **极简** — Only necessary surfaces: sessions, chat, settings, tool cards, grants.
2. **Consent-first** — Sandbox by default; SAF / runtime permissions expand power.
3. **BYOK** — Custom base URL, model id, API key (EncryptedSharedPreferences).
4. **Agent, not chatbot** — Tool loop with budgets, Stop, resume-friendly persistence.
5. **Play-safe primary track** — No downloaded executables, no required All-files, no embedded Termux in Play flavor.
6. **Original UI** — Inspired by quiet monochrome spirit; no trademarked clone.

---



## Phase v2.0 — Polish milestone (shipped)

- [x] Bug hunt: regenerate deleteFrom, AgentLoop flowOn(IO), blank tool names, cert pins on back, IO for backup streams
- [x] Settings density + chat/sessions empty-state copy
- [x] In-app About (version + open source notes)
- [x] FIRST_RUN_CN.md ↔ Settings labels
- [x] device_info builtin; MORNING_SUMMARY.md
- [x] Tests rising; assembleDebug + Release
- [ ] Device soak (blocked: no KVM)

## Phase v1.6 — Agent depth + polish (shipped)

- [x] Provider presets (OpenAI / OpenRouter / DeepSeek / Custom)
- [x] Global system prompt + AGENT.md from SAF/skills
- [x] Parallel tool_calls + HttpRetry backoff
- [x] Session search + pin/archive
- [x] README Agent examples + more tests
- [ ] Device soak (blocked: no KVM)

## Phase v1.9 — Morning-ready UX + signing (shipped)

- [x] App-specific debug keystore (`keystore/minima-debug.jks`) + optional upload signing via env
- [x] Share session transcript (plain text)
- [x] Delete message; Edit last user + resend when clean (no running tools)
- [x] Settings: confirm Available tools (built-in + MCP)
- [x] Chat recomposition mitigations (remember, ChatRow, SSE 48ms flush)
- [x] `docs/FIRST_RUN_CN.md`
- [x] Tests ≥122; assembleDebug + Release green
- [ ] Device soak (blocked: no KVM)

## Phase v1.8 — Quality / security / UX (shipped)

- [x] Secret redaction broadened; cleartext remains off
- [x] Optional certificate pinning (blank = off)
- [x] Copy message + regenerate last assistant turn
- [x] Per-tool timeout + overall agent deadline
- [x] Unicode-safe display helpers
- [x] Full settings backup (no key / with-key warning)
- [x] Tests ≥113; assembleDebug + Release
- [ ] Device soak (blocked: no KVM)
- [ ] Haptic (skipped — too noisy)

## Phase v1.7 — Safe upgrades + quiet polish (shipped)

- [x] Real Room migrations 1→4 (no destructive wipe on bumps)
- [x] Pre-migrate `minima.db` snapshot + export tip in Settings
- [x] Always-visible archived sessions toggle
- [x] Optional ~chars/4 usage estimate (opt-in)
- [x] `append_daily_note` sandbox tool
- [x] MCP health last-checked timestamp
- [x] Empty-state / copy polish; migration + estimator tests
- [ ] Device soak (blocked: no KVM)

## Architecture target (modules)

```
:app
:feature:chat          # sessions + thread + composer + tool cards
:feature:settings      # provider, appearance, grants, MCP URLs, skills folder
:feature:agent         # (v1) AgentLoop UI bindings / run controller
:core:ui               # theme, Composer, bubbles, EmptyState
:core:network          # OpenAI-compatible SSE + tool_calls aggregator
:core:data             # Room sessions/messages, DataStore, secure prefs
:core:agent            # (v1) AgentLoop, ToolRegistry, ToolPolicy
:core:tools            # (v1) builtin file/notify/web tools
:core:mcp              # (v1) Streamable HTTP MCP client bridge
:core:skills           # (v1) SKILL.md discovery/loader
```

Overnight we may keep agent code inside `core-network` / `feature-chat` until the first green APK, then extract modules.

---

## Phase MVP — “Solid chat tonight” (priority P0)

**Goal:** Installable debug APK; streaming chat against any OpenAI-compatible endpoint; settings that work; no crashes; looks intentionally minimal.

### Checklist

- [x] Finish `:app` + feature screens (session list, chat, settings)
- [x] Theme tokens: near-ink `#141414`, soft off-whites, ~24dp composer radius (original)
- [x] Room: sessions + messages CRUD
- [x] DataStore / EncryptedSharedPreferences: baseUrl, model, apiKey, appearance
- [x] OkHttp SSE streaming + cancel
- [x] Error states: empty key, bad URL, HTTP 401/429, network down
- [x] Unit tests: SSE parser, request builder, settings round-trip
- [x] `./gradlew :app:assembleDebug` succeeds on box SDK (`android-36`)
- [ ] Manual smoke: create session → stream → stop → relaunch persistence
- [x] README + STATUS reflect real paths / version `0.1.0`

### Explicitly out of MVP

Agent tools, MCP, skills folder, FGS, shell, All-files.

### Done when

A stranger with an API key can chat in <60s setup; UI feels calm; tests pass; APK path documented.

---

## Phase v1 — “Agent that can work” (priority P1 — overnight stretch / morning)

**Goal:** Same app becomes a **bounded tool-using agent** with file workspace + notifications + remote MCP + MD skills.

### 1. Agent loop (core)

- [x] `ToolCallAggregator` for streamed `tool_calls` deltas
- [x] `AgentLoop`: maxIterations + wallClockMs + cooperative cancel
- [x] Persist assistant text + tool-call rows in Room; render tool cards from DB
- [x] System prompt: concise agent persona + skill index + tool policy summary
- [ ] Unit tests: mock provider returning tool_calls → tool → stop

### 2. Builtin tools (permission-gated)

- [x] `list_dir` / `read_file` / `write_file` / `mkdir` over **SAF roots + app files**
- [x] Settings: “Choose Agent workspace folder” (`OPEN_DOCUMENT_TREE` + persist)
- [x] `http_get` (opt-in): HTTPS GET, size limit, private hosts blocked
- [ ] `device_info` (safe subset)
- [x] `ToolPolicy` + permission sheet: allow once / session / deny (PublicFiles)

### 3. Skills (MD)

- [x] Scan `filesDir/skills` + bundled assets + user SAF skills
- [x] Skill index in prompt + frontmatter + read_skill lazy-load (v2.25.0)
- [x] Ship 2 example skills (`summarize-doc`, `daily-notes`)
- [x] Settings: skills list + MD preview + reload

### 4. MCP client (remote)

- [x] Settings: MCP JSON list (Streamable HTTP)
- [x] On settings load: `initialize` + `tools/list` → ToolRegistry (`mcp__server__tool`)
- [x] `tools/call` bridge; surface errors as tool results
- [ ] Depend on `io.modelcontextprotocol:kotlin-sdk-client` + Ktor OkHttp engine
- [ ] Compat fallback for legacy SSE servers (optional)

### 5. Long runs / notifications

- [x] `POST_NOTIFICATIONS` (API 33+)
- [x] FGS ongoing notification + **Stop** for agent runs
- [x] `AgentForegroundService` with `foregroundServiceType="specialUse"`
- [x] Tap notification → correct session

### 6. Quality bar (v1)

- [x] Point tests: permission gate, message kind, SAF/path, MCP parse (37 total)
- [x] No secret logging of API keys (keys only in EncryptedSharedPreferences)
- [x] Prompt-injection note in agent system prompt
- [x] STATUS.md: components, versions, known gaps

### Done when

User points Minima at Documents/Minima + one remote MCP + API key → agent can read/write workspace files and call MCP tools with visible cards and Stop working.

---

## Phase v1.5 — Hardening / UX (shipped)

- [x] Crash/ANR boundaries + cancel hygiene
- [x] Settings filter + regrouped sections
- [x] Agent step counter + toast rate-limit
- [x] In-app skills list + MD preview
- [x] API key redaction / no key clipboard
- [x] Onboarding 1-screen
- [x] Chat list recomposition mitigations
- [x] Expanded unit tests

## Phase v2 — “Industrial phone Agent" (priority P2 — after morning demo)

**Goal:** Differentiated power without chaos; still 极简.

### Capabilities

- [x] Declarative HTTP tools from `tools.json` in skill packs (v2.18.0)
- [x] Global `AGENT.md` + `append_daily_note` (sandbox daily notes)
- [x] Scheduled sandbox note (WorkManager daily); quiet hours later
- [x] Multi-provider presets (OpenAI, DeepSeek, OpenRouter, Ollama, Custom)
- [x] Export/import sessions JSON (settings QR later)
- [x] Markdown renderer + code copy / numbered lists / **GFM tables** (v2.9.0)
- [ ] Attachment images → vision-capable models
- [x] Subagent / “focus task” mode with separate budget (learn from Poci / Orion) — v2.32.0 focus_task
- [ ] Debug flavor: on-device MCP server (`mcp-android-sdk`) for desktop Claude/Cursor via `adb reverse`
- [ ] F-Droid flavor experimentation: Termux `RUN_COMMAND` bridge (**not** Play)
- [ ] Optional All-files path **sideload-only** with typed “I understand”
- [ ] Telemetry: local-only diagnostics screen (no phoning home)
- [x] Accessibility contentDescriptions on key controls (large font later)
- [x] R8 minify release (~2.3 MB); Baseline Profiles later

### Distribution

- [ ] Play track: SAF + notifications + remote MCP only
- [ ] GitHub Releases: same + power-user flags
- [ ] Clear privacy policy: keys stay on device

### Done when

Could market as “best-in-class open BYOK Android Agent” without lying — terminal/shell remains optional companion, not a broken promise.

---

## Overnight execution order (do in sequence)

| # | Task | Phase | Est. |
|---|------|-------|------|
| 1 | Unblock Gradle / fix module graph / assembleDebug | MVP | now |
| 2 | Flesh missing UI + ViewModels + repositories | MVP | |
| 3 | SSE + settings hardened + unit tests green | MVP | |
| 4 | ToolCallAggregator + AgentLoop behind feature flag | v1 | |
| 5 | SAF workspace tools + policy sheets | v1 | |
| 6 | SKILL.md loader + 2 examples | v1 | |
| 7 | MCP HTTP client + settings list | v1 | |
| 8 | FGS / notification Stop | v1 | |
| 9 | Self-test matrix + STATUS + APK path | MVP/v1 | |
| 10 | Only then: v2 polish / extras | v2 | |

**Rule:** Never block on user input. If a dependency fails, pin an alternate (e.g. hand-rolled MCP JSON-RPC over OkHttp if kotlin-sdk version conflicts). Prefer smaller green APK over half-integrated mega-features.

---

## Verification matrix (point-to-point)

| Case | Expect |
|------|--------|
| Empty API key → send | Inline error, no crash |
| Invalid base URL | Clear message |
| Stream cancel | Partial text kept or rolled back consistently (pick one; document) |
| Process death mid-stream | Session reopens; no corrupt DB |
| SAF not granted + file tool | Tool result explains how to grant |
| MCP server down | Tool result / banner, loop continues or stops gracefully |
| Budget exhausted | Final assistant note + notification cleared |
| Stop from notification | Loop cancels ≤1s |

---

## Reporting cadence

Every **3 hours** (routine): answer self-check questions from RESEARCH §11, then report: phase %, APK status, tests, blockers, next 3 actions. No spam between reports unless a deliverable APK is ready.

---

## Success for “tomorrow morning”

**Minimum satisfactory:** MVP APK + clean minimal UI + proven BYOK streaming.  
**Stretch satisfactory:** v1 with SAF files + agent loop + at least stub/remote MCP path.  
**v2.0 polish:** bug fixes + About + CN morning summary — **done** (device soak still open).

**v1.9 morning:** stamped APKs + share/edit/delete + tool inventory + CN first-run — **done** (device soak still open).  
**Do not claim:** “world’s best” until v2 quality bar and real device soak tests pass.
