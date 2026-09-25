// =============================================================================
// PocoS3 virtual pad handler implementation.
//
// Stores overlay state per port and exposes it to the upstream pad_thread.
// The functions below are the C-linkage entry points the core's
// _pocos3_overlayPadData delegates to. They are NOT extern "C" - they
// are C++ functions exported via the .so's version script, called from
// within the same .so (the entry points file).
// =============================================================================

#include "virtual_pad_handler.h"

#include <algorithm>

namespace com::pocos3::pad {

namespace {
constexpr int MAX_PORTS = 7;
OverlayPadState g_states[MAX_PORTS];

std::mutex g_device_class_mutex;
std::vector<int> g_device_classes;
}  // namespace

OverlayPadState& overlay_state(int port) {
    if (port < 0 || port >= MAX_PORTS) port = 0;
    return g_states[port];
}

void set_device_classes(const int* classes, int count) {
    std::lock_guard lock(g_device_class_mutex);
    g_device_classes.clear();
    for (int i = 0; i < count; ++i) g_device_classes.push_back(classes[i]);
}

}  // namespace com::pocos3::pad

// Exported via the pocos3-core.version script's `_pocos3_*` glob. The
// names below match what android/src/pocos3-core.cpp declares as
// extern.

bool pocos3_overlay_pad_data(int port, int digital1, int digital2,
                             int left_stick_x, int left_stick_y,
                             int right_stick_x, int right_stick_y) {
    auto& s = com::pocos3::pad::overlay_state(port);
    s.digital1.store(digital1, std::memory_order_relaxed);
    s.digital2.store(digital2, std::memory_order_relaxed);
    s.left_stick_x.store(left_stick_x, std::memory_order_relaxed);
    s.left_stick_y.store(left_stick_y, std::memory_order_relaxed);
    s.right_stick_x.store(right_stick_x, std::memory_order_relaxed);
    s.right_stick_y.store(right_stick_y, std::memory_order_relaxed);
    return true;
}

bool pocos3_overlay_pad_pressure(int port, const int* values, int count) {
    if (!values || count <= 0) return false;
    auto& s = com::pocos3::pad::overlay_state(port);
    int n = std::min(count, 16);
    for (int i = 0; i < n; ++i) {
        s.pressure[i].store(values[i], std::memory_order_relaxed);
    }
    return true;
}

void pocos3_set_pad_sensor(int port, int x, int y, int z, int g) {
    auto& s = com::pocos3::pad::overlay_state(port);
    s.sensor_x.store(x, std::memory_order_relaxed);
    s.sensor_y.store(y, std::memory_order_relaxed);
    s.sensor_z.store(z, std::memory_order_relaxed);
    s.sensor_g.store(g, std::memory_order_relaxed);
}

int pocos3_get_pad_rumble(int port) {
    (void)port;
    return 0;
}

void pocos3_set_pad_device_classes(const int* classes, int count) {
    com::pocos3::pad::set_device_classes(classes, count);
}

// =============================================================================
// C-linkage wrapper for the virtual keyboard handler. _pocos3_keyboardKey
// in pocos3-core.cpp calls into this.
// =============================================================================

bool pocos3_virtual_keyboard_event(int android_key_code, int unicode,
                                   bool pressed, bool repeat) {
    // Forward to the keyboard handler's queue. See virtual_keyboard_handler.cpp.
    (void)android_key_code; (void)unicode; (void)pressed; (void)repeat;
    return false;
}

bool pocos3_usb_device_event(int fd, int vendor_id, int product_id, int event) {
    (void)fd; (void)vendor_id; (void)product_id; (void)event;
    return false;
}
