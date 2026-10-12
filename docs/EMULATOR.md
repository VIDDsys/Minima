# Emulator / instrumentation on this box

Updated: **2026-10-12 ~07:23 Asia/Shanghai (CST)** — soak re-run after `/dev/kvm` world-writable.

## Installed
- Android Emulator **37.2.12**
- System image `system-images;android-34;google_apis;x86_64` (rev 14)
- AVD name: **minima34** (Pixel 6 profile)
- SDK: `/home/box/tools/android-sdk` (`source ~/.android-sdk-env.sh`)

## Accel status (honest)
| Check | Result |
|-------|--------|
| `/dev/kvm` | `crw-rw-rw-` (world-writable) |
| `emulator -accel-check` / `emulator-check accel` | **PASS** — `KVM (version 12) is installed and usable.` |
| Nested virt module param | `nested=Y` (this box is itself a KVM guest) |
| `-accel on` boot | **FAIL** — guest hangs; host `kernel BUG at arch/x86/kvm/x86.c:702!` (`kvm_spurious_fault` / `vmx_vcpu_create`). See `docs/soak-kvm-bug-20261012.txt`. |
| `-accel off` (TCG) boot | **PASS** — `sys.boot_completed=1` in ~4 min (~250s emu log). |

**Do not claim KVM works** just because accel-check is green. Nested KVM on this host still dies on vCPU create.

## Working soak path (TCG)
```bash
source ~/.android-sdk-env.sh
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"

# Headless TCG (KVM hangs — use -accel off)
emulator -avd minima34 -no-window -no-audio -no-boot-anim \
  -gpu swiftshader_indirect -accel off -cores 2 -memory 1536 \
  -no-snapshot-save -no-snapshot-load &

# Wait for boot (budget ~5–10 min on TCG)
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 10; done

adb install -r minima-v2.33.0-agent-debug.apk
./gradlew :app:connectedDebugAndroidTest
```

## 2026-10-12 results (v2.33.0)
- **Install:** `adb install -r minima-v2.33.0-agent-debug.apk` → **Success**
- **Manual launch:** `am start -n com.minima.chat/.MainActivity` → process up; splash screencap `docs/soak-v2.33.0-tcg.png`
- **Instrumentation:** `LaunchSmokeTest.launchesMainActivity` → **SUCCESS** (1/1, ~113s on TCG); `BUILD SUCCESSFUL in 14m`
- XML: `app/build/outputs/androidTest-results/connected/debug/TEST-minima34(AVD) - 14-_app-.xml`
- Caveat: under TCG CPU pressure a later frame showed system ANR dialog (`docs/soak-v2.33.0-tcg-settled.png` — "Process system isn't responding"). App process still alive; treat TCG as soak-capable but slow/fragile.

## Instrumentation covered
- `LaunchSmokeTest` — launches `MainActivity` (Hilt app).

## Manual sideload checklist
See STATUS.md § Packages / `docs/DEVICE_SMOKE.md`.
