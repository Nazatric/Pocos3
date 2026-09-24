// =============================================================================
// PocoS3 virtual pad handler.
//
// Provides the on-screen touch overlay with a pad handler that the upstream
// RPCS3 pad_thread can poll. The Kotlin TouchOverlay composes touch events
// into pad state via PocoS3Core.nativeOverlayPadData; the JNI glue forwards
// to _pocos3_overlayPadData; this handler applies it to the cellPad buffer.
//
// Modeled on ARMSX3's rpcs3/Input/virtual_pad_handler.cpp. The function
// signatures (overlay_pad_data, overlay_pad_pressure, set_pad_sensor,
// get_pad_rumble, set_pad_device_classes, usb_device_event) match the
// upstream pad_thread::pad_handler interface; the implementation is
// PocoS3's own.
// =============================================================================

#pragma once

#include "Emu/Io/pad_thread.h"
#include "Emu/Cell/Modules/cellPad.h"

#include <atomic>
#include <mutex>

namespace pad {

// Overlay pad state from the JNI side. Updated atomically; read by pad_thread.
struct OverlayPadState {
    std::atomic<int> digital1{0};
    std::atomic<int> digital2{0};
    std::atomic<int> left_stick_x{0};
    std::atomic<int> left_stick_y{0};
    std::atomic<int> right_stick_x{0};
    std::atomic<int> right_stick_y{0};
    std::atomic<int> pressure[16]{};  // CELL_PAD_MAX_CODES
    std::atomic<int> sensor_x{0};
    std::atomic<int> sensor_y{0};
    std::atomic<int> sensor_z{0};
    std::atomic<int> sensor_g{0};
};

OverlayPadState& overlay_state(int port);

// Called from _pocos3_overlayPadData (JNI side).
bool overlay_pad_data(int port, int digital1, int digital2,
                      int left_stick_x, int left_stick_y,
                      int right_stick_x, int right_stick_y);

bool overlay_pad_pressure(int port, const int* values, int count);

void set_pad_sensor(int port, int x, int y, int z, int g);

int get_pad_rumble(int port);

void set_pad_device_classes(const int* classes, int count);

bool usb_device_event(int fd, int vendor_id, int product_id, int event);

}  // namespace pad
