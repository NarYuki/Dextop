"""Apply the experimental XEmbed status-item bridge verified on Dextop.

Dynamic width, tray-manager restart and full menu behavior remain unfinished.
"""
import argparse
from pathlib import Path
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source',type=Path,required=True)
args=parser.parse_args()
base=args.source/'src/external/cocotron/AppKit'
p=base/'X11.backend/X11Window.m';s=p.read_text()
method='''
- (BOOL)_dextopDockStatusItem {
    Atom selection = XInternAtom(_display, "_NET_SYSTEM_TRAY_S0", False);
    Window owner = XGetSelectionOwner(_display, selection);
    if (owner == None) return NO;
    unsigned long info[2] = {0, 1};
    Atom embedInfo = XInternAtom(_display, "_XEMBED_INFO", False);
    XChangeProperty(_display, _window, embedInfo, embedInfo, 32,
                    PropModeReplace, (unsigned char *)info, 2);
    XEvent event = {0};
    event.xclient.type = ClientMessage;
    event.xclient.window = owner;
    event.xclient.message_type = XInternAtom(_display, "_NET_SYSTEM_TRAY_OPCODE", False);
    event.xclient.format = 32;
    event.xclient.data.l[0] = CurrentTime;
    event.xclient.data.l[1] = 0;
    event.xclient.data.l[2] = _window;
    XSendEvent(_display, owner, False, NoEventMask, &event);
    XFlush(_display);
    return YES;
}
'''
if '_dextopDockStatusItem' not in s:
    anchor='@implementation X11Window'
    assert s.count(anchor)==1
    s=s.replace(anchor,anchor+'\n'+method);p.write_text(s)
p=base/'NSStatusItem.m';s=p.read_text()
if 'dextopStatusWindowKey' not in s:
    anchor='#import <AppKit/NSStatusItem.h>'
    if anchor not in s:
        anchor='#import <AppKit/AppKit.h>'
    assert s.count(anchor)==1,anchor
    s=s.replace(anchor,anchor+'''
#import <objc/runtime.h>
static char dextopStatusWindowKey;
@interface NSObject (DextopTrayPlatform)
- (BOOL)_dextopDockStatusItem;
- (id)platformWindow;
@end
''')
    anchor='''        [_button setAction: [self action]];
    }
    return _button;'''
    replacement='''        [_button setAction: [self action]];
#ifndef WIN32
        NSWindow *host = [[NSWindow alloc] initWithContentRect:[_button frame]
            styleMask:NSBorderlessWindowMask backing:NSBackingStoreBuffered defer:NO];
        [host setReleasedWhenClosed:NO];
        [host setTitle:[[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleName"] ?: @"Status item"];
        [host setContentView:_button];
        id platform = [host platformWindow];
        if ([platform respondsToSelector:@selector(_dextopDockStatusItem)] &&
            [platform _dextopDockStatusItem]) {
            objc_setAssociatedObject(self, &dextopStatusWindowKey, host, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
            [host orderFront:nil];
        }
        [host release];
#endif
    }
    return _button;'''
    assert s.count(anchor)==1
    s=s.replace(anchor,replacement)
    anchor='''- (void) dealloc {
    [_image release];'''
    replacement='''- (void) dealloc {
    NSWindow *host = objc_getAssociatedObject(self, &dextopStatusWindowKey);
    [host orderOut:nil];
    [host close];
    objc_setAssociatedObject(self, &dextopStatusWindowKey, nil, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    [_image release];'''
    assert s.count(anchor)==1
    p.write_text(s.replace(anchor,replacement))
print('Applied experimental AppKit XEmbed status item bridge')
