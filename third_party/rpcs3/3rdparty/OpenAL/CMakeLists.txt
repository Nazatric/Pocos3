# Stub OpenAL library for Pocos3 Android builds with -DWITHOUT_OPENAL=1.
# Provides only the include path to stub headers under include/AL/.
# The real OpenAL function calls in cellMic.cpp are guarded by
# #ifndef WITHOUT_OPENAL, so they are never linked.
add_library(3rdparty_openal INTERFACE)
target_include_directories(3rdparty_openal SYSTEM INTERFACE
    "${CMAKE_CURRENT_SOURCE_DIR}/include"
    "${CMAKE_CURRENT_SOURCE_DIR}/include/AL"
)
