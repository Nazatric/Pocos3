# =============================================================================
# PocoS3 Android native core build.
#
# Builds libpocos3-core.so: the upstream RPCS3 core (rpcs3_emu) + PocoS3's
# entry points (src/pocos3-core.cpp) + PocoS3's Android Input handlers +
# the Storage Access Framework bridge.
#
# The resulting .so is dlopen()ed by libpocos3-glue.so (built by Gradle's
# externalNativeBuild from app/src/main/cpp/CMakeLists.txt). The .so
# exports `_pocos3_*` extern "C" symbols that the glue resolves via
# dlsym() at runtime.
#
# Modeled on ARMSX3's android/CMakeLists.txt. The list of input handler
# sources, the input-source subset selection, and the target_link_libraries
# chain mirror ARMSX3 because that is the right set for building the
# upstream core against Android; the CMake target names, output name, and
# comments are PocoS3's own.
# =============================================================================

# IMPORTANT: this file is included from the repo root CMakeLists.txt, so
# CMAKE_SOURCE_DIR is the repo root (third_party/rpcs3 is at
# ${CMAKE_SOURCE_DIR}/third_party/rpcs3). This matches how ARMSX3 lays
# things out - see the long comment in their android/CMakeLists.txt about
# why android/ is NOT the top-level project.

# -----------------------------------------------------------------------------
# PocoS3-specific source files.
# -----------------------------------------------------------------------------
set(POCOS3_CORE_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/pocos3-core.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/src/pocos3_saf.cpp
)

# -----------------------------------------------------------------------------
# Input handler subset.
#
# rpcs3/Input/ is not a library upstream - its sources are compiled into
# the rpcs3 executable. On Android nothing builds them, yet rpcs3_emu
# references pad_thread and ps_move_tracker (cellPad's LDD pad API,
# cellGem's tracker), and the link fails with undefined symbols.
#
# Build the platform-safe subset here. Deliberately EXCLUDED:
#   evdev_/xinput_/mm_/sdl_*  - Linux/Windows/SDL backends, all disabled
#   basic_keyboard_/basic_mouse_/
#   keyboard_pad_/raw_mouse_/
#   gui_pad_thread/mouse_gyro_ - Qt-dependent (rpcs3qt is not built here)
#   camera_video_sink          - Qt multimedia
# -----------------------------------------------------------------------------
set(POCOS3_INPUT_SOURCES
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/rpcs3/Input/pad_thread.cpp
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/rpcs3/Input/product_info.cpp
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/rpcs3/Input/hid_pad_handler.cpp
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/rpcs3/Input/ds3_pad_handler.cpp
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/rpcs3/Input/ds4_pad_handler.cpp
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/rpcs3/Input/dualsense_pad_handler.cpp
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/rpcs3/Input/skateboard_pad_handler.cpp
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/rpcs3/Input/ps_move_handler.cpp
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/rpcs3/Input/ps_move_config.cpp
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/rpcs3/Input/ps_move_calibration.cpp
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/rpcs3/Input/ps_move_tracker.cpp
    # pad_thread references mouse_gyro_handler directly.
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/rpcs3/Input/mouse_gyro_handler.cpp
    # PocoS3: on-screen touch controls (NEW file - PocoS3's own).
    ${CMAKE_SOURCE_DIR}/android/src/virtual_pad_handler.cpp
    # PocoS3: cellKb fed from Android IME / physical keyboard (NEW file).
    ${CMAKE_SOURCE_DIR}/android/src/virtual_keyboard_handler.cpp
)

# rpcs3::get_version() and friends. Like Input/, this is compiled into the
# rpcs3 executable upstream, not into rpcs3_emu, so Android never gets it.
set(POCOS3_VERSION_SOURCES
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/rpcs3/rpcs3_version.cpp
)

# -----------------------------------------------------------------------------
# The core library target.
#
# `rpcs3_emu` is defined by third_party/rpcs3/rpcs3/CMakeLists.txt (already
# added via add_subdirectory at the repo root). We create a NEW shared
# library that includes our glue + the input sources + the version sources,
# and links against rpcs3_emu + LLVM + Vulkan + everything else.
# -----------------------------------------------------------------------------
add_library(pocos3-core SHARED
    ${POCOS3_CORE_SOURCES}
    ${POCOS3_INPUT_SOURCES}
    ${POCOS3_VERSION_SOURCES}
)

target_compile_features(pocos3-core PRIVATE cxx_std_23)

# Output name: libpocos3-core.so. Setting OUTPUT_NAME (rather than renaming
# the file after the fact) also fixes the SONAME, so the file name and the
# name the dynamic linker registers it under agree.
set_target_properties(pocos3-core PROPERTIES
    OUTPUT_NAME pocos3-core
    POSITION_INDEPENDENT_CODE ON
)

# Include paths so our files can find RPCS3 headers.
target_include_directories(pocos3-core PRIVATE
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/rpcs3
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/3rdparty
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/3rdparty/hidapi/hidapi/hidapi
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/3rdparty/hidapi/hidapi/libusb
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/3rdparty/libusb/libusb/libusb
    ${CMAKE_SOURCE_DIR}/third_party/rpcs3/3rdparty/yaml-cpp/yaml-cpp/include
)

# librashader (optional upscaler) - header-only loader, no link required.
if(EXISTS ${CMAKE_SOURCE_DIR}/3rdparty/librashader/include)
    target_include_directories(pocos3-core PRIVATE
        ${CMAKE_SOURCE_DIR}/3rdparty/librashader/include
    )
endif()

# Link against the upstream RPCS3 core object library + Android framework.
target_link_libraries(pocos3-core PRIVATE
    rpcs3_emu
    log
    android
    EGL
    GLESv3
    # nlohmann/json (fetched via FetchContent in root CMakeLists.txt)
    nlohmann_json::nlohmann_json
)

# Hide every symbol except our `_pocos3_*` entry points. Keeps the .so
# small and avoids symbol clashes with libpocos3-glue.so at dlopen time.
target_link_options(pocos3-core PRIVATE
    -Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/pocos3-core.version
    -Wl,--gc-sections
)

# Install: the .so lands where the Gradle module will package it.
install(TARGETS pocos3-core
    LIBRARY DESTINATION ${CMAKE_SOURCE_DIR}/android/pocos3-ui/app/src/main/jniLibs/${ANDROID_ABI}
)
