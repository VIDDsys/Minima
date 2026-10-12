# Phone storage and skills

**Last verified:** 2026-10-12

Shared phone files and the Linux sandbox are different folders. The model is told that in `## Where files live`, and only the half that this request can actually use. Tool descriptions use the same split.

## Two places

`/storage/emulated/0` is the phone's shared storage (Download, DCIM, Documents, and the rest of that tree).

| Place | Tool | Path | Use it for |
|---|---|---|---|
| External storage. The file manager can see it | `public_storage` | `/storage/emulated/0` | Reading and writing user files |
| Linux sandbox. The file manager cannot see it | `execute_shell_command` | `/root` | Running a command, installing a package, SSH |
| External folder, seen from inside the shell | `execute_shell_command` | `/sdcard` and `/storage/emulated/0` | Running a command on a phone file, after All files access |

`/sdcard` inside the shell is the external folder, not a second copy of `/root`. Do not copy a file across to sync it. `/sdcard`, `/mnt/sdcard`, and `/storage/self/primary` are aliases of `/storage/emulated/0` for `public_storage`.

If this request has `public_storage` but no shell, the prompt says there is no sandbox and does not mention `execute_shell_command`. If both tools are present, the prompt ends with a choose-list: edit a phone file with `public_storage`, run a program under `/root`, run a command on a phone file at `/sdcard/...`.

Without the grant, `public_storage` returns an error and the bind is omitted. The sandbox under `/root` still starts. The Play flavor does not declare the permission.

A shell that was already running picks up a grant or a revoke on its next command. That restarts proot and clears cwd and exported variables. A background process started before the change keeps the old mount.

## What stays on `/root`

`/root` is internal app storage. Packages, executables, SSH keys, and skills (`~/skills/<id>/`) stay there. Shared storage is not where those skills are installed.

`execute_shell_command` is advertised only when the sandbox toggle is on and the install is Ready. Granting All files access does not install the sandbox, so a turn can include `public_storage` and not the shell until the package is installed.

## Key files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../data/ChatSystemPromptBuilder.kt` | `## Where files live` |
| `composeApp/src/androidMain/.../tools/PublicStorageTool.kt` | Native door onto shared storage |
| `composeApp/src/androidMain/.../tools/ShellCommandTool.kt` | Shell door; `/root` vs `/sdcard` |
| `composeApp/src/androidMain/.../sandbox/LinuxSandboxManager.kt` | Bind-mounts shared storage at `/sdcard` and `/storage/emulated/0` |
| `composeApp/src/commonMain/.../skills/SkillManager.kt` | Sandbox skills at `~/skills/` |
| `composeApp/src/commonMain/composeResources/files/skills/create-skill/SKILL.md` | Writes a new skill under `/root/skills/<id>/` |
