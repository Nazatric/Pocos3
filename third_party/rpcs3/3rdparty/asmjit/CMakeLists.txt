set(ASMJIT_EMBED TRUE)
set(ASMJIT_STATIC TRUE)
set(ASMJIT_BUILD_X86 TRUE)
set(ASMJIT_BUILD_ARM TRUE)
set(ASMJIT_BUILD_TEST FALSE)
set(ASMJIT_NO_DEPRECATED TRUE)
set(ASMJIT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/asmjit" CACHE PATH "Location of 'asmjit'")

include("${ASMJIT_DIR}/CMakeLists.txt")

# ASMJIT_SRC may contain relative OR absolute paths depending on the
# asmjit version. Convert relative ones to absolute.
set(ASMJIT_SRC_ABS "")
foreach(_src ${ASMJIT_SRC})
        if(IS_ABSOLUTE "${_src}")
                list(APPEND ASMJIT_SRC_ABS "${_src}")
        else()
                list(APPEND ASMJIT_SRC_ABS "${ASMJIT_DIR}/${_src}")
        endif()
endforeach()

add_library(asmjit ${ASMJIT_SRC_ABS})
target_include_directories(asmjit SYSTEM PUBLIC ${ASMJIT_DIR} ${ASMJIT_DIR}/src)
target_link_libraries(asmjit PRIVATE ${ASMJIT_DEPS})

set_property(TARGET asmjit PROPERTY FOLDER "3rdparty/")
add_library(3rdparty::asmjit ALIAS asmjit)
