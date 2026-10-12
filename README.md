# Minima

**Minima** (`com.minima.chat`) is an original, ink-minimal Android AI **agent** client for any OpenAI-compatible API (BYOK).  
Not affiliated with any other product.

[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](./LICENSE)
[![Android](https://img.shields.io/badge/Android%20minSdk-26-green)](#)
[![Release](https://img.shields.io/badge/release-v2.42.0-black)](https://github.com/VIDDsys/Minima/releases/tag/v2.42.0)

## Features

- Streaming chat (SSE + non-stream fallback) with a **tool-using Agent loop**
- **Vision**: multi-image attach via system Photo Picker → multimodal `image_url` parts
- **Public workspace**: `/storage/emulated/0/Minima`
  - `AGENTS.md` (any casing) injected every agent turn
  - `skills/<name>/SKILL.md` — Agent Skills style name/description index + `read_skill`
- Builtin + public-storage file tools, declarative HTTP tools, remote MCP (Streamable HTTP)
- Encrypted API key (EncryptedSharedPreferences), R8 release builds
- Quiet Compose / Material3 UI (Chinese settings for Base URL / model / key)

## Requirements

- Android 8.0+ (API 26)
- JDK 17 to build
- Android SDK (see `TOOLCHAIN.md`)

## Quick start (sideload)

1. Install a stamped APK from [Releases](https://github.com/VIDDsys/Minima/releases) (or build locally).
2. Open **设置 → 服务商**: Base URL, 模型, API 密钥 (defaults: DeepSeek).
3. Optional: edit `/storage/emulated/0/Minima/AGENTS.md` and add skills under `Minima/skills/`.
4. New chat → send. Attach images with the paperclip (Photo Picker).

```bash
adb install -r minima-v2.42.0-agent-debug.apk
```

## Build

```bash
./scripts/verify.sh          # unit tests + debug APK
./gradlew :app:assembleRelease
```

JDK 17 and an Android SDK are required (`local.properties` or `ANDROID_HOME`). See [`TOOLCHAIN.md`](./TOOLCHAIN.md).

Stamped outputs (after a full stamp):

| Kind | Path |
|------|------|
| Debug | `minima-v2.42.0-agent-debug.apk` |
| Release | `minima-v2.42.0-agent-release.apk` |

The v2.42.0 debug APK is attached to the [GitHub Release](https://github.com/VIDDsys/Minima/releases/tag/v2.42.0). APKs are not stored in git.

Signing keystores (`*.jks`), `local.properties`, and `keystore/upload-pass.local` stay on your machine and are gitignored. A release build uses the upload key only when `MINIMA_USE_UPLOAD_SIGNING=1` and that key is present; otherwise it falls back to a local debug keystore or the Android debug key.

## Public Minima folder layout

```
/storage/emulated/0/Minima/
  AGENTS.md                 # always-on context (all *agents.md* injected)
  skills/
    my-skill/
      SKILL.md              # YAML name + description → index; body via read_skill
```

Follows the common [Agent Skills](https://agentskills.io/specification) / `AGENTS.md` conventions.

## Architecture (short)

```
app/          Application + NavHost
feature/chat  Sessions + Chat UI / ViewModels
feature/settings
core/ui       Compose theme, markdown, composer, bubbles
core/data     Room, DataStore, vision attach, PublicMinimaWorkspace
core/network  OkHttp SSE, multimodal request encoding
core/agent    AgentLoop, tools, skills, MCP, FGS notifications
```

## Status & roadmap

- Live status: [`STATUS.md`](./STATUS.md)
- Changelog: [`CHANGELOG.md`](./CHANGELOG.md)
- Roadmap notes: [`ROADMAP.md`](./ROADMAP.md)

## Privacy

API keys and chat history stay on device. Minima does not operate a cloud backend for your messages — traffic goes only to the Base URL you configure.

## License

[MIT](./LICENSE). Copyright (c) 2026 VIDDsys.
