#include <stdio.h>
#include <stdlib.h>
#include <X11/Xlib.h>
#include <X11/extensions/XI2.h>
typedef struct {int deviceid;int mask_len;unsigned char *mask;} XIEventMask;
extern int XIGrabDevice(Display*,int,Window,Time,Cursor,int,int,Bool,XIEventMask*);
extern int XIUngrabDevice(Display*,int,Time);
extern int XISelectEvents(Display*,Window,XIEventMask*,int);
extern int XIQueryVersion(Display*,int*,int*);
typedef struct{int mask_len;unsigned char*mask;double*values;} Valuators;
typedef struct{int type;unsigned long serial;Bool send_event;Display*display;int extension,evtype;Time time;int deviceid,sourceid,detail,flags;Valuators valuators;double*raw_values;} Raw;
static int xi_opcode;
static void raw_motion(Display*d,int dx,int dy){double x=0,y=0;int count=0;while(XPending(d)){XEvent e;XNextEvent(d,&e);if(e.type==GenericEvent&&e.xcookie.extension==xi_opcode&&e.xcookie.evtype==XI_RawMotion&&XGetEventData(d,&e.xcookie)){Raw*r=e.xcookie.data;int n=0;for(int a=0;a<r->valuators.mask_len*8;a++)if(XIMaskIsSet(r->valuators.mask,a)){if(a==0)x+=r->raw_values[n];if(a==1)y+=r->raw_values[n];n++;}count++;XFreeEventData(d,&e.xcookie);}}if(!count||x!=dx||y!=dy){fprintf(stderr,"raw motion %.2f %.2f count=%d expected %d %d\n",x,y,count,dx,dy);exit(6);}}

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
static void state(int expected){for(int i=0;i<200;i++){FILE*f=fopen("/tmp/pointer-probe-state.json","r");char s[100]={0};if(f){fread(s,1,99,f);fclose(f);}if(strstr(s,expected?"true":"false"))return;usleep(10000);}fprintf(stderr,"grab state mismatch %d\n",expected);exit(2);}
static void delta(int dx,int dy){FILE*f=fopen("/tmp/pointer-probe-actions.jsonl","a");fprintf(f,"{\"dx\":%d,\"dy\":%d}\n",dx,dy);fclose(f);usleep(100000);}
static void location(Display*d,Window w,int x,int y){Window root,child;int rx,ry,wx,wy;unsigned mask;XQueryPointer(d,w,&root,&child,&rx,&ry,&wx,&wy,&mask);if(rx!=x||ry!=y){fprintf(stderr,"location %d %d expected %d %d\n",rx,ry,x,y);exit(3);}}
int main(){Display*d=XOpenDisplay(":98");if(!d)return 1;int event,error;XQueryExtension(d,"XInputExtension",&xi_opcode,&event,&error);int major=2,minor=3;XIQueryVersion(d,&major,&minor);
 Window root=DefaultRootWindow(d);unsigned char rawbits[4]={0};XISetMask(rawbits,XI_RawMotion);XIEventMask rawmask={XIAllMasterDevices,sizeof(rawbits),rawbits};XISelectEvents(d,root,&rawmask,1);
 Window w=XCreateSimpleWindow(d,root,0,0,400,400,0,0,0);XMapWindow(d,w);XSync(d,False);usleep(200000);state(0);
 int status=XGrabPointer(d,w,False,PointerMotionMask|ButtonPressMask|ButtonReleaseMask,GrabModeAsync,GrabModeAsync,None,None,CurrentTime);XSync(d,False);if(status!=GrabSuccess)return 4;state(1);
 Display*other=XOpenDisplay(":98");if(XGrabPointer(other,DefaultRootWindow(other),False,0,GrabModeAsync,GrabModeAsync,None,None,CurrentTime)!=AlreadyGrabbed)return 7;XCloseDisplay(other);state(1);
 XWarpPointer(d,None,root,0,0,0,0,100,100);XSync(d,False);delta(7,-3);location(d,w,107,97);raw_motion(d,7,-3);
 XWarpPointer(d,None,root,0,0,0,0,200,200);XSync(d,False);delta(-9,11);location(d,w,191,211);raw_motion(d,-9,11);
 XUngrabPointer(d,CurrentTime);XSync(d,False);state(0);
 int device=2;unsigned char bits[4]={0};XISetMask(bits,XI_Motion);XIEventMask mask={device,sizeof(bits),bits};
 status=XIGrabDevice(d,device,w,CurrentTime,None,GrabModeAsync,GrabModeAsync,False,&mask);XSync(d,False);if(status!=GrabSuccess)return 5;state(1);XIUngrabDevice(d,device,CurrentTime);XSync(d,False);state(0);
 XGrabPointer(d,w,False,0,GrabModeAsync,GrabModeAsync,None,None,CurrentTime);XSync(d,False);state(1);
 XCloseDisplay(d);state(0);puts("X11_CORE_XI_GRAB_RELEASE_RAW_RELATIVE_WARP_OK");return 0;}
