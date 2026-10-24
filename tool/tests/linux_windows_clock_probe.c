#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <x86intrin.h>
int main(void){LARGE_INTEGER freq,a,b;QueryPerformanceFrequency(&freq);QueryPerformanceCounter(&a);ULONGLONG tick=GetTickCount64(),tsc=__rdtsc();Sleep(1000);ULONGLONG td=GetTickCount64()-tick,rd=__rdtsc()-tsc;QueryPerformanceCounter(&b);double seconds=(double)(b.QuadPart-a.QuadPart)/(double)freq.QuadPart;
 char text[256];snprintf(text,sizeof(text),"WINDOWS_CLOCK bits=%u qpc_frequency=%lld qpc_delta=%lld seconds=%.6f ticks_ms=%llu tsc_delta=%llu\n",(unsigned)(sizeof(void*)*8),(long long)freq.QuadPart,(long long)(b.QuadPart-a.QuadPart),seconds,td,rd);
 DWORD n;HANDLE f=CreateFileA("Z:\\tmp\\dextop-windows-clock-probe.txt",GENERIC_WRITE,FILE_SHARE_READ,0,CREATE_ALWAYS,0,0);if(f!=INVALID_HANDLE_VALUE){WriteFile(f,text,(DWORD)strlen(text),&n,0);CloseHandle(f);}puts(text);return seconds>=0.5&&seconds<2.0&&td>=500&&td<2000?0:2;}