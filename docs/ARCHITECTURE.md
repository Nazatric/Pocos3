# PocoS3 Architecture

This document is the **authoritative** architectural comparison between
upstream RPCS3, ARMSX3, and PocoS3. It is written from the actual source trees
of both upstreams as of the date of this commit, not from memory.

## TL;DR

PocoS3 is **current RPCS3 core + a fresh Android platform layer**. The Android
layer follows the same *pattern* ARMSX3 pioneered (a `dlopen`'d prebuilt core
+ a Compose UI + a JNI glue with a function-pointer table), but PocoS3's
Android source is written from scratch under its own package name
(`com.pocos3`) and its own file names. PocoS3 does **not** vendor ARMSX3
source and rename strings — doing so would have produced a 77K-line rename
churn with no semantic value, made future upstream merges unreadable, and
tripped ARMSX3's contributor copyrights unnecessarily.

## What "the same pattern" means, concretely

ARMSX3's runtime architecture is:

```
┌──────────────────────────────────────────────────┐
│  Android App (Kotlin/Compose, com.armsx2)        │
│                                                  │
│   Activity ─── JNI ──┐                           │
│                      ▼                           │
│   native-lib.cpp (dlopen("libarmsx3-core.so"))   │
│   RPCSXApi api = *(RPCSXApi*)dlsym(...);         │
│   api.boot("/sdcard/game.ps3")                   │
└──────────────────────────────────────────────────┘
                    │
                    ▼  dlopen() at process start
┌──────────────────────────────────────────────────┐
│  libarmsx3-core.so  (= upstream RPCS3, compiled   │
│  with the ARMSX3 Android patches applied)         │
│                                                  │
│   PPU interpreter + LLVM recompiler              │
│   SPU interpreter + LLVM recompiler              │
│   RSX Vulkan renderer                             │
│   Cell / kernel / syscalls / audio / etc.        │
└──────────────────────────────────────────────────┘
                    │
                    ▼  vkQueuePresentKHR
┌──────────────────────────────────────────────────┐
│  Mali / Adreno Vulkan driver                      │
└──────────────────────────────────────────────────┘
```

PocoS3 preserves this exact shape, with these renames:

| ARMSX3                                   | PocoS3                                   |
|------------------------------------------|------------------------------------------|
| `android/src/rpcsx-android.cpp`          | `android/src/pocos3-android.cpp`         |
| `android/armsx3-ui/app/.../native-lib.cpp` | `android/pocos3-ui/app/.../native-lib.cpp` |
| `libarmsx3-core.so`                      | `libpocos3-core.so`                      |
| `RPCSXApi` (struct of fn pointers)       | `PocoS3Api` (struct of fn pointers)      |
| `com.armsx2.*` (Kotlin package)          | `com.pocos3.*` (Kotlin package)          |
| `android/armsx3-ui/`                     | `android/pocos3-ui/`                     |
| `android/armsx3-app/`                    | (not present; ARMSX3 kept it as legacy)  |
| `android/configure.sh`                   | `android/configure.sh` (rewritten)       |
| `patches/ARMSX3.patch` (implicit)        | `patches/pocos3-android-entry-points.patch` |

## Why preserve the pattern

The `dlopen`-of-a-prebuilt-core pattern is the **only** way to keep Android
Studio responsive while iterating on the UI. If you put LLVM inside Gradle's
`externalNativeBuild`, every sync re-cmake's the world, every clean rebuilds
LLVM from scratch, and the IDE becomes unusable. Keeping the core as a
separately-built `.so` that Gradle only packages — never builds — is correct.

The `PocoS3Api` function-pointer table is also the right abstraction: it gives
the Kotlin/Java side a stable C ABI to call into, while the C++ side stays
free to use STL types internally. JNI calls into the glue; the glue calls the
table; the table lives in the core `.so`. No per-frame JNI work touches the
emulator core.

## What PocoS3 deliberately does *not* copy from ARMSX3

| ARMSX3 feature                                   | PocoS3 decision                       | Reason |
|--------------------------------------------------|---------------------------------------|--------|
| Discord Social SDK integration                    | **Omitted**                           | Proprietary; not redistributable; not on the critical path. Documented as an optional integration in `docs/`. |
| In-app GitHub release updater                     | **Omitted by default**                | Adds `REQUEST_INSTALL_PACKAGES` permission; Play Store rejects; sideload users can update manually. Documented as a build flag. |
| `MANAGE_EXTERNAL_STORAGE` (all-files access)      | **Off by default, opt-in**            | Modern Android uses SAF. PocoS3 ships a SAF-based browser as the primary path; all-files is a developer toggle. |
| ARMSX3's per-game `underclock` / `mods` / `textures` UI screens | **Deferred** | These are downstream of game-compatibility patches that don't exist in upstream RPCS3. PocoS3's first release focuses on the core boot path; per-game tweaks land later. |
| ARMSX3's custom `flurry`/`savers` OpenGL screensavers | **Omitted**                       | Screensaver is a UI flourish; not on the critical path. |
| `android/armsx3-app/` legacy module               | **Not vendored**                       | ARMSX3 keeps the pre-ARMSX3 ARMSX2 app as a sibling module; PocoS3 has only one UI module. |
| PGO build mode (`-DARMSX3_PGO=generate`)          | **Re-implemented as `POCOS3_PGO`**     | The technique is good; the symbol names are PocoS3's. |

## What PocoS3 *adds* over ARMSX3

These are first-class PocoS3 features:

1. **Runtime Vulkan capability detection** (`platform/VulkanProbe.kt`) —
   enumerates `VkPhysicalDeviceProperties2` + `VkPhysicalDeviceDriverProperties` +
   all `vkEnumerateDeviceExtensionProperties` results, sorts them into a
   capability tier (`BASELINE`, `OPTIMIZED`, `ADVANCED`, `MALI_OPTIMIZED`),
   and exposes the chosen tier to the C++ core via a `setCapabilities()` entry
   point. ARMSX3 ships a fixed feature set; PocoS3 adapts per-device.

2. **CPU topology probe** (`platform/CpuTopology.kt`) — parses
   `/sys/devices/system/cpu/cpu*/topology/` at runtime, detects
   big.LITTLE layout, and feeds the result into the RPCS3 scheduler's
   thread affinity hint. ARMSX3 hardcodes thread counts.

3. **Device profile registry** (`platform/DeviceProfile.kt` +
   `platform/profiles/PocoX7ProProfile.kt`) — a profile is a *function of
   detected features*, not a string match. The POCO X7 Pro is the *default*
   profile when its exact hardware signature is detected; other hardware gets
   a `GenericMaliProfile` / `GenericAdrenoProfile` / `GenericFallbackProfile`.

4. **Frame pacing layer** (`platform/FramePacer.kt`) — wraps
   `ANativeWindow_setFrameRate` + `getRefreshCycle` + Vulkan present modes
   into a single object that picks the right present mode for the device's
   reported refresh rate (60 / 90 / 120 Hz).

5. **Thermal-aware performance presets** (`platform/ThermalPolicy.kt`) —
   reads `PowerManager.getThermalStatusHeadroom()` and reduces the
   background-compile thread count when the device is hot.

6. **Mornye UI design system** (`ui/theme/`) — a coherent visual language for
   the app, not Material 3 defaults. See `docs/MORNYE_DESIGN.md`.

## What PocoS3 deliberately does *not* modify in the RPCS3 core

PocoS3 does **not** patch the emulator core for accuracy or compatibility.
Every change to `third_party/rpcs3/rpcs3/Emu/`, `third_party/rpcs3/rpcs3/PPU/`,
`third_party/rpcs3/rpcs3/SPU/`, `third_party/rpcs3/rpcs3/RSX/`, etc. is upstream
work. PocoS3's patches under `patches/` are **strictly additive** — they only
add `extern "C"` Android entry points that bridge into the existing RPCS3
public API (`Emu::Init`, `Emu::Boot`, etc.).

If you find yourself wanting to patch an emulator subsystem for "Android
performance", the correct path is to send the patch upstream to RPCS3, not to
carry it as a PocoS3 delta. Carrying deltas makes future RPCS3 merges painful
and creates a maintenance fork.

## Layer-by-layer

```
┌─────────────────────────────────────────────────────────────────┐
│  Android UI (Kotlin / Jetpack Compose)                          │
│  android/pocos3-ui/app/src/main/java/com/pocos3/ui/...          │
│  - HomeScreen / GameLibraryScreen / SettingsScreen              │
│  - EmulationSurface (Vulkan SurfaceView host)                   │
│  - TouchOverlay (virtual controller)                            │
│  - PerformanceHud (FPS / frame time / thermals)                 │
│  - SystemInfoScreen (capability dump)                           │
│  - Onboarding (firmware, games, controller)                    │
└─────────────────────────────────────────────────────────────────┘
                            │ JNI
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  JNI Glue (C++)                                                 │
│  android/pocos3-ui/app/src/main/cpp/native-lib.cpp              │
│  - dlopen("libpocos3-core.so")                                  │
│  - Resolves PocoS3Api function-pointer table                    │
│  - Marshalls surface events, input, storage from Java to C      │
└─────────────────────────────────────────────────────────────────┘
                            │ dlsym
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  PocoS3 Native Core (= upstream RPCS3 + Android entry points)   │
│  third_party/rpcs3/rpcs3/  (submodule)                          │
│  + patches/pocos3-android-entry-points.patch                     │
│  - PPU interpreter / LLVM recompiler                             │
│  - SPU interpreter / LLVM recompiler                             │
│  - RSX / Vulkan renderer                                         │
│  - Cell kernel / syscalls / audio / io                           │
└─────────────────────────────────────────────────────────────────┘
                            │ vkQueuePresentKHR
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  Mali-G720 Vulkan driver (stock Android driver; no root)        │
└─────────────────────────────────────────────────────────────────┘
```

## Subsystem responsibilities

### UI layer (Kotlin)
Owns: screen composition, theming, touch input rendering, Compose state.
Does **not** own: any per-frame emulation work, any Vulkan calls, any PPU/SPU
work. The UI thread never blocks on the emulator; the emulator pushes frame
state to a `SharedFlow` the HUD observes.

### JNI glue (C++)
Owns: marshalling between Java types and the `PocoS3Api` table, lifecycle of
the `dlopen`'d core handle, surface event queueing.
Does **not** own: emulation logic.

### Native core (RPCS3 + PocoS3 entry-point patches)
Owns: everything PS3-related — PPU, SPU, RSX, Cell, audio, GCM, etc.
Does **not** own: Android UI, Android storage semantics (the glue translates
SAF URIs to file descriptors before handing them to the core), Android input
(the glue translates `MotionEvent` to pad state before handing them to the
core).

### Android platform layer (`platform/`)
Kotlin classes that wrap a single Android API surface each:
`VulkanProbe`, `CpuTopology`, `DeviceProfile`, `FramePacer`,
`ThermalPolicy`, `StorageAccess`, `ControllerManager`, `AudioRouter`. Each is
testable in isolation; the glue never reaches into Android framework APIs
directly.

## Why a separate JNI glue file instead of inline JNI in Kotlin

The PocoS3 glue is ~1500 lines of C++ that knows the `PocoS3Api` table shape.
Putting that inline in `externalNativeBuild` would force Gradle to recompile
it on every CMake sync. Keeping it as a tiny CMake target in `android/src/`
and having Gradle only build *that* target means UI iteration stays fast.

## Decision log

| #  | Decision                                                   | Rationale                                              |
|----|------------------------------------------------------------|-------------------------------------------------------|
| D1 | One UI module, not two                                     | ARMSX3's legacy `armsx3-app` exists for historical migration only. PocoS3 starts clean. |
| D2 | `dlopen` core, never `externalNativeBuild` the core         | LLVM recompiles burn IDE responsiveness.              |
| D3 | Function-pointer table, not direct JNI into core            | Lets the C++ core keep its STL types in its ABI.       |
| D4 | SAF-first storage, all-files opt-in                        | Play policy; Android 14+ sandboxing; user trust.      |
| D5 | No Discord SDK in the default build                         | License; not redistributable; not critical.            |
| D6 | No in-app updater by default                               | `REQUEST_INSTALL_PACKAGES` permission cost.           |
| D7 | PocoS3 patches to RPCS3 are additive only                   | Future upstream merges stay trivial.                  |
| D8 | Runtime capability detection over static device-name match  | Survives Poco phone variants; works on other Mali devices. |
| D9 | Compose UI, no XML layouts                                  | Modern Android; fewer files; better tooling.         |
| D10 | Mornye design system, not Material 3 defaults              | User requirement; produces a coherent premium feel.   |
