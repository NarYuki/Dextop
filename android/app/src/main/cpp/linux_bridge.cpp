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
#include <vector>
#include <new>
#if defined(__ARM_NEON)
#include <arm_neon.h>
#endif

namespace {

constexpr const char *kTag = "DextopLinuxBridge";

struct Target {
    ANativeWindow *window = nullptr;
    int32_t width = 0;
    int32_t height = 0;
    std::vector<uint32_t> image;
    ARect pending{0, 0, 0, 0};
};

inline void copyRow(const uint8_t *src, uint8_t *dst, int32_t pixels) {
    // BGRX -> RGBX: swap bytes 0 and 2 of each pixel.
    const auto *in = reinterpret_cast<const uint32_t *>(src);
    auto *out = reinterpret_cast<uint32_t *>(dst);
    int32_t i = 0;
#if defined(__ARM_NEON)
    for (; i + 16 <= pixels; i += 16) {
        auto channels = vld4q_u8(src + i * 4);
        const auto red = channels.val[0];
        channels.val[0] = channels.val[2];
        channels.val[2] = red;
        channels.val[3] = vdupq_n_u8(255);
        vst4q_u8(dst + i * 4, channels);
    }
#endif
    for (; i < pixels; ++i) {
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
    std::vector<uint32_t>().swap(target->image);
    target->pending = {0, 0, 0, 0};
    return JNI_TRUE;
}

namespace {

bool present(Target *target) {
    if (target->pending.right <= target->pending.left || target->pending.bottom <= target->pending.top) return true;
    ARect dirty = target->pending;
    ANativeWindow_Buffer out;
    if (ANativeWindow_lock(target->window, &out, &dirty) != 0) return false;
    // BufferQueue may require pixels outside the latest X11 damage rectangle.
    // The retained RGBX image supplies those pixels without another X round trip.
    const auto left = std::clamp(dirty.left, 0, std::min(out.width, target->width));
    const auto right = std::clamp(dirty.right, left, std::min(out.width, target->width));
    const auto top = std::clamp(dirty.top, 0, std::min(out.height, target->height));
    const auto bottom = std::clamp(dirty.bottom, top, std::min(out.height, target->height));
    for (int row = top; row < bottom; ++row) {
        std::memcpy(static_cast<uint32_t *>(out.bits) + static_cast<size_t>(row) * out.stride + left,
            target->image.data() + static_cast<size_t>(row) * target->width + left, static_cast<size_t>(right - left) * 4);
    }
    if (ANativeWindow_unlockAndPost(target->window) != 0) return false;
    target->pending = {0, 0, 0, 0};
    return true;
}

bool stage(Target *target, const uint8_t *src, size_t length, int stride, int x, int y, int w, int h) {
    if (!target || !src || w <= 0 || h <= 0 || stride <= 0 || static_cast<int64_t>(w) * 4 > stride) return false;
    const auto required = static_cast<uint64_t>(h - 1) * stride + static_cast<uint64_t>(w) * 4;
    if (required > length) return false;
    const int left = std::clamp(x, 0, target->width), top = std::clamp(y, 0, target->height);
    const int right = static_cast<int>(std::clamp<int64_t>(static_cast<int64_t>(x) + w, left, target->width));
    const int bottom = static_cast<int>(std::clamp<int64_t>(static_cast<int64_t>(y) + h, top, target->height));
    if (right <= left || bottom <= top) return true;
    try {
        if (target->image.empty()) target->image.resize(static_cast<size_t>(target->width) * target->height, 0xff000000u);
    } catch (const std::bad_alloc &) { return false; }
    for (int row = top; row < bottom; ++row) {
        copyRow(src + static_cast<size_t>(row - y) * stride + static_cast<size_t>(left - x) * 4,
            reinterpret_cast<uint8_t *>(target->image.data() + static_cast<size_t>(row) * target->width + left), right - left);
    }
    if (target->pending.right <= target->pending.left || target->pending.bottom <= target->pending.top) {
        target->pending = {left, top, right, bottom};
    } else {
        target->pending = {std::min(target->pending.left, left), std::min(target->pending.top, top),
            std::max(target->pending.right, right), std::max(target->pending.bottom, bottom)};
    }
    return true;
}

jboolean pixels(JNIEnv *env, jlong handle, jbyteArray array, jint offset, jint stride,
                jint x, jint y, jint w, jint h, bool post) {
    if (!handle || !array || offset < 0 || offset > env->GetArrayLength(array)) return JNI_FALSE;
    const auto length = env->GetArrayLength(array) - offset;
    auto *src = static_cast<uint8_t *>(env->GetPrimitiveArrayCritical(array, nullptr));
    if (!src) return JNI_FALSE;
    auto *target = reinterpret_cast<Target *>(handle);
    const bool ok = stage(target, src + offset, length, stride, x, y, w, h);
    env->ReleasePrimitiveArrayCritical(array, src, JNI_ABORT);
    return ok && (!post || present(target));
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_moe_n4tsu_dextop_linux_LinuxFrameBridge_nativeBlitPixels(
        JNIEnv *env, jclass, jlong handle, jbyteArray array, jint offset, jint stride,
        jint x, jint y, jint w, jint h) {
    return pixels(env, handle, array, offset, stride, x, y, w, h, true);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_moe_n4tsu_dextop_linux_LinuxFrameBridge_nativeStagePixels(
        JNIEnv *env, jclass, jlong handle, jbyteArray array, jint offset, jint stride,
        jint x, jint y, jint w, jint h) {
    return pixels(env, handle, array, offset, stride, x, y, w, h, false);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_moe_n4tsu_dextop_linux_LinuxFrameBridge_nativeBlitAddress(
        JNIEnv *, jclass, jlong handle, jlong address, jlong length, jint stride,
        jint x, jint y, jint w, jint h) {
    auto *target = reinterpret_cast<Target *>(handle);
    return length >= 0 && stage(target, reinterpret_cast<const uint8_t *>(address), length, stride, x, y, w, h) && present(target);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_moe_n4tsu_dextop_linux_LinuxFrameBridge_nativeStageAddress(
        JNIEnv *, jclass, jlong handle, jlong address, jlong length, jint stride,
        jint x, jint y, jint w, jint h) {
    return length >= 0 && stage(reinterpret_cast<Target *>(handle), reinterpret_cast<const uint8_t *>(address), length, stride, x, y, w, h);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_moe_n4tsu_dextop_linux_LinuxFrameBridge_nativePresent(JNIEnv *, jclass, jlong handle) {
    return handle && present(reinterpret_cast<Target *>(handle));
}
