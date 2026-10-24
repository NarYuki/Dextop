#define UNICODE
#include <windows.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
typedef struct {int user;int callback;unsigned char *data;int size;} Callback;
static void note(const char*s){DWORD n;HANDLE f=CreateFileW(L"Z:\\tmp\\dextop-steam-session-ticket-probe.txt",FILE_APPEND_DATA,FILE_SHARE_READ|FILE_SHARE_WRITE,0,OPEN_ALWAYS,0,0);if(f!=INVALID_HANDLE_VALUE){WriteFile(f,s,(DWORD)strlen(s),&n,0);CloseHandle(f);}puts(s);fflush(stdout);}
#define LOAD(type,name) type name=(void*)GetProcAddress(dll,#name)
typedef bool(*Init)(void);typedef int(*Handle)(void);typedef void(*Void)(void);typedef void(*Frame)(int);typedef bool(*Next)(int,Callback*);typedef void*(*Interface)(int,const char*);typedef unsigned(*Ticket)(void*,void*,int,unsigned*,void*);typedef void(*Cancel)(void*,unsigned);
int wmain(int argc,WCHAR **argv){
 if(argc!=2)return 2;HMODULE dll=LoadLibraryW(argv[1]);if(!dll){note("SDK_LOAD_FAILED\n");return 3;}
 LOAD(Init,SteamAPI_Init);LOAD(Handle,SteamAPI_GetHSteamUser);LOAD(Handle,SteamAPI_GetHSteamPipe);LOAD(Interface,SteamInternal_FindOrCreateUserInterface);LOAD(Void,SteamAPI_ManualDispatch_Init);LOAD(Frame,SteamAPI_ManualDispatch_RunFrame);LOAD(Next,SteamAPI_ManualDispatch_GetNextCallback);LOAD(Frame,SteamAPI_ManualDispatch_FreeLastCallback);LOAD(Ticket,SteamAPI_ISteamUser_GetAuthSessionTicket);LOAD(Cancel,SteamAPI_ISteamUser_CancelAuthTicket);LOAD(Void,SteamAPI_Shutdown);
 if(!SteamAPI_Init||!SteamAPI_GetHSteamUser||!SteamAPI_GetHSteamPipe||!SteamInternal_FindOrCreateUserInterface||!SteamAPI_ManualDispatch_Init||!SteamAPI_ManualDispatch_RunFrame||!SteamAPI_ManualDispatch_GetNextCallback||!SteamAPI_ManualDispatch_FreeLastCallback||!SteamAPI_ISteamUser_GetAuthSessionTicket){note("SDK_REQUIRED_EXPORT_MISSING\n");return 4;}
 if(!SteamAPI_Init()){note("STEAM_SESSION_API_INIT=0\n");return 5;}note("STEAM_SESSION_API_INIT=1\n");SteamAPI_ManualDispatch_Init();
 void *user=SteamInternal_FindOrCreateUserInterface(SteamAPI_GetHSteamUser(),"SteamUser021");if(!user){note("STEAM_USER_INTERFACE_MISSING\n");return 6;}
 unsigned char bytes[8192];unsigned count=0;unsigned ticket=SteamAPI_ISteamUser_GetAuthSessionTicket(user,bytes,sizeof(bytes),&count,0);char text[160];snprintf(text,sizeof(text),"SESSION_TICKET valid=%d bytes=%u\n",ticket!=0,count);note(text);
 int pipe=SteamAPI_GetHSteamPipe(),result=0;ULONGLONG start=GetTickCount64();
 while(GetTickCount64()-start<15000&&!result){SteamAPI_ManualDispatch_RunFrame(pipe);Callback cb;
  while(SteamAPI_ManualDispatch_GetNextCallback(pipe,&cb)){
   if(cb.callback==163&&cb.size>=8){uint32_t values[2];memcpy(values,cb.data,8);if(values[0]==ticket){snprintf(text,sizeof(text),"SESSION_TICKET_CALLBACK result=%u size=%d\n",values[1],cb.size);note(text);result=values[1]==1?1:2;}}
   SteamAPI_ManualDispatch_FreeLastCallback(pipe);
  }Sleep(50);
 }
 if(!result)note("SESSION_TICKET_CALLBACK_TIMEOUT\n");
 if(ticket&&SteamAPI_ISteamUser_CancelAuthTicket)SteamAPI_ISteamUser_CancelAuthTicket(user,ticket);
 if(SteamAPI_Shutdown)SteamAPI_Shutdown();return result==1?0:7;
}