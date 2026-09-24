# =============================================================================
# PocoS3 Android JNI glue build
#
# This CMake target builds ONLY the JNI glue (the small C++ shim that dlopen()s
# the upstream-RPCS3 core .so and exposes a PocoS3Api function-pointer table).
#
# The upstream RPCS3 core itself is built separately by android/configure.sh,
# producing libpocos3-core.so. That .so is NOT built by Gradle; building it from
# Gradle would force LLVM into every IDE sync.
#
# Resulting artifact: libpocos3-glue.so, packaged into the APK under jniLibs/.
# At runtime, libpocos3-glue.so dlopen()s libpocos3-core.so from the same dir.
# =============================================================================

cmake_minimum_required(VERSION 3.30)

project(pocos3-glue
    VERSION 0.1.0
    LANGUAGES CXX
)

# -----------------------------------------------------------------------------
# Toolchain sanity: this target only ever targets Android arm64.
# -----------------------------------------------------------------------------
if(NOT ANDROID)
    message(FATAL_ERROR
        "pocos3-glue is an Android-only target. Use the NDK toolchain file: "
        "-DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake "
        "-DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-33")
endif()

if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(WARNING
        "PocoS3 only ships arm64-v8a; you are building '${ANDROID_ABI}'. "
        "The APK will install but the core .so is arm64-only.")
endif()

# -----------------------------------------------------------------------------
# C++ standard. Match upstream RPCS3's requirement.
# -----------------------------------------------------------------------------
set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# -----------------------------------------------------------------------------
# Compile-time flags.
# -----------------------------------------------------------------------------
# Visibility: hide every symbol except the JNI entry points; reduces .so size
# and avoids symbol clashes with libpocos3-core.so at dlopen time.
add_compile_options(
    -fvisibility=hidden -fvisibility-inlines-hidden
    -fno-rtti
    -fno-exceptions
    -ffunction-sections -fdata-sections
    -Wall -Wextra -Werror=format -Werror=sign-compare
    -Werror=return-type -Wno-unused-parameter
)

# Release-only LTO. Debug builds keep symbols for proper stack traces.
if(CMAKE_BUILD_TYPE STREQUAL "Release" OR CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo")
    add_compile_options(-flto=thin)
    add_link_options(-flto=thin -Wl,--gc-sections)
endif()

# -----------------------------------------------------------------------------
# Sources.
# -----------------------------------------------------------------------------
set(POCOS3_GLUE_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/pocos3-android.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/src/pocos3_saf.cpp
)

set(POCOS3_GLUE_HEADERS
    ${CMAKE_CURRENT_SOURCE_DIR}/src/pocos3_saf.h
    ${CMAKE_CURRENT_SOURCE_DIR}/src/pocos3-android.h
)

add_library(pocos3-glue SHARED ${POCOS3_GLUE_SOURCES} ${POCOS3_GLUE_HEADERS})

target_include_directories(pocos3-glue PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/src)

# Android log + android framework; the NDK ships both.
target_link_libraries(pocos3-glue
    PUBLIC
        log
        android
)

# Optional: libadrenotools is a sibling checkout the user added manually.
# Build-flagged because it is GPL-incompatible-attribution-wise and not on
# the critical path for Mali devices.
set(POCOS3_WITH_ADRENOTOOLS OFF CACHE BOOL "Build adrenotools driver-override support (Adreno-only)")
if(POCOS3_WITH_ADRENOTOOLS)
    set(ADRENOTOOLS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../pocos3-ui/app/src/main/cpp/libadrenotools)
    if(EXISTS ${ADRENOTOOLS_DIR}/CMakeLists.txt)
        add_subdirectory(${ADRENOTOOLS_DIR} ${CMAKE_BINARY_DIR}/libadrenotools)
        target_link_libraries(pocos3-glue PUBLIC adrenotools)
        target_compile_definitions(pocos3-glue PUBLIC POCOS3_WITH_ADRENOTOOLS=1)
        target_include_directories(pocos3-glue PUBLIC ${ADRENOTOOLS_DIR}/include)
    else()
        message(WARNING
            "POCOS3_WITH_ADRENOTOOLS=ON but ${ADRENOTOOLS_DIR} not found. "
            "Clone https://github.com/bylaws/libadrenotools to that path.")
    endif()
endif()

# Strip in release builds; keeps the .so small enough that Gradle doesn't
# complain about ABI limits.
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_link_options("-Wl,--strip-debug")
endif()

# -----------------------------------------------------------------------------
# Install: the .so lands where the Gradle module expects it.
# -----------------------------------------------------------------------------
install(TARGETS pocos3-glue
    LIBRARY DESTINATION ${CMAKE_CURRENT_SOURCE_DIR}/../pocos3-ui/app/src/main/jniLibs/${ANDROID_ABI}
)
