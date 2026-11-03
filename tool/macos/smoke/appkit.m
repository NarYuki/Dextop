/* Own-source GUI probe using the public AppKit interface headers. */
#import <AppKit/AppKit.h>
#include <stdio.h>
#include <stdlib.h>

static NSString *documentPath;

@interface DextopProbeDelegate : NSObject <NSApplicationDelegate, NSWindowDelegate> {
    NSTextField *input;
    NSWindow *probeWindow;
}
- (void)reportInput:(id)sender;
- (void)showDialog:(id)sender;
- (void)traceTick:(NSTimer *)timer;
@end

@implementation DextopProbeDelegate
- (void)applicationDidFinishLaunching:(NSNotification *)notification {
    (void)notification;
    NSWindow *window = [[NSWindow alloc]
        initWithContentRect:NSMakeRect(80, 80, 480, 280)
                  styleMask:(NSTitledWindowMask | NSClosableWindowMask |
                             NSResizableWindowMask)
                    backing:NSBackingStoreBuffered defer:NO];
    const char *title = getenv("DEXTOP_GUI_TITLE");
    [window setTitle:title ? [NSString stringWithUTF8String:title] : @"Dextop macOS AppKit probe"];
    probeWindow = window;
    [window setDelegate:self];
    NSTextField *label = [[NSTextField alloc]
        initWithFrame:NSMakeRect(24, 110, 432, 40)];
    [label setStringValue:@"macOS window on Dextop — 日本語"];
    [label setEditable:NO];
    [label setBezeled:NO];
    [label setDrawsBackground:NO];
    [[window contentView] addSubview:label];
    input = [[NSTextField alloc] initWithFrame:NSMakeRect(24, 70, 300, 28)];
    [input setStringValue:@"Type here"];
    if (documentPath && [[NSFileManager defaultManager] fileExistsAtPath:documentPath]) {
        NSError *error = nil;
        NSString *saved = [NSString stringWithContentsOfFile:documentPath
                                                  encoding:NSUTF8StringEncoding error:&error];
        if (saved) {
            [input setStringValue:saved];
            printf("appkit: loaded=%s\n", [saved UTF8String]);
        } else {
            fprintf(stderr, "appkit: load failed: %s\n", [[error description] UTF8String]);
        }
    }
    [[window contentView] addSubview:input];
    NSButton *button = [[NSButton alloc] initWithFrame:NSMakeRect(330, 70, 120, 28)];
    [button setTitle:@"Report input"];
    [button setTarget:self];
    [button setAction:@selector(reportInput:)];
    [[window contentView] addSubview:button];
    NSButton *closeButton = [[NSButton alloc] initWithFrame:NSMakeRect(330, 24, 120, 28)];
    [closeButton setTitle:@"Close"];
    [closeButton setTarget:window];
    [closeButton setAction:@selector(performClose:)];
    [[window contentView] addSubview:closeButton];
    NSButton *dialogButton = [[NSButton alloc] initWithFrame:NSMakeRect(180, 24, 120, 28)];
    [dialogButton setTitle:@"Dialog"];
    [dialogButton setTarget:self];
    [dialogButton setAction:@selector(showDialog:)];
    [[window contentView] addSubview:dialogButton];
    [window makeKeyAndOrderFront:nil];
    NSRect frame = [window frame];
    NSRect contentBounds = [[window contentView] bounds];
    printf("appkit: geometry frame=%gx%g content=%gx%g scale=%g\n",
           frame.size.width, frame.size.height, contentBounds.size.width,
           contentBounds.size.height, [window backingScaleFactor]);
    if (getenv("DEXTOP_GUI_TRACE")) {
        [NSTimer scheduledTimerWithTimeInterval:1 target:self selector:@selector(traceTick:)
                                      userInfo:nil repeats:YES];
    }
    puts("appkit: window requested");
    fflush(stdout);
}
- (void)reportInput:(id)sender {
    (void)sender;
    printf("appkit: input=%s\n", [[input stringValue] UTF8String]);
    NSString *document = [[input stringValue] stringByAppendingString:@" 日本語"];
    NSError *error = nil;
    if (documentPath && [document writeToFile:documentPath atomically:YES encoding:NSUTF8StringEncoding error:&error]) {
        printf("appkit: saved=%s\n", [document UTF8String]);
    } else if (documentPath) {
        fprintf(stderr, "appkit: save failed: %s\n", [[error description] UTF8String]);
    }
    fflush(stdout);
}
- (void)traceTick:(NSTimer *)timer {
    (void)timer;
    printf("appkit: tick visible=%d dirty=%d\n", [probeWindow isVisible],
           [[probeWindow contentView] needsDisplay]);
    if (getenv("DEXTOP_GUI_FORCE_DISPLAY")) {
        [probeWindow display];
        puts("appkit: explicit display returned");
    }
    if (getenv("DEXTOP_GUI_TEST_FILL")) {
        NSView *view = [probeWindow contentView];
        [view lockFocus];
        printf("appkit: focus context=%d port=%d flushDisabled=%d\n",
               [NSGraphicsContext currentContext] != nil,
               [[NSGraphicsContext currentContext] graphicsPort] != NULL,
               [probeWindow isFlushWindowDisabled]);
        [[NSColor colorWithCalibratedRed:1 green:0 blue:0 alpha:1] setFill];
        NSRectFill([view bounds]);
        [view unlockFocus];
        [probeWindow flushWindow];
        puts("appkit: test fill flushed");
    }
    fflush(stdout);
}
- (void)windowDidResize:(NSNotification *)notification {
    NSRect size = [[notification object] contentRectForFrameRect:[[notification object] frame]];
    printf("appkit: resized=%.0fx%.0f\n", size.size.width, size.size.height);
    fflush(stdout);
}
- (void)showDialog:(id)sender {
    (void)sender;
    NSAlert *alert = [[NSAlert alloc] init];
    [alert setMessageText:@"Dextop modal dialog"];
    [alert setInformativeText:@"Confirm this dialog to return to the main window."];
    [alert addButtonWithTitle:@"OK"];
    [[alert window] setTitle:@"Dextop dialog probe"];
    [alert layout];
    NSView *content = [[alert window] contentView];
    NSRect bounds = [content bounds];
    BOOL layoutValid = YES;
    printf("appkit: dialog bounds=%.0f,%.0f %.0fx%.0f\n", bounds.origin.x, bounds.origin.y,
           bounds.size.width, bounds.size.height);
    for (NSView *child in [content subviews]) {
        NSRect frame = [child frame];
        printf("appkit: dialog child=%.0f,%.0f %.0fx%.0f\n", frame.origin.x, frame.origin.y,
               frame.size.width, frame.size.height);
        if (!NSContainsRect(bounds, frame)) layoutValid = NO;
    }
    puts(layoutValid ? "appkit: dialog layout OK" : "appkit: dialog layout INVALID");
    fflush(stdout);
    NSInteger result = [alert runModal];
    if (result == NSAlertFirstButtonReturn) {
        puts("appkit: dialog accepted");
    } else {
        fprintf(stderr, "appkit: unexpected dialog result=%ld\n", (long)result);
    }
    fflush(stdout);
    [alert release];
}
- (BOOL)applicationShouldTerminateAfterLastWindowClosed:(NSApplication *)sender {
    (void)sender;
    return YES;
}
@end

int main(void) {
    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
    const char *path = getenv("DEXTOP_GUI_DOCUMENT");
    if (path) documentPath = [NSString stringWithUTF8String:path];
    NSApplication *application = [NSApplication sharedApplication];
    DextopProbeDelegate *delegate = [[DextopProbeDelegate alloc] init];
    [application setDelegate:delegate];
    [application run];
    [delegate release];
    [pool drain];
    return 0;
}
