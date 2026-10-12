# Changelog

All notable changes to Minima are documented in this file.

## [2.43.0] — 2026-10-12

### Fixed
- **全局 MD 单一真源** — 设置里的「全局 MD」就是 `/storage/emulated/0/Minima/AGENTS.md`（任意大小写），不再使用 DataStore / `filesDir/AGENT.md` 分叉副本。
- **手动保存** — Base URL / 模型 / API 密钥 / 全局 MD 取消自动保存；点「保存」一次性写入。
- **磁盘热更新** — 设置页 `ON_RESUME` + `FileObserver` 监听 Minima 目录，外部改 AGENTS.md / skills 会刷新 UI。
- Agent 系统提示只注入公共 `AGENTS.md`，不再叠加 DataStore `systemPrompt`。

### Added
- 设置 → **技能**：列出 `Minima/skills/*/SKILL.md` 的 name / description，可刷新。

## [2.42.0] — 2026-10-12


### Fixed
- **Vision attach end-to-end** — Photo Picker (`PickMultipleVisualMedia`), incremental chip updates, hardened MIME/decode path, inline attach errors (no popup covering the composer). Multimodal `image_url` parts still go through `VisionHistoryPatch` + `MultimodalContent`.
- **User message layout** — attachment filename chips render **above** the user bubble; text stays inside the bubble only (no embedded image previews).
- **Open-chat flash** — wait for first Room emission (`messagesReady`) so opening a session never flashes the empty “new chat” EmptyState before history appears; list still uses `reverseLayout` so the first paint is at the bottom.
- **Autoscroll** — stay pinned to the newest message; scrolling up pauses pin until the user returns to the bottom.

### Added
- **Public workspace** `/storage/emulated/0/Minima` created on first launch (soft-fail if storage blocked).
  - All `AGENTS.md` / `agents.md` (case-insensitive) injected into agent context.
  - `Minima/skills/<name>/SKILL.md` (or `skill.md`) — name + description injected; full body via `read_skill`.
- GFM list polish: `+` bullets, blank lines between list items no longer break list runs.

### Docs
- Public-ready `README.md` + this `CHANGELOG.md` for https://github.com/VIDDsys/Minima

## [2.41.0] — 2026-10-12

- Attach UX: no snackbar popups; multi-image chips; transcript without photo previews; reverseLayout open-chat scroll.

## Earlier

See `STATUS.md` for the full overnight version trail (2.0 → 2.40).
