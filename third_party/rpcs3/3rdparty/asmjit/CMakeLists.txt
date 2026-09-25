set(ASMJIT_EMBED TRUE)
set(ASMJIT_STATIC TRUE)
set(ASMJIT_BUILD_X86 TRUE)
set(ASMJIT_BUILD_ARM TRUE)
set(ASMJIT_BUILD_TEST FALSE)
set(ASMJIT_NO_DEPRECATED TRUE)
set(ASMJIT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/asmjit" CACHE PATH "Location of 'asmjit'")

include("${ASMJIT_DIR}/CMakeLists.txt")

# ASMJIT_SRC contains RELATIVE paths (e.g. "asmjit/core.h") that are
# relative to ASMJIT_DIR. Convert them to absolute paths so the
# add_library below can find them from any CMakeLists.txt context.
set(ASMJIT_SRC_ABS "")
foreach(_src ${ASMJIT_SRC})
        list(APPEND ASMJIT_SRC_ABS "${ASMJIT_DIR}/${_src}")
endforeach()

add_library(asmjit ${ASMJIT_SRC_ABS})
target_include_directories(asmjit SYSTEM PUBLIC ${ASMJIT_DIR})
target_link_libraries(asmjit PRIVATE ${ASMJIT_DEPS})

# ASMJIT should have a option for disabling installing and this wouldnt
# be required to avoid installing ASMJIT...

set_property(TARGET asmjit PROPERTY FOLDER "3rdparty/")
add_library(3rdparty::asmjit ALIAS asmjit)
