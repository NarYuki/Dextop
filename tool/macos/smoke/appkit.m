/* Own-source GUI probe using the public AppKit interface headers. */
#import <AppKit/AppKit.h>
#include <stdio.h>

static NSString *const documentPath = @"/tmp/dextop-appkit-probe.txt";

@interface DextopProbeDelegate : NSObject <NSApplicationDelegate, NSWindowDelegate> {
    NSTextField *input;
}
- (void)reportInput:(id)sender;
@end

@implementation DextopProbeDelegate
- (void)applicationDidFinishLaunching:(NSNotification *)notification {
    (void)notification;
    NSWindow *window = [[NSWindow alloc]
        initWithContentRect:NSMakeRect(80, 80, 480, 280)
                  styleMask:(NSTitledWindowMask | NSClosableWindowMask |
                             NSResizableWindowMask)
                    backing:NSBackingStoreBuffered defer:NO];
    [window setTitle:@"Dextop macOS AppKit probe"];
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
    if ([[NSFileManager defaultManager] fileExistsAtPath:documentPath]) {
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
    [window makeKeyAndOrderFront:nil];
    puts("appkit: window requested");
    fflush(stdout);
}
- (void)reportInput:(id)sender {
    (void)sender;
    printf("appkit: input=%s\n", [[input stringValue] UTF8String]);
    NSString *document = [[input stringValue] stringByAppendingString:@" 日本語"];
    NSError *error = nil;
    if ([document writeToFile:documentPath atomically:YES encoding:NSUTF8StringEncoding error:&error]) {
        printf("appkit: saved=%s\n", [document UTF8String]);
    } else {
        fprintf(stderr, "appkit: save failed: %s\n", [[error description] UTF8String]);
    }
    fflush(stdout);
}
- (void)windowDidResize:(NSNotification *)notification {
    NSRect size = [[notification object] contentRectForFrameRect:[[notification object] frame]];
    printf("appkit: resized=%.0fx%.0f\n", size.size.width, size.size.height);
    fflush(stdout);
}
- (BOOL)applicationShouldTerminateAfterLastWindowClosed:(NSApplication *)sender {
    (void)sender;
    return YES;
}
@end

int main(void) {
    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
    NSApplication *application = [NSApplication sharedApplication];
    DextopProbeDelegate *delegate = [[DextopProbeDelegate alloc] init];
    [application setDelegate:delegate];
    [application run];
    [delegate release];
    [pool drain];
    return 0;
}
