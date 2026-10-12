# Minima

**Minima**（应用包名 `com.minima.chat`）是一款面向 Android 的开源 AI Agent 客户端。

本仓库工程基于 [Kai 9000](https://github.com/SimonSchubert/Kai)（**Apache License 2.0**）二次开发：保留上游能力（多服务商、工具调用、技能、MCP、Linux 沙箱、后台任务等），并按 Minima 产品方向定制品牌与默认配置。

> 说明：`com.minima.chat` 是 Android **应用包名（applicationId）**，不是网站域名。

## 当前版本

- **versionName**：`3.3.1`
- **versionCode**：`118`
- 上游钉扎：见 `UPSTREAM_PIN.txt`
- 分发风味：默认 `foss`（另有 `playStore`）

## 本 fork 相对上游的主要定制

- `applicationId` → `com.minima.chat`（Kotlin / Gradle 命名空间仍为 `com.inspiredandroid.kai.*` / 工程名 `Kai`，以免破坏 `kai.composeapp.generated.resources`）
- 启动器名称与用户可见产品名 → **Minima**
- 应用图标 → Minima 紫色侧视双眼（见 `docs/assets/launcher-icon-source.svg`）
- 默认服务商 → **DeepSeek**，默认模型 → `deepseek-flash`
- 增加 `NOTICE`、`FORK.md`、`MIGRATION.md`、`UPSTREAM_PIN.txt`

## 许可证与归因

- 主体代码遵循 **Apache License 2.0**（`LICENSE.txt`）
- 必须保留上游版权与本仓库的 `NOTICE`、`THIRD_PARTY_LICENSES.md`
- 上游项目：https://github.com/SimonSchubert/Kai
- 本产品名称与商标与上游无关

## 构建（Android FOSS）

需要 JDK 21、Android SDK（compileSdk 37）、NDK `29.0.14206865`。

```bash
./gradlew :androidApp:assembleFossDebug
# 可选（无签名密钥时会回退 debug 签名）
./gradlew :androidApp:assembleFossRelease
```

产物：

- Debug：`androidApp/build/outputs/apk/foss/debug/androidApp-foss-debug.apk`
- Release：`androidApp/build/outputs/apk/foss/release/androidApp-foss-release.apk`

## 致谢

感谢 [Simon Schubert](https://github.com/SimonSchubert) 与 Kai 9000 贡献者。
