# Build xioTechnologies/Fusion library if the submodule is present
# (the workflow fetches it via "Fetch non-submodule dependencies" step).
# Otherwise fall back to a stub INTERFACE library so the build still configures.
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/fusion/CMakeLists.txt")
    set(ORIG_BUILD_TYPE ${CMAKE_BUILD_TYPE})
    set(CMAKE_BUILD_TYPE Release)
    add_subdirectory(fusion EXCLUDE_FROM_ALL)
    set(CMAKE_BUILD_TYPE ${ORIG_BUILD_TYPE})
else()
    add_library(Fusion INTERFACE)
endif()
