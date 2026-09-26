#pragma once

// Stub al.h for Pocos3 Android builds with -DWITHOUT_OPENAL=1.
// Provides minimal AL types/decls so any header that includes al.h directly
// compiles. Real OpenAL function calls are guarded by #ifndef WITHOUT_OPENAL.

#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

// OpenAL core types (defined here, NOT in alc.h)
typedef void  ALCdevice;
typedef void  ALCcontext;
typedef char  ALCchar;
typedef int   ALCenum;
typedef int   ALCboolean;
typedef int   ALCint;
typedef unsigned int  ALCuint;
typedef int   ALCsizei;
typedef int   ALenum;
typedef int   ALboolean;
typedef int   ALint;
typedef unsigned int  ALuint;
typedef int   ALsizei;
typedef char  ALchar;
typedef void  ALvoid;
typedef unsigned int  ALbitfield;
typedef float  ALfloat;
typedef double ALdouble;

#define AL_TRUE                                  1
#define AL_FALSE                                 0
#define AL_NONE                                  0
#define AL_FORMAT_MONO8                          0x1100
#define AL_FORMAT_MONO16                         0x1101
#define AL_FORMAT_STEREO8                        0x1102
#define AL_FORMAT_STEREO16                       0x1103
#define AL_FORMAT_QUAD16                         0x1204
#define AL_FORMAT_51CHN16                        0x120B
#define AL_FORMAT_71CHN16                        0x1212

#ifdef __cplusplus
}
#endif
