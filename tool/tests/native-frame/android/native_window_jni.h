#pragma once
#include <jni.h>
#include "native_window.h"
inline ANativeWindow* ANativeWindow_fromSurface(JNIEnv*,jobject surface){return reinterpret_cast<ANativeWindow*>(surface);}
