# Minima STATUS

Updated: 2026-10-12 Asia/Shanghai (CST) — **v2.43.0 全局 MD = 公共 AGENTS.md + 技能列表 + 手动保存**

**Minima**（应用包名 `com.minima.chat`）**v2.43.0** — 极简本机 AI Agent（BYOK）。

| | | |
|---|---|---|
| Stamped debug | `minima-v2.43.0-agent-debug.apk` | minimaDebug |
| Stamped release | `minima-v2.43.0-agent-release.apk` | R8 |

```bash
adb install -r minima-v2.43.0-agent-debug.apk
```

## 本轮要点（2.43.0）
1. 设置展示 `Minima/skills/*/SKILL.md` 的 name/description。
2. 「全局 MD」= `/storage/emulated/0/Minima/AGENTS.md`，与文件管理器 100% 同步。
3. 取消自动保存；显式「保存」写入服务商字段 + AGENTS.md。
4. 设置页 resume / FileObserver 实时拉取磁盘改动。
5. 去掉 DataStore systemPrompt / filesDir AGENT.md 分叉。

## 验证
- `./scripts/verify.sh` PASS（testDebugUnitTest + assembleDebug）
- release 已 assemble；日志 `docs/verify-v2.43.0.log`

## 版本
- versionName **2.43.0** / versionCode **56**
