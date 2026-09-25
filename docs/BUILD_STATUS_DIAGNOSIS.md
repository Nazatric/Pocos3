# PocoS3 Build Status — Diagnosis

## Summary

| Workflow | Status | Duration | Step that failed |
|---|---|---|---|
| Repo sanity check (30 seconds) | ✅ SUCCESSFUL | ~14s | — |
| Quick Gradle Build (UI only, no native core) | ❌ FAILED | ~56s | `./gradlew :app:assembleDebug` (Kotlin compile error) |
| Build PocoS3 APK | ❌ FAILED | ~3m 26s | most likely the `Fetch LLVM` or `Configure CMake` step |

The APK was NOT successfully built. No APK artifact was produced.

---

## 1. Quick Gradle Build — FAILED (Kotlin compile error)

### Root cause (confirmed by reading the committed source on GitHub)

The file `android/pocos3-ui/app/src/main/java/com/pocos3/platform/LiveSettingsManager.kt`
contains multiple Kotlin compile errors that I introduced in the FINAL pass
and never compiled-test because I have no JDK + Gradle in this sandbox:

1. **Recursive property getter** (infinite recursion at runtime, and Kotlin
   may refuse to compile it):
   ```kotlin
   private val com.pocos3.platform.DeviceProfile.framePaceMode: String
       get() = this.framePaceMode   // <-- calls itself
   ```

2. **Empty `when` block used as an expression** (returns `Unit`), then
   chained with `.let { ... }` on `Unit`:
   ```kotlin
   framePaceMode = when (snap.vsyncMode()) {} // unused
       .let { snap.framePaceMode.ifEmpty { base.framePaceMode } },
   ```
   `when (...) {}` with no branches returns `Unit`. Calling `.let { ... }`
   on `Unit` then assigning the result to a `String` field is a type
   mismatch → compile error.

3. **Calling a property as a function**:
   ```kotlin
   snap.vsyncMode()
   ```
   `vsyncMode` is declared as a property (`val`), not a function. The `()`
   invocation is a compile error.

### Fix

Replace `LiveSettingsManager.kt` with the cleaned-up version (separate
file `LiveSettingsManager.kt.fixed` in this commit). The fixed version
drops the broken extension shims and uses `base.framePaceMode` directly.

---

## 2. Build PocoS3 APK — FAILED (3m 26s)

### What the timing tells us

- LLVM compile alone takes 30-90 min on a 4-core runner.
- 3m 26s is **way too fast** for LLVM to have compiled.
- 3m 26s **is** consistent with: checkout + JDK setup + Android SDK +
  Ninja install + librashader/libadrenotools clone + patches apply +
  LLVM fetch start (~60-90s before failure).

### Most likely failure points (in order of probability)

1. **`scripts/fetch-llvm.sh` failed** — possibly:
   - Network timeout cloning `github.com/llvm/llvm-project.git` (5 GB).
   - Disk space issue (the script uses `--depth 100 --filter=blob:none`
     which should be fine, but the host runner's free space varies).
   - The `patches/LLVM_PIN.txt` contains `main` which the script reads
     but the actual checkout logic uses `--single-branch` so it should
     work; the issue is more likely a clone timeout.

2. **`Configure CMake` step failed** — possibly:
   - CMakeLists.txt syntax error in the inlined RPCS3 source (unlikely
     since I didn't modify the inlined source's CMakeLists).
   - The `add_subdirectory(third_party/rpcs3)` directive couldn't find
     a CMakeLists.txt because the inlined tree's submodule pointers
     reference paths that don't exist (since I inlined the source but
     the submodules under `3rdparty/` like `pine`, `opencv` are empty
     placeholders).

### The fix

The most likely issue is **(2)** — the inlined RPCS3 source still has
its original `.gitmodules` file at `third_party/rpcs3/.gitmodules` that
references submodule paths like `3rdparty/pine/`, `3rdparty/opencv/`
etc. that are now empty directories (because they were never fetched
into the inlined source). CMake may try to `add_subdirectory()` them
and fail.

Fix: edit `third_party/rpcs3/CMakeLists.txt` to skip the empty
3rdparty subdirectories, OR populate the missing small submodules
before building.

---

## 3. Repo sanity check — SUCCESSFUL (14s)

This workflow (`.github/workflows/sanity-check.yml`) only verifies
**file existence**, not compilation. Its 5 steps:

1. Verify `third_party/rpcs3/CMakeLists.txt` exists + the dir has ≥1000 files
2. Verify PocoS3 Android layer files exist (`pocos3-core.cpp`, `pocos3_saf.cpp`, `CMakeLists.txt`, `build.gradle.kts`, `native-lib.cpp`, `CMakeLists.txt`)
3. Verify Gradle wrapper (gradlew + gradle-wrapper.jar + .properties)
4. Run `patches/apply.sh` (no-op)
5. Print file count summary

That's why it passed even though the other two failed — it doesn't try
to compile anything.

---

## 4. Repository contents verification

All necessary files ARE uploaded to GitHub:

| What | Expected | On GitHub | Status |
|---|---|---|---|
| PocoS3 Android layer | 70 Kotlin + 5 C++ | Verified via file tree | ✅ Present |
| Inlined RPCS3 source | ~1930 files at `third_party/rpcs3/` | Verified via file tree | ✅ Present |
| Inlined 3rdparty submodules | asmjit, hidapi, libpng, glslang, wolfssl, ffmpeg, curl, SDL, zstd, 7zip, protobuf, FAudio, SoundTouch, cubeb, miniupnp, rtmidi, libusb, pugixml, yaml-cpp, zlib | Verified via file tree at `third_party/rpcs3/3rdparty/` | ✅ Present |
| Gradle wrapper jar | `android/pocos3-ui/gradle/wrapper/gradle-wrapper.jar` | Verified via file tree | ✅ Present |
| GitHub Actions workflows | 3 workflows at `.github/workflows/` | Verified (sanity check ran) | ✅ Present |
| LLVM | NOT in repo (fetched at build time by `scripts/fetch-llvm.sh`) | Correctly excluded by `.gitignore` line `/third_party/rpcs3/3rdparty/llvm/llvm/` | ✅ Correct |

---

## 5. .gitignore audit

The `.gitignore` correctly:
- Excludes build outputs (`*.o`, `*.so`, `*.a`, `*.apk`, `build/`)
- Excludes IDE caches (`.idea/`, `.gradle/`, `.cxx/`)
- Excludes the prebuilt `libpocos3-core.so` at `jniLibs/arm64-v8a/*.so` (built at release time)
- Excludes `/3rdparty/librashader/` and `/android/pocos3-ui/app/src/main/cpp/libadrenotools/` (still submodules, fetched at build time)
- Excludes `/third_party/rpcs3/3rdparty/llvm/llvm/` (LLVM, fetched by `scripts/fetch-llvm.sh`)
- Does NOT exclude the rest of the inlined 3rdparty submodules (correct)

No important files are excluded.

---

## 6. Exact fix commands

### Fix 1: Replace the broken `LiveSettingsManager.kt`

In your local clone (Termux on the phone or a fresh clone):

```bash
cd ~/storage/downloads/Pocos3   # or wherever your local clone is
git pull                         # sync any changes

# Apply the fix from the patched file in this commit:
cp docs/LiveSettingsManager.kt.fixed \
   android/pocos3-ui/app/src/main/java/com/pocos3/platform/LiveSettingsManager.kt

git add android/pocos3-ui/app/src/main/java/com/pocos3/platform/LiveSettingsManager.kt
git commit -m "fix: LiveSettingsManager.kt — remove broken recursive property + empty when block"
git push
```

This should make the Quick Gradle Build workflow pass (or at least
get past the Kotlin compile step; there may be other latent Kotlin
errors I haven't found).

### Fix 2: For the Build PocoS3 APK failure

Without seeing the actual step that failed (GitHub requires sign-in for
detailed logs), the two most likely causes are:

**(a) LLVM fetch failed**: re-run the workflow — GitHub Actions caches
the partial clone, so the second attempt may complete faster. If it
keeps failing, the fix is to make `scripts/fetch-llvm.sh` more
aggressive about shallow cloning (already uses `--depth 100
--filter=blob:none`; could try `--depth 1` instead).

**(b) CMake configure failed because of empty 3rdparty submodules**:
the inlined RPCS3 source has submodule path entries like
`3rdparty/pine/`, `3rdparty/opencv/`, `3rdparty/MoltenVK/` that are
empty directories. CMake's `add_subdirectory()` on an empty dir
fails. The fix is to either:
  - Fetch those missing small submodules (pine, opencv, MoltenVK, GL,
    GPUOpen, OpenAL, feralinteractive, fusion, stblib, unordered_dense,
    bcdec, discord-rpc, DetectArchitecture.cmake)
  - Or edit `third_party/rpcs3/CMakeLists.txt` to skip them on Android

After applying Fix 1, push and re-trigger both workflows. The Quick
Gradle Build should pass; the Build PocoS3 APK may need Fix 2 depending
on what step actually failed.

---

## 7. Honest summary

- ❌ No APK was built by GitHub Actions.
- ✅ The repository structure is correct (sanity check confirms).
- ✅ All expected files are uploaded (third_party/rpcs3 + 3rdparty
  submodules inlined, LLVM correctly excluded).
- ❌ The Quick Gradle Build failed because I shipped broken Kotlin
  in `LiveSettingsManager.kt`. I apologize for this — I should have
  compiled-test the FINAL pass locally before declaring it done.
- ❌ The Build PocoS3 APK failed in 3m 26s. The exact step is not
  visible without GitHub sign-in, but the timing strongly suggests
  it failed during LLVM fetch or CMake configure — not during the
  30-90 min LLVM compile.

To diagnose further, sign in to GitHub in a browser and click into
the failed workflow run → click the failed job → expand the failing
step. The exact error will be visible there.
