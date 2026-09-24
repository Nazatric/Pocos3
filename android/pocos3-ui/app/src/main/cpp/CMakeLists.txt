# =============================================================================
# PocoS3 JNI glue build (the small Gradle-built .so).
#
# This produces libpocos3-glue.so, which System.loadLibrary("pocos3-glue")
# brings up. The glue then dlopen()s libpocos3-core.so (prebuilt in
# jniLibs/arm64-v8a/, built by android/configure.sh).
#
# Built by Gradle's externalNativeBuild; path is set in
# app/build.gradle.kts:externalNativeBuild.cmake.path.
# =============================================================================

cmake_minimum_required(VERSION 3.30)

project(pocos3-glue
    VERSION 0.1.0
    LANGUAGES CXX
)

# Toolchain sanity: this is an Android-only target.
if(NOT ANDROID)
    message(FATAL_ERROR "pocos3-glue is Android-only. Use the NDK toolchain file.")
endif()

if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(WARNING
        "PocoS3 only ships arm64-v8a; you are building '${ANDROID_ABI}'. "
        "The APK will install but the core .so is arm64-only.")
endif()

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_compile_options(
    -fvisibility=hidden -fvisibility-inlines-hidden
    -fno-rtti
    -fno-exceptions
    -ffunction-sections -fdata-sections
    -Wall -Wextra -Werror=format -Werror=sign-compare
    -Werror=return-type -Wno-unused-parameter
)

if(CMAKE_BUILD_TYPE STREQUAL "Release" OR CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo")
    add_compile_options(-flto=thin)
    add_link_options(-flto=thin -Wl,--gc-sections)
endif()

add_library(pocos3-glue SHARED
    native-lib.cpp
)

target_link_libraries(pocos3-glue
    PUBLIC
        log
        android
)

# Optional: adrenotools (Adreno driver override). Off by default - Mali is
# the primary target. Set via -DPOCOS3_WITH_ADRENOTOOLS=ON.
set(POCOS3_WITH_ADRENOTOOLS OFF CACHE BOOL "Build adrenotools driver-override support (Adreno-only)")
if(POCOS3_WITH_ADRENOTOOLS)
    if(EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/libadrenotools/CMakeLists.txt)
        add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/libadrenotools
                         ${CMAKE_BINARY_DIR}/libadrenotools)
        target_link_libraries(pocos3-glue PUBLIC adrenotools)
        target_compile_definitions(pocos3-glue PUBLIC POCOS3_WITH_ADRENOTOOLS=1)
        target_include_directories(pocos3-glue PUBLIC
            ${CMAKE_CURRENT_SOURCE_DIR}/libadrenotools/include)
    else()
        message(WARNING
            "POCOS3_WITH_ADRENOTOOLS=ON but libadrenotools not found. "
            "Clone https://github.com/bylaws/libadrenotools to "
            "${CMAKE_CURRENT_SOURCE_DIR}/libadrenotools")
    endif()
endif()

if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_link_options("-Wl,--strip-debug")
endif()
