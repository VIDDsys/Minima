# Changelog

All notable changes to Minima are documented in this file.

## [2.42.0] — 2026-10-12

### Fixed
- **Vision multi-image** — Photo Picker (`PickMultipleVisualMedia`) attaches multiple images end to end: incremental chips, hardened MIME/decode, inline errors beside the composer (no popup). Multimodal `image_url` parts go through `VisionHistoryPatch` and `MultimodalContent`.
- **Attach chips above the bubble** — filename chips render above the user bubble. The bubble itself is text only (no embedded image previews).
- **Open-chat gate** — wait for the first Room emission (`messagesReady`) so opening a session does not flash the empty “new chat” state before history appears. The list still uses `reverseLayout`, so the first paint is at the bottom.
- **Autoscroll** — stay pinned to the newest message. Scrolling up pauses the pin until the user returns to the bottom.

### Added
- **Public workspace** `/storage/emulated/0/Minima`, created on first launch (soft-fail if storage is blocked).
  - Every `AGENTS.md` / `agents.md` (any casing) is injected into the agent context.
  - `Minima/skills/<name>/SKILL.md` (or `skill.md`) contributes name and description to the index; the full body is loaded with `read_skill`.
- **GFM lists** — `+` bullets work, and a blank line between items no longer breaks the list.

### Docs
- Public `README.md` and this changelog for https://github.com/VIDDsys/Minima (MIT).

## [2.41.0] — 2026-10-12

- Attach UX: no snackbar popups; multi-image chips; transcript without photo previews; reverseLayout open-chat scroll.

## Earlier

See `STATUS.md` for the full overnight version trail (2.0 → 2.40).
