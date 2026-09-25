// =============================================================================
// PocoS3 virtual keyboard handler.
//
// Feeds Android KeyEvent (from a physical keyboard or the IME) into
// cellKb so that PS3 games that use cellKb for text input work on
// Android.
//
// Modeled on ARMSX3's rpcs3/Input/virtual_keyboard_handler.cpp.
// =============================================================================

#pragma once

#include <atomic>
#include <mutex>

namespace pad {

// Called from _pocos3_keyboardKey (JNI side). Returns true if the event
// was processed; false if the keycode was not mapped.
bool virtual_keyboard_event(int android_key_code, int unicode,
                            bool pressed, bool repeat);

}  // namespace pad
