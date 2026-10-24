#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
static int seen;
static void note(const char*s){DWORD n;HANDLE f=CreateFileA("Z:\\tmp\\dextop-raw-pointer.log",FILE_APPEND_DATA,FILE_SHARE_READ|FILE_SHARE_WRITE,0,OPEN_ALWAYS,0,0);if(f!=INVALID_HANDLE_VALUE){WriteFile(f,s,lstrlenA(s),&n,0);CloseHandle(f);}}
static LRESULT CALLBACK proc(HWND w,UINT m,WPARAM wp,LPARAM lp){
 if(m==WM_INPUT){RAWINPUT raw;UINT size=sizeof(raw);if(GetRawInputData((HRAWINPUT)lp,RID_INPUT,&raw,&size,sizeof(RAWINPUTHEADER))!=(UINT)-1&&raw.header.dwType==RIM_TYPEMOUSE&&!(raw.data.mouse.usFlags&MOUSE_MOVE_ABSOLUTE)&&raw.data.mouse.lLastX==7&&raw.data.mouse.lLastY==-3){seen=1;note("WINE_RAW_RELATIVE_OK\n");}return DefWindowProcA(w,m,wp,lp);}
 return DefWindowProcA(w,m,wp,lp);
}
int main(){WNDCLASSA c={0};c.lpfnWndProc=proc;c.hInstance=GetModuleHandleA(0);c.lpszClassName="DextopRawPointerProbe";RegisterClassA(&c);HWND w=CreateWindowA(c.lpszClassName,"Dextop raw input probe",WS_OVERLAPPEDWINDOW,30,30,300,250,0,0,c.hInstance,0);ShowWindow(w,SW_SHOW);UpdateWindow(w);SetForegroundWindow(w);SetFocus(w);
 RAWINPUTDEVICE device={1,2,RIDEV_INPUTSINK,w};if(!RegisterRawInputDevices(&device,1,sizeof(device)))return 2;
 RECT r;GetClientRect(w,&r);MapWindowPoints(w,0,(POINT*)&r,2);SetCapture(w);ClipCursor(&r);note("WINE_RAW_READY\n");
 ULONGLONG end=GetTickCount64()+12000;while(!seen&&GetTickCount64()<end){MSG m;while(PeekMessageA(&m,0,0,0,PM_REMOVE)){TranslateMessage(&m);DispatchMessageA(&m);}Sleep(10);}
 ClipCursor(0);ReleaseCapture();DestroyWindow(w);return seen?0:3;
}
