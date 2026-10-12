---
name: http-tools-example
description: Example skill pack with declarative HTTP tools (tools.json). Use when calling fixed https APIs without a full MCP server.
---
# HTTP tools example

This pack ships a `tools.json` that registers **declarative HTTP tools** for the agent.

## How to add your own
1. In the workspace `skills/<pack>/` folder put `tools.json` (and optional `SKILL.md`).
2. Or place `skills/<name>.tools.json` next to a markdown skill.
3. Tap **Reload skills** / **Refresh tool list** in Settings.
4. Tools appear under **Available tools → HTTP (skills)** and are offered to the model when Agent is ON.

## tools.json rules (safety)
- **https only**; **host must be fixed** (no `{{param}}` in the host).
- Methods: GET, POST, PUT, PATCH, HEAD.
- Private / link-local hosts blocked.
- Response body truncated to 32KB; secrets redacted in the tool result.
- Do not put live API keys in shared packs; prefer MCP Bearer headers in Settings for secrets.

Template placeholders: `{{paramName}}` in url path/query, headers, or body — filled from tool arguments.
