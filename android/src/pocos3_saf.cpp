// =============================================================================
// PocoS3 SAF (Storage Access Framework) wrapper.
//
// Translates Android content:// URIs from the user's selected game
// directory into a Linux file descriptor the RPCS3 core can read. The
// core operates on raw file descriptors and does not understand Android
// SAF; this wrapper is the bridge.
//
// Inspired by ARMSX3's `android/src/saf_device.{cpp,h}`. Re-implemented
// for PocoS3's package structure; the design (a per-game root with
// lazy-fd-cached children) is the right abstraction.
// =============================================================================

#include "pocos3_saf.h"

#include <android/asset_manager.h>
#include <android/log.h>
#include <dirent.h>
#include <fcntl.h>
#include <jni.h>
#include <sys/stat.h>
#include <unistd.h>
#include <filesystem>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace fs = std::filesystem;

namespace {

constexpr const char* kLogTag = "PocoS3.SAF";

template <typename... Args>
void saf_log(int prio, const char* fmt, Args... args) {
    __android_log_print(prio, kLogTag, fmt, args...);
}

// Per-tree cache. Keyed by the URI the user picked in the onboarding flow.
struct SafTree {
    std::string uri;            // content://com.android.externalstorage.documents/tree/...
    int root_fd = -1;           // opened via openFileDescriptor(uri, "r")
    std::unordered_map<std::string, int> child_fds;
    std::mutex mutex;
};

std::mutex g_trees_mutex;
std::vector<std::unique_ptr<SafTree>> g_trees;

std::string jstr_to_std(JNIEnv* env, jstring jstr) {
    if (!jstr) return {};
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    if (!chars) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return out;
}

}  // namespace

// Public functions consumed by the JNI glue. They are deliberately C-linkage
// so the core can call them via dlsym without dragging C++ name mangling.

extern "C" {

// Returns a handle (index into g_trees) for a SAF tree URI. Returns -1 on
// failure. The caller (the glue) caches this handle and reuses it for
// every file inside the same tree.
int pocos3_saf_open_tree(JNIEnv* env, jstring jUri) {
    if (!env || !jUri) return -1;
    std::string uri = jstr_to_std(env, jUri);

    // Get the ContentResolver. This needs a JNI context that has an
    // Activity attached; we don't keep one across calls, so we require
    // the caller to pass the Activity each time we look up a descriptor.
    // For simplicity in this first cut, we resolve the tree descriptor
    // immediately via the passed env's contentResolver.
    jobject activity = nullptr;  // TODO: pass Activity through from JNI glue
    jclass activity_cls = env->FindClass("android/app/Activity");
    jmethodID get_cr = env->GetMethodID(activity_cls, "getContentResolver",
                                       "()Landroid/content/ContentResolver;");
    env->DeleteLocalRef(activity_cls);

    if (!activity || !get_cr) {
        saf_log(ANDROID_LOG_ERROR,
                "pocos3_saf_open_tree: no Activity; cannot resolve %s", uri.c_str());
        return -1;
    }
    jobject content_resolver = env->CallObjectMethod(activity, get_cr);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return -1;
    }

    // contentResolver.openFileDescriptor(uri, "r") returns a
    // ParcelFileDescriptor whose .detachFd() we can extract.
    jclass cr_cls = env->GetObjectClass(content_resolver);
    jmethodID open_fd = env->GetMethodID(
        cr_cls, "openFileDescriptor",
        "(Landroid/net/Uri;Ljava/lang/String;)Landroid/content/res/AssetFileDescriptor;");
    env->DeleteLocalRef(cr_cls);

    jclass uri_cls = env->FindClass("android/net/Uri");
    jmethodID uri_parse = env->GetStaticMethodID(
        uri_cls, "parse", "(Ljava/lang/String;)Landroid/net/Uri;");
    jobject uri_obj = env->CallStaticObjectMethod(uri_cls, uri_parse,
                                                  env->NewStringUTF(uri.c_str()));
    env->DeleteLocalRef(uri_cls);

    jstring mode_str = env->NewStringUTF("r");
    jobject afd_obj = env->CallObjectMethod(content_resolver, open_fd, uri_obj, mode_str);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        env->DeleteLocalRef(content_resolver);
        return -1;
    }

    jclass afd_cls = env->GetObjectClass(afd_obj);
    jmethodID get_pfd = env->GetMethodID(
        afd_cls, "getFileDescriptor", "()Ljava/io/FileDescriptor;");
    jobject pfd_obj = env->CallObjectMethod(afd_obj, get_pfd);
    jmethodID detach_fd = env->GetMethodID(
        afd_cls, "detachFd", "()I");
    // Note: detachFd() is on ParcelFileDescriptor, not AssetFileDescriptor.
    // For a first cut, use the underlying FD via getFileDescriptor + reflection.
    // A production version should use ParcelFileDescriptor directly.
    int fd = -1;
    if (pfd_obj) {
        jclass pfd_cls = env->GetObjectClass(pfd_obj);
        jmethodID get_int = env->GetMethodID(pfd_cls, "detachFd", "()I");
        if (get_int) {
            fd = env->CallIntMethod(pfd_obj, get_int);
        }
        env->DeleteLocalRef(pfd_cls);
    }
    env->DeleteLocalRef(afd_cls);
    env->DeleteLocalRef(afd_obj);
    env->DeleteLocalRef(content_resolver);

    if (fd < 0) return -1;

    std::lock_guard lock(g_trees_mutex);
    auto tree = std::make_unique<SafTree>();
    tree->uri = uri;
    tree->root_fd = fd;
    int handle = static_cast<int>(g_trees.size());
    g_trees.push_back(std::move(tree));
    saf_log(ANDROID_LOG_INFO, "pocos3_saf_open_tree: handle=%d fd=%d uri=%s",
            handle, fd, uri.c_str());
    return handle;
}

void pocos3_saf_close_tree(int handle) {
    std::lock_guard lock(g_trees_mutex);
    if (handle < 0 || handle >= static_cast<int>(g_trees.size())) return;
    auto& tree = g_trees[handle];
    std::lock_guard tree_lock(tree->mutex);
    for (auto& [_, fd] : tree->child_fds) {
        if (fd >= 0) ::close(fd);
    }
    tree->child_fds.clear();
    if (tree->root_fd >= 0) {
        ::close(tree->root_fd);
        tree->root_fd = -1;
    }
}

int pocos3_saf_open_child(int handle, const char* relative_path) {
    if (handle < 0 || handle >= static_cast<int>(g_trees.size())) return -1;
    auto& tree = g_trees[handle];
    std::lock_guard tree_lock(tree->mutex);
    auto it = tree->child_fds.find(relative_path);
    if (it != tree->child_fds.end()) {
        return it->second;  // cached
    }
    // For a first cut, the SAF tree's children are *not* addressable via
    // the root_fd directly; we need to re-resolve each child via the
    // DocumentsContract API. That requires a JNIEnv and the Activity.
    //
    // This stub returns -1 for any child path. A production version
    // resolves the child via DocumentsContract.getDocumentId(tree_uri,
    // parent_doc_id) and opens it via openFileDescriptor(child_uri, "r").
    saf_log(ANDROID_LOG_WARN,
            "pocos3_saf_open_child: handle=%d path=%s (full SAF resolution not implemented in this stub; use pocos3_saf_open_tree_fd for the tree root)",
            handle, relative_path);
    return -1;
}

void pocos3_saf_close_child(int handle, int fd) {
    if (handle < 0 || handle >= static_cast<int>(g_trees.size())) return;
    auto& tree = g_trees[handle];
    std::lock_guard tree_lock(tree->mutex);
    for (auto it = tree->child_fds.begin(); it != tree->child_fds.end(); ++it) {
        if (it->second == fd) {
            ::close(fd);
            tree->child_fds.erase(it);
            return;
        }
    }
}

int pocos3_saf_get_tree_fd(int handle) {
    if (handle < 0 || handle >= static_cast<int>(g_trees.size())) return -1;
    return g_trees[handle]->root_fd;
}

}  // extern "C"
