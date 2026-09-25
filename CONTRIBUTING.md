# Contributing to PocoS3

PocoS3 welcomes contributions. The rules below exist to keep the project
maintainable and to keep future upstream RPCS3 merges painless.

## Architecture in one paragraph

PocoS3 = upstream RPCS3 core (as a git submodule at `third_party/rpcs3/`)
+ a clean Android platform layer in `android/` (Gradle Compose UI + a thin
C++ JNI glue that `dlopen()`s a prebuilt core `.so`). The JNI glue looks
up a `PocoS3Api` function-pointer table via `dlsym()`. Every entry on that
table is an `extern "C"` symbol in the core, added by the additive
patches under `patches/`. **The emulator core is not modified for
performance, accuracy, or compatibility**; such changes belong upstream in
RPCS3 itself, not as a PocoS3 delta.

Read these before contributing:

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — layer-by-layer architecture.
- [`docs/MERGE_STRATEGY.md`](docs/MERGE_STRATEGY.md) — what to port, what to skip, why.
- [`docs/PERFORMANCE_PLAN.md`](docs/PERFORMANCE_PLAN.md) — honest performance targets and what is explicitly out of scope.
- [`docs/MORNYE_DESIGN.md`](docs/MORNYE_DESIGN.md) — the UI design language.

## What kind of PRs are welcome

- **Android platform layer improvements** — capability detection, frame
  pacing, thermal policy, touch overlay, controller manager, audio
  router, storage SAF wrappers.
- **Mornye UI** — new screens that follow the design language.
- **Build system** — CMake, Gradle, GitHub Actions improvements.
- **Device profile defaults** — better-tuned defaults for a specific
  device, derived from detected capabilities.
- **Documentation** — clearer docs, more accurate capability matrix.
- **Patches under `patches/`** — when upstream RPCS3 changes, the patch
  series needs re-basing. That work is welcome.

## What kind of PRs are NOT welcome

- **Patches to `third_party/rpcs3/`** that are not part of the additive
  entry-point patch series. If you need to modify emulator behaviour,
  send the change upstream to RPCS3.
- **"Android performance" hacks** that disable correctness checks,
  skip frame simulation, or fake FPS. See `docs/PERFORMANCE_PLAN.md`.
- **Renamed ARMSX3 source.** Read ARMSX3, understand it, write your own
  implementation. Do not paste ARMSX3 code with `armsx3` → `pocos3`
  substitutions.
- **Game-specific compatibility hacks** carried as PocoS3 deltas. They
  belong upstream.
- **Discord SDK integration by default.** Proprietary; not
  redistributable. Can be opt-in via a build flag if carefully scoped.
- **`MANAGE_EXTERNAL_STORAGE` by default.** The default storage path is
  SAF; all-files access is a developer opt-in.

## PR checklist

- [ ] `./patches/apply.sh third_party/rpcs3` runs cleanly (exit 0).
- [ ] `cmake -S . -B build-android -G Ninja ...` configures without errors.
- [ ] The change does not modify any file under `third_party/rpcs3/`
      except via the patch series.
- [ ] No new TODO/FIXME in core paths (TODOs in UI scaffolding are OK if
      tagged).
- [ ] If the change adds a new device profile, the profile is **derived
      from detected features**, not from a device-name string match.
- [ ] If the change touches the JNI glue, the corresponding `extern "C"`
      entry point is declared in `android/src/pocos3-android.h` and the
      patch that adds it is in `patches/`.
- [ ] Build locally on a real device or emulator before submitting.

## Commit message format

```
<area>: <imperative subject, lowercase, ≤72 chars>

<body, wrapped at 72>

<footer: BREAKING CHANGE:, Fixes #x, etc.>
```

`<area>` is one of: `android`, `cmake`, `docs`, `patches`, `platform`,
`ui`, `ci`, `glue`. Use `core:` only for changes to `CMakeLists.txt` at
the repo root.

## Licence

By contributing, you agree your contributions are licensed under the
GPL-2.0-only, the same licence as the rest of PocoS3 and as upstream
RPCS3.
