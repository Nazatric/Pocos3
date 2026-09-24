# =============================================================================
# PocoS3 top-level CMake project.
#
# This is the source root for `cmake -S . -B build-android -G Ninja ...`.
# It pulls in:
#   1. third_party/rpcs3/CMakeLists.txt - the upstream RPCS3 core, with
#      PocoS3's additive entry-point patches applied (see patches/).
#   2. android/CMakeLists.txt            - the JNI glue target (pocos3-glue)
#                                          + the SAF wrapper.
#
# The result is two artifacts:
#   * libpocos3-core.so  (the upstream RPCS3 core, as a shared library)
#   * libpocos3-glue.so  (the small JNI shim that dlopens libpocos3-core.so)
#
# Only the former is needed at runtime by the APK; the latter is built by
# Gradle's externalNativeBuild in android/pocos3-ui/app/build.gradle.kts.
# But building the glue here too is useful for standalone IDE builds.
# =============================================================================

cmake_minimum_required(VERSION 3.30)

project(pocos3
    VERSION 0.1.0
    LANGUAGES C CXX
)

# -----------------------------------------------------------------------------
# Sanity: this project only targets Android arm64. The desktop RPCS3 build
# is handled by third_party/rpcs3's own build system; this file is for
# Android only.
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
# Apply PocoS3 patches to upstream RPCS3 if not yet applied.
# -----------------------------------------------------------------------------
find_program(BASH_EXECUTABLE bash)
if(BASH_EXECUTABLE)
    execute_process(
        COMMAND ${BASH_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/patches/apply.sh ${RPCS3_SUBMODULE_DIR}
        WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
        RESULT_VARIABLE PATCH_RESULT
    )
    if(NOT PATCH_RESULT EQUAL 0)
        message(WARNING "PocoS3 patches did not apply cleanly; the build may fail. "
                "Resolve conflicts manually and re-run.")
    endif()
else()
    message(WARNING "bash not found; skipping PocoS3 patch application. "
            "If the upstream RPCS3 has not been patched, the build will fail "
            "with unresolved symbols at link time.")
endif()

# -----------------------------------------------------------------------------
# Pull in upstream RPCS3. The submodule's CMakeLists.txt will create a
# target named (typically) `rpcs3` (desktop executable) plus several object
# libraries. PocoS3's pocos3-android-entry-points.patch adds a
# `pocos3-core` SHARED library target that aggregates the object libraries
# into a single .so.
# -----------------------------------------------------------------------------
add_subdirectory(${RPCS3_SUBMODULE_DIR} ${CMAKE_BINARY_DIR}/rpcs3)

# -----------------------------------------------------------------------------
# PGO instrumentation. The poco3-android-pgo.patch wires this through to
# the LLVM_BUILD_INSTRUMENTED_* flags.
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
# The JNI glue. This is the small shim that dlopen()s libpocos3-core.so.
# In the Android build, this is also built by Gradle's externalNativeBuild
# from android/pocos3-ui/app/build.gradle.kts; including it here lets you
# build both targets in one invocation during development.
# -----------------------------------------------------------------------------
option(POCOS3_BUILD_GLUE "Also build the JNI glue" ON)
if(POCOS3_BUILD_GLUE)
    add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/android ${CMAKE_BINARY_DIR}/pocos3-glue)
endif()

# -----------------------------------------------------------------------------
# Install: the core .so lands where Gradle will pick it up.
# -----------------------------------------------------------------------------
if(TARGET pocos3-core)
    install(TARGETS pocos3-core
        LIBRARY DESTINATION ${CMAKE_CURRENT_SOURCE_DIR}/android/pocos3-ui/app/src/main/jniLibs/${ANDROID_ABI}
    )
endif()
