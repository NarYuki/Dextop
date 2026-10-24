#define COBJMACROS
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <d3d9.h>
#include <stdio.h>
#include <string.h>
static void note(const char*s){DWORD n;HANDLE f=CreateFileA("Z:\\tmp\\dextop-d3d9-probe.txt",FILE_APPEND_DATA,FILE_SHARE_READ|FILE_SHARE_WRITE,0,OPEN_ALWAYS,0,0);if(f!=INVALID_HANDLE_VALUE){WriteFile(f,s,(DWORD)strlen(s),&n,0);CloseHandle(f);}puts(s);fflush(stdout);}
int main(int argc,char **argv){HMODULE dll=LoadLibraryA("d3d9.dll");IDirect3D9*(WINAPI*create)(UINT)=(void*)GetProcAddress(dll,"Direct3DCreate9");if(!create)return 2;
 HWND window=CreateWindowExA(0,"STATIC","Dextop D3D9 isolated probe",WS_OVERLAPPEDWINDOW|WS_VISIBLE,0,0,320,240,0,0,GetModuleHandleA(0),0);
 IDirect3D9*d3d=create(D3D_SDK_VERSION);if(!d3d||!window)return 3;D3DPRESENT_PARAMETERS p={0};p.Windowed=TRUE;p.SwapEffect=D3DSWAPEFFECT_DISCARD;p.hDeviceWindow=window;p.BackBufferWidth=320;p.BackBufferHeight=240;p.BackBufferFormat=D3DFMT_X8R8G8B8;p.PresentationInterval=argc>1?D3DPRESENT_INTERVAL_ONE:D3DPRESENT_INTERVAL_IMMEDIATE;IDirect3DDevice9*dev=0;
 HRESULT hr=IDirect3D9_CreateDevice(d3d,D3DADAPTER_DEFAULT,D3DDEVTYPE_HAL,window,D3DCREATE_SOFTWARE_VERTEXPROCESSING,&p,&dev);char text[180];snprintf(text,sizeof(text),"D3D9_CREATE hr=%08lx\n",(unsigned long)hr);note(text);if(FAILED(hr))return 4;
 ULONGLONG start=GetTickCount64();unsigned frames=0;for(;frames<30;frames++){MSG msg;while(PeekMessageA(&msg,0,0,0,PM_REMOVE)){TranslateMessage(&msg);DispatchMessageA(&msg);}hr=IDirect3DDevice9_Clear(dev,0,0,D3DCLEAR_TARGET,0xff000000|(frames*123456),1,0);if(FAILED(hr))break;snprintf(text,sizeof(text),"D3D9_PRESENT_ENTER frame=%u\n",frames);note(text);hr=IDirect3DDevice9_Present(dev,0,0,0,0);snprintf(text,sizeof(text),"D3D9_PRESENT_RETURN frame=%u hr=%08lx\n",frames,(unsigned long)hr);note(text);if(FAILED(hr))break;}
 snprintf(text,sizeof(text),"D3D9_PRESENT frames=%u hr=%08lx elapsed_ms=%llu\n",frames,(unsigned long)hr,GetTickCount64()-start);note(text);IDirect3DDevice9_Release(dev);IDirect3D9_Release(d3d);DestroyWindow(window);return frames==30?0:5;}