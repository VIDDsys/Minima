# Minima status

Updated: 2026-10-12 Asia/Shanghai (CST) — **v2.42.0 vision + public Minima workspace + open-chat polish**

**Minima** (`com.minima.chat`) **v2.42.0** — original minimal on-device AI agent (BYOK).

## Packages
| Kind | File | Notes |
|------|------|-------|
| Stamped debug | `minima-v2.42.0-agent-debug.apk` | minimaDebug |
| Stamped release | `minima-v2.42.0-agent-release.apk` | R8 |
| Build outputs | `app/build/outputs/apk/debug/app-debug.apk`, `…/release/app-release.apk` | |

```bash
adb install -r minima-v2.42.0-agent-debug.apk
```

## Public GitHub sync target
- **Repo:** https://github.com/VIDDsys/Minima (public)
- **Layout:** repository **root** = this project’s source files (do **not** nest under a `minima-chat/` folder)
- Include: `README.md`, `CHANGELOG.md`, `STATUS.md`, modules, `scripts/`, stamped APKs optional via Releases

## Causes fixed (user-visible) in 2.42.0
1. Vision attach broken → Photo Picker + hardened prepare + incremental chips + inline errors; model receives `image_url` parts.
2. User attachments as chips **above** bubble (text-only bubble).
3. Open from list flashed empty new-chat page → `messagesReady` gate.
4. Autoscroll pin / pause-on-scroll-up.
5. GFM list polish (`+`, blank-separated items).
6. First-launch `/storage/emulated/0/Minima` + `AGENTS.md` + `skills/*/SKILL.md` injection.

## verify.sh evidence
- **Command:** `./scripts/verify.sh` → **BUILD SUCCESSFUL**
- **Unit tests (debug):** **528** passed, **0** failures
- **Release:** `:app:assembleRelease` **BUILD SUCCESSFUL**
- Log: `docs/verify-v2.42.0.log`

## Version
- versionName **2.42.0** / versionCode **55**
