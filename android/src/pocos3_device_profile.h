// =============================================================================
// PocoS3 device-specific optimizations.
//
// Compiled INTO libpocos3-core.so alongside the upstream RPCS3 source.
// Reads the device profile JSON pushed via _pocos3_setProfile and applies
// it to upstream g_cfg defaults. Reads the Vulkan capability JSON pushed
// via _pocos3_setCapabilities and sets driver-bug flags. Reads the thermal
// headroom pushed via _pocos3_setThermals and scales LLVM thread count.
//
// All guarded by #ifdef __ANDROID__ so desktop builds are unaffected.
// =============================================================================

#pragma once

#ifdef __ANDROID__

#include "Emu/system_config.h"
#include "Utilities/Config.h"

#include <atomic>
#include <string>
#include <string_view>
#include <nlohmann/json.hpp>

namespace com::pocos3::device {

struct DriverBugFlags {
    bool mali_clear_load_race = false;
    bool mali_dynamic_state_library_bug = false;
    bool mali_descriptor_pool_reset_leak = false;
    bool adreno_timeline_wraparound = false;
    bool adreno_pipeline_cache_corruption = false;
};

struct DeviceProfileState {
    std::string profile_name = "GENERIC_FALLBACK";
    std::string soc_info;
    std::string vulkan_tier = "BASELINE";
    int num_spu_threads_override = 0;
    bool use_armv9_features = true;
    bool mali_optimizations_enabled = false;
    bool thermal_aware_scheduling = true;
    DriverBugFlags driver_bug_flags;
    int num_big_cores = 4;
    int num_little_cores = 4;
};

inline DeviceProfileState g_profile;
inline std::atomic<int> g_thermal_headroom_percent{100};

inline void apply_profile_to_cfg(std::string_view json) {
    try {
        auto p = nlohmann::json::parse(json);
        if (p.contains("profileName")) g_profile.profile_name = p["profileName"];
        if (p.contains("socInfo"))    g_profile.soc_info    = p["socInfo"];
        if (p.contains("vulkanCapabilityTier"))
            g_profile.vulkan_tier = p["vulkanCapabilityTier"];
        if (p.contains("numSPUThreads"))
            g_profile.num_spu_threads_override = p["numSPUThreads"];
        if (p.contains("useArmv9Features"))
            g_profile.use_armv9_features = p["useArmv9Features"];
        if (p.contains("maliOptimizationsEnabled"))
            g_profile.mali_optimizations_enabled = p["maliOptimizationsEnabled"];
        if (p.contains("thermalAwareScheduling"))
            g_profile.thermal_aware_scheduling = p["thermalAwareScheduling"];

        if (g_profile.num_spu_threads_override > 0) {
            g_cfg.core.preferred_spu_threads.set(
                std::to_string(g_profile.num_spu_threads_override));
        }
    } catch (const std::exception&) {
        // Bad JSON; leave g_cfg defaults alone.
    }
}

inline void apply_capabilities_to_driver_flags(std::string_view json) {
    try {
        auto c = nlohmann::json::parse(json);
        if (c.contains("driverName")) {
            const std::string driver = c["driverName"];
            if (driver.rfind("Mali-G7", 0) == 0 || driver.rfind("Mali-Immortalis", 0) == 0) {
                g_profile.driver_bug_flags.mali_clear_load_race = true;
                g_profile.driver_bug_flags.mali_dynamic_state_library_bug = true;
                g_profile.driver_bug_flags.mali_descriptor_pool_reset_leak = true;
                g_profile.mali_optimizations_enabled = true;
            } else if (driver.rfind("Adreno", 0) == 0) {
                g_profile.driver_bug_flags.adreno_timeline_wraparound = true;
                g_profile.driver_bug_flags.adreno_pipeline_cache_corruption = true;
            }
        }
        if (c.contains("knownBugs")) {
            for (const auto& bug : c["knownBugs"]) {
                const std::string s = bug;
                if (s == "maliClearLoadRace")
                    g_profile.driver_bug_flags.mali_clear_load_race = true;
                else if (s == "maliDynamicStateLibraryBug")
                    g_profile.driver_bug_flags.mali_dynamic_state_library_bug = true;
                else if (s == "maliDescriptorPoolResetLeak")
                    g_profile.driver_bug_flags.mali_descriptor_pool_reset_leak = true;
                else if (s == "adrenoTimelineWraparound")
                    g_profile.driver_bug_flags.adreno_timeline_wraparound = true;
                else if (s == "adrenoPipelineCacheCorruption")
                    g_profile.driver_bug_flags.adreno_pipeline_cache_corruption = true;
            }
        }
    } catch (const std::exception&) {
        // Bad JSON; leave defaults alone.
    }
}

inline void apply_thermal_update(float headroom) {
    int percent = static_cast<int>(headroom * 100.0f);
    g_thermal_headroom_percent.store(percent, std::memory_order_relaxed);

    if (!g_profile.thermal_aware_scheduling) return;

    int llvm_threads = 4;
    if (percent < 50) llvm_threads = 2;
    if (percent < 30) llvm_threads = 1;
    if (percent < 10) llvm_threads = 0;

    g_cfg.core.llvm_threads.set(std::to_string(llvm_threads));
}

}  // namespace com::pocos3::device

#endif  // __ANDROID__
