## v2.33.0 focus_task polish (budget knobs + transcript)

Asia/Shanghai · 2026-10-12

- nestedBudget honors requestedMaxSteps/Seconds tighten-only
- summarize emits `tools:` transcript with SecretRedact peeks
- FocusTaskTool schema: max_steps / max_seconds integers

**Result: ship v2.33.0 (versionCode 46).**

Sideload:
- `minima-v2.33.0-agent-debug.apk`
- `minima-v2.33.0-agent-release.apk`

# Minima v2.8.x regression check (Cancel / toolId / nav / systemPrompt)

Date: 2026-10-12 Asia/Shanghai · No KVM / no device soak

## Scope
Focused passes:
1. CancellationException rethrow (tools / HttpRetry / completeChat)
2. Session-scoped `toolCallId` + unique synthetic ids
3. `onCleared` without `runBlocking` + cancel paths
4. Nav `popUpTo(Sessions)` (one Chat VM)
5. Non-agent `sendAndStream` honors custom `systemPrompt`
6. **v2.8.2**: Stop join+hold ceiling; `runCatching` must not swallow CE in tool collect

## Result
**Real bugs found → shipped v2.8.1 then v2.8.2.**

### Bug (v2.8.0 regression) → fixed in v2.8.1
Removing `runBlocking` from `onCleared` made `cancelRunningTools(sessionId)` fire-and-forget with **no time bound**. Combined with uncapped cancels in the `CancellationException` catch of `send` / `regenerate`, a fast re-open / Stop→Send / regenerate could list **newer** Running tool cards in the same session and mark them cancelled.

### Fix (v2.8.1)
- `MessageDao.listRunningTools(sessionId, createdAtCeilingMs)`
- `cancelRunningTools(sessionId, createdAtCeilingMs)` + `RunningToolCancel.shouldCancel`
- `stop` / `onCleared` / regenerate capture ceiling at cancel time
- CE catch no longer uncapped-cancels tools
- `sendAndStream` system prompt via `RunningToolCancel.nonAgentSystemPrompt`

### Bug (leftover after v2.8.1) → fixed in v2.8.2
1. `stop()` took `createdAt` ceiling **before** cancelling the stream job, so a late `ToolStarted` → `appendToolStart` after the ceiling could leave stuck **Running** cards.
2. Agent collect used `runCatching { appendToolStart/finishTool }`, which **swallows `CancellationException`**, so Stop could fail to abort the collect loop before further tool inserts.

### Fix (v2.8.2)
- `ChatRunGate.beginStopHold` / `endStopHold`: hold gate through Stop cleanup
- `stop()`: cancel job → `join` (5s) → **post-join** ceiling → `cancelRunningTools`; skip late ceiling if `onCleared` cancelled cleanup
- `onCleared`: cancel `stopCleanupJob` first, then early-ceiling fire-and-forget (unchanged safety vs replacement VM)
- `RunningToolCancel.runCatchingExceptCancel(Suspending)` + `ensureActive` on ToolStarted/ToolFinished

## Path checklist
| Path | Verdict |
|------|---------|
| CE rethrow ToolRegistry / HttpRetry / AgentLoop / completeChat | PASS (existing + CancellationPropagationTest / HttpRetryTest) |
| Synthetic `syn_{index}_{name}_{uuid}` uniqueness | PASS (ToolCallAggregatorTest) |
| `findByToolCallId(sessionId, …)` / `finishTool(sessionId, …)` | PASS (code review; session filter in DAO) |
| Stop / onCleared / regenerate cancel ceiling | PASS (RunningToolCancelTest + ChatRunGate stop-hold + wiring) |
| CE not swallowed in tool collect | PASS (RunningToolCancelTest.runCatchingExceptCancel_*) |
| Nav open chat `popUpTo(Sessions)` + `launchSingleTop` | PASS (MinimaNavHost + ChatNavContractTest) |
| Non-agent custom system prompt | PASS (sendAndStream + RunningToolCancelTest) |
| `./scripts/verify.sh` | PASS — **205** unit tests, 0 failures |
| Emulator / connectedAndroidTest | SKIPPED — no KVM |

## Packages
- `minima-v2.8.2-agent-debug.apk`
- `minima-v2.8.2-agent-release.apk` (~2.5MB, minimaDebug-signed sideload)

## Settle check (v2.8.2 — no bump)

Date: 2026-10-12 Asia/Shanghai · Focused re-read of `ChatRunGate.beginStopHold` / `endStopHold`, `ChatViewModel.stop()` join+ceiling, `onCleared`.

| Check | Verdict |
|-------|---------|
| `beginStopHold` bumps gen + keeps `active` so stale `finish(gen)` cannot release mid-cleanup | PASS |
| `endStopHold` CAS idempotent after `cancelAll` (onCleared wins) | PASS |
| `stop()` idempotent while `stopCleanupJob.isActive`; hold through cancel→join(5s)→post-join ceiling | PASS |
| `ensureActive` before late ceiling skips cleanup when `onCleared` cancelled the job | PASS |
| `onCleared` cancels `stopCleanupJob` before early-ceiling FF; per-VM gate + ceiling vs replacement VM | PASS |
| `tryBegin` blocked for whole hold (Stop→fast Send cannot overlap cleanup) | PASS |
| Cross-thread `tryBegin`/`beginStopHold` interleave / join-timeout stuck tool | Theoretical only (UI+FGS Stop on Main; dying collect `ensureActive`+CE); not shipped |

**Result: settle passed — remain on v2.8.2 (versionCode 20). No v2.8.3.**

---

## v2.31.0 Top-p / draft settle (2026-10-12 ~06:35 CST)

**Result: settle passed — remain on v2.31.0 (versionCode 44). No v2.32.**

Reviewed:
- `SamplingParams.parseTopP` / `clampTopP` / `formatTopP` / `filterDecimalDraft`
- Settings VM draft → save → store → ChatProviderConfig / Multimodal encode `top_p`
- Locale comma draft round-trip (`0,9` → 0.9); blank omit; NaN/Inf omit

No real Top-p or draft bug found. Leading `-` stripped by draft filter is intentional (range is [0,1]/[0,2]).

- `./scripts/verify.sh` PASS (assembleDebug + testDebugUnitTest)
- `docs/MORNING_SUMMARY.md` already recommends **v2.31.0**
- No KVM / no emulator attempt

---

## Hold wake (2026-10-12 ~06:33 CST) — no v2.32

Parent: prefer not churning; ship v2.32 only if clear value; no KVM.

**Decision: remain on v2.31.0 (versionCode 44).**

- Docs: refreshed `PRE_MORNING_CHECK.md` (was stale at 2.27.0); `MORNING_SUMMARY` / `FIRST_RUN_CN` / `CHECKLIST` already on 2.31.0
- `./scripts/verify.sh` **PASS** (473 debug unit tests; assembleDebug OK)
- No code change; no new APK stamp needed

---

## v2.32.0 focus_task settle (nesting / budget / no-recursion)

Date: 2026-10-12 Asia/Shanghai · No KVM / no device soak

**Result: settle passed — remain on v2.32.0 (versionCode 45). No v2.32.1.**

### Reviewed
1. `FocusTaskLogic.nestedBudget` — null when wall < 5s or iters < 1; clamps ≤4 / ≤60s / ≤30s tool; never exceeds parent tool-timeout envelope
2. `FocusTaskLogic.withoutFocusTask` — definitions filtered; dispatch of `focus_task` returns hard fail (no inner call)
3. `FocusRunContext.tryEnterNested` CAS 0→1; concurrent / nested depth rejected; `leaveNested` resets
4. `FocusTaskTool` — requires parent frame; `tryEnter` then budget; nested `AgentLoop` with `toolsOverride`; CE rethrown; `finally leaveNested`
5. `AgentLoop.run` — `install` with nested tools; `updateIterations` on current frame; `finally restore(previous)` so parent iteration AtomicInteger survives nest

### Path checklist
| Path | Verdict |
|------|---------|
| No focus_task → focus_task recursion | PASS (filter + depth CAS + dispatch block) |
| Concurrent nest in parallel tool batch | PASS (second `tryEnterNested` fails) |
| Parent frame restore after nested run | PASS (settle probe + AgentLoop finally) |
| Budget too small → clear error, depth cleared | PASS (early return inside try still runs finally) |
| `FocusRunContextSettleTest` probes | PASS (added this wake; no version bump) |
| Emulator / connectedAndroidTest | SKIPPED — no KVM |

### Packages (unchanged)
- `minima-v2.32.0-agent-debug.apk`
- `minima-v2.32.0-agent-release.apk`

