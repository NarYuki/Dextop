#include <jni.h>

#include <android/input.h>
#include <android/keycodes.h>
#include <android/log.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <sys/epoll.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <map>
#include <memory>
#include <mutex>
#include <set>
#include <sstream>
#include <string>
#include <thread>
#include <unordered_map>
#include <utility>
#include <vector>

namespace {

constexpr const char* kTag = "DextopNativeInput";
constexpr int kProtocolVersion = 2;
constexpr int kConfigSize = 25;
constexpr int kProfileDisabled = 0;
constexpr int kProfileTouchpad = 1;
constexpr int kProfileMouse = 2;
constexpr int kMaxPhysicalSlots = 32;
constexpr int kMaxVirtualSlots = 5;

enum ConfigIndex {
    CFG_VERSION = 0,
    CFG_PROFILE = 1,
    CFG_ROTATION = 2,
    CFG_HOST_WIDTH = 3,
    CFG_HOST_HEIGHT = 4,
    CFG_FULLSCREEN_LEFT = 5,
    CFG_FULLSCREEN_TOP = 6,
    CFG_FULLSCREEN_RIGHT = 7,
    CFG_FULLSCREEN_BOTTOM = 8,
    CFG_TRACKPAD_LEFT = 9,
    CFG_TRACKPAD_TOP = 10,
    CFG_TRACKPAD_RIGHT = 11,
    CFG_TRACKPAD_BOTTOM = 12,
    CFG_DIRECT_TOUCH = 13,
    CFG_LAPTOP_MODE = 14,
    CFG_TOUCHPAD_MAX_X = 15,
    CFG_TOUCHPAD_MAX_Y = 16,
    CFG_TOUCHPAD_RESOLUTION = 17,
    CFG_DEBUG_ALL_EVENTS = 18,
    CFG_NATURAL_SCROLL = 19,
    CFG_MOUSE_SENSITIVITY_MILLI = 20,
    CFG_TAP_TIMEOUT_MS = 21,
    CFG_DOUBLE_TAP_TIMEOUT_MS = 22,
    CFG_TAP_SLOP_MILLI = 23,
    CFG_GENERATION = 24,
};

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, kTag, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, kTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, kTag, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kTag, __VA_ARGS__)

int64_t nowMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

std::string errnoText(const char* operation, const std::string& path = {}) {
    std::ostringstream out;
    out << operation;
    if (!path.empty()) out << " path=" << path;
    out << " errno=" << errno << "(" << std::strerror(errno) << ")";
    return out.str();
}

template <size_t N>
bool testBit(const std::array<unsigned long, N>& bits, int bit) {
    constexpr int width = static_cast<int>(sizeof(unsigned long) * 8);
    const int index = bit / width;
    return index >= 0 && index < static_cast<int>(N) &&
        (bits[static_cast<size_t>(index)] & (1UL << (bit % width))) != 0;
}

struct Rect {
    int left = 0;
    int top = 0;
    int right = 0;
    int bottom = 0;

    bool valid() const { return right > left && bottom > top; }
    int width() const { return std::max(1, right - left); }
    int height() const { return std::max(1, bottom - top); }
    bool contains(float x, float y) const {
        return valid() && x >= static_cast<float>(left) && x < static_cast<float>(right) &&
            y >= static_cast<float>(top) && y < static_cast<float>(bottom);
    }
};

struct Config {
    int version = kProtocolVersion;
    int profile = kProfileDisabled;
    int rotation = 0;
    int hostWidth = 1;
    int hostHeight = 1;
    Rect fullscreen;
    Rect trackpad;
    bool directTouch = false;
    bool laptopMode = false;
    int touchpadMaxX = 1839;
    int touchpadMaxY = 1199;
    int touchpadResolution = 12;
    bool debugAllEvents = false;
    bool naturalScroll = false;
    float mouseSensitivity = 1.1f;
    int tapTimeoutMs = 250;
    int doubleTapTimeoutMs = 300;
    float tapSlopFraction = 0.018f;
    int generation = 0;
};

Config parseConfig(const std::vector<int>& values) {
    Config config;
    if (values.size() != kConfigSize) return config;
    config.version = values[CFG_VERSION];
    config.profile = values[CFG_PROFILE];
    config.rotation = values[CFG_ROTATION];
    config.hostWidth = std::max(1, values[CFG_HOST_WIDTH]);
    config.hostHeight = std::max(1, values[CFG_HOST_HEIGHT]);
    config.fullscreen = {
        values[CFG_FULLSCREEN_LEFT], values[CFG_FULLSCREEN_TOP],
        values[CFG_FULLSCREEN_RIGHT], values[CFG_FULLSCREEN_BOTTOM]
    };
    config.trackpad = {
        values[CFG_TRACKPAD_LEFT], values[CFG_TRACKPAD_TOP],
        values[CFG_TRACKPAD_RIGHT], values[CFG_TRACKPAD_BOTTOM]
    };
    config.directTouch = values[CFG_DIRECT_TOUCH] != 0;
    config.laptopMode = values[CFG_LAPTOP_MODE] != 0;
    config.touchpadMaxX = std::max(1, values[CFG_TOUCHPAD_MAX_X]);
    config.touchpadMaxY = std::max(1, values[CFG_TOUCHPAD_MAX_Y]);
    config.touchpadResolution = std::max(1, values[CFG_TOUCHPAD_RESOLUTION]);
    config.debugAllEvents = values[CFG_DEBUG_ALL_EVENTS] != 0;
    config.naturalScroll = values[CFG_NATURAL_SCROLL] != 0;
    config.mouseSensitivity = std::max(0.05f, values[CFG_MOUSE_SENSITIVITY_MILLI] / 1000.0f);
    config.tapTimeoutMs = std::max(50, values[CFG_TAP_TIMEOUT_MS]);
    config.doubleTapTimeoutMs = std::max(100, values[CFG_DOUBLE_TAP_TIMEOUT_MS]);
    config.tapSlopFraction = std::clamp(values[CFG_TAP_SLOP_MILLI] / 1000.0f, 0.002f, 0.1f);
    config.generation = values[CFG_GENERATION];
    return config;
}

bool sameRect(const Rect& left, const Rect& right) {
    return left.left == right.left && left.top == right.top &&
        left.right == right.right && left.bottom == right.bottom;
}

bool sameConfigSemantics(const Config& left, const Config& right) {
    return left.version == right.version && left.profile == right.profile &&
        left.rotation == right.rotation && left.hostWidth == right.hostWidth &&
        left.hostHeight == right.hostHeight && sameRect(left.fullscreen, right.fullscreen) &&
        sameRect(left.trackpad, right.trackpad) && left.directTouch == right.directTouch &&
        left.laptopMode == right.laptopMode && left.touchpadMaxX == right.touchpadMaxX &&
        left.touchpadMaxY == right.touchpadMaxY &&
        left.touchpadResolution == right.touchpadResolution &&
        left.debugAllEvents == right.debugAllEvents &&
        left.naturalScroll == right.naturalScroll &&
        left.mouseSensitivity == right.mouseSensitivity &&
        left.tapTimeoutMs == right.tapTimeoutMs &&
        left.doubleTapTimeoutMs == right.doubleTapTimeoutMs &&
        left.tapSlopFraction == right.tapSlopFraction;
}

bool configInvalidatesActiveGesture(const Config& left, const Config& right) {
    return left.profile != right.profile || left.rotation != right.rotation ||
        left.hostWidth != right.hostWidth || left.hostHeight != right.hostHeight ||
        !sameRect(left.fullscreen, right.fullscreen) || !sameRect(left.trackpad, right.trackpad) ||
        left.directTouch != right.directTouch || left.laptopMode != right.laptopMode ||
        left.touchpadMaxX != right.touchpadMaxX || left.touchpadMaxY != right.touchpadMaxY ||
        left.touchpadResolution != right.touchpadResolution;
}

struct PhysicalContact {
    int slot = 0;
    int trackingId = -1;
    int x = 0;
    int y = 0;
    int major = 0;
};

struct MappedContact {
    int trackingId = -1;
    float screenX = 0;
    float screenY = 0;
    float localX = 0;
    float localY = 0;
    int virtualX = 0;
    int virtualY = 0;
    int major = 20;
};

struct SlotState {
    int trackingId = -1;
    int x = 0;
    int y = 0;
    int major = 0;
};

struct Device {
    int fd = -1;
    std::string path;
    std::string name;
    input_id id{};
    input_absinfo xInfo{};
    input_absinfo yInfo{};
    int currentSlot = 0;
    std::array<SlotState, kMaxPhysicalSlots> slots{};

    ~Device() {
        if (fd >= 0) close(fd);
    }

    std::vector<PhysicalContact> contacts() const {
        std::vector<PhysicalContact> result;
        for (int index = 0; index < kMaxPhysicalSlots; ++index) {
            const auto& slot = slots[static_cast<size_t>(index)];
            if (slot.trackingId < 0) continue;
            result.push_back({index, slot.trackingId, slot.x, slot.y, slot.major});
        }
        return result;
    }

    void clear() {
        currentSlot = 0;
        for (auto& slot : slots) slot = {};
        for (auto& slot : slots) slot.trackingId = -1;
    }
};

enum class Target { NONE, FULLSCREEN, TRACKPAD, IGNORED };

const char* targetName(Target target) {
    switch (target) {
        case Target::FULLSCREEN: return "fullscreen";
        case Target::TRACKPAD: return "laptop_trackpad";
        case Target::IGNORED: return "ignored";
        default: return "none";
    }
}

int linuxKeyCodeForAndroid(int keyCode) {
    switch (keyCode) {
        case AKEYCODE_ESCAPE: return KEY_ESC;
        case AKEYCODE_1: return KEY_1;
        case AKEYCODE_2: return KEY_2;
        case AKEYCODE_3: return KEY_3;
        case AKEYCODE_4: return KEY_4;
        case AKEYCODE_5: return KEY_5;
        case AKEYCODE_6: return KEY_6;
        case AKEYCODE_7: return KEY_7;
        case AKEYCODE_8: return KEY_8;
        case AKEYCODE_9: return KEY_9;
        case AKEYCODE_0: return KEY_0;
        case AKEYCODE_MINUS: return KEY_MINUS;
        case AKEYCODE_EQUALS: return KEY_EQUAL;
        case AKEYCODE_DEL: return KEY_BACKSPACE;
        case AKEYCODE_TAB: return KEY_TAB;
        case AKEYCODE_Q: return KEY_Q;
        case AKEYCODE_W: return KEY_W;
        case AKEYCODE_E: return KEY_E;
        case AKEYCODE_R: return KEY_R;
        case AKEYCODE_T: return KEY_T;
        case AKEYCODE_Y: return KEY_Y;
        case AKEYCODE_U: return KEY_U;
        case AKEYCODE_I: return KEY_I;
        case AKEYCODE_O: return KEY_O;
        case AKEYCODE_P: return KEY_P;
        case AKEYCODE_LEFT_BRACKET: return KEY_LEFTBRACE;
        case AKEYCODE_RIGHT_BRACKET: return KEY_RIGHTBRACE;
        case AKEYCODE_ENTER: return KEY_ENTER;
        case AKEYCODE_A: return KEY_A;
        case AKEYCODE_S: return KEY_S;
        case AKEYCODE_D: return KEY_D;
        case AKEYCODE_F: return KEY_F;
        case AKEYCODE_G: return KEY_G;
        case AKEYCODE_H: return KEY_H;
        case AKEYCODE_J: return KEY_J;
        case AKEYCODE_K: return KEY_K;
        case AKEYCODE_L: return KEY_L;
        case AKEYCODE_SEMICOLON: return KEY_SEMICOLON;
        case AKEYCODE_APOSTROPHE: return KEY_APOSTROPHE;
        case AKEYCODE_GRAVE: return KEY_GRAVE;
        case AKEYCODE_BACKSLASH: return KEY_BACKSLASH;
        case AKEYCODE_Z: return KEY_Z;
        case AKEYCODE_X: return KEY_X;
        case AKEYCODE_C: return KEY_C;
        case AKEYCODE_V: return KEY_V;
        case AKEYCODE_B: return KEY_B;
        case AKEYCODE_N: return KEY_N;
        case AKEYCODE_M: return KEY_M;
        case AKEYCODE_COMMA: return KEY_COMMA;
        case AKEYCODE_PERIOD: return KEY_DOT;
        case AKEYCODE_SLASH: return KEY_SLASH;
        case AKEYCODE_SPACE: return KEY_SPACE;
        case AKEYCODE_META_LEFT: return KEY_LEFTMETA;
        case AKEYCODE_DPAD_LEFT: return KEY_LEFT;
        case AKEYCODE_DPAD_RIGHT: return KEY_RIGHT;
        case AKEYCODE_DPAD_UP: return KEY_UP;
        case AKEYCODE_DPAD_DOWN: return KEY_DOWN;
        case AKEYCODE_FORWARD_DEL: return KEY_DELETE;
        case AKEYCODE_F1: return KEY_F1;
        case AKEYCODE_F2: return KEY_F2;
        case AKEYCODE_F3: return KEY_F3;
        case AKEYCODE_F4: return KEY_F4;
        case AKEYCODE_F5: return KEY_F5;
        case AKEYCODE_F6: return KEY_F6;
        case AKEYCODE_F7: return KEY_F7;
        case AKEYCODE_F8: return KEY_F8;
        case AKEYCODE_F9: return KEY_F9;
        case AKEYCODE_F10: return KEY_F10;
        case AKEYCODE_F11: return KEY_F11;
        case AKEYCODE_F12: return KEY_F12;
        default: return -1;
    }
}

class Engine {
public:
    Engine(JavaVM* vm, JNIEnv* env, jobject service)
        : vm_(vm), service_(env->NewGlobalRef(service)) {
        jclass cls = env->GetObjectClass(service);
        onState_ = env->GetMethodID(cls, "onNativeState", "(Ljava/lang/String;Ljava/lang/String;)V");
        onThreeFinger_ = env->GetMethodID(cls, "onNativeThreeFinger", "()V");
        onHaptic_ = env->GetMethodID(cls, "onNativeHaptic", "(Z)V");
        env->DeleteLocalRef(cls);
        for (auto& id : virtualSlotTrackingIds_) id = -1;
        for (auto& physical : virtualSlotPhysicalIds_) physical = -1;
    }

    ~Engine() {
        stop("engine_destructor");
        JNIEnv* env = attach();
        if (env != nullptr && service_ != nullptr) {
            env->DeleteGlobalRef(service_);
            service_ = nullptr;
        }
    }

    void configure(const std::vector<int>& values) {
        const Config next = parseConfig(values);
        {
            std::lock_guard<std::mutex> lock(configMutex_);
            config_ = next;
            configDirty_.store(true);
        }
        std::ostringstream out;
        out << "config generation=" << next.generation << " profile=" << next.profile
            << " rotation=" << next.rotation << " host=" << next.hostWidth << "x" << next.hostHeight
            << " fullscreen=[" << next.fullscreen.left << "," << next.fullscreen.top << ","
            << next.fullscreen.right << "," << next.fullscreen.bottom << "] trackpad=["
            << next.trackpad.left << "," << next.trackpad.top << "," << next.trackpad.right << ","
            << next.trackpad.bottom << "] directTouch=" << next.directTouch
            << " laptop=" << next.laptopMode << " debugAll=" << next.debugAllEvents;
        state("config", out.str());
    }

    bool start() {
        if (running_.exchange(true)) return true;
        outputReady_.store(false);
        worker_ = std::thread([this] { run(); });
        return true;
    }

    void stop(const std::string& reason) {
        const bool wasRunning = running_.exchange(false);
        if (wasRunning && worker_.joinable()) worker_.join();
        if (!wasRunning && worker_.joinable()) worker_.join();
        cleanupGesture("stop:" + reason);
        destroyKeyboard();
        destroyOutput();
        closeDevices();
        std::ostringstream out;
        out << "reason=" << reason << " rawEvents=" << rawEvents_.load()
            << " rawFrames=" << rawFrames_.load() << " outputFrames=" << outputFrames_.load()
            << " dropped=" << droppedFrames_.load();
        state("stopped", out.str());
    }

    void setOutputReady(bool ready) {
        outputReady_.store(ready);
        state("output_ready", ready ? "true" : "false");
    }

    void inject(const std::vector<int>& triples) {
        std::vector<input_event> events;
        events.reserve(triples.size() / 3);
        for (size_t index = 0; index + 2 < triples.size(); index += 3) {
            events.push_back(makeEvent(
                static_cast<uint16_t>(triples[index]),
                static_cast<uint16_t>(triples[index + 1]),
                triples[index + 2]
            ));
        }
        emit(events, false, "binder_inject");
    }

    bool injectKeyboard(int keyCode, int action, int metaState, int repeatCount) {
        std::lock_guard<std::recursive_mutex> lock(keyboardMutex_);
        if (keyboardFd_ < 0) {
            state("keyboard_injection_error", "keyboard uinput device is not ready");
            return false;
        }
        const int linuxCode = linuxKeyCodeForAndroid(keyCode);
        if (linuxCode < 0) {
            state("keyboard_injection_error", "unsupported Android keyCode=" + std::to_string(keyCode));
            return false;
        }

        if (action == AKEY_EVENT_ACTION_DOWN) {
            const auto existing = keyboardPresses_.find(keyCode);
            if (existing != keyboardPresses_.end()) {
                return writeKeyboardEvents(
                    {makeEvent(EV_KEY, static_cast<uint16_t>(existing->second.linuxCode), 2)},
                    repeatCount > 0 ? "keyboard_repeat" : "keyboard_duplicate_down"
                );
            }

            KeyboardPress press;
            press.linuxCode = linuxCode;
            if ((metaState & AMETA_SHIFT_ON) != 0) press.modifiers.push_back(KEY_LEFTSHIFT);
            if ((metaState & AMETA_CTRL_ON) != 0) press.modifiers.push_back(KEY_LEFTCTRL);
            if ((metaState & AMETA_ALT_ON) != 0) press.modifiers.push_back(KEY_LEFTALT);
            if ((metaState & AMETA_META_ON) != 0) press.modifiers.push_back(KEY_LEFTMETA);

            std::vector<input_event> events;
            for (const int modifier : press.modifiers) {
                if (keyboardModifierRefs_[modifier] == 0) {
                    events.push_back(makeEvent(EV_KEY, static_cast<uint16_t>(modifier), 1));
                }
            }
            events.push_back(makeEvent(EV_KEY, static_cast<uint16_t>(linuxCode), 1));
            if (!writeKeyboardEvents(events, "keyboard_down")) return false;
            for (const int modifier : press.modifiers) keyboardModifierRefs_[modifier] += 1;
            keyboardPresses_[keyCode] = std::move(press);
            return true;
        }

        if (action == AKEY_EVENT_ACTION_UP) {
            const auto existing = keyboardPresses_.find(keyCode);
            if (existing == keyboardPresses_.end()) {
                return writeKeyboardEvents(
                    {makeEvent(EV_KEY, static_cast<uint16_t>(linuxCode), 0)},
                    "keyboard_orphan_up"
                );
            }

            const KeyboardPress press = existing->second;
            std::vector<input_event> events{
                makeEvent(EV_KEY, static_cast<uint16_t>(press.linuxCode), 0)
            };
            for (auto modifier = press.modifiers.rbegin(); modifier != press.modifiers.rend(); ++modifier) {
                const auto refs = keyboardModifierRefs_.find(*modifier);
                if (refs != keyboardModifierRefs_.end() && refs->second <= 1) {
                    events.push_back(makeEvent(EV_KEY, static_cast<uint16_t>(*modifier), 0));
                }
            }
            if (!writeKeyboardEvents(events, "keyboard_up")) return false;
            for (const int modifier : press.modifiers) {
                const auto refs = keyboardModifierRefs_.find(modifier);
                if (refs == keyboardModifierRefs_.end()) continue;
                refs->second -= 1;
                if (refs->second <= 0) keyboardModifierRefs_.erase(refs);
            }
            keyboardPresses_.erase(existing);
            return true;
        }

        state("keyboard_injection_error", "unsupported key action=" + std::to_string(action));
        return false;
    }

    void setKeyboardVisible(bool visible) {
        keyboardRequested_.store(visible);
    }

    std::string probe() const {
        const bool inputReadable = access("/dev/input", R_OK | X_OK) == 0;
        const bool uinputPresent = access("/dev/uinput", F_OK) == 0 || access("/dev/input/uinput", F_OK) == 0;
        std::ostringstream out;
        out << "abi=" << (sizeof(void*) * 8) << " inputDir=" << inputReadable
            << " uinputNode=" << uinputPresent << " inputEventSize=" << sizeof(input_event);
        return out.str();
    }

private:
    JavaVM* vm_ = nullptr;
    jobject service_ = nullptr;
    jmethodID onState_ = nullptr;
    jmethodID onThreeFinger_ = nullptr;
    jmethodID onHaptic_ = nullptr;

    mutable std::mutex configMutex_;
    Config config_;
    std::atomic<bool> configDirty_{true};
    std::atomic<bool> running_{false};
    std::atomic<bool> outputReady_{false};
    std::atomic<bool> keyboardRequested_{false};
    std::thread worker_;

    struct KeyboardPress {
        int linuxCode = -1;
        std::vector<int> modifiers;
    };
    std::recursive_mutex keyboardMutex_;
    // Pointer frames can arrive through Binder while the worker recreates or
    // tears down the uinput device. Keep the descriptor and its complete
    // frame writes under one lock so an fd cannot be closed or reused midway
    // through an injected frame.
    std::recursive_mutex outputMutex_;
    std::unordered_map<int, KeyboardPress> keyboardPresses_;
    std::unordered_map<int, int> keyboardModifierRefs_;

    int epollFd_ = -1;
    std::unordered_map<int, std::unique_ptr<Device>> devices_;
    std::atomic<int> deviceCount_{0};
    std::atomic<int> activeFd_{-1};
    int outputFd_ = -1;
    int keyboardFd_ = -1;
    int appliedGeneration_ = -1;
    Config appliedConfig_;
    bool hasAppliedConfig_ = false;

    Target target_ = Target::NONE;
    bool threeFingerCaptured_ = false;
    int suppressedPhysicalFd_ = -1;
    std::atomic<int> lastContactCount_{0};
    int64_t gestureStartedAt_ = 0;
    int gestureMaxContacts_ = 0;
    float gestureStartX_ = 0;
    float gestureStartY_ = 0;
    float gestureMaxTravel_ = 0;
    float lastLocalX_ = 0;
    float lastLocalY_ = 0;
    bool gestureMoved_ = false;
    bool gestureTwoFinger_ = false;

    bool lastTapValid_ = false;
    int64_t lastTapAt_ = 0;
    float lastTapX_ = 0;
    float lastTapY_ = 0;
    bool touchpadSecondTap_ = false;
    bool leftButtonDown_ = false;

    float wheelFractionX_ = 0;
    float wheelFractionY_ = 0;
    float moveFractionX_ = 0;
    float moveFractionY_ = 0;
    bool mouseLongPressTriggered_ = false;

    std::array<int, kMaxVirtualSlots> virtualSlotPhysicalIds_{};
    std::array<int, kMaxVirtualSlots> virtualSlotTrackingIds_{};
    int nextVirtualTrackingId_ = 1;
    int activeToolKey_ = -1;

    std::atomic<uint64_t> rawEvents_{0};
    std::atomic<uint64_t> rawFrames_{0};
    std::atomic<uint64_t> outputFrames_{0};
    std::atomic<uint64_t> droppedFrames_{0};
    int64_t lastStatsAt_ = 0;
    int64_t lastScanAt_ = 0;

    Config configSnapshot() const {
        std::lock_guard<std::mutex> lock(configMutex_);
        return config_;
    }

    JNIEnv* attach() const {
        if (vm_ == nullptr) return nullptr;
        JNIEnv* env = nullptr;
        const jint status = vm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (status == JNI_OK) return env;
        if (vm_->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
        return env;
    }

    void clearJavaException(JNIEnv* env, const char* callback) const {
        if (env != nullptr && env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            LOGW("Java callback failed callback=%s", callback);
        }
    }

    void state(const std::string& category, const std::string& message) const {
        LOGI("%s: %s", category.c_str(), message.c_str());
        JNIEnv* env = attach();
        if (env == nullptr || service_ == nullptr || onState_ == nullptr) return;
        jstring jCategory = env->NewStringUTF(category.c_str());
        jstring jMessage = env->NewStringUTF(message.c_str());
        env->CallVoidMethod(service_, onState_, jCategory, jMessage);
        env->DeleteLocalRef(jCategory);
        env->DeleteLocalRef(jMessage);
        clearJavaException(env, "onNativeState");
    }

    void callbackThreeFinger() const {
        JNIEnv* env = attach();
        if (env == nullptr || service_ == nullptr || onThreeFinger_ == nullptr) return;
        env->CallVoidMethod(service_, onThreeFinger_);
        clearJavaException(env, "onNativeThreeFinger");
    }

    void callbackHaptic(bool strong) const {
        JNIEnv* env = attach();
        if (env == nullptr || service_ == nullptr || onHaptic_ == nullptr) return;
        env->CallVoidMethod(service_, onHaptic_, strong ? JNI_TRUE : JNI_FALSE);
        clearJavaException(env, "onNativeHaptic");
    }

    static input_event makeEvent(uint16_t type, uint16_t code, int32_t value) {
        input_event event{};
        event.type = type;
        event.code = code;
        event.value = value;
        return event;
    }

    void logOutputEvents(const std::vector<input_event>& events, const std::string& reason) const {
        if (!configSnapshot().debugAllEvents) return;
        for (const auto& event : events) {
            LOGD("uinput reason=%s type=%u code=%u value=%d", reason.c_str(), event.type, event.code, event.value);
        }
    }

    bool writeEvents(int fd, const std::vector<input_event>& events, const std::string& reason) {
        if (fd < 0 || events.empty()) return false;
        logOutputEvents(events, reason);
        const auto* bytes = reinterpret_cast<const uint8_t*>(events.data());
        size_t remaining = events.size() * sizeof(input_event);
        while (remaining > 0) {
            const ssize_t written = write(fd, bytes, remaining);
            if (written < 0 && errno == EINTR) continue;
            if (written <= 0) {
                state("native_error", errnoText("write uinput"));
                return false;
            }
            bytes += written;
            remaining -= static_cast<size_t>(written);
        }
        outputFrames_.fetch_add(1);
        return true;
    }

    bool emit(const std::vector<input_event>& events, bool force, const std::string& reason) {
        std::lock_guard<std::recursive_mutex> lock(outputMutex_);
        if (!force && !outputReady_.load()) {
            droppedFrames_.fetch_add(1);
            if (configSnapshot().debugAllEvents) LOGD("drop output frame reason=%s outputReady=false", reason.c_str());
            return false;
        }
        return writeEvents(outputFd_, events, reason);
    }

    void run() {
        epollFd_ = epoll_create1(EPOLL_CLOEXEC);
        if (epollFd_ < 0) {
            state("native_error", errnoText("epoll_create1"));
            running_.store(false);
            return;
        }
        lastStatsAt_ = nowMs();
        lastScanAt_ = 0;
        while (running_.load()) {
            applyConfigIfNeeded();
            applyKeyboardIfNeeded();
            scanDevicesIfNeeded();
            checkMouseLongPress();

            std::array<epoll_event, 16> ready{};
            const int count = epoll_wait(epollFd_, ready.data(), static_cast<int>(ready.size()), 12);
            if (count < 0 && errno != EINTR) {
                state("native_error", errnoText("epoll_wait"));
                break;
            }
            for (int index = 0; index < count; ++index) {
                const int fd = ready[static_cast<size_t>(index)].data.fd;
                auto it = devices_.find(fd);
                if (it == devices_.end()) continue;
                if ((ready[static_cast<size_t>(index)].events & (EPOLLERR | EPOLLHUP)) != 0) {
                    state("device_removed", "path=" + it->second->path + " epoll_hup_or_error");
                    if (activeFd_.load() == fd) cleanupGesture("active_device_removed");
                    if (suppressedPhysicalFd_ == fd) suppressedPhysicalFd_ = -1;
                    epoll_ctl(epollFd_, EPOLL_CTL_DEL, fd, nullptr);
                    devices_.erase(it);
                    deviceCount_.store(static_cast<int>(devices_.size()));
                    continue;
                }
                readDevice(*it->second);
            }
            emitStatsIfNeeded();
        }
        const bool unexpectedExit = running_.exchange(false);
        cleanupGesture("worker_exit");
        closeDevices();
        if (epollFd_ >= 0) close(epollFd_);
        epollFd_ = -1;
        if (unexpectedExit) state("worker_exited", "native input loop terminated unexpectedly");
    }

    void applyConfigIfNeeded() {
        if (!configDirty_.exchange(false)) return;
        const Config cfg = configSnapshot();
        const bool duplicate = hasAppliedConfig_ && sameConfigSemantics(appliedConfig_, cfg);
        const bool recreate = outputFd_ < 0 || !hasAppliedConfig_ ||
            cfg.profile != appliedConfig_.profile ||
            cfg.touchpadMaxX != appliedConfig_.touchpadMaxX ||
            cfg.touchpadMaxY != appliedConfig_.touchpadMaxY ||
            cfg.touchpadResolution != appliedConfig_.touchpadResolution;
        if (duplicate && !recreate) {
            appliedGeneration_ = cfg.generation;
            if (cfg.debugAllEvents) {
                LOGD("config duplicate suppressed generation=%d", cfg.generation);
            }
            return;
        }
        const bool invalidatesGesture = !hasAppliedConfig_ || recreate ||
            configInvalidatesActiveGesture(appliedConfig_, cfg);
        if (invalidatesGesture && (gestureStartedAt_ > 0 || activeFd_.load() >= 0)) {
            cancelGestureForConfigChange(appliedConfig_, cfg);
        }
        if (recreate) {
            destroyOutput();
            outputReady_.store(false);
            if (cfg.profile != kProfileDisabled && !createOutput(cfg)) {
                state("native_error", "unable to create virtual pointer profile=" + std::to_string(cfg.profile));
            }
        }
        appliedGeneration_ = cfg.generation;
        appliedConfig_ = cfg;
        hasAppliedConfig_ = true;
        std::ostringstream out;
        out << "generation=" << cfg.generation << " duplicate=" << duplicate
            << " invalidatesGesture=" << invalidatesGesture << " recreate=" << recreate;
        state("config_applied", out.str());
    }

    void applyKeyboardIfNeeded() {
        std::lock_guard<std::recursive_mutex> lock(keyboardMutex_);
        const bool requested = keyboardRequested_.load();
        if (requested && keyboardFd_ < 0) createKeyboard();
        if (!requested && keyboardFd_ >= 0) destroyKeyboard();
    }

    bool writeKeyboardEvents(std::vector<input_event> events, const std::string& reason) {
        if (keyboardFd_ < 0 || events.empty()) return false;
        events.push_back(makeEvent(EV_SYN, SYN_REPORT, 0));
        return writeEvents(keyboardFd_, events, reason);
    }

    void releaseAllKeyboardState(const std::string& reason) {
        if (keyboardFd_ < 0) {
            keyboardPresses_.clear();
            keyboardModifierRefs_.clear();
            return;
        }
        std::vector<input_event> events;
        for (const auto& [keyCode, press] : keyboardPresses_) {
            (void)keyCode;
            events.push_back(makeEvent(EV_KEY, static_cast<uint16_t>(press.linuxCode), 0));
        }
        for (const auto& [modifier, refs] : keyboardModifierRefs_) {
            if (refs > 0) events.push_back(makeEvent(EV_KEY, static_cast<uint16_t>(modifier), 0));
        }
        if (!events.empty()) writeKeyboardEvents(std::move(events), reason);
        keyboardPresses_.clear();
        keyboardModifierRefs_.clear();
    }

    int openUinput() const {
        int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK | O_CLOEXEC);
        if (fd < 0) fd = open("/dev/input/uinput", O_WRONLY | O_NONBLOCK | O_CLOEXEC);
        return fd;
    }

    bool setupAbs(int fd, int code, int minimum, int maximum, int resolution) {
        if (ioctl(fd, UI_SET_ABSBIT, code) < 0) return false;
        uinput_abs_setup setup{};
        setup.code = static_cast<__u16>(code);
        setup.absinfo.minimum = minimum;
        setup.absinfo.maximum = maximum;
        setup.absinfo.resolution = resolution;
        return ioctl(fd, UI_ABS_SETUP, &setup) >= 0;
    }

    bool createOutput(const Config& cfg) {
        std::lock_guard<std::recursive_mutex> lock(outputMutex_);
        outputFd_ = openUinput();
        if (outputFd_ < 0) {
            state("native_error", errnoText("open /dev/uinput"));
            return false;
        }
        bool ok = ioctl(outputFd_, UI_SET_EVBIT, EV_SYN) >= 0 &&
            ioctl(outputFd_, UI_SET_EVBIT, EV_KEY) >= 0;
        uinput_setup setup{};
        setup.id.bustype = BUS_USB;
        setup.id.vendor = 6353;
        setup.id.product = cfg.profile == kProfileTouchpad ? 5418 : 5419;
        setup.id.version = 1;
        if (cfg.profile == kProfileTouchpad) {
            std::strncpy(setup.name, "Dextop Virtual Touchpad", UINPUT_MAX_NAME_SIZE - 1);
            ok = ok && ioctl(outputFd_, UI_SET_EVBIT, EV_ABS) >= 0;
            for (const int key : {
                BTN_LEFT, BTN_RIGHT, BTN_TOUCH, BTN_TOOL_FINGER, BTN_TOOL_DOUBLETAP,
                BTN_TOOL_TRIPLETAP, BTN_TOOL_QUADTAP, BTN_TOOL_QUINTTAP
            }) {
                ok = ok && ioctl(outputFd_, UI_SET_KEYBIT, key) >= 0;
            }
            ok = ok && ioctl(outputFd_, UI_SET_PROPBIT, INPUT_PROP_POINTER) >= 0;
            ok = ok && ioctl(outputFd_, UI_SET_PROPBIT, INPUT_PROP_BUTTONPAD) >= 0;
            ok = ok && setupAbs(outputFd_, ABS_MT_SLOT, 0, kMaxVirtualSlots - 1, 0);
            ok = ok && setupAbs(outputFd_, ABS_MT_TOUCH_MAJOR, 0, 255, 0);
            ok = ok && setupAbs(outputFd_, ABS_MT_POSITION_X, 0, cfg.touchpadMaxX, cfg.touchpadResolution);
            ok = ok && setupAbs(outputFd_, ABS_MT_POSITION_Y, 0, cfg.touchpadMaxY, cfg.touchpadResolution);
            ok = ok && setupAbs(outputFd_, ABS_MT_TRACKING_ID, 0, 65535, 0);
            ok = ok && setupAbs(outputFd_, ABS_MT_PRESSURE, 0, 255, 0);
        } else {
            std::strncpy(setup.name, "Dextop Virtual Mouse", UINPUT_MAX_NAME_SIZE - 1);
            ok = ok && ioctl(outputFd_, UI_SET_EVBIT, EV_REL) >= 0;
            for (const int rel : {REL_X, REL_Y, REL_WHEEL, REL_HWHEEL}) {
                ok = ok && ioctl(outputFd_, UI_SET_RELBIT, rel) >= 0;
            }
            for (const int key : {BTN_LEFT, BTN_RIGHT, BTN_MIDDLE, BTN_SIDE, BTN_EXTRA}) {
                ok = ok && ioctl(outputFd_, UI_SET_KEYBIT, key) >= 0;
            }
        }
        ok = ok && ioctl(outputFd_, UI_DEV_SETUP, &setup) >= 0;
        ok = ok && ioctl(outputFd_, UI_DEV_CREATE) >= 0;
        if (!ok) {
            state("native_error", errnoText("configure uinput"));
            destroyOutput();
            return false;
        }
        std::ostringstream out;
        out << "profile=" << cfg.profile << " name=" << setup.name
            << " range=" << cfg.touchpadMaxX << "x" << cfg.touchpadMaxY
            << " resolution=" << cfg.touchpadResolution << " fd=" << outputFd_;
        state("uinput_created", out.str());
        activeToolKey_ = -1;
        return true;
    }

    void destroyOutput() {
        std::lock_guard<std::recursive_mutex> lock(outputMutex_);
        if (outputFd_ < 0) return;
        releaseAllOutputState("uinput_destroy", true);
        ioctl(outputFd_, UI_DEV_DESTROY);
        close(outputFd_);
        outputFd_ = -1;
        outputReady_.store(false);
        state("uinput_destroyed", "virtual pointer removed");
    }

    void createKeyboard() {
        std::lock_guard<std::recursive_mutex> lock(keyboardMutex_);
        keyboardFd_ = openUinput();
        if (keyboardFd_ < 0) {
            state("native_error", errnoText("open keyboard uinput"));
            return;
        }
        bool ok = ioctl(keyboardFd_, UI_SET_EVBIT, EV_SYN) >= 0 &&
            ioctl(keyboardFd_, UI_SET_EVBIT, EV_KEY) >= 0;
        for (int key = 1; key <= 127; ++key) ok = ok && ioctl(keyboardFd_, UI_SET_KEYBIT, key) >= 0;
        uinput_setup setup{};
        setup.id.bustype = BUS_USB;
        setup.id.vendor = 6353;
        setup.id.product = 5417;
        setup.id.version = 1;
        std::strncpy(setup.name, "Dextop Laptop Keyboard", UINPUT_MAX_NAME_SIZE - 1);
        ok = ok && ioctl(keyboardFd_, UI_DEV_SETUP, &setup) >= 0;
        ok = ok && ioctl(keyboardFd_, UI_DEV_CREATE) >= 0;
        if (!ok) {
            state("native_error", errnoText("configure keyboard uinput"));
            destroyKeyboard();
            return;
        }
        state("keyboard_created", "Dextop Laptop Keyboard");
    }

    void destroyKeyboard() {
        std::lock_guard<std::recursive_mutex> lock(keyboardMutex_);
        if (keyboardFd_ < 0) return;
        releaseAllKeyboardState("keyboard_destroy");
        ioctl(keyboardFd_, UI_DEV_DESTROY);
        close(keyboardFd_);
        keyboardFd_ = -1;
        state("keyboard_destroyed", "Dextop Laptop Keyboard");
    }

    bool isDirectTouchDevice(int fd, Device& device) {
        std::array<unsigned long, (INPUT_PROP_MAX / (sizeof(unsigned long) * 8)) + 1> props{};
        std::array<unsigned long, (ABS_MAX / (sizeof(unsigned long) * 8)) + 1> absBits{};
        if (ioctl(fd, EVIOCGPROP(sizeof(props)), props.data()) < 0) return false;
        if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(absBits)), absBits.data()) < 0) return false;
        if (!testBit(props, INPUT_PROP_DIRECT) || testBit(props, INPUT_PROP_POINTER)) return false;
        if (!testBit(absBits, ABS_MT_POSITION_X) || !testBit(absBits, ABS_MT_POSITION_Y) ||
            !testBit(absBits, ABS_MT_TRACKING_ID)) return false;
        if (ioctl(fd, EVIOCGABS(ABS_MT_POSITION_X), &device.xInfo) < 0 ||
            ioctl(fd, EVIOCGABS(ABS_MT_POSITION_Y), &device.yInfo) < 0) return false;
        return device.xInfo.maximum > device.xInfo.minimum && device.yInfo.maximum > device.yInfo.minimum;
    }

    void scanDevicesIfNeeded() {
        const int64_t now = nowMs();
        if (now - lastScanAt_ < 1'000) return;
        lastScanAt_ = now;
        DIR* directory = opendir("/dev/input");
        if (directory == nullptr) {
            state("native_error", errnoText("opendir", "/dev/input"));
            return;
        }
        std::set<std::string> existing;
        for (const auto& entry : devices_) existing.insert(entry.second->path);
        while (dirent* entry = readdir(directory)) {
            if (std::strncmp(entry->d_name, "event", 5) != 0) continue;
            const std::string path = std::string("/dev/input/") + entry->d_name;
            if (existing.contains(path)) continue;
            int fd = open(path.c_str(), O_RDONLY | O_NONBLOCK | O_CLOEXEC);
            if (fd < 0) {
                if (configSnapshot().debugAllEvents) LOGD("reject path=%s reason=open errno=%d", path.c_str(), errno);
                continue;
            }
            auto device = std::make_unique<Device>();
            device->fd = fd;
            device->path = path;
            device->clear();
            std::array<char, 256> name{};
            if (ioctl(fd, EVIOCGNAME(name.size()), name.data()) >= 0) device->name = name.data();
            ioctl(fd, EVIOCGID, &device->id);
            if (device->name.rfind("Dextop ", 0) == 0 || !isDirectTouchDevice(fd, *device)) {
                if (configSnapshot().debugAllEvents) {
                    LOGD("reject path=%s name=%s reason=capabilities", path.c_str(), device->name.c_str());
                }
                continue;
            }
            epoll_event event{};
            event.events = EPOLLIN | EPOLLERR | EPOLLHUP;
            event.data.fd = fd;
            if (epoll_ctl(epollFd_, EPOLL_CTL_ADD, fd, &event) < 0) {
                state("native_error", errnoText("epoll_ctl add", path));
                continue;
            }
            std::ostringstream out;
            out << "path=" << path << " name=" << device->name
                << " id=" << device->id.vendor << ":" << device->id.product
                << " x=[" << device->xInfo.minimum << ".." << device->xInfo.maximum
                << "] y=[" << device->yInfo.minimum << ".." << device->yInfo.maximum
                << "] resolution=" << device->xInfo.resolution << ":" << device->yInfo.resolution;
            state("device_discovered", out.str());
            devices_.emplace(fd, std::move(device));
        }
        closedir(directory);
        deviceCount_.store(static_cast<int>(devices_.size()));
        if (devices_.empty()) state("device_waiting", "no INPUT_PROP_DIRECT multitouch event nodes available");
    }

    void readDevice(Device& device) {
        std::array<input_event, 64> events{};
        while (running_.load()) {
            const ssize_t bytes = read(device.fd, events.data(), sizeof(events));
            if (bytes < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) return;
            if (bytes < 0 && errno == EINTR) continue;
            if (bytes <= 0) return;
            const size_t count = static_cast<size_t>(bytes) / sizeof(input_event);
            for (size_t index = 0; index < count; ++index) processRawEvent(device, events[index]);
        }
    }

    void processRawEvent(Device& device, const input_event& event) {
        rawEvents_.fetch_add(1);
        const Config cfg = configSnapshot();
        if (cfg.debugAllEvents) {
            LOGD("raw path=%s sec=%lld usec=%lld type=%u code=%u value=%d slot=%d",
                device.path.c_str(), static_cast<long long>(event.time.tv_sec),
                static_cast<long long>(event.time.tv_usec), event.type, event.code, event.value,
                device.currentSlot);
        }
        if (event.type == EV_ABS) {
            if (event.code == ABS_MT_SLOT) {
                device.currentSlot = std::clamp(event.value, 0, kMaxPhysicalSlots - 1);
            } else {
                auto& slot = device.slots[static_cast<size_t>(device.currentSlot)];
                switch (event.code) {
                    case ABS_MT_TRACKING_ID:
                        slot.trackingId = event.value;
                        if (event.value < 0) slot.major = 0;
                        break;
                    case ABS_MT_POSITION_X: slot.x = event.value; break;
                    case ABS_MT_POSITION_Y: slot.y = event.value; break;
                    case ABS_MT_TOUCH_MAJOR: slot.major = event.value; break;
                    default: break;
                }
            }
        } else if (event.type == EV_SYN && event.code == SYN_DROPPED) {
            device.clear();
            cleanupGesture("syn_dropped");
            state("syn_dropped", "path=" + device.path);
        } else if (event.type == EV_SYN && event.code == SYN_REPORT) {
            rawFrames_.fetch_add(1);
            handleFrame(device, device.contacts());
        }
    }

    std::pair<float, float> rotatePoint(const Device& device, int rawX, int rawY, const Config& cfg) const {
        const float xSpan = static_cast<float>(std::max(1, device.xInfo.maximum - device.xInfo.minimum));
        const float ySpan = static_cast<float>(std::max(1, device.yInfo.maximum - device.yInfo.minimum));
        const float x = std::clamp((rawX - device.xInfo.minimum) / xSpan, 0.0f, 1.0f);
        const float y = std::clamp((rawY - device.yInfo.minimum) / ySpan, 0.0f, 1.0f);
        switch (cfg.rotation) {
            case 1: return {y, 1.0f - x};
            case 2: return {1.0f - x, 1.0f - y};
            case 3: return {1.0f - y, x};
            default: return {x, y};
        }
    }

    const Rect* targetRect(const Config& cfg) const {
        if (target_ == Target::TRACKPAD) return &cfg.trackpad;
        if (target_ == Target::FULLSCREEN) return &cfg.fullscreen;
        return nullptr;
    }

    Target chooseTarget(float screenX, float screenY, const Config& cfg) const {
        if (cfg.laptopMode && cfg.trackpad.contains(screenX, screenY)) return Target::TRACKPAD;
        if (!cfg.directTouch && cfg.fullscreen.contains(screenX, screenY)) return Target::FULLSCREEN;
        return Target::IGNORED;
    }

    std::vector<MappedContact> mapContacts(
        const Device& device,
        const std::vector<PhysicalContact>& contacts,
        const Config& cfg
    ) const {
        std::vector<MappedContact> mapped;
        const Rect* rect = targetRect(cfg);
        if (rect == nullptr || !rect->valid()) return mapped;
        for (const auto& contact : contacts) {
            const auto [nx, ny] = rotatePoint(device, contact.x, contact.y, cfg);
            const float screenX = nx * static_cast<float>(std::max(1, cfg.hostWidth - 1));
            const float screenY = ny * static_cast<float>(std::max(1, cfg.hostHeight - 1));
            const float localX = std::clamp(screenX - rect->left, 0.0f, static_cast<float>(rect->width()));
            const float localY = std::clamp(screenY - rect->top, 0.0f, static_cast<float>(rect->height()));
            mapped.push_back({
                contact.trackingId,
                screenX,
                screenY,
                localX,
                localY,
                std::clamp(static_cast<int>(std::lround(localX / rect->width() * cfg.touchpadMaxX)), 0, cfg.touchpadMaxX),
                std::clamp(static_cast<int>(std::lround(localY / rect->height() * cfg.touchpadMaxY)), 0, cfg.touchpadMaxY),
                std::clamp(contact.major > 0 ? contact.major : 20, 1, 255)
            });
        }
        return mapped;
    }

    float tapSlopPixels(const Config& cfg) const {
        const Rect* rect = targetRect(cfg);
        if (rect == nullptr) return 8.0f;
        return std::hypot(static_cast<float>(rect->width()), static_cast<float>(rect->height())) * cfg.tapSlopFraction;
    }

    void beginGesture(const std::vector<MappedContact>& contacts, const Config& cfg) {
        gestureStartedAt_ = nowMs();
        gestureMaxContacts_ = static_cast<int>(contacts.size());
        gestureMoved_ = false;
        gestureTwoFinger_ = contacts.size() >= 2;
        mouseLongPressTriggered_ = false;
        wheelFractionX_ = 0;
        wheelFractionY_ = 0;
        moveFractionX_ = 0;
        moveFractionY_ = 0;
        if (!contacts.empty()) {
            gestureStartX_ = contacts.front().localX;
            gestureStartY_ = contacts.front().localY;
            lastLocalX_ = gestureStartX_;
            lastLocalY_ = gestureStartY_;
        }
        gestureMaxTravel_ = 0;
        touchpadSecondTap_ = false;
        if (cfg.profile == kProfileTouchpad && lastTapValid_ && !contacts.empty()) {
            const int64_t gap = gestureStartedAt_ - lastTapAt_;
            const float distance = std::hypot(contacts.front().localX - lastTapX_, contacts.front().localY - lastTapY_);
            if (gap >= 0 && gap <= cfg.doubleTapTimeoutMs && distance <= tapSlopPixels(cfg) * 2.0f) {
                touchpadSecondTap_ = true;
                leftButtonDown_ = true;
                state("drag", "touchpad second tap armed BTN_LEFT_DOWN gapMs=" + std::to_string(gap) +
                    " distance=" + std::to_string(distance));
                callbackHaptic(false);
            }
        }
        std::ostringstream out;
        out << "profile=" << cfg.profile << " target=" << targetName(target_)
            << " contacts=" << contacts.size() << " secondTap=" << touchpadSecondTap_;
        state("gesture_started", out.str());
    }

    void updateGestureMetrics(const std::vector<MappedContact>& contacts, const Config& cfg) {
        gestureMaxContacts_ = std::max(gestureMaxContacts_, static_cast<int>(contacts.size()));
        if (contacts.size() >= 2) gestureTwoFinger_ = true;
        if (contacts.empty()) return;
        const float travel = std::hypot(contacts.front().localX - gestureStartX_, contacts.front().localY - gestureStartY_);
        gestureMaxTravel_ = std::max(gestureMaxTravel_, travel);
        if (gestureMaxTravel_ > tapSlopPixels(cfg)) gestureMoved_ = true;
    }

    void handleFrame(Device& device, const std::vector<PhysicalContact>& physical) {
        const Config cfg = configSnapshot();
        if (suppressedPhysicalFd_ >= 0) {
            if (device.fd == suppressedPhysicalFd_ && physical.empty()) {
                state(
                    "gesture_resynchronized",
                    "path=" + device.path + " all physical contacts released after config change"
                );
                suppressedPhysicalFd_ = -1;
            }
            return;
        }
        if (cfg.profile == kProfileDisabled || cfg.version != kProtocolVersion) return;
        const int currentActive = activeFd_.load();
        if (currentActive >= 0 && currentActive != device.fd) return;

        if (currentActive < 0 && !physical.empty()) {
            const auto [nx, ny] = rotatePoint(device, physical.front().x, physical.front().y, cfg);
            const float screenX = nx * static_cast<float>(std::max(1, cfg.hostWidth - 1));
            const float screenY = ny * static_cast<float>(std::max(1, cfg.hostHeight - 1));
            target_ = chooseTarget(screenX, screenY, cfg);
            activeFd_.store(device.fd);
            std::ostringstream out;
            out << "path=" << device.path << " name=" << device.name << " target=" << targetName(target_)
                << " screen=" << screenX << ":" << screenY;
            state("source_selected", out.str());
        }

        if (activeFd_.load() != device.fd) return;
        if (target_ == Target::IGNORED) {
            if (physical.empty()) finishPhysicalSource(device, "ignored_all_up");
            return;
        }

        const auto mapped = mapContacts(device, physical, cfg);
        lastContactCount_.store(static_cast<int>(mapped.size()));
        if (cfg.debugAllEvents) {
            std::ostringstream out;
            out << "frame path=" << device.path << " physical=" << physical.size() << " mapped=" << mapped.size()
                << " target=" << targetName(target_) << " contacts=";
            for (const auto& contact : mapped) {
                out << "[id=" << contact.trackingId << " screen=" << contact.screenX << ":" << contact.screenY
                    << " local=" << contact.localX << ":" << contact.localY << " virtual="
                    << contact.virtualX << ":" << contact.virtualY << "]";
            }
            LOGD("%s", out.str().c_str());
        }

        if (gestureStartedAt_ == 0 && !mapped.empty()) beginGesture(mapped, cfg);
        updateGestureMetrics(mapped, cfg);

        if (threeFingerCaptured_) {
            if (physical.empty()) {
                threeFingerCaptured_ = false;
                state("three_finger", "completed; invoking configured action");
                callbackThreeFinger();
                finishPhysicalSource(device, "three_finger_all_up");
            }
            return;
        }
        if (mapped.size() >= 3) {
            threeFingerCaptured_ = true;
            releaseAllOutputState("three_finger_capture", true);
            state("three_finger", "captured contacts=" + std::to_string(mapped.size()));
            callbackHaptic(true);
            return;
        }

        if (cfg.profile == kProfileTouchpad) {
            forwardTouchpad(mapped, cfg);
        } else if (cfg.profile == kProfileMouse) {
            forwardMouse(mapped, cfg);
        }

        if (physical.empty()) finishPhysicalSource(device, "all_contacts_up");
    }

    int allocateVirtualSlot(int physicalId) {
        for (int slot = 0; slot < kMaxVirtualSlots; ++slot) {
            if (virtualSlotPhysicalIds_[static_cast<size_t>(slot)] == physicalId) return slot;
        }
        for (int slot = 0; slot < kMaxVirtualSlots; ++slot) {
            if (virtualSlotPhysicalIds_[static_cast<size_t>(slot)] >= 0) continue;
            virtualSlotPhysicalIds_[static_cast<size_t>(slot)] = physicalId;
            virtualSlotTrackingIds_[static_cast<size_t>(slot)] = nextVirtualTrackingId_++;
            if (nextVirtualTrackingId_ >= 65535) nextVirtualTrackingId_ = 1;
            return slot;
        }
        return -1;
    }

    int activeVirtualSlots() const {
        return static_cast<int>(std::count_if(
            virtualSlotPhysicalIds_.begin(), virtualSlotPhysicalIds_.end(), [](int id) { return id >= 0; }));
    }

    int toolKeyForContacts(int count) const {
        switch (count) {
            case 1: return BTN_TOOL_FINGER;
            case 2: return BTN_TOOL_DOUBLETAP;
            case 3: return BTN_TOOL_TRIPLETAP;
            case 4: return BTN_TOOL_QUADTAP;
            default: return count >= 5 ? BTN_TOOL_QUINTTAP : -1;
        }
    }

    void forwardTouchpad(const std::vector<MappedContact>& contacts, const Config&) {
        const int previousCount = activeVirtualSlots();
        std::set<int> activeIds;
        for (const auto& contact : contacts) activeIds.insert(contact.trackingId);
        std::vector<input_event> events;
        for (int slot = 0; slot < kMaxVirtualSlots; ++slot) {
            const int physicalId = virtualSlotPhysicalIds_[static_cast<size_t>(slot)];
            if (physicalId < 0 || activeIds.contains(physicalId)) continue;
            events.push_back(makeEvent(EV_ABS, ABS_MT_SLOT, slot));
            events.push_back(makeEvent(EV_ABS, ABS_MT_TRACKING_ID, -1));
            virtualSlotPhysicalIds_[static_cast<size_t>(slot)] = -1;
            virtualSlotTrackingIds_[static_cast<size_t>(slot)] = -1;
        }
        for (const auto& contact : contacts) {
            int existing = -1;
            for (int slot = 0; slot < kMaxVirtualSlots; ++slot) {
                if (virtualSlotPhysicalIds_[static_cast<size_t>(slot)] == contact.trackingId) existing = slot;
            }
            const int slot = allocateVirtualSlot(contact.trackingId);
            if (slot < 0) continue;
            events.push_back(makeEvent(EV_ABS, ABS_MT_SLOT, slot));
            if (existing < 0) {
                events.push_back(makeEvent(EV_ABS, ABS_MT_TRACKING_ID,
                    virtualSlotTrackingIds_[static_cast<size_t>(slot)]));
            }
            events.push_back(makeEvent(EV_ABS, ABS_MT_POSITION_X, contact.virtualX));
            events.push_back(makeEvent(EV_ABS, ABS_MT_POSITION_Y, contact.virtualY));
            events.push_back(makeEvent(EV_ABS, ABS_MT_TOUCH_MAJOR, contact.major));
            events.push_back(makeEvent(EV_ABS, ABS_MT_PRESSURE, 40));
        }
        const int currentCount = activeVirtualSlots();
        if (previousCount == 0 && currentCount > 0) events.push_back(makeEvent(EV_KEY, BTN_TOUCH, 1));
        const int nextToolKey = toolKeyForContacts(currentCount);
        if (activeToolKey_ != nextToolKey) {
            if (activeToolKey_ >= 0) {
                events.push_back(makeEvent(EV_KEY, static_cast<uint16_t>(activeToolKey_), 0));
            }
            if (nextToolKey >= 0) {
                events.push_back(makeEvent(EV_KEY, static_cast<uint16_t>(nextToolKey), 1));
            }
            activeToolKey_ = nextToolKey;
            state(
                "touchpad_contact_count",
                "previous=" + std::to_string(previousCount) +
                    " current=" + std::to_string(currentCount) +
                    " toolKey=" + std::to_string(activeToolKey_)
            );
        }
        if (touchpadSecondTap_ && leftButtonDown_ && previousCount == 0 && currentCount > 0) {
            events.push_back(makeEvent(EV_KEY, BTN_LEFT, 1));
        }
        if (previousCount > 0 && currentCount == 0) events.push_back(makeEvent(EV_KEY, BTN_TOUCH, 0));
        if (currentCount == 0 && leftButtonDown_) {
            events.push_back(makeEvent(EV_KEY, BTN_LEFT, 0));
            leftButtonDown_ = false;
            state("drag", "touchpad BTN_LEFT_UP all_contacts_up");
        }
        if (!events.empty()) {
            events.push_back(makeEvent(EV_SYN, SYN_REPORT, 0));
            emit(events, false, "touchpad_frame");
        }
    }

    void emitButton(int button, bool pressed, const std::string& reason) {
        std::vector<input_event> events{
            makeEvent(EV_KEY, static_cast<uint16_t>(button), pressed ? 1 : 0),
            makeEvent(EV_SYN, SYN_REPORT, 0)
        };
        emit(events, false, reason);
        if (button == BTN_LEFT) leftButtonDown_ = pressed;
        state("button", "code=" + std::to_string(button) + " pressed=" +
            std::to_string(pressed) + " reason=" + reason);
    }

    void emitClick(int button, const std::string& reason) {
        std::vector<input_event> events{
            makeEvent(EV_KEY, static_cast<uint16_t>(button), 1),
            makeEvent(EV_SYN, SYN_REPORT, 0),
            makeEvent(EV_KEY, static_cast<uint16_t>(button), 0),
            makeEvent(EV_SYN, SYN_REPORT, 0)
        };
        emit(events, false, reason);
        state("click", "code=" + std::to_string(button) + " reason=" + reason);
    }

    void forwardMouse(const std::vector<MappedContact>& contacts, const Config& cfg) {
        if (contacts.empty()) {
            finishMouseGesture(cfg);
            return;
        }
        const auto& first = contacts.front();
        const float dx = (first.localX - lastLocalX_) * cfg.mouseSensitivity;
        const float dy = (first.localY - lastLocalY_) * cfg.mouseSensitivity;
        lastLocalX_ = first.localX;
        lastLocalY_ = first.localY;
        if (contacts.size() >= 2) {
            gestureTwoFinger_ = true;
            if (std::fabs(dx) > 0.01f || std::fabs(dy) > 0.01f) {
                const float direction = cfg.naturalScroll ? 1.0f : -1.0f;
                wheelFractionX_ += dx * direction / 12.0f;
                wheelFractionY_ += -dy * direction / 12.0f;
                const int hWheel = std::clamp(static_cast<int>(wheelFractionX_), -12, 12);
                const int wheel = std::clamp(static_cast<int>(wheelFractionY_), -12, 12);
                if (hWheel != 0 || wheel != 0) {
                    wheelFractionX_ -= hWheel;
                    wheelFractionY_ -= wheel;
                    std::vector<input_event> events;
                    if (hWheel != 0) events.push_back(makeEvent(EV_REL, REL_HWHEEL, hWheel));
                    if (wheel != 0) events.push_back(makeEvent(EV_REL, REL_WHEEL, wheel));
                    events.push_back(makeEvent(EV_SYN, SYN_REPORT, 0));
                    emit(events, false, "mouse_scroll");
                }
            }
            return;
        }
        moveFractionX_ += dx;
        moveFractionY_ += dy;
        const int relX = static_cast<int>(moveFractionX_);
        const int relY = static_cast<int>(moveFractionY_);
        if (relX != 0 || relY != 0) {
            std::vector<input_event> events;
            if (relX != 0) events.push_back(makeEvent(EV_REL, REL_X, relX));
            if (relY != 0) events.push_back(makeEvent(EV_REL, REL_Y, relY));
            moveFractionX_ -= static_cast<float>(relX);
            moveFractionY_ -= static_cast<float>(relY);
            if (!events.empty()) {
                events.push_back(makeEvent(EV_SYN, SYN_REPORT, 0));
                emit(events, false, "mouse_move");
            }
        }
    }

    void checkMouseLongPress() {
        const Config cfg = configSnapshot();
        if (cfg.profile != kProfileMouse || gestureStartedAt_ == 0 ||
            lastContactCount_.load() != 1 || gestureTwoFinger_ || gestureMoved_ ||
            mouseLongPressTriggered_) return;
        if (nowMs() - gestureStartedAt_ < 450) return;
        mouseLongPressTriggered_ = true;
        emitButton(BTN_LEFT, true, "mouse_long_press_drag");
        callbackHaptic(true);
    }

    void finishMouseGesture(const Config& cfg) {
        if (gestureStartedAt_ == 0) return;
        const int64_t duration = nowMs() - gestureStartedAt_;
        if (leftButtonDown_) {
            emitButton(BTN_LEFT, false, "mouse_drag_release");
        } else if (gestureTwoFinger_ && !gestureMoved_) {
            emitClick(BTN_RIGHT, "mouse_two_finger_tap");
            callbackHaptic(true);
        } else if (!gestureTwoFinger_ && !gestureMoved_ && duration <= cfg.tapTimeoutMs) {
            emitClick(BTN_LEFT, "mouse_tap");
            callbackHaptic(false);
        }
    }

    void finishPhysicalSource(Device& device, const std::string& reason) {
        const Config cfg = configSnapshot();
        const int64_t duration = gestureStartedAt_ > 0 ? nowMs() - gestureStartedAt_ : 0;
        if (cfg.profile == kProfileTouchpad && gestureStartedAt_ > 0) {
            const bool tap = gestureMaxContacts_ == 1 && !gestureMoved_ && duration <= cfg.tapTimeoutMs;
            if (tap && !touchpadSecondTap_) {
                lastTapValid_ = true;
                lastTapAt_ = nowMs();
                lastTapX_ = lastLocalX_;
                lastTapY_ = lastLocalY_;
            } else {
                lastTapValid_ = false;
            }
        }
        std::ostringstream out;
        out << "reason=" << reason << " path=" << device.path << " target=" << targetName(target_)
            << " durationMs=" << duration << " maxContacts=" << gestureMaxContacts_
            << " maxTravel=" << gestureMaxTravel_ << " moved=" << gestureMoved_;
        state("gesture_finished", out.str());
        resetGestureState();
    }

    void resetGestureState() {
        target_ = Target::NONE;
        activeFd_.store(-1);
        lastContactCount_.store(0);
        gestureStartedAt_ = 0;
        gestureMaxContacts_ = 0;
        gestureStartX_ = 0;
        gestureStartY_ = 0;
        gestureMaxTravel_ = 0;
        lastLocalX_ = 0;
        lastLocalY_ = 0;
        gestureMoved_ = false;
        gestureTwoFinger_ = false;
        touchpadSecondTap_ = false;
        mouseLongPressTriggered_ = false;
        wheelFractionX_ = 0;
        wheelFractionY_ = 0;
        moveFractionX_ = 0;
        moveFractionY_ = 0;
    }

    void releaseAllOutputState(const std::string& reason, bool force) {
        std::lock_guard<std::recursive_mutex> lock(outputMutex_);
        std::vector<input_event> events;
        for (int slot = 0; slot < kMaxVirtualSlots; ++slot) {
            if (virtualSlotPhysicalIds_[static_cast<size_t>(slot)] < 0) continue;
            events.push_back(makeEvent(EV_ABS, ABS_MT_SLOT, slot));
            events.push_back(makeEvent(EV_ABS, ABS_MT_TRACKING_ID, -1));
            virtualSlotPhysicalIds_[static_cast<size_t>(slot)] = -1;
            virtualSlotTrackingIds_[static_cast<size_t>(slot)] = -1;
        }
        if (!events.empty()) events.push_back(makeEvent(EV_KEY, BTN_TOUCH, 0));
        if (activeToolKey_ >= 0) {
            events.push_back(makeEvent(EV_KEY, static_cast<uint16_t>(activeToolKey_), 0));
            activeToolKey_ = -1;
        }
        if (leftButtonDown_) {
            events.push_back(makeEvent(EV_KEY, BTN_LEFT, 0));
            leftButtonDown_ = false;
        }
        if (!events.empty()) {
            events.push_back(makeEvent(EV_SYN, SYN_REPORT, 0));
            if (force) writeEvents(outputFd_, events, reason); else emit(events, false, reason);
        }
    }

    void cancelGestureForConfigChange(const Config& previous, const Config& next) {
        const int physicalFd = activeFd_.load();
        releaseAllOutputState("config_changed", true);
        if (gestureStartedAt_ > 0 || physicalFd >= 0) {
            std::ostringstream out;
            out << "oldGeneration=" << previous.generation
                << " newGeneration=" << next.generation
                << " oldProfile=" << previous.profile << " newProfile=" << next.profile
                << " target=" << targetName(target_) << " physicalFd=" << physicalFd;
            state("gesture_cancelled", "reason=config_changed " + out.str());
        }
        threeFingerCaptured_ = false;
        lastTapValid_ = false;
        resetGestureState();

        const auto device = devices_.find(physicalFd);
        if (physicalFd >= 0 && device != devices_.end() && !device->second->contacts().empty()) {
            suppressedPhysicalFd_ = physicalFd;
            state(
                "gesture_suppressed_until_all_up",
                "path=" + device->second->path +
                    " contacts=" + std::to_string(device->second->contacts().size())
            );
        }
    }

    void cleanupGesture(const std::string& reason) {
        releaseAllOutputState(reason, true);
        if (gestureStartedAt_ > 0 || activeFd_.load() >= 0) {
            state("gesture_cancelled", "reason=" + reason + " target=" + targetName(target_));
        }
        threeFingerCaptured_ = false;
        lastTapValid_ = false;
        suppressedPhysicalFd_ = -1;
        resetGestureState();
        for (auto& [fd, device] : devices_) {
            (void)fd;
            device->clear();
        }
    }

    void emitStatsIfNeeded() {
        const int64_t now = nowMs();
        if (now - lastStatsAt_ < 1'000) return;
        lastStatsAt_ = now;
        std::ostringstream out;
        out << "rawEvents=" << rawEvents_.load() << " rawFrames=" << rawFrames_.load()
            << " outputFrames=" << outputFrames_.load() << " dropped=" << droppedFrames_.load()
            << " devices=" << deviceCount_.load() << " activeFd=" << activeFd_.load()
            << " contacts=" << lastContactCount_.load() << " leftDown=" << leftButtonDown_
            << " toolKey=" << activeToolKey_ << " suppressedFd=" << suppressedPhysicalFd_
            << " ready=" << outputReady_.load();
        state("stats", out.str());
    }

    void closeDevices() {
        devices_.clear();
        deviceCount_.store(0);
        activeFd_.store(-1);
        suppressedPhysicalFd_ = -1;
    }
};

std::mutex gMutex;
std::unique_ptr<Engine> gEngine;

Engine* engineFor(JNIEnv* env, jobject service) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!gEngine) {
        JavaVM* vm = nullptr;
        env->GetJavaVM(&vm);
        gEngine = std::make_unique<Engine>(vm, env, service);
    }
    return gEngine.get();
}

std::string jstringText(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars != nullptr ? chars : "";
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::vector<int> jintArrayValues(JNIEnv* env, jintArray array) {
    if (array == nullptr) return {};
    const jsize size = env->GetArrayLength(array);
    std::vector<int> values(static_cast<size_t>(size));
    env->GetIntArrayRegion(array, 0, size, reinterpret_cast<jint*>(values.data()));
    return values;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_moe_n4tsu_dextop_input_PrivilegedInputService_nativeProbe(
    JNIEnv* env, jobject service) {
    const std::string result = engineFor(env, service)->probe();
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_input_PrivilegedInputService_nativeConfigure(
    JNIEnv* env, jobject service, jintArray config) {
    engineFor(env, service)->configure(jintArrayValues(env, config));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_moe_n4tsu_dextop_input_PrivilegedInputService_nativeStart(
    JNIEnv* env, jobject service) {
    return engineFor(env, service)->start() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_input_PrivilegedInputService_nativeSetOutputReady(
    JNIEnv* env, jobject service, jboolean ready) {
    engineFor(env, service)->setOutputReady(ready == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_input_PrivilegedInputService_nativeInject(
    JNIEnv* env, jobject service, jintArray events) {
    engineFor(env, service)->inject(jintArrayValues(env, events));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_moe_n4tsu_dextop_input_PrivilegedInputService_nativeInjectKeyboard(
    JNIEnv* env,
    jobject service,
    jint keyCode,
    jint action,
    jint metaState,
    jint repeatCount) {
    return engineFor(env, service)->injectKeyboard(keyCode, action, metaState, repeatCount)
        ? JNI_TRUE
        : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_input_PrivilegedInputService_nativeSetKeyboardVisible(
    JNIEnv* env, jobject service, jboolean visible) {
    engineFor(env, service)->setKeyboardVisible(visible == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_input_PrivilegedInputService_nativeStop(
    JNIEnv* env, jobject service, jstring reason) {
    engineFor(env, service)->stop(jstringText(env, reason));
}
