# Minima device smoke — 2026-10-12 (second run)

**Result: PASS (TCG) / KVM still FAIL**

Time zone: Asia/Shanghai (CST, UTC+8). Window ~06:54–07:23 CST.

Target APK: `minima-v2.33.0-agent-debug.apk` (versionCode **46**)  
App version was **not** bumped by this smoke run (docs-only update).

AVD: `minima34` (API 34, `google_apis` x86_64, Pixel 6)  
SDK: `/home/box/tools/android-sdk`  
Emulator: 37.2.12.0

---

## Summary

| Step | Outcome |
|------|---------|
| `/dev/kvm` world-writable | OK (`crw-rw-rw-`) |
| `emulator-check accel` | Reports: KVM (version 12) usable |
| Boot with **KVM** (`-accel on -cores 1`) | **FAIL** — new `kvm_spurious_fault` Oops #4 @ host uptime ~26964s; qemu idle in `sigsuspend`; adb empty/`offline` |
| Boot with **TCG** (`-accel off -cores 2 -memory 1536`) | **PASS** — `adb` `device` ~60s; `sys.boot_completed=1` at ~+234s wall; emu log “Boot completed in 250570 ms” |
| `adb install -r` v2.33.0 debug | **PASS** — Streamed Install **Success** (~151s) |
| Manual `am start` MainActivity | **PASS** — pid alive; `ResumedActivity=…MainActivity`; splash PNG |
| `connectedDebugAndroidTest` | **PASS** — `LaunchSmokeTest > launchesMainActivity` **SUCCESS** (1/1); BUILD SUCCESSFUL in 14m |
| Post-UTP reinstall stamped APK | **PASS** — Success; relaunch pid observed |
| Emulator cleanup | Stopped TCG qemu after evidence collected (~07:22 CST) |

---

## Blockers (remaining)

### 1. Host nested KVM still broken (primary accel path)

`emulator-check` lies relative to real boot: creating vCPUs still hits:

```
kernel BUG at arch/x86/kvm/x86.c:702!
Oops: invalid opcode: 0000 [#4] …
  vmx_vcpu_create → kvm_arch_vcpu_create → kvm_vm_ioctl_create_vcpu
```

Repro:

```bash
source ~/.android-sdk-env.sh
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"
emulator -avd minima34 -no-window -no-audio -no-boot-anim \
  -gpu swiftshader_indirect -accel on -cores 1
# Within seconds: dmesg shows kvm_spurious_fault; adb never becomes useful
```

Artifact: `docs/soak-kvm-bug-20261012.txt`, log `docs/soak-emulator.log`.

**Unblock:** fix nested KVM on the host (kernel / hypervisor nested policy), or use a machine with working KVM / a physical device. Until then **use TCG** for on-box soak.

### 2. TCG is slow and can ANR the system process

Under sustained ~100% qemu CPU, a settled screencap showed **“Process system isn't responding”** (`docs/soak-v2.33.0-tcg-settled.png`). Earlier splash frame and LaunchSmokeTest still succeeded. Prefer KVM when fixed; keep TCG budgets generous (boot 5–10 min, install ~2–3 min, instrument ~15 min).

---

## Attempts log (this run)

1. **06:54** — KVM headless (`-accel on -cores 1 -memory 2048`). Opens `/dev/kvm` + `anon_inode:kvm-vm`; ports 5554/5555 listen; **no adb device**; dmesg new BUG @ 26964s. Polled ~90s → offline. Killed.
2. **06:56** — TCG (`-accel off -cores 2 -memory 1536`). Boot completed ~07:01.
3. **07:01** — Install v2.33.0 debug → Success.
4. **07:03** — `am start -n com.minima.chat/.MainActivity`; screencap splash; no app FATAL.
5. **07:05** — `./gradlew :app:connectedDebugAndroidTest` → LaunchSmokeTest SUCCESS (~07:19).
6. **07:19** — Reinstall stamped APK after UTP uninstall → Success; relaunch.
7. **07:22** — Stop emulator; write docs.

---

## Evidence artifacts

| Path | Notes |
|------|--------|
| `docs/soak-emulator.log` | KVM attempt |
| `docs/soak-emulator-tcg.log` | TCG boot (includes Boot completed) |
| `docs/soak-kvm-bug-20261012.txt` | dmesg Oops #4 stack |
| `docs/soak-v2.33.0-tcg.png` | Splash / logo after launch |
| `docs/soak-v2.33.0-tcg-settled.png` | Later frame: system ANR dialog |
| `docs/soak-connectedDebugAndroidTest.log` | Full gradle/UTP log |
| `app/build/outputs/androidTest-results/connected/debug/TEST-minima34(AVD) - 14-_app-.xml` | 1 test, 0 failures |

---

## What was verified

- Device online + boot completed (TCG)
- Sideload of stamped **v2.33.0** debug APK
- Manual MainActivity launch (process + splash UI)
- Instrumentation `LaunchSmokeTest`

## What was NOT verified

- KVM-accelerated emulator
- Full Agent feature soak / MCP on-device
- Play upload (out of scope)
- Smooth UI under TCG without system ANR pressure
