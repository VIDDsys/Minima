# Android build toolchain

Verified: 2026-10-12 ~01:51 Asia/Shanghai (CST)

## Paths
- `JAVA_HOME`: /usr/lib/jvm/java-21-openjdk-amd64
- `ANDROID_HOME` / `ANDROID_SDK_ROOT`: /home/box/tools/android-sdk
- Gradle: /home/box/tools/gradle → gradle-8.13
- Env file: ~/.android-sdk-env.sh (sourced from ~/.bashrc)

## Versions
- JDK: openjdk version "21.0.12.1" 2026-08-18
- ADB: Android Debug Bridge version 1.0.41
- aapt2: Android Asset Packaging Tool (aapt) 2.20-13193326
- Gradle: 8.13
- cmdline-tools: 22.0

## sdkmanager --list_installed
```
  Path                 | Version | Description                       | Location            
  -------              | ------- | -------                           | -------             
  build-tools;34.0.0   | 34.0.0  | Android SDK Build-Tools 34        | build-tools/34.0.0  
  build-tools;36.0.0   | 36.0.0  | Android SDK Build-Tools 36        | build-tools/36.0.0  
  platform-tools       | 37.0.1  | Android SDK Platform-Tools 37.0.1 | platform-tools      
  platforms;android-36 | 2       | Android SDK Platform 36           | platforms/android-36
```

## Installed packages
- platforms;android-36 → /home/box/tools/android-sdk/platforms/android-36
- build-tools;36.0.0 → /home/box/tools/android-sdk/build-tools/36.0.0
- platform-tools 37.0.1 → /home/box/tools/android-sdk/platform-tools
- cmdline-tools/latest → /home/box/tools/android-sdk/cmdline-tools/latest

## Also present
- Node v22.23.3, npm 10.9.9

## Usage
```bash
source ~/.android-sdk-env.sh
cd /workspace/minima-chat && ./gradlew :app:assembleDebug
```

App build is owned by the other agent; this file documents toolchain only.


## Signing (Minima app)

See [`keystore/README.md`](./keystore/README.md).

| Config | File | Password |
|--------|------|----------|
| App debug (`minimaDebug`) | `keystore/minima-debug.jks` | `minima-debug` (documented; debug-only) |
| Optional upload (`minimaUpload`) | `keystore/minima-upload.jks` | `MINIMA_UPLOAD_STORE_PASSWORD` or `keystore/upload-pass.local` (gitignored) |

Default `assembleRelease` uses **minimaDebug** for sideload smoke (same as debug signing identity when present).

For upload signing:
```bash
export MINIMA_USE_UPLOAD_SIGNING=1
export MINIMA_UPLOAD_STORE_PASSWORD="$(cat keystore/upload-pass.local)"
./gradlew :app:assembleRelease
```

Never commit `upload-pass.local` or Play upload keystores with real secrets.
