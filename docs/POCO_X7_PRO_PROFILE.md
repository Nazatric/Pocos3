# POCO X7 Pro Device Profile

This document records the **detected** hardware characteristics of the POCO
X7 Pro and the runtime profile that `DeviceProfile` derives from those
characteristics. It is the source of truth for the default settings PocoS3
applies when this device is detected.

## Important: this is a detection, not a name match

PocoS3 does **not** match `"POCO X7 Pro"` against `Build.MODEL`. Phone
variants, region-specific SKUs, and carrier rebrands change the model string.
Instead, PocoS3 detects the **hardware signature**:

1. CPU topology from `/sys/devices/system/cpu/cpu*/topology/`.
2. GPU vendor + device ID via Vulkan's
   `VK_KHR_physical_device_driver_properties` extension.
3. Total RAM via `ActivityManager.MemoryInfo`.
4. Display refresh rate via `Display.getRefreshRate()` and
   `WindowManager.getCurrentWindowMetrics()`.

If those four match the POCO X7 Pro signature, the
`PocoX7ProProfile` is selected. Otherwise a generic profile is used.

## POCO X7 Pro hardware signature

| Field                   | Detected value                                       | Source |
|-------------------------|-------------------------------------------------------|--------|
| `Build.MANUFACTURER`    | `Xiaomi`                                              | informational only; not used for selection |
| `Build.MODEL`           | `23090PF98G` / `23090PG38G` / region variants         | informational only |
| `Build.BOARD`           | `mt6899` (Dimensity 8400-Ultra platform tag)          | used as a *hint* only |
| SoC family              | `MediaTek Dimensity 8400-Ultra`                       | inferred from `/proc/cpuinfo` `Hardware:` line + CPU topology |
| CPU topology            | 4× "big" (Cortex-A725-class, 3.0 GHz peak) + 4× "LITTLE" (Cortex-A520-class, 2.0 GHz peak) | `/sys/devices/system/cpu/cpu*/topology/physical_package_id` + `/sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq` |
| ARM ISA features        | ARMv9.2-A, NEON v2, FP16, dot-prod, SVE2, BF16, I8MM  | `getauxval(AT_HWCAP)` + `AT_HWCAP2` |
| GPU vendor              | `ARM` (`vkGetPhysicalDeviceProperties2.vendorID == 0x13B5`) | Vulkan probe |
| GPU device ID           | Mali-G720 (Immortalis-class)                          | `vkGetPhysicalDeviceProperties2.deviceID` + driver name match |
| GPU driver name         | `Mali` (`VK_KHR_physical_device_driver_properties.name` starts with `Mali-`) | Vulkan probe |
| Vulkan API version      | `1.3.x` minimum, `1.4.x` on current driver            | `vkGetPhysicalDeviceProperties.apiVersion` |
| Total RAM               | ~12 GB (12,4XX,XXX,XXX bytes)                         | `ActivityManager.MemoryInfo` |
| Display resolution      | 2712×1220 (≈1.5K), 480 DPI                            | `WindowManager` + `DisplayMetrics` |
| Display refresh rate    | 120 Hz (variable; supports 60 / 90 / 120)             | `Display.getRefreshRate()` + `Display.getSupportedRefreshRates()` |

## Default profile values

When the signature above matches, `PocoX7ProProfile` produces these defaults:

| Setting                          | Default    | Rationale                                                   |
|----------------------------------|------------|-------------------------------------------------------------|
| `renderer`                       | `VULKAN`   | Mali-G720's Vulkan driver is its primary path; GL is for fallback only. |
| `vulkanCapabilityTier`            | `MALI_OPTIMIZED` | Mali-G720 exposes timeline semaphores, sync2, dynamic rendering, descriptor indexing, and pipeline libraries. |
| `resolutionScale`                | `1.0` (native PS3 → 720p) | The POCO's display is 1.5K, but PS3 content is natively 720p. Upscaling beyond that hurts Mali-G720 bandwidth. |
| `framePaceMode`                  | `ADAPTIVE_VSYNC` | Match PS3's 30/60 Hz timing; present on 120 Hz panel via `VK_PRESENT_MODE_FIFO_RELAXED_KHR` to avoid tearing. |
| `maxFrameRate`                   | `60`       | PS3's frame timing cap. `120` mode is opt-in for games that support it; default is `60` to preserve accuracy. |
| `shaderCacheMode`                | `PERSISTENT` | Shader/pipeline cache must survive restarts; stored in `getCacheDir()/pocos3/shaders/`. |
| `ppuDecoder`                     | `LLVM_RECOMPILER` | Dimensity A725 big cores can handle LLVM JIT; the SPU fast-path benefits. |
| `spuDecoder`                      | `LLVM_RECOMPILER` | Same rationale; uses NEON v2. |
| `spuBlockMaxSize`                | `MEDIUM`   | Larger blocks help throughput but blow icache on A520 LITTLE cores. |
| `numPPUThreads`                  | `2`        | PS3 has 2 hardware PPU threads; emulator default is right. |
| `numSPUThreads`                  | `6`        | Default 4 + 2 for SPU overflow on the 4 big cores. |
| `cpuAffinity`                    | `BIG_CORES_ONLY` | Pin emulator threads to the 4 big cores; let background tasks use the LITTLE cores. |
| `threadAffinityMode`             | `HINT`     | Hard affinity breaks thermal throttling recovery; soft hint is correct. |
| `audioBackend`                   | `OBOE_AAUDIO` | AAudio has lower latency than OpenSL on Android 13+. |
| `audioSampleRate`                | `48000`    | PS3 native rate; Android HAL expects this. |
| `audioBufferSize`                | `256`      | ~5 ms at 48 kHz; balance between latency and underruns. |
| `touchOverlayOpacity`            | `0.55`     | Visible but doesn't dominate gameplay. |
| `touchOverlayScale`              | `1.0`      | Default; user-adjustable. |
| `thermalPolicy`                  | `SUSTAINED` | Cap background-compile threads at 2 when `PowerManager.getThermalStatusHeadroom()` < 0.3; this prevents the 5-minute throttle cliff. |
| `performancePreset`               | `BALANCED` | "Safe" out of the box; user can opt to "Performance" or "Extreme" in settings. |

## Detection code path

```
PocoS3Application.onCreate()
  └─ DeviceProfile.detect(applicationContext)
       ├─ CpuTopology.probe()          // reads /sys/devices/system/cpu/
       ├─ VulkanProbe.probe()           // creates a VkInstance, enumerates VkPhysicalDevices
       ├─ MemoryProbe.probe()           // ActivityManager.MemoryInfo
       └─ DisplayProbe.probe()          // Display + WindowManager
            ↓
       matches PocoX7ProProfile.signature() → true
            ↓
       selectedProfile = PocoX7ProProfile()
```

The result is passed to the JNI glue via `PocoS3Api.setProfile(json)` at
`dlopen` time, before any game boots. The C++ side deserialises the JSON
into the upstream RPCS3 `cfg` tree so the emulator picks up the defaults.

## Thermal behaviour of the Dimensity 8400-Ultra

The Dimensity 8400-Ultra is a mid-premium SoC. Sustained CPU+GPU emulation
load will heat it past the second thermal threshold within ~10 minutes
without intervention. `ThermalPolicy` mitigates this by:

1. Reading `PowerManager.getThermalStatusHeadroom()` every 30 seconds during
   emulation.
2. When headroom drops below 0.3 (i.e. ~30% of the way to `THERMAL_STATUS_CRITICAL`),
   the policy lowers the SPU LLVM recompiler thread count by 1 and reduces
   background shader-compile concurrency to 2.
3. When headroom drops below 0.1, the policy pauses background shader
   compilation entirely. Foreground emulation continues.
4. The policy **never** lowers the foreground frame rate or skips simulation
   frames. Doing so would corrupt PS3 timing.

This is conservative on purpose. The Mali-G720 driver, when thermally
stressed, will start returning `VK_ERROR_DEVICE_LOST` on queue submission;
staying below the second threshold avoids that.

## Known driver issues on Mali-G720

The stock Mali-G720 driver on POCO X7 Pro firmware (as of Android 15
initial release) has the following observed issues. PocoS3's
`VulkanProbe` flags them at startup and adjusts the capability tier
down accordingly:

| Issue | Mitigation in PocoS3 |
|---|---|
| `vkCmdBeginRendering` with `VK_ATTACHMENT_LOAD_OP_CLEAR` on a transient attachment that was previously `STORE` can cause a GPU hang on certain render-pass sequences. | `VulkanProbe` reports `maliClearLoadRace = true`; the renderer side falls back to explicit `vkCmdClearAttachments`. |
| `VK_EXT_extended_dynamic_state` `vkCmdSetPrimitiveTopologyEXT` is occasionally ignored on pipelines built from a pipeline library. | `VulkanProbe` reports `maliDynamicStateLibraryBug = true`; the renderer uses monolithic pipelines when `pipeline_libraries` are stacked with dynamic state. |
| `VK_EXT_descriptor_indexing` `updateAfterBind` descriptors can leak across `vkResetDescriptorPool` calls if the pool was used in a still-in-flight command buffer. | `VulkanProbe` reports `maliDescriptorPoolResetLeak = true`; descriptor pools are not reset until the GPU is idle. |

These flags are **detection, not bypass**. They reduce the chosen capability
tier to `OPTIMIZED` (not `MALI_OPTIMIZED`) on affected driver versions, and
the renderer picks the safer code path. When the driver is fixed upstream,
detection picks the faster path automatically without a PocoS3 update.

## Fallback for non-POCO devices

If the signature does not match `PocoX7ProProfile`, PocoS3 picks one of:

| Detected GPU | Profile | Notes |
|---|---|---|
| Mali-G720 family | `PocoX7ProProfile` (signature match) | Default for the primary target. |
| Mali-G715 / G610 / G77 / G78 | `GenericMaliProfile` | Same tier selection, more conservative defaults. |
| Adreno 7xx / 740+ | `GenericAdrenoProfile` | Use `adrenotools` driver override if present; otherwise stock driver. |
| Adreno 6xx | `GenericAdrenoProfile` with `capabilityTier = OPTIMIZED` | Older drivers have more issues. |
| Xclipse / Mali-G52 / Mali-G57 | `GenericFallbackProfile` | Baseline tier; expect crashes on heavy games. |
| Other | `GenericFallbackProfile` | Baseline; everything enabled defensively. |

The fallback is *capability-driven*, not name-driven. A device that reports
the same Vulkan extensions as a Mali-G720 gets the same fast path.
