/* Own-source native presentation test: leave a red CPU marker, replace with blue layers. */
#import <AppKit/AppKit.h>
#include <stdio.h>
#include <stdlib.h>

@interface PresentationView : NSView {
@public BOOL layered;
}
@end
@implementation PresentationView
- (void)drawRect:(NSRect)dirty {
    if (layered) { [[NSColor blueColor] setFill]; NSRectFill(dirty); }
    else {
        [[NSColor grayColor] setFill]; NSRectFill(dirty);
        [[NSColor redColor] setFill]; NSRectFill(NSMakeRect(20,20,60,30));
    }
}
@end

int main(void) {
    @autoreleasepool {
        [NSApplication sharedApplication];
        NSWindow *window = [[NSWindow alloc] initWithContentRect:NSMakeRect(100,100,160,400)
            styleMask:NSBorderlessWindowMask backing:NSBackingStoreBuffered defer:NO];
        [window setReleasedWhenClosed:NO];
        [window setTitle:@"Dextop Presentation Probe"];
        PresentationView *view = [[[PresentationView alloc] initWithFrame:NSMakeRect(0,0,160,400)] autorelease];
        [window setContentView:view];
        [window orderFront:nil];
        [view display]; [window flushWindow];
        view->layered = YES;
        [view setWantsLayer:YES];
        [view display]; [window flushWindow];
        [window setContentSize:NSMakeSize(180,430)];
        [view display]; [window flushWindow];
        printf("presentation: blue layer ready for native capture root=%p\n",[view layer]); fflush(stdout);
        const char *release = getenv("DEXTOP_PRESENT_RELEASE");
        NSString *path = release ? [NSString stringWithUTF8String:release] : nil;
        NSDate *deadline = [NSDate dateWithTimeIntervalSinceNow:30];
        while ([deadline timeIntervalSinceNow] > 0 &&
            (!path || ![[NSFileManager defaultManager] fileExistsAtPath:path])) {
            NSEvent *event = [NSApp nextEventMatchingMask:NSAnyEventMask
                untilDate:[NSDate dateWithTimeIntervalSinceNow:0.05]
                inMode:NSDefaultRunLoopMode dequeue:YES];
            if (event) [NSApp sendEvent:event];
            [NSApp updateWindows];
        }
        [window close]; [window release];
        puts("presentation: test window closed");
    }
    return 0;
}
