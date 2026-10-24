/* Wine's tray adapter has no window title. Publish its notification tooltip/app
 * identity through WM_SETTEXT, without changing an application's window or code.
 * Wine icon layout: programs/explorer/systray.c, LGPL-2.1-or-later (Wine project).
 * Only validated __wine_tray_icon adapters are read; unknown layouts are skipped. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdint.h>
#include <wchar.h>
#include <stdlib.h>
struct icon64 {uint64_t next,prev,image,owner,window;int32_t layered,pad;uint64_t tooltip;uint32_t state,id,callback;int32_t display;WCHAR tip[128];};
struct icon32 {uint32_t next,prev,image,owner,window;int32_t layered;uint32_t tooltip,state,id,callback;int32_t display;WCHAR tip[128];};
static BOOL CALLBACK update(HWND window,LPARAM unused){
 WCHAR cls[80],name[512]={0},before[512],path[1024];DWORD pid=0;SIZE_T got;BOOL wow=FALSE;HWND owner=0;WCHAR tip[128]={0};
 (void)unused;
 if(!GetClassNameW(window,cls,80)||wcscmp(cls,L"__wine_tray_icon"))return TRUE;
 GetWindowThreadProcessId(window,&pid);HANDLE process=OpenProcess(PROCESS_VM_READ|PROCESS_QUERY_LIMITED_INFORMATION,FALSE,pid);
 if(!process)return TRUE;
 uintptr_t pointer=(uintptr_t)GetWindowLongPtrW(window,GWLP_USERDATA);
 IsWow64Process(process,&wow);
 if(pointer&&wow){struct icon32 icon;
  if(ReadProcessMemory(process,(void*)pointer,&icon,sizeof(icon),&got)&&got==sizeof(icon)&&icon.window==(uint32_t)(uintptr_t)window&&icon.layered>=0&&icon.layered<=1){owner=(HWND)(uintptr_t)icon.owner;memcpy(tip,icon.tip,sizeof(tip));}
 }else if(pointer){struct icon64 icon;
  if(ReadProcessMemory(process,(void*)pointer,&icon,sizeof(icon),&got)&&got==sizeof(icon)&&icon.window==(uint64_t)(uintptr_t)window&&icon.layered>=0&&icon.layered<=1){owner=(HWND)(uintptr_t)icon.owner;memcpy(tip,icon.tip,sizeof(tip));}
 }
 CloseHandle(process);
 if(!owner||!IsWindow(owner))return TRUE;
 tip[127]=0;
 if(tip[0]){lstrcpynW(name,tip,512);WCHAR *end=wcspbrk(name,L"\r\n");if(end)*end=0;}
 if(!name[0])GetWindowTextW(owner,name,512);
 if(!name[0]){
  GetWindowThreadProcessId(owner,&pid);process=OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION,FALSE,pid);
  if(process){DWORD length=1024;if(QueryFullProcessImageNameW(process,0,path,&length)){WCHAR *base=wcsrchr(path,L'\\');lstrcpynW(name,base?base+1:path,512);WCHAR *dot=wcsrchr(name,L'.');if(dot&&!lstrcmpiW(dot,L".exe"))*dot=0;}CloseHandle(process);}
 }
 if(!name[0])return TRUE;
 GetWindowTextW(window,before,512);
 if(wcscmp(name,before))SetWindowTextW(window,name);
 return TRUE;
}
int main(int argc,char **argv){
 HANDLE mutex=CreateMutexW(NULL,FALSE,L"Local\\DextopWineTrayMetadataV1");
 if(!mutex||GetLastError()==ERROR_ALREADY_EXISTS)return 0;
 typedef LONG (WINAPI *setinfo_t)(HANDLE,ULONG,void*,ULONG);
 setinfo_t setinfo=(setinfo_t)GetProcAddress(GetModuleHandleW(L"ntdll.dll"),"NtSetInformationProcess");
 HANDLE shutdown=0;
 /* Wine system processes do not keep Proton's waitforexitandrun alive. */
 if(!setinfo||setinfo(GetCurrentProcess(),1000,&shutdown,sizeof(shutdown))<0||!shutdown)return 78;
 do{EnumWindows(update,0);if(argc>1&&argv[1][0]=='1')break;}while(WaitForSingleObject(shutdown,500)==WAIT_TIMEOUT);
 CloseHandle(shutdown);CloseHandle(mutex);return 0;
}
