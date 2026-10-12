# Fork notes (Minima ← Kai 9000)

## Upstream

- Repo: https://github.com/SimonSchubert/Kai
- License: Apache-2.0 (`LICENSE.txt`)
- Pin: `UPSTREAM_PIN.txt`
- `NOTICE` added by this fork for Apache attribution
- Third-party: `THIRD_PARTY_LICENSES.md` (PRoot GPL-2.0, talloc LGPL-3.0)

## Renames

| Item | Upstream | This fork |
|------|----------|-----------|
| Gradle root / Compose resources package | Kai / `kai.composeapp…` | **unchanged**（改名会破坏 generated resources） |
| applicationId | com.inspiredandroid.kai | **com.minima.chat** |
| Launcher label / user-facing “Kai 9000” | Kai 9000 | **Minima** |
| Launcher icon | purple dual-circle | **Minima eyes SVG** (`docs/assets/launcher-icon-source.svg`) |
| Default service | Free (kai9000 proxy) | **DeepSeek** / `deepseek-flash` |
| versionName | 3.3.0 | **3.3.4** (versionCode 121) |
| Kotlin packages / namespace | com.inspiredandroid.kai.* | **unchanged**（内部 OK） |

## Intentionally still “Kai*” internally

- `rootProject.name = "Kai"`
- `import kai.composeapp.generated.resources…`
- SQLDelight `KaiDatabase`
- Class names `KaiApplication`, `KaiNotificationListenerService`, …
- Feature label **Kai Build**（子系统；后续可再品牌化）

## Public storage (path D)

FOSS flavor only. `MANAGE_EXTERNAL_STORAGE` plus legacy `READ`/`WRITE_EXTERNAL_STORAGE` for older APIs. Play may reject all-files access, so `playStore` does not declare it.

- Native tool: `public_storage` (not via proot)
- Sandbox mounts, when granted: **`/sdcard`** and **`/storage/emulated/0`** → host shared storage
- `/root` stays on internal storage

Grant: Settings → Sandbox → All files access.

## Build

```bash
./gradlew :androidApp:assembleFossDebug
./gradlew :androidApp:assembleFossRelease
```

Do **not** rename `rootProject.name` without migrating every Compose resources import.


## Build status (2026-10-12 CST)

- `./gradlew :androidApp:assembleFossDebug :androidApp:assembleFossRelease` → **SUCCESS**
- Stamped:
  - `/workspace/minima-v3.3.1-kai-foss-debug.apk`
  - `/workspace/minima-v3.3.1-kai-foss-release.apk`
  - also under repo root `minima-v3.3.1-foss-*.apk`
- Release signing: debug keystore fallback (no `KEYSTORE_FILE` env)
