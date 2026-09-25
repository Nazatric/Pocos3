# PocoS3 Patches against upstream RPCS3

## Good news: no entry-point patches are needed

PocoS3's architecture is **cleaner** than the original spec: instead of
patching upstream RPCS3 to add `extern "C" poco3_*` entry points, PocoS3
puts the entry points in its OWN source file
(`android/src/pocos3-core.cpp`) that gets compiled alongside the
upstream `rpcs3_emu` target into `libpocos3-core.so`.

So the only "patches" needed are the kind that touch upstream RPCS3
behaviour — and PocoS3's policy is to **send those upstream** rather than
carry them as deltas.

## What this directory contains

| File | Purpose |
|------|---------|
| `README.md` | This document. |
| `VERSION` | Records the upstream RPCS3 commit SHA PocoS3 was last built against. Bump when you bump the submodule. |
| `apply.sh` | A no-op stub that succeeds even without any `.patch` files, so `configure.sh` can call it unconditionally. |

## What this directory DOES NOT contain

- Patches that add `extern "C" poco3_*` entry points to upstream RPCS3.
  These are **not needed** — the entry points live in
  `android/src/pocos3-core.cpp` and are compiled into the .so by
  `android/CMakeLists.txt`'s `pocos3-core` target.

## Why this is better than the patch-based approach

1. **No rebases.** When upstream RPCS3 advances, you just bump the
   submodule; PocoS3's `android/src/pocos3-core.cpp` calls the same
   public API (`Emulator::Init`, `Emulator::Boot`, `Emulator::Pause`,
   etc.) that the desktop frontend uses, so it's stable across upstream
   updates.
2. **No license complications.** PocoS3's entry points are clearly
   PocoS3's source; they don't modify a GPL'd file.
3. **Easier code review.** The entry points are all in one file, in
   one tree, under PocoS3's namespace.
4. **Smaller diff.** The user can rebase PocoS3 by bumping one
   submodule; the patches directory stays empty.

## What KIND of patches might end up here in the future

If a future PocoS3 contributor writes a change that:

1. **Must touch upstream RPCS3 source**, AND
2. **Cannot be submitted upstream** (e.g. Android-specific logging,
   Android-specific path handling, or a deliberate Android-only behaviour
   fork that the upstream maintainers decline to merge),

then that patch lives here, with a clear `## Rationale` header explaining
why it cannot go upstream.

## apply.sh

`apply.sh` is now a no-op stub. It exists so the GitHub Actions workflow
and `android/configure.sh` can call it unconditionally without breaking
when there's nothing to apply.

```bash
./patches/apply.sh third_party/rpcs3
# Exits 0 always (no patches to apply in this scaffold).
```

## Building the native core

The actual build command for the native core is:

```bash
./android/configure.sh --release
```

Which wraps:

```bash
cmake -S . -B build-android-release-a13 -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-33 \
    -DCMAKE_BUILD_TYPE=RelWithDebInfo \
    ...
cmake --build build-android-release-a13 --target pocos3-core -j$(nproc)
```

The CMake build:
1. Pulls in upstream RPCS3 via `add_subdirectory(third_party/rpcs3)`,
   which creates the `rpcs3_emu` object library.
2. PocoS3's `android/CMakeLists.txt` creates the `pocos3-core` SHARED
   library target, which compiles:
   - `android/src/pocos3-core.cpp` (the `_pocos3_*` entry points)
   - `android/src/pocos3_saf.cpp` (Storage Access Framework bridge)
   - `android/src/virtual_pad_handler.cpp` (touch controls for pad_thread)
   - `android/src/virtual_keyboard_handler.cpp` (cellKb keyboard)
   - `third_party/rpcs3/rpcs3/Input/*.cpp` (input handler subset)
   - `third_party/rpcs3/rpcs3/rpcs3_version.cpp` (version helpers)
3. Links everything against `rpcs3_emu` + LLVM + Vulkan + etc.
4. Outputs `libpocos3-core.so` with a version script that hides all
   symbols except `_pocos3_*`.
