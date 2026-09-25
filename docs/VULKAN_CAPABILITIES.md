# Vulkan Capability System

PocoS3 probes Vulkan at runtime and assigns the device to one of four
capability tiers. The tier controls which Vulkan features the renderer is
allowed to use, which present mode is preferred, and how aggressively the
shader compiler can pre-compile pipelines in the background.

## Capability tiers

### BASELINE

Requirements:
- Vulkan `apiVersion >= 1.1.0`
- `VK_KHR_swapchain`
- `VK_KHR_get_physical_device_properties2`

Behaviour: monolithic pipelines, no descriptor indexing, no dynamic state,
`VK_PRESENT_MODE_FIFO_KHR` only. Shader compilation is synchronous at draw
call time. This tier will be **painfully slow** but correct.

### OPTIMIZED

Requirements (BASELINE + all of):
- Vulkan `apiVersion >= 1.2.0`
- `VK_KHR_dynamic_rendering` (or core in 1.3)
- `VK_KHR_synchronization2` (or core in 1.3)
- `VK_EXT_extended_dynamic_state`
- `VK_KHR_create_renderpass2`
- `VK_EXT_scalar_block_layout`
- `VK_KHR_driver_properties` (or core in 1.2)

Behaviour: dynamic rendering, sync2, dynamic viewport/scissor/blend, scalar
block layout. Shader compilation still synchronous at first-use but cached
on disk. This is what most 2022+ Mali devices land on.

### ADVANCED

Requirements (OPTIMIZED + all of):
- Vulkan `apiVersion >= 1.3.0`
- `VK_KHR_timeline_semaphore` (or core in 1.2)
- `VK_EXT_descriptor_indexing` (or core in 1.2)
- `VK_KHR_pipeline_library` (or core in 1.3)
- `VK_EXT_conservative_rasterization`
- `VK_KHR_shader_non_semantic_info` (or core in 1.3)

Behaviour: descriptor indexing (update-after-bind), pipeline libraries for
shader-stage reuse, timeline semaphores for host-GPU sync, conservative
raster for cell-shading-style fast paths. Background shader pre-compile with
priority queue.

### MALI_OPTIMIZED

Requirements (ADVANCED + all of):
- GPU vendorID == `0x13B5` (ARM)
- GPU driver name matches `^Mali-`
- `VK_EXT_transform_feedback` (for non-attached stream-out)
- `VK_EXT_memory_budget` (for cache eviction)
- `VK_EXT_external_memory_host` (for zero-copy staging buffers when available)
- `VK_GOOGLE_display_timing` or `VK_EXT_present_mode_fifo_latest` (for
  per-frame present timing feedback)

Behaviour: everything in ADVANCED, plus Mali-specific tuning:
- Render-pass structure optimized for tile-based deferred rendering (TBDR).
  Load/store operations minimized; transient attachments preferred for
  intermediate render targets.
- Pipeline libraries used aggressively to deduplicate shader stages across
  draw calls with the same vertex shader.
- Persistent mapped staging buffers for vertex/index/texture uploads.
- Per-frame present timing feedback used to pace frames against the display.

If any of the MALI_OPTIMIZED-only extensions are missing on a Mali device,
the device falls back to ADVANCED. If `maliClearLoadRace` or
`maliDynamicStateLibraryBug` is detected (see device profile), the device
also falls back to ADVANCED with the Mali-specific workarounds active.

## Detection algorithm (pseudocode)

```kotlin
fun probe(context: Context): VulkanCapabilityReport {
    val instance = createInstance(minApi = VkVersion.V1_3)
    val physical = pickPhysicalDevice(instance)
    val props = vkGetPhysicalDeviceProperties2(physical,
        chain = listOf(
            VkPhysicalDeviceDriverPropertiesKHR(),
            VkPhysicalDeviceIDPropertiesKHR(),
            VkPhysicalDeviceMaintenancePropertiesKHR()
        )
    )
    val extensions = vkEnumerateDeviceExtensionProperties(physical)
    val features = vkGetPhysicalDeviceFeatures2(physical,
        chain = listOf(
            VkPhysicalDeviceVulkan12Features(),
            VkPhysicalDeviceVulkan13Features(),
            VkPhysicalDeviceDescriptorIndexingFeaturesEXT(),
            VkPhysicalDeviceDynamicRenderingFeaturesKHR(),
            VkPhysicalDeviceSynchronization2FeaturesKHR(),
            VkPhysicalDevicePipelineLibraryGroupFeaturesKHR()
        )
    )

    val tier = when {
        isMaliG720Family(props, extensions, features) &&
            hasAllAdvancedRequirements(extensions, features) &&
            hasMaliSpecificExtensions(extensions) &&
            !hasMaliKnownDriverBugs(props) -> Tier.MALI_OPTIMIZED
        hasAllAdvancedRequirements(extensions, features) -> Tier.ADVANCED
        hasAllOptimizedRequirements(extensions, features) -> Tier.OPTIMIZED
        else -> Tier.BASELINE
    }

    return VulkanCapabilityReport(
        tier = tier,
        vendorId = props.vendorID,
        deviceName = props.deviceName,
        driverName = props.driverProperties.driverName,
        driverVersion = props.driverProperties.driverInfo,
        apiVersion = props.apiVersion,
        extensions = extensions.map { it.extensionName },
        knownBugs = detectKnownDriverBugs(props, extensions),
        maxBoundDescriptorSets = features.maxBoundDescriptorSets,
        maxUpdateAfterBindDescriptors = features.maxUpdateAfterBindDescriptors,
        timelineSemaphores = features.timelineSemaphore,
        dynamicRendering = features.dynamicRendering,
        synchronization2 = features.synchronization2,
        descriptorIndexing = features.descriptorIndexing,
        pipelineLibrary = features.pipelineLibrary,
        memoryBudget = "VK_EXT_memory_budget" in extensions,
        presentTiming = "VK_GOOGLE_display_timing" in extensions
    )
}
```

## What the tier controls in the renderer

| Renderer behaviour | BASELINE | OPTIMIZED | ADVANCED | MALI_OPTIMIZED |
|---|---|---|---|---|
| Render pass type | `VkRenderPass` (legacy) | `VkRenderPass2` | Dynamic rendering | Dynamic rendering (TBDR-tuned) |
| Synchronization | Old-style barriers | `VkRenderPass2` deps | Sync2 barriers | Sync2 + sub-tile self-deps |
| Descriptor sets | Static, per-draw | Static, per-draw | Update-after-bind, bind-once | Update-after-bind + descriptor pool size hints |
| Pipeline creation | Monolithic | Monolithic | Pipeline libraries, dedup | Pipeline libraries, aggressively cached |
| Staging buffers | Per-frame alloc | Pool, recycled | Persistent mapped | Persistent mapped + zero-copy where supported |
| Present mode | `FIFO_KHR` | `FIFO_KHR` | `FIFO_RELAXED_KHR` (or `MAILBOX` on >60 Hz panels) | `FIFO_RELAXED_KHR` + Google display timing |
| Frame pacing | VSync-cap | VSync-cap | Adaptive VSync | Adaptive VSync + present-time feedback |
| Background shader compile | Off | On, low priority | On, normal priority | On, high priority, capacity budget from `memory_budget` |
| Pipeline cache | In-memory only | Disk, per-game | Disk, versioned, per-game | Disk, versioned, per-game + per-driver-version |
| Vertex attribute binding | Per-draw `vkCmdBindVertexBuffers` | Dynamic state | Dynamic state + binding-less for static meshes | Dynamic state + binding-less + persistent vertex buffer pool |

## Driver-bug flag registry

`VulkanProbe.detectKnownDriverBugs()` returns a set of flags. Each flag
has a known workaround in the renderer. When the upstream driver is fixed,
the flag's version check is removed and the workaround stops firing.

| Flag | Trigger | Workaround |
|---|---|---|
| `maliClearLoadRace` | Mali driver `name` starts with `Mali-G720` and `driverInfo` matches `<= r44p0` | Render pass uses explicit `vkCmdClearAttachments` instead of `LOAD_OP_CLEAR` on transient attachments that previously had `STORE`. |
| `maliDynamicStateLibraryBug` | Same driver family; pipeline library + `VK_EXT_extended_dynamic_state` | Pipeline libraries disabled when dynamic state is active; monolithic pipelines built instead. |
| `maliDescriptorPoolResetLeak` | Same driver family; `vkResetDescriptorPool` after GPU work that touched the pool | Descriptor pools are not reset until GPU is idle; pools grow rather than recycle. |
| `adrenoTimelineWraparound` | Adreno `driverName` matches `Adreno.*` and driver build number < 615.x | Timeline semaphore values clamped to 31-bit; `vkWaitSemaphores` wraps instead of monotonic. |
| `adrenoPipelineCacheCorruption` | Adreno 7xx driver build number in known-bad range | Pipeline cache disabled; pipelines recompiled on every boot. |

These are **detection-only** flags. They reduce the chosen tier or activate
known-good fallbacks; they do not bypass correctness. If a flag misfires,
the slow path runs and produces correct (if slower) output.

## What this is NOT

- It is **not** a static lookup table. The result of `VulkanProbe.probe()` is
  recomputed every time the activity is created. A driver update changes
  the result.
- It is **not** a way to bypass correctness. BASELINE is correct on every
  device. Higher tiers are strictly faster, never less accurate.
- It is **not** a way to enable game-specific hacks. The tier is a property
  of the device + driver, not of the game.
