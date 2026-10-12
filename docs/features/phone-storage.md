# Phone storage and skills

**Last verified:** 2026-10-12

Shared phone files and the Linux sandbox are different trees. Shared phone files themselves are **one** tree with two doors. The model is told that once, in `## Phone storage`, and the tool descriptions match it.

## One shared tree

`/storage/emulated/0` is the phone's shared storage (Download, DCIM, Documents, and the Minima workspace).

| Door | When | Use it for |
|---|---|---|
| `public_storage` | All files access is granted (FOSS). Android file APIs, no proot | Reading and writing user files |
| Shell `/sdcard` and `/storage/emulated/0` | The same grant. proot bind-mounts that directory at both names | A command that needs the file (a pipeline, an archive, a script input) |

A file written either way is the same bytes. Do not copy it into the other door to "sync" it. `/sdcard`, `/mnt/sdcard`, and `/storage/self/primary` are aliases of `/storage/emulated/0` for `public_storage`.

Without the grant, `public_storage` returns an error and the bind is omitted. The sandbox under `/root` still starts. The Play flavor does not declare the permission.

A shell that was already running picks up a grant or a revoke on its next command. That restarts proot and clears cwd and exported variables. A background process started before the change keeps the old mount.

## What stays on `/root`

`/root` is internal app storage. Packages, executables, SSH keys, and marketplace skills (`~/skills/<id>/`) stay there. Shared storage is not a place to run binaries, so `~/skills` is **not** a symlink to the public folder. A symlink would also dangle whenever All files access is off and would hide skills that were installed before the grant.

## Durable user workspace

`/storage/emulated/0/Minima` (shell: `/sdcard/Minima`) is the only home for instructions the user owns:

- `AGENTS.md` — standing instructions. When `public_storage` is on the request and the file is non-empty, its text is injected as `## User workspace` (capped). The model is told not to copy it under `/root`.
- `skills/<id>/SKILL.md` — durable user skills, editable from a file manager.

Marketplace installs and the built-in `create-skill` fallback still write `~/skills/<id>/` when public storage cannot be written. `create-skill` writes the public folder when `public_storage` works, and does not also write a second `SKILL.md` under `/root`.

## Which skill the model sees

| Home | Wins over |
|---|---|
| `/storage/emulated/0/Minima/skills/<id>/` | sandbox `~/skills/<id>/` and built-ins |
| `~/skills/<id>/` | built-ins |
| Built-in (`create-skill`) | nothing |

The losing folder stays on disk. The prompt lists bundled files from the home that won and does not merge the two bodies. Removing a skill deletes the home that is currently winning. If that was the public override, the sandbox folder shows up again on the next load.

`execute_shell_command` is a separate question from this layout. It is advertised only when the sandbox toggle is on and the install is Ready. Granting All files access does not install the sandbox, so a turn can include `public_storage` and not the shell until the package is installed.

## Key files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../data/ChatSystemPromptBuilder.kt` | `## Phone storage` and `## User workspace` |
| `composeApp/src/androidMain/.../tools/PublicStorageTool.kt` | Native door onto shared storage |
| `composeApp/src/androidMain/.../tools/ShellCommandTool.kt` | Shell door; `/root` vs `/sdcard` |
| `composeApp/src/androidMain/.../sandbox/LinuxSandboxManager.kt` | Bind-mounts shared storage at `/sdcard` and `/storage/emulated/0` |
| `composeApp/src/commonMain/.../skills/SkillManager.kt` | Public skill overrides sandbox skill |
| `composeApp/src/androidMain/.../skills/MinimaWorkspace.android.kt` | Reads `Minima/AGENTS.md` and `Minima/skills` |
| `composeApp/src/commonMain/composeResources/files/skills/create-skill/SKILL.md` | Writes one home, public first |
