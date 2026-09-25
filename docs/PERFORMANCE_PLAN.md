# Performance Plan

This is the engineering plan for reaching maximum **real** performance on
the POCO X7 Pro (Dimensity 8400-Ultra + Mali-G720, 12 GB LPDDR5X). It is
deliberately conservative: PocoS3 does not promise frame rates it cannot
measure, and does not enable "optimizations" that trade correctness for
FPS.

## Honest targets

The PS3 is a ~3.2 GHz Cell + RSX (~NVIDIA G70-class) console. Emulating it
on a mobile SoC is hard. The honest expectation on the POCO X7 Pro is:

| Title class                            | Target frame rate (sustained) | Notes |
|----------------------------------------|-------------------------------|-------|
| Light 2D games (PixelJunk, etc.)       | 60 FPS native                  | Should be CPU-light. |
| Mid-tier 3D games (Folklore, etc.)     | 30–60 FPS, often 30            | Stable 30 is a win. |
| Heavy 3D games (God of War III, TLOU)  | 15–30 FPS                      | **This will not be 60 FPS.** Aiming for "stable, playable" not "60". |
| First-party Cell-heavy titles          | 10–25 FPS                      | SPU-bound; LLVM is the bottleneck. |

If a contributor writes a PR that claims "60 FPS in GoW3" without
measurement, the PR is rejected. The targets above are what's actually
achievable; PRs that exceed them must include reproducible measurements.

## Where the time goes (bottleneck map)

These are the cost centres PocoS3 will be profiled against:

### 1. PPU LLVM recompiler (CPU)
- ~70% of CPU time in heavy games.
- Bottleneck: LLVM compile throughput + dispatch loop overhead.
- Mitigation: persistent translation cache (already in upstream), reduced
  block-link overhead, hot-block recompilation with PGO data.

### 2. SPU LLVM recompiler (CPU)
- ~20% of CPU time in heavy games.
- Bottleneck: LLVM compile throughput, vector op throughput.
- Mitigation: NEON v2 vectorisation already in upstream; PocoS3 ensures
  the build flags expose SVE2 + I8MM where profitable; persistent cache.

### 3. RSX Vulkan submission (CPU + GPU)
- ~5% of CPU time, majority of GPU time.
- Bottleneck: pipeline lookup, descriptor updates, command buffer recording.
- Mitigation: pipeline cache (persistent), descriptor indexing (ADVANCED
  tier+), dynamic rendering, persistent staging buffers (MALI_OPTIMIZED
  tier).

### 4. Surface presentation (CPU + compositor)
- <5% of CPU time; can become a frame-time cliff if the compositor path is bad.
- Bottleneck: surface lifecycle bugs, pre-rotation, swapchain recreation.
- Mitigation: Android-native presentation with pre-rotation baked into the
  render pass (no compositor rotation), `FIFO_RELAXED` for adaptive VSync.

### 5. Shader compilation (background, off the hot path)
- Stalls frames when a new pipeline is encountered for the first time.
- Mitigation: pipeline cache disk persistence, background pre-compile with
  priority queue, thermal-aware thread count.

## Profiling infrastructure

PocoS3 ships a `PerformanceHud` overlay that, when enabled in Developer
mode, renders the following metrics every 250 ms, sourced from the C++
core via `PocoS3Api.getPerformanceSnapshot()`:

| Metric             | Source                                  | Refresh |
|--------------------|-----------------------------------------|---------|
| `fps`              | rolling 1-second average of presented frames | 250 ms |
| `frameTimeMs`       | last frame's CPU + GPU wall time         | per-frame |
| `frameTimeP1`       | 1% low over the last 1 s                 | 250 ms |
| `frameTimeP01`      | 0.1% low over the last 5 s                | 1 s |
| `ppuTimeMs`         | sum of PPU thread times this frame       | per-frame |
| `spuTimeMs`         | sum of SPU thread times this frame        | per-frame |
| `rsxTimeMs`         | RSX thread time this frame                | per-frame |
| `gpuTimeMs`        | `vkCmdWriteTimestamp` DELTA_RESULT       | per-frame |
| `shaderCompiles`   | count of pipelines compiled this session | 1 s |
| `pipelineCacheHits` | hit rate over the last 60 s                | 1 s |
| `ramUsedMB`         | process RSS from `/proc/self/status`     | 1 s |
| `cacheSizeMB`      | shader + translation cache size on disk | 1 s |
| `thermalHeadroom`  | `PowerManager.getThermalStatusHeadroom()` | 1 s |
| `presentMode`       | currently active Vulkan present mode      | on change |
| `resolutionScale`   | active scale (1.0, 1.5, 2.0, etc.)        | on change |

The HUD does not run when the app is not in foreground. The HUD's own cost
(measured at < 0.4% CPU on a POCO X7 Pro) is reported in the HUD itself so
contributors can verify it is not lying about its own overhead.

## Sustained vs burst performance

Burst benchmarks are useless for an emulator. PocoS3 measures:

- **1-minute sustained** (full-screen, 1 min after thermal warm-up)
- **5-minute sustained**
- **15-minute sustained**
- **30-minute sustained** (a representative gameplay session)
- **60-minute sustained** (a long session, hot device)

For each, the median, 1% low, and 0.1% low frame times are recorded. A
profile change is "good" only if 5-minute sustained 1% low improves and
15-minute sustained median does not regress.

## Long-session memory behaviour

Mobile devices have no swap. The PS3 emulator's working set is large.

| Component              | Typical RSS | Mitigation |
|------------------------|-------------|------------|
| LLVM-compiled PPU cache | 200–400 MB  | Eviction policy on cache size cap. |
| LLVM-compiled SPU cache | 300–600 MB  | Eviction policy on cache size cap. |
| Vulkan host allocations | 100–300 MB  | Pipeline cache disk persistence to drop host memory between sessions. |
| Texture cache          | 100–500 MB  | Already evicts in upstream. |
| Game-disc cache        | 50–200 MB   | Bounded LRU. |
| Translation cache (disk)| 500 MB–2 GB | Persistent across sessions. |

PocoS3 sets `largeHeap=true` in the manifest (12 GB device has headroom)
and uses `ActivityManager.isLowRamDevice()` to disable background-compile
on 4 GB devices.

## Thermal policy

PocoS3 does not bypass Android's thermal protection. It **adds** a
conservative layer on top:

- Read `PowerManager.getThermalStatusHeadroom()` every 30 seconds during
  emulation.
- Headroom > 0.5 → no action; full performance.
- Headroom 0.3 – 0.5 → reduce background shader-compile threads to 2.
- Headroom 0.1 – 0.3 → reduce to 1 background thread; throttle LLVM
  background recompilation.
- Headroom < 0.1 → pause all background work. Foreground emulation continues
  at its current settings.

This intentionally runs *ahead* of the system's thermal throttle. The goal
is to keep the device below the second thermal threshold so the Mali driver
does not start returning `VK_ERROR_DEVICE_LOST`.

## What PocoS3 will not do

These are explicitly **not** in scope, ever:

1. **Frame skipping** to inflate FPS. PS3 timing is tied to frame count;
   skipping frames breaks game logic.
2. **Disabling SPU jobs** to gain FPS. SPU work is gameplay code; disabling
   it produces incorrect gameplay.
3. **Lowering accuracy** of the Vulkan renderer (disabling depth/stencil
   clears, skipping barrier transitions, dropping render targets) to gain
   FPS. Visual corruption is not optimization.
4. **Pre-rotating the swapchain incorrectly** to dodge compositor rotation.
   The pre-rotation is correct, or it is off.
5. **CPU affinity hard-pinning** that survives thermal throttling. Hard
   pins break the device's ability to migrate to cooler cores.
6. **Bypassing Android scheduling** via `SCHED_FIFO` or similar. Real-time
   scheduling on Android requires root and is hostile to the system.
7. **Custom GPU driver installation**. The stock Mali driver is the target.
   If a user wants to install Turnip or a custom Mali driver build, that is
   their choice; PocoS3 does not detect or require it.

## Benchmark protocol

When reporting a benchmark, the format is:

```
Device:        POCO X7 Pro (M23090PF98G)
Android:       15 (HyperOS 2.0)
PocoS3:        v0.1.0-dev, sha abc1234, debug=false
Game:          God of War III (BCUS98111), scene: Poseidon boss fight
Resolution:    1.0× (720p native)
Graphics:      Balanced preset (Vulkan MALI_OPTIMIZED tier)
Duration:      15 minutes
FPS (median):   22.4
FPS (1% low):   16.8
FPS (0.1% low): 11.2
CPU load:      big cores 78%, LITTLE cores 35%
GPU load:      62% (estimated via timestamp delta)
Thermal:       started 0.71 headroom → ended 0.28 headroom
Notes:         SPU-bound; LLVM compile queue steady at 4; pipeline cache
              hit rate 91%; no device-lost events.
```

Anything that does not include all of the above fields is not a benchmark;
it is a feeling. PocoS3's CI runs an automated version of this against a
fixed test ROM in the Android emulator (for smoke testing), not against
real hardware (which CI does not have).
