# Merge Strategy: RPCS3 + ARMSX3 → PocoS3

PocoS3 is built by combining **current RPCS3 upstream** with a clean Android
platform layer that takes inspiration from — but does not vendor — ARMSX3's
Android work. This document records the per-subsystem delta analysis so that
future maintainers know which ARMSX3 ideas are alive in PocoS3, which were
re-implemented, and which were dropped.

## Methodology

The deltas below were derived by reading the actual source trees of both
repositories:

- **RPCS3** upstream (`https://github.com/RPCS3/rpcs3`), `main` branch.
- **ARMSX3** (`https://github.com/ARMSX2/ARMSX3`), default branch.

ARMSX3 itself declares in its `README.md`: *"Uses the latest RPCS3 upstream
code (the recent ARM64 improvements included)."* Therefore the ARMSX3 →
RPCS3 delta in the emulator core itself is small — most ARMSX3 value is in
its `android/` tree, not in `rpcs3/`.

## Subsystem delta matrix

Legend:
- **A** = ARMSX3 has it, RPCS3 does not (Android-specific).
- **B** = Both have it, ARMSX3's is the same as RPCS3's (no real delta).
- **C** = ARMSX3 has an older version than RPCS3.
- **D** = ARMSX3's idea is sound; PocoS3 re-implements it against current RPCS3.
- **E** = ARMSX3 carries it as a delta against RPCS3; PocoS3 drops it.

### Android platform layer

| Subsystem                          | ARMSX3 file                                         | Status | PocoS3 decision |
|------------------------------------|----------------------------------------------------|--------|-----------------|
| JNI glue (function-pointer table)  | `android/src/rpcsx-android.cpp`                    | A      | **D**: re-implemented as `android/src/pocos3-android.cpp` with a `PocoS3Api` table. |
| Storage Access Framework wrapper   | `android/src/saf_device.{cpp,h}`                   | A      | **D**: re-implemented as `android/src/pocos3_saf.{cpp,h}`. |
| Android CMake glue                 | `android/CMakeLists.txt`                            | A      | **D**: rewritten to consume `third_party/rpcs3/` as an external CMake subproject instead of carrying the RPCS3 source inline. |
| Build scripts                      | `android/{configure,build-variants,stamp-git-version}.sh` | A | **D**: rewritten; same shape (`cmake -B build-android -G Ninja …`), PocoS3 file names. |
| Compose UI module                  | `android/armsx3-ui/app/…/com/armsx2/`               | A      | **D**: written fresh as `android/pocos3-ui/app/…/com/pocos3/`. |
| Compose UI theme                    | `android/armsx3-ui/app/…/ui/theme/`                 | A      | **D**: written fresh as **Mornye** design language, not ARMSX3's theme. |
| Onboarding flow                    | `android/armsx3-ui/app/…/ui/onboarding/`            | A      | **D**: re-implemented; SAF-first (no `MANAGE_EXTERNAL_STORAGE` by default). |
| Touch overlay                      | `android/armsx3-ui/app/…/ui/controls/`              | A      | **D**: re-implemented as movable/scalable Compose primitives. |
| Performance HUD                    | `android/armsx3-ui/app/…/ui/emulation/`             | A      | **D**: re-implemented with thermal + capability readout. |
| Settings UI                        | `android/armsx3-ui/app/…/ui/settings{,hub}/`        | A      | **D**: re-implemented with three-tier (Simple / Advanced / Developer) split. |
| Game library                       | `android/armsx3-ui/app/…/ui/home/`, `data/library/` | A      | **D**: re-implemented with SAF-based scanning. |
| Firmware installer                 | `android/armsx3-ui/app/…/ui/bios/`                  | A      | **D**: re-implemented; PKG install via `installFw()` in `PocoS3Api`. |
| Controller manager                 | `android/armsx3-ui/app/…/input/`, `ui/controls/`     | A      | **D**: re-implemented for Bluetooth + USB + handheld-bridged HID. |
| Discord SDK bridge                  | `android/armsx3-ui/app/…/cpp/discord_bridge.cpp`    | A      | **E**: omitted by default (proprietary SDK, not redistributable). |
| In-app updater                      | `android/armsx3-ui/app/src/github/java/…`           | A      | **E**: omitted by default (permission cost). |
| Screensavers (flurry/savers)        | `android/armsx3-ui/app/src/main/cpp/{flurry,savers}/` | A    | **E**: omitted (UI flourish, not critical path). |
| Per-game UI (mods/textures/underclock) | `android/armsx3-ui/app/…/ui/{mods,textures,underclock}/` | A | **E**: deferred (depends on game-specific patches PocoS3 does not carry). |
| PGO build mode                     | `android/src/rpcsx-android.cpp` (ARMSX3_PGO)        | A      | **D**: re-implemented as `POCOS3_PGO`. |

### Emulator core (`rpcs3/`)

ARMSX3 does not maintain a long-running fork of the RPCS3 emulator core.
Its `rpcs3/` directory tracks upstream with the occasional ARM64-specific
fix that has either already been merged upstream or is pending. The
**non-Android** delta in ARMSX3 against current RPCS3 is therefore small
and not a fruitful merge target.

PocoS3's policy on the emulator core:

> **Do not patch the emulator core for "Android performance".** Patches under
> `patches/` are strictly additive — they only add `extern "C"` Android entry
> points that delegate to existing RPCS3 public APIs. Any accuracy or
> compatibility change belongs upstream in RPCS3, not as a PocoS3 delta.

This keeps future RPCS3 merges trivial.

### ARM64 / AArch64 JIT

Both RPCS3 (upstream) and ARMSX3 carry the same AArch64 backends for PPU
and SPU (ARMSX3 sources them from upstream). The PocoS3 build system compiles
RPCS3's AArch64 paths unmodified. There is **no** PocoS3-specific JIT delta
to merge — by design.

### Vulkan renderer

Upstream RPCS3's Vulkan renderer is already ARM-friendly (it does not use
NVIDIA-specific extensions by default). PocoS3 does not modify the renderer.
The PocoS3 contribution in this area is:

- `platform/VulkanProbe.kt` — runtime capability detection, fed into the
  renderer via the `setCapabilities()` entry point on the `PocoS3Api` table.
- `platform/FramePacer.kt` — present-mode selection based on detected
  refresh rate.
- `docs/VULKAN_CAPABILITIES.md` — written guidance for which Vulkan
  extensions are required, optional, and Mali-specific.

### Build system

ARMSX3's `android/CMakeLists.txt` builds the entire core from source.
PocoS3's `android/CMakeLists.txt` builds **only** the JNI glue
(`android/src/pocos3-android.cpp` + `pocos3_saf.cpp`) and links against the
prebuilt `libpocos3-core.so`. The core itself is built by
`android/configure.sh` (a standalone CMake + Ninja invocation), exactly like
ARMSX3's `configure.sh`. This keeps Gradle out of the LLVM business.

## Re-implementation rules

When re-implementing an ARMSX3 idea in PocoS3:

1. **Do not copy ARMSX3 source verbatim.** Read the ARMSX3 file, understand
   what it does, write the PocoS3 version in your own style and naming.
2. **Preserve the public-domain-API shape where it's correct.** The
   `PocoS3Api` function-pointer table mirrors the ARMSX3 `RPCSXApi` shape
   because the shape itself is the right abstraction; the implementation is
   fresh.
3. **License headers.** Files that are mechanical translations of ARMSX3
   carry an attribution comment at the top pointing at the ARMSX3 file
   they were inspired by. Files that are pure PocoS3 work do not.
4. **No copyrighted material from Sony.** No PS3 firmware, no SDK headers,
   no game code. PocoS3 expects the user to supply their own legally obtained
   firmware and games.

## Merge order for future RPCS3 updates

When RPCS3 upstream advances and you want to bring PocoS3 forward:

1. Bump the submodule: `cd third_party/rpcs3 && git checkout <new-sha>`.
2. Re-apply PocoS3 patches: `./patches/apply.sh third_party/rpcs3`.
3. If a patch conflicts, it's because an upstream file PocoS3 patches has
   moved or changed. Read the conflict, update `patches/*.patch` to apply
   cleanly, do **not** modify `third_party/rpcs3/` directly.
4. Bump `patches/VERSION` to the new upstream SHA so future maintainers
   know what the patches were last rebased against.
5. Rebuild: `./android/configure.sh --release && cd android/pocos3-ui && ./gradlew :app:assembleRelease`.

## What this strategy gives up

PocoS3's strategy does carry costs, and they should be acknowledged:

- **Slower to ship game-specific compatibility tweaks** — PocoS3 will not
  carry game-specific patches as deltas. If a popular PS3 title needs a
  specific behavior change, that change has to go upstream to RPCS3 first.
  This is the right long-term call but means PocoS3 will lag ARMSX3 on
  per-game tuning.
- **No Discord integration by default** — Users who want rich presence need
  to provide their own Discord SDK `.aar` and enable the build flag.
- **No in-app updater** — Sideload users update by downloading a new APK.

These are deliberate trade-offs in favor of long-term maintainability and
correctness over short-term feature breadth.
