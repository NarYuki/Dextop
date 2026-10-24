#pragma once
#include <cstdint>
#include <vector>
struct ARect { int32_t left, top, right, bottom; };
struct ANativeWindow_Buffer { int32_t width, height, stride, format; void *bits; };
struct ANativeWindow { int width=0,height=0,posts=0; bool expand=true,fail=false; std::vector<uint32_t> pixels; };
#define WINDOW_FORMAT_RGBX_8888 2
inline int ANativeWindow_setBuffersGeometry(ANativeWindow*w,int width,int height,int){w->width=width;w->height=height;w->pixels.assign(width*height,0);return 0;}
inline void ANativeWindow_release(ANativeWindow*){}
inline int ANativeWindow_lock(ANativeWindow*w,ANativeWindow_Buffer*out,ARect*dirty){if(w->fail)return -1;if(w->expand){*dirty={0,0,w->width,w->height};w->pixels.assign(w->width*w->height,0xcccccccc);}*out={w->width,w->height,w->width,2,w->pixels.data()};return 0;}
inline int ANativeWindow_unlockAndPost(ANativeWindow*w){w->posts++;return 0;}
