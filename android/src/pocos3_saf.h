// =============================================================================
// PocoS3 SAF (Storage Access Framework) wrapper - public header.
// =============================================================================

#pragma once

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

// Opens a SAF tree URI and returns a handle (index) for it. Negative on error.
int pocos3_saf_open_tree(JNIEnv* env, jstring jUri);

// Releases a SAF tree handle and closes its underlying FDs.
void pocos3_saf_close_tree(int handle);

// Returns the root file descriptor for the tree. -1 if not opened.
int pocos3_saf_get_tree_fd(int handle);

// Opens a child within the tree by relative path (e.g. "GAMES/BCUS98111/PS3_GAME/USRDIR/EBOOT.BIN").
// Returns -1 on failure. The handle is cached; subsequent calls with the same
// path return the cached FD.
int pocos3_saf_open_child(int handle, const char* relative_path);

// Closes a child FD opened via pocos3_saf_open_child. Safe to call with an
// invalid handle/FD.
void pocos3_saf_close_child(int handle, int fd);

#ifdef __cplusplus
}  // extern "C"
#endif
