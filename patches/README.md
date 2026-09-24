# PocoS3 Patches against upstream RPCS3

These patches are **strictly additive**: they only add `extern "C"` entry
points that delegate to existing RPCS3 public APIs. They do not modify
emulator accuracy, compatibility, or rendering behaviour. The intent is
that a future RPCS3 update can be merged by simply re-applying these
patches with a 3-way merge — no semantic conflicts.

## Files

| File                                          | Purpose |
|-----------------------------------------------|---------|
| `pocos3-android-entry-points.patch`          | Adds `extern "C"` functions (`pocos3_initialize`, `pocos3_boot`, `pocos3_pause`, `pocos3_resume`, `pocos3_kill`, `pocos3_getState`, `pocos3_surfaceEvent`, `pocos3_overlayPadData`, ...) to RPCS3's `rpcs3/main.cpp` / `rpcs3/Emu/System.cpp` / `rpcs3/Emu/System.h`. These functions are what `dlsym()` in `android/src/pocos3-android.cpp` looks for. |
| `pocos3-android-cmake-target.patch`          | Adds a `pocos3-core` CMake target to `rpcs3/CMakeLists.txt` that builds the core into a `.so` named `libpocos3-core.so` (instead of the desktop executable). This target links against LLVM statically and produces a single self-contained library that the JNI glue `dlopen()`s. |
| `pocos3-android-pgo.patch`                  | Adds a `POCOS3_PGO` CMake option that enables LLVM profile instrumentation. Used by `android/configure.sh --pgo-generate` / `--pgo-use`. |
| `VERSION`                                    | Records the upstream RPCS3 commit SHA these patches were last rebased against. |
| `apply.sh`                                   | Idempotent patch applier. Refuses to apply twice; safe to re-run. |

## Why patches, not a fork

A fork would carry every future RPCS3 merge as a manual rebase. A patch
series carries them as `git apply` operations against whatever SHA the
submodule points at, which is the same model the Linux kernel uses for
subsystem patch series.

If a patch fails to apply, the fix is to update the patch — not to
modify the submodule. Modifying the submodule makes future merges
opaque.

## apply.sh

`apply.sh` takes the path to the RPCS3 submodule root and applies every
`*.patch` file in this directory in alphabetical order. It is idempotent:
if the patches have already been applied (detected by `git apply --check`),
it prints a message and exits 0.

```bash
./patches/apply.sh third_party/rpcs3
```

If a patch fails to apply cleanly, `apply.sh` exits non-zero and prints
the failing patch. To resolve:

1. `cd third_party/rpcs3 && git checkout .` (reset any partial application)
2. Manually rebase the failing patch onto the new upstream
3. Commit the rebased patch in this directory
4. Re-run `./patches/apply.sh third_party/rpcs3`
5. Update `VERSION` to the new upstream SHA

## Required extern "C" symbols

The JNI glue in `android/src/pocos3-android.cpp` looks for the following
symbols in `libpocos3-core.so`. If a symbol is missing, the glue treats it
as optional and silently skips it (except for the **required** set, which
causes `dlopen` resolution to fail).

### Required

- `pocos3_initialize`
- `pocos3_shutdown`
- `pocos3_boot`
- `pocos3_kill`
- `pocos3_pause`
- `pocos3_resume`
- `pocos3_getState`
- `pocos3_surfaceEvent`
- `pocos3_overlayPadData`

### Optional

- `pocos3_surfaceSizeChanged`
- `pocos3_overlayPadPressure`
- `pocos3_keyboardKey`
- `pocos3_setPadSensor`
- `pocos3_getPadRumble`
- `pocos3_setPadDeviceClasses`
- `pocos3_usbDeviceEvent`
- `pocos3_installFw`
- `pocos3_isInstallableFile`
- `pocos3_getDirInstallPath`
- `pocos3_probePkgInfo`
- `pocos3_install`
- `pocos3_getFramePeriodNs`
- `pocos3_getFrameWorkNs`
- `pocos3_getRsxThreadTid`
- `pocos3_getTitleId`
- `pocos3_getCurrentTrophyName`
- `pocos3_setThermals`
- `pocos3_setRenderPosition`
- `pocos3_setCapabilities`
- `pocos3_setProfile`
- `pocos3_setSocInfo`
- `pocos3_processCompilationQueue`
- `pocos3_startMainThreadProcessor`
- `pocos3_collectGameInfo`
- `pocos3_isRestartPending`
- `pocos3_openHomeMenu`
- `pocos3_captureFrame`
- `pocos3_getPerformanceSnapshot`

The signatures these symbols must match are declared in
`android/src/pocos3-android.h` (`struct PocoS3Api`).

## Status as of this commit

The patches in this directory are written as a **specification**, not as
finalised source. To actually build PocoS3 you (or someone) must:

1. Read `pocos3-android-entry-points.patch.template`.
2. For each function in the required / optional list above, write the
   `extern "C"` wrapper in `rpcs3/rpcs3/main.cpp` or `rpcs3/Emu/System.cpp`
   that delegates to the existing RPCS3 public API
   (`Emu::Init`, `Emu::Boot`, `Emu::Pause`, `Emu::Resume`, `Emu::Kill`,
   `Emu::GetStatus()`, etc.).
3. Generate the actual `.patch` files via `git diff` against the
   modified submodule.
4. Update `VERSION` with the new SHA.

The template below (and the apply script) verify the patches apply
correctly once they exist; they do not generate the implementation for
you. Writing these wrappers is straightforward but tedious — it's
roughly the volume of `android/src/rpcsx-android.cpp`'s surface area
against `rpcs3/rpcs3/main.cpp` minus the PGO plumbing.

If you have a working ARMSX3 checkout, the equivalent wrappers live in
its `rpcs3/rpcs3/` tree (look for `extern "C"` symbols with the `rpcsx_`
prefix). Porting them to `pocos3_` is mechanical, but **do not copy
ARMSX3 source verbatim** — read it, understand the delegation, write
your own.
