# Cloud-agent handoff: sync Minima v2.38.0 → VIDDsys/Test + Release

## Goal
Replace private repo https://github.com/VIDDsys/Test contents with full Minima source + stamped debug APK, then publish Release `v2.38.0`.

## Artifacts (already on box)
- Slim pack: `/workspace/minima-v2.38.0-source-and-apk.tar.gz` (~40MB)
  - Contains `README.md`, `minima-v2.38.0-agent-debug.apk`, `minima-v2.38.0-agent-release.apk`, `minima-chat/` (full Gradle source, no build/)
- Debug APK alone: `/workspace/minima-v2.38.0-agent-debug.apk` and `/workspace/minima-chat/minima-v2.38.0-agent-debug.apk`
- Staging git tree: `/tmp/minima-gh` (same contents)

## Steps
1. Extract tarball into the cloud workspace (or copy from uploads if attached).
2. In VIDDsys/Test clone: delete prior root files (old APK-only tree), copy in pack contents so repo root has `README.md`, both APKs, and `minima-chat/`.
3. Commit on a branch → PR → merge to `main` (or push main if allowed).
4. `gh release create v2.38.0 minima-v2.38.0-agent-debug.apk --repo VIDDsys/Test --title "Minima v2.38.0" --notes "SplashScreen launch-flash root cause; remove bubble chips; chronological tool/text timeline; white eyes icon. versionCode 51."`
5. Report release URL + asset URL.

## Do not
- Make the repo public
- Commit `build/`, keystores, or `local.properties`
