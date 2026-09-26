# =============================================================================
# PocoS3 top-level CMake project.
#
# Source root for `cmake -S . -B build-android -G Ninja ...`. Pulls in:
#   1. third_party/rpcs3/CMakeLists.txt - the upstream RPCS3 core. This
#      creates the `rpcs3_emu` object library (PPU + SPU + RSX + Cell +
#      audio + io + everything else).
#   2. android/CMakeLists.txt            - the PocoS3 Android core build.
#      Creates the `pocos3-core` SHARED library target that compiles
#      android/src/pocos3-core.cpp + virtual pad + virtual keyboard + SAF +
#      links against rpcs3_emu, producing libpocos3-core.so.
#
# Resulting artifacts (in build-android/android/):
#   * libpocos3-core.so  (the upstream RPCS3 core + PocoS3 entry points)
#
# The small JNI glue (libpocos3-glue.so) is built by Gradle's
# externalNativeBuild from android/pocos3-ui/app/src/main/cpp/CMakeLists.txt
# (NOT by this root CMakeLists.txt).
# =============================================================================

cmake_minimum_required(VERSION 3.30)

project(pocos3
    VERSION 0.1.0
    LANGUAGES C CXX
)

# -----------------------------------------------------------------------------
# Sanity: this project only targets Android arm64. The desktop RPCS3 build
# is handled by third_party/rpcs3's own build system.
# -----------------------------------------------------------------------------
if(NOT ANDROID)
    message(FATAL_ERROR
        "PocoS3's top-level CMakeLists.txt is Android-only. "
        "For a desktop RPCS3 build, use third_party/rpcs3's CMake directly.")
endif()

if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "PocoS3 only supports arm64-v8a; got ${ANDROID_ABI}")
endif()

# -----------------------------------------------------------------------------
# Options
# -----------------------------------------------------------------------------
option(POCOS3_PGO              "Enable PGO instrumentation" OFF)
set(POCOS3_PGO_MODE            "generate" CACHE STRING "generate | use")
set(POCOS3_PGO_DATA_FILE       ""        CACHE FILEPATH "Path to .profdata for PGO use")
option(POCOS3_BUILD_GLUE        "Also build the JNI glue (for IDE builds)" OFF)

# -----------------------------------------------------------------------------
# Verify the upstream RPCS3 submodule exists.
# -----------------------------------------------------------------------------
set(RPCS3_SUBMODULE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/rpcs3)
if(NOT EXISTS ${RPCS3_SUBMODULE_DIR}/CMakeLists.txt)
    message(FATAL_ERROR
        "third_party/rpcs3 is not initialized. Run:\n"
        "    git submodule update --init --recursive\n"
        "from the repo root.")
endif()

# -----------------------------------------------------------------------------
# Apply PocoS3 patches to upstream RPCS3 if any are present. The
# patches/apply.sh script is a no-op when no .patch files exist; it only
# does work if a future contributor adds an Android-specific patch that
# cannot be sent upstream.
# -----------------------------------------------------------------------------
find_program(BASH_EXECUTABLE bash)
if(BASH_EXECUTABLE)
    execute_process(
        COMMAND ${BASH_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/patches/apply.sh ${RPCS3_SUBMODULE_DIR}
        WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
        RESULT_VARIABLE PATCH_RESULT
    )
    if(NOT PATCH_RESULT EQUAL 0)
        message(WARNING "PocoS3 patches did not apply cleanly; the build may fail.")
    endif()
endif()

# -----------------------------------------------------------------------------
# Pull in upstream RPCS3. The submodule's CMakeLists.txt will create:
#   * `rpcs3_emu`  - object library with PPU + SPU + RSX + Cell + audio + io
#   * Various 3rdparty targets: LLVM, FFmpeg, glslang, etc.
# Upstream already guards Qt / rpcs3qt / the rpcs3 executable behind
# `if (NOT ANDROID)`, so a core-only Android configuration is half-supported
# by upstream already.
# -----------------------------------------------------------------------------
set(RPCS3_EMU_DIR ${RPCS3_SUBMODULE_DIR})
add_subdirectory(${RPCS3_EMU_DIR} ${CMAKE_BINARY_DIR}/rpcs3)

# Fetch nlohmann/json (single-header) — used by android/src/pocos3-core.cpp.
include(FetchContent)
FetchContent_Declare(
    nlohmann_json
    GIT_REPOSITORY https://github.com/nlohmann/json.git
    GIT_TAG v3.11.3
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(nlohmann_json)

# After RPCS3 is configured, add LLVM include paths directly to rpcs3_emu
# if BUILD_LLVM=ON. The 3rdparty_llvm INTERFACE target may not propagate
# includes correctly on Android.
if(TARGET rpcs3_emu AND BUILD_LLVM)
    target_include_directories(rpcs3_emu SYSTEM PRIVATE
        "${CMAKE_SOURCE_DIR}/third_party/rpcs3/3rdparty/llvm/llvm/llvm/include"
        "${CMAKE_BINARY_DIR}/rpcs3/3rdparty/llvm/llvm_build/include"
        "${CMAKE_BINARY_DIR}/rpcs3/3rdparty/llvm/llvm_build/llvm/include")
    # Demote return-type warnings from errors to warnings on ARM64 where
    # some RPCS3 code paths have missing returns that don't affect ARM64.
    target_compile_options(rpcs3_emu PRIVATE -Wno-error=return-type)
    # Demote implicit-fallthrough to warning: bin_patch.cpp and a few other
    # switch cases throw_exception and fall through to the next case.
    target_compile_options(rpcs3_emu PRIVATE -Wno-error=implicit-fallthrough)
    # ARM: enable RDM intrinsics (vqrdmlahq_s16 etc.) — clang 18 does not
    # enable +rdm by default for -march=armv8.1-a, so simd.hpp's gv_rmuladds_hds16
    # fails to compile in PPUInterpreter.cpp.
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        target_compile_options(rpcs3_emu PRIVATE -march=armv8.1-a+rdm)
    endif()
    # PocoS3 Android: protobuf is eliminated from the build entirely.
    # The np2_structs.pb.cc file and the protobuf-using NP sources are
    # excluded from rpcs3_emu on Android (see rpcs3/Emu/CMakeLists.txt),
    # and empty stubs are provided in rpcs3/Emu/NP/np_android_stubs.cpp.
    # The regenerate_np2_protobuf custom target is also wrapped in
    # if(NOT ANDROID) in third_party/rpcs3/3rdparty/protobuf/CMakeLists.txt.
    if(NOT ANDROID)
        if(TARGET regenerate_np2_protobuf)
            add_dependencies(rpcs3_emu regenerate_np2_protobuf)
        endif()
    endif()
    # hidapi is required by Input/hid_pad_handler.h (#include <hidapi.h>).
    # Upstream links it only via the desktop-only rpcs3_lib target, so we
    # must explicitly link 3rdparty::hidapi to rpcs3_emu on Android
    # (otherwise we hit a link-time failure with undefined hid_* symbols).
    if(TARGET 3rdparty::hidapi)
        target_link_libraries(rpcs3_emu PUBLIC 3rdparty::hidapi)
    endif()
    # fusion (xioTechnologies/Fusion) is required by Emu/Io/PadHandler.cpp
    # which calls FusionAhrsInitialise / FusionAhrsSetSettings / fusionAhrsDefaultSettings.
    # Upstream links it via rpcs3_lib (desktop only), so on Android we link it here.
    if(TARGET 3rdparty::fusion)
        target_link_libraries(rpcs3_emu PUBLIC 3rdparty::fusion)
    endif()
    # Ensure LLVM-generated headers (.inc files from tablegen) exist
    # before rpcs3_emu source files try to compile.
    # With EXCLUDE_FROM_ALL on the LLVM subdirectory, only explicitly-
    # requested targets are built. We must explicitly depend on the LLVM
    # libraries that own the tablegen .inc files rpcs3_emu needs
    # (e.g. LLVMAnalysis owns TargetLibraryInfo.inc).
    if(TARGET llvm-tblgen)
        add_dependencies(rpcs3_emu llvm-tblgen)
    endif()
    # Add explicit deps on every LLVM component we might pull headers from.
    # The if(TARGET ...) guards make this safe across LLVM versions.
    foreach(_llvm_dep
            LLVM
            LLVMCore
            LLVMAnalysis
            LLVMTargetParser
            LLVMSupport
            LLVMTarget
            LLVMCodeGen
            LLVMSelectionDAG
            LLVMGlobalISel
            LLVMAsmPrinter
            LLVMMC
            LLVMObject
            LLVMBitReader
            LLVMBitWriter
            LLVMProfileData
            LLVMInstrumentation
            LLVMPasses
            LLVMIRReader
            LLVMIRPrinter
            LLVMTransformUtils
            LLVMDebugInfoDWARF
            LLVMDebugInfoMSF
            LLVMDebugInfoCodeView
            LLVMDebugInfoPDB
            LLVMDemangle
            LLVMCodeGenTypes
            LLVMBinaryFormat
            LLVMRemarks
            LLVMIRPrinter
            LLVMTextAPI
            LLVMSupport
            LLVMObjectYAML
            LLVMMCDisassembler
            LLVMMCParser
            LLVMCIrPrinter
            LLVMFuzzerCLI
            LLVMFuzzerMutate
            LLVMCore
            LLVMAArch64CodeGen
            LLVMAArch64AsmParser
            LLVMAArch64AsmPrinter
            LLVMAArch64Desc
            LLVMAArch64Disassembler
            LLVMAArch64Info
            LLVMAArch64Utils)
        if(TARGET ${_llvm_dep})
            add_dependencies(rpcs3_emu ${_llvm_dep})
        endif()
    endforeach()
endif()

# -----------------------------------------------------------------------------
# PGO instrumentation. Forward to compile flags.
# -----------------------------------------------------------------------------
if(POCOS3_PGO)
    if(POCOS3_PGO_MODE STREQUAL "generate")
        message(STATUS "PocoS3 PGO: instrumenting for profile generation")
        add_compile_definitions(POCOS3_PGO_GENERATE)
        add_compile_options(-fprofile-instr-generate -fcoverage-mapping)
        add_link_options(-fprofile-instr-generate)
    elseif(POCOS3_PGO_MODE STREQUAL "use")
        if(NOT POCOS3_PGO_DATA_FILE)
            message(FATAL_ERROR "POCOS3_PGO=use requires POCOS3_PGO_DATA_FILE")
        endif()
        message(STATUS "PocoS3 PGO: using ${POCOS3_PGO_DATA_FILE}")
        add_compile_options(-fprofile-instr-use=${POCOS3_PGO_DATA_FILE})
        add_link_options(-fprofile-instr-use=${POCOS3_PGO_DATA_FILE})
    endif()
endif()

# -----------------------------------------------------------------------------
# Pull in the PocoS3 Android core (defines the `pocos3-core` SHARED library
# target that compiles android/src/pocos3-core.cpp + Input handlers + links
# against rpcs3_emu, producing libpocos3-core.so).
# -----------------------------------------------------------------------------
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/android ${CMAKE_BINARY_DIR}/pocos3-android)

# -----------------------------------------------------------------------------
# Optionally also build the JNI glue from CMake (for IDE-driven builds).
# By default this is OFF because Gradle builds the glue.
# -----------------------------------------------------------------------------
if(POCOS3_BUILD_GLUE)
    add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/android/pocos3-ui/app/src/main/cpp
                     ${CMAKE_BINARY_DIR}/pocos3-glue)
endif()
