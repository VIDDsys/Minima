# Minima

**Minima** 是一款原创的极简 Android AI Agent 客户端（应用包名 `com.minima.chat`），对接任意 OpenAI 兼容 API（自带密钥 / BYOK）。与其他产品无关联。

[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](./LICENSE)
[![Android](https://img.shields.io/badge/Android%20minSdk-26-green)](#)
[![Release](https://img.shields.io/badge/release-v2.42.0-black)](https://github.com/VIDDsys/Minima/releases/tag/v2.42.0)

## 功能

- 流式对话（SSE，失败时可回退非流式）+ **可调用工具的 Agent 循环**
- **Vision**：系统相册多选图片 → 多模态 `image_url`
- **公共工作区**：`/storage/emulated/0/Minima`
  - `AGENTS.md`（大小写不敏感）每次 Agent 回合注入上下文
  - `skills/<名称>/SKILL.md` — 按常见 Agent Skills 规范注入 name/description，正文可用 `read_skill`
- 内置与公共存储文件工具、声明式 HTTP 工具、远程 MCP（Streamable HTTP）
- API 密钥加密存储（EncryptedSharedPreferences），Release 经 R8 混淆
- 安静的 Compose / Material3 界面（中文设置：Base URL / 模型 / 密钥）

## 环境要求

- Android 8.0+（API 26）
- 构建需 JDK 17
- Android SDK（见 `TOOLCHAIN.md`）

## 快速开始（侧载）

1. 从 [Releases](https://github.com/VIDDsys/Minima/releases) 安装 APK（或本地编译）。
2. 打开 **设置 → 服务商**：填写 Base URL、模型、API 密钥（默认 DeepSeek）。
3. 可选：编辑 `/storage/emulated/0/Minima/AGENTS.md`，并在 `Minima/skills/` 下添加技能。
4. 新建对话 → 发送。用回形针附加图片（系统相册）。

```bash
adb install -r minima-v2.42.0-agent-debug.apk
```

## 构建

```bash
./scripts/verify.sh          # 单元测试 + debug APK
./gradlew :app:assembleRelease
```

需要 JDK 17 与 Android SDK（`local.properties` 或 `ANDROID_HOME`）。详见 [`TOOLCHAIN.md`](./TOOLCHAIN.md)。

完整打标后的产物：

| 类型 | 路径 |
|------|------|
| Debug | `minima-v2.42.0-agent-debug.apk` |
| Release | `minima-v2.42.0-agent-release.apk` |

v2.42.0 debug APK 挂在 [GitHub Release](https://github.com/VIDDsys/Minima/releases/tag/v2.42.0)。APK 不入库。

签名 keystore（`*.jks`）、`local.properties`、`keystore/upload-pass.local` 仅留在本机并已 gitignore。设置 `MINIMA_USE_UPLOAD_SIGNING=1` 且上传密钥可用时，Release 使用上传密钥；否则回退本地 debug keystore 或 Android debug 密钥。

## 公共 Minima 目录结构

```
/storage/emulated/0/Minima/
  AGENTS.md                 # 常驻上下文（所有 *agents.md* 都会注入）
  skills/
    my-skill/
      SKILL.md              # YAML 的 name + description → 索引；正文经 read_skill
```

遵循常见的 [Agent Skills](https://agentskills.io/specification) / `AGENTS.md` 约定。

## 架构（简）

```
app/          Application + NavHost
feature/chat  会话列表 + 聊天界面 / ViewModel
feature/settings
core/ui       Compose 主题、Markdown、输入框、气泡
core/data     Room、DataStore、Vision 附件、PublicMinimaWorkspace
core/network  OkHttp SSE、多模态请求编码
core/agent    AgentLoop、工具、技能、MCP、前台通知
```

## 状态与路线图

- 当前状态：[`STATUS.md`](./STATUS.md)
- 更新记录：[`CHANGELOG.md`](./CHANGELOG.md)
- 路线图：[`ROADMAP.md`](./ROADMAP.md)

## 隐私

API 密钥与聊天记录保存在本机。Minima 不为你的消息运营云端后端——流量只发往你配置的 Base URL。

## 许可证

[MIT](./LICENSE)。Copyright (c) 2026 VIDDsys.
