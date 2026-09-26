#pragma once

// Stub alext.h for Pocos3 Android builds with -DWITHOUT_OPENAL=1.
// Provides minimal extension constants referenced by guarded OpenAL code
// paths (which are #ifdef'd out at compile time, so the body is unused).

#include "al.h"

#define ALC_ENUMERATE_ALL_EXT 1
#define AL_EXT_MCFORMATS 1
