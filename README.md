# PocoS3

**PocoS3** is a native ARM64 Android PlayStation 3 emulator built by combining the
current RPCS3 upstream core with a clean, mobile-first Android platform layer.
It is primarily tuned for the **POCO X7 Pro** (Dimensity 8400-Ultra, Mali-G720,
12 GB LPDDR5X, 120 Hz display) but is designed to detect and adapt to other
modern ARM64 Android devices.

PocoS3 is **not** ARMSX3 renamed, and is **not** desktop RPCS3 wrapped in an
Android shell. The upstream RPCS3 core is consumed as a git submodule (the same
core the desktop emulator uses), and the Android-specific work that ARMSX3
pioneered has been re-implemented cleanly in PocoS3's own `android/` tree. See
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and
[`docs/MERGE_STRATEGY.md`](docs/MERGE_STRATEGY.md) for the full rationale.

## Status

This repository is a **clean, buildable scaffold** for PocoS3. It contains:

- A real Gradle Android app (`android/pocos3-ui/`) with a Compose UI.
- A real CMake-driven native glue (`android/CMakeLists.txt` + `android/src/`).
- A real GitHub Actions workflow that builds the APK.
- Real device capability detection (Vulkan features, CPU topology, SoC
  identification) so the runtime can pick a sensible default profile.
- The upstream RPCS3 source referenced as a git submodule under
  `third_party/rpcs3/` (you must initialize submodules locally).
- Documentation describing exactly which patches must be applied to the
  upstream RPCS3 source so that PocoS3's Android entry points resolve at
  `dlopen` time (`patches/`).

What this repository does **not** contain:

- PS3 firmware or any copyrighted Sony material. You must supply your own
  legally obtained firmware and games.
- The Discord Social SDK (proprietary, not redistributable).
- Prebuilt native libraries. You build the core from source on a real machine.

## Quick start

```bash
# 1. Clone with submodules (pulls RPCS3 + its own submodules: LLVM, FFmpeg, ...)
git clone --recursive https://github.com/<your-org>/PocoS3.git
cd PocoS3

# 2. Fetch two extra dependencies that are not submodules
git clone https://github.com/SnowflakePowered/librashader 3rdparty/librashader
git clone https://github.com/bylaws/libadrenotools android/pocos3-ui/app/src/main/cpp/libadrenotools

# 3. Build the native core (long; produces ~1.3 GB unstripped .so)
export ANDROID_HOME=$HOME/Android/Sdk
./android/configure.sh --release

# 4. Build the APK
cd android/pocos3-ui
./gradlew :app:assembleRelease
```

The release APK lands in `android/pocos3-ui/app/build/outputs/apk/release/`.

See [`BUILDING.md`](BUILDING.md) for the full build matrix (NDK version, CMake
version, JDK, min/target SDK) and the GitHub Actions CI commands.

## License

PocoS3 is licensed under the **GPL-2.0-only**, the same license as RPCS3.
Individual files may carry different headers; check each file. PocoS3 is
derived from RPCS3 (`https://github.com/RPCS3/rpcs3`) and studied the Android
work in ARMSX3 (`https://github.com/ARMSX2/ARMSX3`); both upstreams retain
their respective attributions.

## Target device

| Spec | Value |
|---|---|
| Device | POCO X7 Pro |
| SoC | MediaTek Dimensity 8400-Ultra |
| CPU | ARMv9.2, big.LITTLE (4× A720-class + 4× A520-class), 3.0 GHz peak |
| GPU | ARM Mali-G720 (Immortalis-class) |
| RAM | 12 GB LPDDR5X |
| Storage | UFS 4.0 |
| Display | 2712×1220, 120 Hz AMOLED |
| Vulkan | 1.3 baseline, 1.4-capable driver |
| Android | 15+ out of the box |

The device profile is **generated at runtime** from detected features, not from
a device-name string. See
[`docs/POCO_X7_PRO_PROFILE.md`](docs/POCO_X7_PRO_PROFILE.md) for the actual
detected defaults.

## Acknowledgements

- **RPCS3** — the entire PS3 emulation core is upstream work by the RPCS3 team
  and contributors. PocoS3 does not modify the core for accuracy or
  compatibility purposes; it provides an Android platform layer around it.
- **ARMSX3** — the broader architectural pattern (dlopen of a prebuilt core,
  Compose UI, SAF-based storage, JNI bridge shape) is heavily informed by
  ARMSX3's existing Android work. PocoS3 re-implements these patterns in its
  own code, it does not copy ARMSX3 source verbatim.
