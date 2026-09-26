#pragma once

// Stub alc.h for Pocos3 Android builds with -DWITHOUT_OPENAL=1.
// Real OpenAL calls in cellMic.cpp are guarded by #ifndef WITHOUT_OPENAL,
// so this stub only needs to provide the type/constant declarations that
// cellMic.h uses unconditionally (e.g. struct mic_device::device member).

#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

typedef void ALCdevice;
typedef int ALCenum;
typedef int ALCboolean;
typedef int ALCint;
typedef int ALCsizei;
typedef int ALenum;
typedef int ALboolean;
typedef int ALint;
typedef int ALsizei;
typedef int ALuint;

#define ALC_TRUE                                 1
#define ALC_FALSE                                0
#define ALC_NO_ERROR                             0
#define ALC_INVALID_DEVICE                       0xA001
#define ALC_INVALID_CONTEXT                       0xA002
#define ALC_INVALID_ENUM                          0xA003
#define ALC_INVALID_VALUE                         0xA004
#define ALC_OUT_OF_MEMORY                         0xA005
#define ALC_MAJOR_VERSION                         0x1000
#define ALC_MINOR_VERSION                         0x1001
#define ALC_ATTRIBUTES_SIZE                       0x1002
#define ALC_ALL_ATTRIBUTES                        0x1003
#define ALC_DEVICE_SPECIFIER                      0x1005
#define ALC_DEFAULT_DEVICE_SPECIFIER             0x1004
#define ALC_CAPTURE_DEVICE_SPECIFIER             0x310
#define ALC_CAPTURE_DEFAULT_DEVICE_SPECIFIER     0x311
#define ALC_CAPTURE_SAMPLES                       0x312
#define ALC_ENUMERATION_EXT                      "ALC_ENUMERATION_EXT"
#define ALC_EXT_DEFAULT_FILTER_ORDER 0x1100
#define ALC_EXT_DISCONNECT 0x1101

#define AL_TRUE                                  1
#define AL_FALSE                                 0
#define AL_NONE                                  0
#define AL_FORMAT_MONO8                          0x1100
#define AL_FORMAT_MONO16                         0x1101
#define AL_FORMAT_STEREO8                        0x1102
#define AL_FORMAT_STEREO16                        0x1103
#define AL_FORMAT_QUAD16                         0x1204
#define AL_FORMAT_51CHN16                        0x120B
#define AL_FORMAT_71CHN16                        0x1212
#define AL_SOURCE_TYPE                           0x200
#define AL_STREAMING                             0x103
#define AL_BUFFER                                0x1005
#define AL_CHANNELS                              0x2003

// Stub function declarations. Since they are only CALLED inside
// #ifndef WITHOUT_OPENAL blocks in cellMic.cpp, no implementation is
// required — but declaring them lets the .h compile cleanly.
const ALCchar* alcGetString(ALCdevice* device, ALCenum param);
ALCenum        alcGetError(ALCdevice* device);
ALCdevice*     alcCaptureOpenDevice(const ALCchar* name, ALCuint frequency, ALCenum format, ALCsizei buffersize);
ALCboolean     alcCaptureCloseDevice(ALCdevice* device);
void           alcCaptureStart(ALCdevice* device);
void           alcCaptureStop(ALCdevice* device);
void           alcCaptureSamples(ALCdevice* device, void* buffer, ALCsizei samples);
void           alcGetIntegerv(ALCdevice* device, ALCenum param, ALCsizei size, ALCint* values);
ALCboolean     alcIsExtensionPresent(ALCdevice* device, const ALCchar* extname);
const ALCchar* alcGetStringi(ALCdevice* device, ALCenum param, ALCsizei index);

#ifdef __cplusplus
}
#endif
