#import <AppKit/AppKit.h>
#include <stdio.h>

static NSMutableString *events;
static BOOL invalidCallback;

@interface DeferredToolbarDelegate : NSObject <NSToolbarDelegate> {
@public
    BOOL ready;
    NSUInteger requests;
}
@end
@implementation DeferredToolbarDelegate
- (NSArray *)toolbarDefaultItemIdentifiers:(NSToolbar *)toolbar {
    ++requests;
    return ready ? [NSArray arrayWithObjects:@"first", @"second", nil] : [NSArray array];
}
- (NSArray *)toolbarAllowedItemIdentifiers:(NSToolbar *)toolbar {
    return [NSArray arrayWithObjects:@"first", @"second", nil];
}
- (NSToolbarItem *)toolbar:(NSToolbar *)toolbar itemForItemIdentifier:(NSString *)identifier willBeInsertedIntoToolbar:(BOOL)inserted {
    NSToolbarItem *item = [[[NSToolbarItem alloc] initWithItemIdentifier:identifier] autorelease];
    item.label = identifier;
    return item;
}
@end

@interface AppearanceController : NSViewController {
@public
    NSUInteger loads;
}
@end
@implementation AppearanceController
- (void)loadView {
    ++loads;
    self.view = [[[NSView alloc] initWithFrame:NSMakeRect(0, 0, 100, 80)] autorelease];
}
- (void)viewWillAppear { [super viewWillAppear]; [events appendFormat:@"%@a ", self.title]; }
- (void)viewDidAppear {
    [super viewDidAppear];
    if (!self.view.window.isVisible || self.view.isHiddenOrHasHiddenAncestor) invalidCallback = YES;
    [events appendFormat:@"%@A ", self.title];
}
- (void)viewWillDisappear { [super viewWillDisappear]; [events appendFormat:@"%@d ", self.title]; }
- (void)viewDidDisappear { [super viewDidDisappear]; [events appendFormat:@"%@D ", self.title]; }
@end

static BOOL expect(NSString *value) {
    if (invalidCallback || ![events isEqualToString:value]) {
        printf("appearance events actual '%s' expected '%s' invalid=%d\n",
            events.UTF8String, value.UTF8String, invalidCallback);
        return NO;
    }
    [events setString:@""];
    return YES;
}

int main(void) {
    @autoreleasepool {
        events = [NSMutableString string];
        NSWindow *window = [[NSWindow alloc] initWithContentRect:NSMakeRect(100, 100, 100, 80)
            styleMask:NSBorderlessWindowMask backing:NSBackingStoreBuffered defer:NO];
        [window setReleasedWhenClosed:NO];
        AppearanceController *root = [[[AppearanceController alloc] init] autorelease]; root.title = @"r";
        AppearanceController *child = [[[AppearanceController alloc] init] autorelease]; child.title = @"c";
        AppearanceController *lazy = [[[AppearanceController alloc] init] autorelease]; lazy.title = @"l";
        [root addChildViewController:child]; [root addChildViewController:lazy];
        window.contentViewController = root;
        [root.view addSubview:child.view];
        if (!expect(@"") || lazy->loads != 0) return 1;
        [window orderFront:nil];
        if (!expect(@"ra ca rA cA ") || lazy->loads != 0) return 2;
        [window orderFront:nil];
        if (!expect(@"")) return 3;
        root.view.hidden = YES;
        if (!expect(@"rd cd rD cD ")) return 4;
        [window orderOut:nil]; [window orderFront:nil];
        if (!expect(@"")) return 5;
        root.view.hidden = NO;
        if (!expect(@"ra ca rA cA ")) return 6;
        [child.view removeFromSuperview];
        if (!expect(@"cd cD ")) return 7;
        [root.view addSubview:child.view];
        if (!expect(@"ca cA ")) return 8;
        [window orderOut:nil];
        if (!expect(@"rd cd rD cD ")) return 9;
        [window setIsVisible:YES];
        if (!expect(@"ra ca rA cA ")) return 10;
        [window setIsVisible:YES];
        if (!expect(@"")) return 11;
        [window setIsVisible:NO];
        if (!expect(@"rd cd rD cD ")) return 12;
        [window orderFront:nil];
        if (!expect(@"ra ca rA cA ")) return 13;
        AppearanceController *replacement = [[[AppearanceController alloc] init] autorelease]; replacement.title = @"n";
        window.contentViewController = replacement;
        if (!expect(@"rd cd rD cD na nA ")) return 14;
        [window close];
        if (!expect(@"nd nD ") || lazy->loads != 0 || root->loads != 1 || child->loads != 1) return 15;
        [window release];
        DeferredToolbarDelegate *delegate = [[[DeferredToolbarDelegate alloc] init] autorelease];
        NSToolbar *toolbar = [[[NSToolbar alloc] initWithIdentifier:@"dextop-appearance-toolbar"] autorelease];
        toolbar.delegate = delegate;
        if (delegate->requests != 0) return 16;
        delegate->ready = YES;
        window = [[NSWindow alloc] initWithContentRect:NSMakeRect(100, 100, 300, 200)
            styleMask:NSTitledWindowMask backing:NSBackingStoreBuffered defer:NO];
        [window setReleasedWhenClosed:NO];
        window.toolbar = toolbar;
        [window orderFront:nil];
        [window displayIfNeeded];
        if (delegate->requests != 1 || toolbar.items.count != 2) return 17;
        [window close]; [window release];
        NSTextField *input = [NSTextField textFieldWithString:@"editable value"];
        if (![input.stringValue isEqualToString:@"editable value"] || !input.isEditable ||
            !input.isSelectable || !input.isBezeled || input.frame.size.height <= 0 ||
            [[input cell] wraps] || ![[input cell] isScrollable]) return 18;
        NSSegmentedControl *segments = [NSSegmentedControl segmentedControlWithLabels:
            [NSArray arrayWithObjects:@"first", @"second", nil] trackingMode:NSSegmentSwitchTrackingSelectAny
            target:delegate action:@selector(description)];
        if (segments.segmentCount != 2 || segments.trackingMode != NSSegmentSwitchTrackingSelectAny ||
            ![[segments labelForSegment:1] isEqualToString:@"second"] || segments.target != delegate ||
            segments.action != @selector(description) || segments.frame.size.width <= 0) return 19;
        NSImage *image = [[[NSImage alloc] initWithSize:NSMakeSize(16, 16)] autorelease];
        segments = [NSSegmentedControl segmentedControlWithImages:[NSArray arrayWithObject:image]
            trackingMode:NSSegmentSwitchTrackingMomentary target:nil action:NULL];
        if (segments.segmentCount != 1 || [segments imageForSegment:0] != image ||
            segments.trackingMode != NSSegmentSwitchTrackingMomentary) return 20;
        puts("controller appearance: window, hidden ancestors, child insertion/removal and replacement OK");
    }
    return 0;
}
