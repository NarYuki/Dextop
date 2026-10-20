// Copies regions of an Xvfb framebuffer (XWD file, 32-bit BGRX) into an
// Android Surface (RGBX_8888). Used by LinuxContainerService, which runs as
// the shell user and receives the Surface of a Plasma v2 window over Binder.

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <cstring>

namespace {

constexpr const char *kTag = "DextopLinuxBridge";

struct Target {
    ANativeWindow *window = nullptr;
    int32_t width = 0;
    int32_t height = 0;
};

inline void copyRow(const uint8_t *src, uint8_t *dst, int32_t pixels) {
    // BGRX -> RGBX: swap bytes 0 and 2 of each pixel.
    const auto *in = reinterpret_cast<const uint32_t *>(src);
    auto *out = reinterpret_cast<uint32_t *>(dst);
    for (int32_t i = 0; i < pixels; ++i) {
        const uint32_t p = in[i];
        out[i] = 0xff000000u | ((p & 0xffu) << 16) | (p & 0xff00u) | ((p >> 16) & 0xffu);
    }
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_moe_n4tsu_dextop_linux_LinuxFrameBridge_nativeAttach(
        JNIEnv *env, jclass, jobject surface, jint width, jint height) {
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (window == nullptr) return 0;
    if (ANativeWindow_setBuffersGeometry(window, width, height, WINDOW_FORMAT_RGBX_8888) != 0) {
        ANativeWindow_release(window);
        return 0;
    }
    auto *target = new Target{window, width, height};
    return reinterpret_cast<jlong>(target);
}

extern "C" JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_linux_LinuxFrameBridge_nativeDetach(JNIEnv *, jclass, jlong handle) {
    auto *target = reinterpret_cast<Target *>(handle);
    if (target == nullptr) return;
    ANativeWindow_release(target->window);
    delete target;
}

/**
 * Copies the framebuffer rectangle (x, y, w, h) to the same place in the
 * Surface. [buffer] is the mmapped XWD file; [offset] is where pixels start.
 * Returns false when the Surface can no longer be locked.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_moe_n4tsu_dextop_linux_LinuxFrameBridge_nativeBlit(
        JNIEnv *env, jclass, jlong handle, jobject buffer, jint offset, jint stride,
        jint fbWidth, jint fbHeight, jint x, jint y, jint w, jint h) {
    auto *target = reinterpret_cast<Target *>(handle);
    auto *base = static_cast<uint8_t *>(env->GetDirectBufferAddress(buffer));
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (target == nullptr || base == nullptr) return JNI_FALSE;

    const int32_t width = std::min(target->width, fbWidth);
    const int32_t height = std::min(target->height, fbHeight);
    const int32_t left = std::clamp(x, 0, width);
    const int32_t top = std::clamp(y, 0, height);
    const int32_t right = std::clamp(x + w, left, width);
    const int32_t bottom = std::clamp(y + h, top, height);
    if (right <= left || bottom <= top) return JNI_TRUE;
    if (offset + static_cast<jlong>(bottom) * stride > capacity) return JNI_FALSE;

    ARect dirty{left, top, right, bottom};
    ANativeWindow_Buffer out;
    if (ANativeWindow_lock(target->window, &out, &dirty) != 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "ANativeWindow_lock failed");
        return JNI_FALSE;
    }
    // lock() may widen the dirty rectangle (for example to the whole buffer
    // when no previous content can be copied back).
    const int32_t copyLeft = std::max<int32_t>(dirty.left, 0);
    const int32_t copyTop = std::max<int32_t>(dirty.top, 0);
    const int32_t copyRight = std::min<int32_t>({dirty.right, width, out.width});
    const int32_t copyBottom = std::min<int32_t>({dirty.bottom, height, out.height});
    auto *pixels = static_cast<uint8_t *>(out.bits);
    for (int32_t row = copyTop; row < copyBottom; ++row) {
        copyRow(base + offset + static_cast<size_t>(row) * stride + copyLeft * 4,
                pixels + (static_cast<size_t>(row) * out.stride + copyLeft) * 4,
                copyRight - copyLeft);
    }
    ANativeWindow_unlockAndPost(target->window);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_moe_n4tsu_dextop_linux_LinuxFrameBridge_nativeResize(JNIEnv *, jclass, jlong handle, jint width, jint height) {
    auto *target = reinterpret_cast<Target *>(handle);
    if (target == nullptr || width <= 0 || height <= 0) return JNI_FALSE;
    if (ANativeWindow_setBuffersGeometry(target->window, width, height, WINDOW_FORMAT_RGBX_8888) != 0) return JNI_FALSE;
    target->width = width;
    target->height = height;
    return JNI_TRUE;
}

/**
 * Copies a block of BGRX pixels (an X GetImage reply) to (x, y) in the
 * Surface. Returns false when the Surface can no longer be locked.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_moe_n4tsu_dextop_linux_LinuxFrameBridge_nativeBlitPixels(
        JNIEnv *env, jclass, jlong handle, jbyteArray pixels, jint offset, jint stride,
        jint x, jint y, jint w, jint h) {
    auto *target = reinterpret_cast<Target *>(handle);
    if (target == nullptr) return JNI_FALSE;
    const jsize length = env->GetArrayLength(pixels);
    const int32_t left = std::clamp(x, 0, target->width);
    const int32_t top = std::clamp(y, 0, target->height);
    const int32_t right = std::clamp(x + w, left, target->width);
    const int32_t bottom = std::clamp(y + h, top, target->height);
    if (right <= left || bottom <= top) return JNI_TRUE;
    if (offset + static_cast<jlong>(h - 1) * stride + static_cast<jlong>(w) * 4 > length) return JNI_TRUE;

    ARect dirty{left, top, right, bottom};
    ANativeWindow_Buffer out;
    if (ANativeWindow_lock(target->window, &out, &dirty) != 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "ANativeWindow_lock failed");
        return JNI_FALSE;
    }
    auto *src = static_cast<uint8_t *>(env->GetPrimitiveArrayCritical(pixels, nullptr));
    auto *dst = static_cast<uint8_t *>(out.bits);
    // lock() may widen the dirty rectangle; only the block itself has new pixels,
    // so a widened lock (no preserved content) is filled from the block where it overlaps.
    const int32_t copyRight = std::min<int32_t>(right, out.width);
    const int32_t copyBottom = std::min<int32_t>(bottom, out.height);
    for (int32_t row = top; row < copyBottom; ++row) {
        copyRow(src + offset + static_cast<size_t>(row - y) * stride + static_cast<size_t>(left - x) * 4,
                dst + (static_cast<size_t>(row) * out.stride + left) * 4,
                copyRight - left);
    }
    env->ReleasePrimitiveArrayCritical(pixels, src, JNI_ABORT);
    ANativeWindow_unlockAndPost(target->window);
    return JNI_TRUE;
}

namespace {

/** Copies a w x h BGRX block at [src] (rows [stride] bytes apart) to (x, y) of [target]. */
jboolean blitBlock(Target *target, const uint8_t *src, size_t length, int32_t stride,
                   int32_t x, int32_t y, int32_t w, int32_t h) {
    const int32_t left = std::clamp(x, 0, target->width);
    const int32_t top = std::clamp(y, 0, target->height);
    const int32_t right = std::clamp(x + w, left, target->width);
    const int32_t bottom = std::clamp(y + h, top, target->height);
    if (right <= left || bottom <= top) return JNI_TRUE;
    if (static_cast<size_t>(h - 1) * stride + static_cast<size_t>(w) * 4 > length) return JNI_TRUE;
    ARect dirty{left, top, right, bottom};
    ANativeWindow_Buffer out;
    if (ANativeWindow_lock(target->window, &out, &dirty) != 0) return JNI_FALSE;
    auto *dst = static_cast<uint8_t *>(out.bits);
    const int32_t copyRight = std::min<int32_t>(right, out.width);
    const int32_t copyBottom = std::min<int32_t>(bottom, out.height);
    for (int32_t row = top; row < copyBottom; ++row) {
        copyRow(src + static_cast<size_t>(row - y) * stride + static_cast<size_t>(left - x) * 4,
                dst + (static_cast<size_t>(row) * out.stride + left) * 4, copyRight - left);
    }
    ANativeWindow_unlockAndPost(target->window);
    return JNI_TRUE;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_moe_n4tsu_dextop_linux_LinuxFrameBridge_nativeBlitAddress(
        JNIEnv *, jclass, jlong handle, jlong address, jlong length, jint stride,
        jint x, jint y, jint w, jint h) {
    auto *target = reinterpret_cast<Target *>(handle);
    if (target == nullptr || address == 0) return JNI_FALSE;
    return blitBlock(target, reinterpret_cast<const uint8_t *>(address), static_cast<size_t>(length), stride, x, y, w, h);
}
