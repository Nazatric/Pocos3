#pragma once

// Stub al.h for Pocos3 Android builds with -DWITHOUT_OPENAL=1.
// Provides minimal AL types/decls so any header that includes al.h directly
// (rare in rpcs3 — none today) compiles.

#include "alc.h"
