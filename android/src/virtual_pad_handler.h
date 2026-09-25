// =============================================================================
// PocoS3 virtual pad handler.
//
// Provides the on-screen touch overlay with a pad handler that the upstream
// RPCS3 pad_thread can poll. The Kotlin TouchOverlay composes touch events
// into pad state via PocoS3Core.nativeOverlayPadData; the JNI glue forwards
// to _pocos3_overlayPadData in the core .so; that delegates to the
// pocos3_overlay_pad_data function in this file.
// =============================================================================

#pragma once

#include <atomic>
#include <mutex>
#include <vector>

namespace com::pocos3::pad {

// Overlay pad state from the JNI side. Updated atomically; read by pad_thread.
struct OverlayPadState {
    std::atomic<int> digital1{0};
    std::atomic<int> digital2{0};
    std::atomic<int> left_stick_x{0};
    std::atomic<int> left_stick_y{0};
    std::atomic<int> right_stick_x{0};
    std::atomic<int> right_stick_y{0};
    std::atomic<int> pressure[16]{};
    std::atomic<int> sensor_x{0};
    std::atomic<int> sensor_y{0};
    std::atomic<int> sensor_z{0};
    std::atomic<int> sensor_g{0};
};

OverlayPadState& overlay_state(int port);

void set_device_classes(const int* classes, int count);

}  // namespace com::pocos3::pad
