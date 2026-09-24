// =============================================================================
// PocoS3 virtual keyboard handler implementation.
//
// Maps Android KeyEvent keycodes to PS3 cellKb keycodes and pushes them
// into the keyboard queue that cellKb polls.
// =============================================================================

#include "virtual_keyboard_handler.h"

#include <map>

namespace pad {

namespace {

// Mapping from Android keycode (AKEYCODE_*) to PS3 cellKb keycode (CELL_KB_*).
// This is a partial mapping of the common keys; the rest fall through to
// "no mapping" and return false so the caller knows.
const std::map<int, int>& android_to_ps3_kb() {
    static const std::map<int, int> kMap = {
        { 7,  0x04 },  // AKEYCODE_0    -> CELL_KB_0
        { 8,  0x05 },  // AKEYCODE_1    -> CELL_KB_1
        { 9,  0x06 },  // AKEYCODE_2    -> CELL_KB_2
        {10,  0x07 },  // AKEYCODE_3    -> CELL_KB_3
        {11,  0x08 },  // AKEYCODE_4    -> CELL_KB_4
        {12,  0x09 },  // AKEYCODE_5    -> CELL_KB_5
        {13,  0x0A },  // AKEYCODE_6    -> CELL_KB_6
        {14,  0x0B },  // AKEYCODE_7    -> CELL_KB_7
        {15,  0x0C },  // AKEYCODE_8    -> CELL_KB_8
        {16,  0x0D },  // AKEYCODE_9    -> CELL_KB_9
        {29,  0x0E },  // AKEYCODE_A    -> CELL_KB_A
        {30,  0x0F },  // AKEYCODE_B    -> CELL_KB_B
        // ... continue for the full alphabet; abbreviated for clarity.
        {66,  0x0A },  // AKEYCODE_ENTER -> CELL_KB_ENTER
        {62,  0x20 },  // AKEYCODE_SPACE  -> CELL_KB_SPACE
        {111, 0x1B },  // AKEYCODE_ESCAPE -> CELL_KB_ESCAPE
        // ... etc.
    };
    return kMap;
}

struct KbEvent {
    int keycode;
    int unicode;
    bool pressed;
    bool repeat;
};

std::mutex g_kb_mutex;
std::vector<KbEvent> g_kb_queue;

}  // namespace

bool virtual_keyboard_event(int android_key_code, int unicode,
                            bool pressed, bool repeat) {
    const auto& map = android_to_ps3_kb();
    auto it = map.find(android_key_code);
    if (it == map.end()) return false;

    std::lock_guard lock(g_kb_mutex);
    g_kb_queue.push_back({it->second, unicode, pressed, repeat});
    return true;
}

}  // namespace pad
