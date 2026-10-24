#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <shellapi.h>
#include <stdio.h>
static int left,right;
static void note(const char *s){DWORD n;HANDLE f=CreateFileA("Z:\\tmp\\dextop-wine-tray-probe.log",FILE_APPEND_DATA,FILE_SHARE_READ|FILE_SHARE_WRITE,0,OPEN_ALWAYS,0,0);if(f!=INVALID_HANDLE_VALUE){WriteFile(f,s,(DWORD)lstrlenA(s),&n,0);CloseHandle(f);}}
static LRESULT CALLBACK proc(HWND w,UINT m,WPARAM wp,LPARAM lp){
 if(m==WM_APP+1){if(lp==WM_LBUTTONUP){left=1;note("WINE_TRAY_PRIMARY_OK\n");}if(lp==WM_RBUTTONUP){right=1;note("WINE_TRAY_CONTEXT_OK\n");}return 0;}
 return DefWindowProcA(w,m,wp,lp);
}
static HICON icon(DWORD color){BITMAPINFO info={0};info.bmiHeader.biSize=sizeof(BITMAPINFOHEADER);info.bmiHeader.biWidth=32;info.bmiHeader.biHeight=-32;info.bmiHeader.biPlanes=1;info.bmiHeader.biBitCount=32;info.bmiHeader.biCompression=BI_RGB;DWORD *pixels;HBITMAP bitmap=CreateDIBSection(0,&info,DIB_RGB_COLORS,(void**)&pixels,0,0);BYTE mask[128];memset(mask,255,sizeof(mask));for(int y=0;y<32;y++)for(int x=0;x<32;x++){int solid=x>=6&&x<26&&y>=6&&y<26;pixels[y*32+x]=solid?color:0;if(solid)mask[y*4+x/8]&=~(128>>(x%8));}HBITMAP mono=CreateBitmap(32,32,1,1,mask);ICONINFO ii={0};ii.fIcon=TRUE;ii.hbmMask=mono;ii.hbmColor=bitmap;HICON image=CreateIconIndirect(&ii);DeleteObject(mono);DeleteObject(bitmap);return image;}
int main(void){DEVMODEA dm={0};dm.dmSize=sizeof(dm);if(!EnumDisplaySettingsA(0,ENUM_CURRENT_SETTINGS,&dm)||dm.dmDisplayFrequency!=120){note("WINE_SESSION_RATE_WRONG\n");return 5;}note("WINE_SESSION_120HZ_OK\n");WNDCLASSA c={0};c.lpfnWndProc=proc;c.hInstance=GetModuleHandleA(0);c.lpszClassName="DextopWineTrayProbe";RegisterClassA(&c);
 HWND w=CreateWindowA(c.lpszClassName,"Dextop Wine Tray Probe",WS_OVERLAPPEDWINDOW,0,0,32,32,0,0,c.hInstance,0);
 NOTIFYICONDATAA n={0};n.cbSize=sizeof(n);n.hWnd=w;n.uID=1;n.uFlags=NIF_MESSAGE|NIF_ICON|NIF_TIP;n.uCallbackMessage=WM_APP+1;n.hIcon=icon(0xffff0000);lstrcpyA(n.szTip,"Dextop Wine Tray Probe");
 if(!Shell_NotifyIconA(NIM_ADD,&n)){note("WINE_TRAY_ADD_FAILED\n");return 2;}note("WINE_TRAY_ADDED\n");
 NOTIFYICONDATAA n2=n;n2.uID=2;n2.hIcon=icon(0xff00ff00);lstrcpyA(n2.szTip,"Dextop Wine Second Icon");if(!Shell_NotifyIconA(NIM_ADD,&n2))return 4;
 ULONGLONG end=GetTickCount64()+60000;while(GetTickCount64()<end&&!(left&&right)){MSG m;while(PeekMessageA(&m,0,0,0,PM_REMOVE)){TranslateMessage(&m);DispatchMessageA(&m);}Sleep(20);}
 Shell_NotifyIconA(NIM_DELETE,&n2);Shell_NotifyIconA(NIM_DELETE,&n);DestroyWindow(w);note(left&&right?"WINE_TRAY_ALL_OK\n":"WINE_TRAY_TIMEOUT\n");return left&&right?0:3;}
