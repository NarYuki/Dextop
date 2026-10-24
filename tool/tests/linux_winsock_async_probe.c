#define WIN32_LEAN_AND_MEAN
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
int main(int argc,char **argv) {
 WSADATA data; if(argc!=2||WSAStartup(MAKEWORD(2,2),&data))return 2;
 SOCKET s=WSASocketW(AF_INET,SOCK_STREAM,IPPROTO_TCP,NULL,0,WSA_FLAG_OVERLAPPED);
 struct sockaddr_in a={0}; a.sin_family=AF_INET; a.sin_port=htons((unsigned short)atoi(argv[1])); a.sin_addr.s_addr=htonl(INADDR_LOOPBACK);
 WSAEVENT event=WSACreateEvent(); WSAEventSelect(s,event,FD_CONNECT);
 int r=connect(s,(struct sockaddr*)&a,sizeof(a));
 if(r&&WSAGetLastError()!=WSAEWOULDBLOCK)return 3;
 if(WSAWaitForMultipleEvents(1,&event,FALSE,10000,FALSE)!=WSA_WAIT_EVENT_0)return 4;
 WSANETWORKEVENTS events; if(WSAEnumNetworkEvents(s,event,&events)||events.iErrorCode[FD_CONNECT_BIT])return 5;
 WSAEventSelect(s,NULL,0); u_long mode=0; ioctlsocket(s,FIONBIO,&mode);
 char buffer[32]={0}; WSABUF b={sizeof(buffer),buffer}; WSAOVERLAPPED ov={0}; ov.hEvent=WSACreateEvent(); DWORD count=0,flags=0;
 r=WSARecv(s,&b,1,&count,&flags,&ov,NULL); if(r&&WSAGetLastError()!=WSA_IO_PENDING)return 6;
 if(send(s,"ping",4,0)!=4)return 7;
 if(WSAWaitForMultipleEvents(1,&ov.hEvent,FALSE,10000,FALSE)!=WSA_WAIT_EVENT_0)return 8;
 if(!WSAGetOverlappedResult(s,&ov,&count,FALSE,&flags)||count!=4||memcmp(buffer,"pong",4))return 9;
 closesocket(s);WSACloseEvent(event);WSACloseEvent(ov.hEvent);WSACleanup();
 puts("WINSOCK_ASYNC_CONNECT_OVERLAPPED_RECEIVE_OK");return 0;
}
