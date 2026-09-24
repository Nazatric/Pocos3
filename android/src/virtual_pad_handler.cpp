// =============================================================================
// PocoS3 virtual pad handler implementation.
//
// Stores overlay state per port and exposes it to the upstream pad_thread.
// =============================================================================

#include "virtual_pad_handler.h"

namespace pad {

namespace {
constexpr int MAX_PORTS = 7;  // CELL_PAD_MAX_PORT_NUM
OverlayPadState g_states[MAX_PORTS];

std::mutex g_device_class_mutex;
std::vector<int> g_device_classes;
}  // namespace

OverlayPadState& overlay_state(int port) {
    if (port < 0 || port >= MAX_PORTS) port = 0;
    return g_states[port];
}

bool overlay_pad_data(int port, int digital1, int digital2,
                      int left_stick_x, int left_stick_y,
                      int right_stick_x, int right_stick_y) {
    auto& s = overlay_state(port);
    s.digital1.store(digital1, std::memory_order_relaxed);
    s.digital2.store(digital2, std::memory_order_relaxed);
    s.left_stick_x.store(left_stick_x, std::memory_order_relaxed);
    s.left_stick_y.store(left_stick_y, std::memory_order_relaxed);
    s.right_stick_x.store(right_stick_x, std::memory_order_relaxed);
    s.right_stick_y.store(right_stick_y, std::memory_order_relaxed);
    return true;
}

bool overlay_pad_pressure(int port, const int* values, int count) {
    if (!values || count <= 0) return false;
    auto& s = overlay_state(port);
    int n = std::min(count, 16);
    for (int i = 0; i < n; ++i) {
        s.pressure[i].store(values[i], std::memory_order_relaxed);
    }
    return true;
}

void set_pad_sensor(int port, int x, int y, int z, int g) {
    auto& s = overlay_state(port);
    s.sensor_x.store(x, std::memory_order_relaxed);
    s.sensor_y.store(y, std::memory_order_relaxed);
    s.sensor_z.store(z, std::memory_order_relaxed);
    s.sensor_g.store(g, std::memory_order_relaxed);
}

int get_pad_rumble(int port) {
    // Read the rumble state set by cellPad::SetRumble in the core.
    // Stubbed for now: real implementation needs to bridge from
    // CellPadData to here. Returns the rumble amplitude (0-255) for the
    // large motor on the given port.
    (void)port;
    return 0;
}

void set_pad_device_classes(const int* classes, int count) {
    std::lock_guard lock(g_device_class_mutex);
    g_device_classes.clear();
    for (int i = 0; i < count; ++i) g_device_classes.push_back(classes[i]);
}

bool usb_device_event(int fd, int vendor_id, int product_id, int event) {
    // Forward USB device attach/detach to the HID pad handlers (ds3/ds4/dualsense).
    // Stubbed: real implementation calls hid_pad_handler::on_usb_device_event.
    (void)fd; (void)vendor_id; (void)product_id; (void)event;
    return false;
}

}  // namespace pad
