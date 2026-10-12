# Migration: minima-chat → Minima (Kai fork)

## Status

**2026-10-12**：产品方向切换为基于 [Kai 9000](https://github.com/SimonSchubert/Kai) 的二次开发。  
自研客户端 `/workspace/minima-chat` **保留不删**，作为参考与功能对照；新主工程为 `/workspace/minima-kai-fork`。

## What stays from minima-chat

可作为参考迁移/对照的能力（不必一次性搬完）：

- 中文设置与文案习惯、DeepSeek 默认 BYOK 体验
- 公共目录 `AGENTS.md` / `Minima/skills` 自动注入已从本 fork 去掉。应用内 `~/skills` 与 `/skill-id` 仍在。
- Vision 附件与消息气泡展示策略（若上游已有则对齐体验）
- 公开仓库 README / CHANGELOG / Release 流程

## What Kai already covers better

多服务商、Agent 工具环、技能市场、MCP、Linux sandbox（proot）、后台 daemon、桌面/iOS/WASM 等多端等——以本 fork 为准继续打磨。

## GitHub ship plan (pending user confirm)

1. 将 `/workspace/minima-kai-fork` 根目录内容推到 [VIDDsys/Minima](https://github.com/VIDDsys/Minima)（替换旧自研树，或新开 `main` 大版本）
2. 保留 Apache-2.0 + `NOTICE` + 上游归因
3. 打 FOSS debug APK Release，包名 `com.minima.chat`
4. CHANGELOG 标明：基于 Kai `UPSTREAM_PIN.txt` 中的 commit 二次开发

## Do not

- 不要删除 `/workspace/minima-chat` 直到公开仓库切换完成且用户确认
- 不要在公开仓库提交 keystore / API key
