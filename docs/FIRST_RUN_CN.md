# Minima 首次使用（简体中文）

面向 VIDDsys · Asia/Shanghai · **v2.43.0**

## 1. 安装
1. 把 `minima-v2.43.0-agent-debug.apk`（或 release）拷到手机。
2. 允许「未知来源」安装。
3. 首次启动会尝试创建 `/storage/emulated/0/Minima/`（含 `AGENTS.md` 与 `skills/`）。

## 2. 设置 → 服务商
填写 Base URL、模型与 API 密钥（可留空走默认 DeepSeek）。**必须点右上角或底部「保存」**，不会自动保存。

## 3. 设置 → 全局 MD
编辑的就是手机上的 `/storage/emulated/0/Minima/AGENTS.md`（任意大小写同一文件）。
用文件管理器改完后，回到设置页会自动拉取；在设置里改完要点「保存」写回磁盘。

## 4. 设置 → 技能
列出 `Minima/skills/<名称>/SKILL.md` 的 name / description。可点「刷新技能列表」。

其它高级开关已移除。Agent / 工具 / 公共存储默认全开，无需开关。

## 5. 开源说明
设置 → 关于 → 开源说明。

## 6. 暂时跳过
欢迎页可「暂时跳过」，之后随时进设置。

```bash
adb install -r minima-v2.43.0-agent-debug.apk
```
