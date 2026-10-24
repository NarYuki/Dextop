#pragma once
#define ANDROID_LOG_WARN 5
inline int __android_log_print(int,const char*,const char*,...){return 0;}
