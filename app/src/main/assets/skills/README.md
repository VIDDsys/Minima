# Minima skills

At runtime, place Markdown skill files in:
- App sandbox: `files/skills/*.md` (created on first SkillsLoader use)
- Or a user-picked SAF folder (Settings → Skills folder), optionally with a `skills/` subfolder

## Declarative HTTP tools (v2.18+)
Put `tools.json` in a skill pack folder (`skills/<pack>/tools.json`) or as `skills/<name>.tools.json`.
See `http-tools-example/` for format. Host must be fixed https; see SKILL.md there.

Skill markdown files are indexed into the agent system prompt; HTTP tools are registered into the tool loop.
