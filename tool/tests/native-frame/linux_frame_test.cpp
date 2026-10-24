#include <cassert>
#include <cstdio>
#include "../../../android/app/src/main/cpp/linux_bridge.cpp"
int main(){
 ANativeWindow win;ANativeWindow_setBuffersGeometry(&win,4,2,2);Target t{&win,4,2};
 uint32_t initial[8]={0x00010203,0x00040506,0x00070809,0x000a0b0c,0x000d0e0f,0x00101112,0x00131415,0x00161718};
 assert(stage(&t,(uint8_t*)initial,sizeof(initial),16,0,0,4,2));assert(present(&t));
 auto old=win.pixels;uint32_t red=0x00ff0000,blue=0x000000ff;
 assert(stage(&t,(uint8_t*)&red,4,4,0,0,1,1));assert(stage(&t,(uint8_t*)&blue,4,4,3,1,1,1));
 assert(win.posts==1);assert(present(&t));assert(win.posts==2);
 assert(win.pixels[0]==0xff0000ff);assert(win.pixels[7]==0xffff0000);
 for(int i=1;i<7;i++)assert(win.pixels[i]==old[i]);
 assert(!stage(&t,(uint8_t*)initial,3,4,0,0,1,1));assert(!stage(&t,(uint8_t*)initial,4,2,0,0,1,1));
 win.fail=true;assert(stage(&t,(uint8_t*)&blue,4,4,0,0,1,1));assert(!present(&t));win.fail=false;assert(present(&t));assert(win.pixels[0]==0xffff0000);
 uint32_t row[2]={0x00112233,0x00445566};assert(stage(&t,(uint8_t*)row,8,8,-1,0,2,1));assert(present(&t));assert(win.pixels[0]==0xff665544);
 assert(Java_moe_n4tsu_dextop_linux_LinuxFrameBridge_nativeResize(nullptr,nullptr,(jlong)&t,2,2));assert(t.image.empty());
 puts("WIDENED_DIRTY_RETAINED_PIXELS_BATCH_SINGLE_POST_RETRY_RESIZE_CLIPPING_OK");
}
