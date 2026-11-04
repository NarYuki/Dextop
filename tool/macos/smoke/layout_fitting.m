#import <AppKit/AppKit.h>
#include <math.h>
#include <stdio.h>

@interface FittingLeaf : NSView
@end
@implementation FittingLeaf
- (NSSize)intrinsicContentSize { return NSMakeSize(120, NSViewNoIntrinsicMetric); }
@end

@interface LayoutCounter : NSView {
@public
    NSUInteger calls;
}
@end
@implementation LayoutCounter
- (void)layout { ++calls; [super layout]; }
@end

static BOOL closeSize(NSSize size, CGFloat w, CGFloat h) {
    return fabs(size.width - w) < 0.001 && fabs(size.height - h) < 0.001;
}

static BOOL constraintAnimation(void) {
    NSView *root = [[[NSView alloc] initWithFrame:NSMakeRect(0, 0, 200, 100)] autorelease];
    NSView *leaf = [[[NSView alloc] initWithFrame:NSZeroRect] autorelease];
    leaf.translatesAutoresizingMaskIntoConstraints = NO;
    [root addSubview:leaf];
    NSLayoutConstraint *width = [NSLayoutConstraint constraintWithItem:leaf attribute:NSLayoutAttributeWidth
        relatedBy:NSLayoutRelationEqual toItem:nil attribute:NSLayoutAttributeNotAnAttribute multiplier:1 constant:20];
    [root addConstraint:width];
    NSDictionary *views = [NSDictionary dictionaryWithObject:leaf forKey:@"leaf"];
    [root addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"H:|[leaf]" options:0 metrics:nil views:views]];
    [root addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"V:|[leaf(20)]" options:0 metrics:nil views:views]];
    [root layoutSubtreeIfNeeded];
    [NSAnimationContext runAnimationGroup:^(NSAnimationContext *context) {
        context.duration = 0.2;
        [[width animator] setConstant:80];
    }];
    [[NSRunLoop currentRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.08]];
    CGFloat middle = width.constant;
    printf("animated constraint intermediate %.3f frame %.3f\n", middle, leaf.frame.size.width);
    if (middle <= 20 || middle >= 80 || fabs(leaf.frame.size.width - middle) > 0.001) return NO;
    [NSAnimationContext runAnimationGroup:^(NSAnimationContext *context) {
        context.duration = 0.08;
        [[width animator] setConstant:40];
    }];
    [[NSRunLoop currentRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.15]];
    if (fabs(width.constant - 40) > 0.001 || fabs(leaf.frame.size.width - 40) > 0.001) return NO;
    [NSAnimationContext runAnimationGroup:^(NSAnimationContext *context) {
        context.duration = 0;
        [[width animator] setConstant:30];
    }];
    return fabs(width.constant - 30) < 0.001 && fabs(leaf.frame.size.width - 30) < 0.001;
}

@interface FlippedLayoutView : NSView
@end
@implementation FlippedLayoutView
- (BOOL)isFlipped { return YES; }
@end

@interface BaselineLeaf : NSView
@end
@implementation BaselineLeaf
- (CGFloat)firstBaselineOffsetFromTop { return 7; }
- (CGFloat)baselineOffsetFromBottom { return 3; }
@end

static BOOL baselineLayout(BOOL flipped, BOOL first) {
    NSView *root = [[[(flipped ? [FlippedLayoutView class] : [NSView class]) alloc]
        initWithFrame:NSMakeRect(0, 0, 200, 100)] autorelease];
    BaselineLeaf *a = [[[BaselineLeaf alloc] initWithFrame:NSZeroRect] autorelease];
    BaselineLeaf *b = [[[BaselineLeaf alloc] initWithFrame:NSZeroRect] autorelease];
    a.translatesAutoresizingMaskIntoConstraints = NO;
    b.translatesAutoresizingMaskIntoConstraints = NO;
    [root addSubview:a]; [root addSubview:b];
    NSDictionary *views = [NSDictionary dictionaryWithObjectsAndKeys:a, @"a", b, @"b", nil];
    [root addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"H:|[a(20)]" options:0 metrics:nil views:views]];
    [root addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"H:|[b(20)]" options:0 metrics:nil views:views]];
    [root addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"V:|-20-[a(20)]" options:0 metrics:nil views:views]];
    [root addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"V:[b(30)]" options:0 metrics:nil views:views]];
    NSLayoutAttribute attribute = first ? NSLayoutAttributeFirstBaseline : NSLayoutAttributeLastBaseline;
    [root addConstraint:[NSLayoutConstraint constraintWithItem:b attribute:attribute relatedBy:NSLayoutRelationEqual
        toItem:a attribute:attribute multiplier:1 constant:4]];
    [root layoutSubtreeIfNeeded];
    CGFloat top = flipped ? NSMinY(b.frame) : 100 - NSMaxY(b.frame);
    CGFloat expected = first ? 24 : 14;
    printf("baseline flipped=%d first=%d top=%.3f expected=%.3f\n", flipped, first, top, expected);
    return fabs(top - expected) < 0.001;
}

static BOOL parentRelativeLayout(BOOL rootFlipped, BOOL parentFlipped) {
    Class rootClass = rootFlipped ? [FlippedLayoutView class] : [NSView class];
    Class parentClass = parentFlipped ? [FlippedLayoutView class] : [NSView class];
    NSView *root = [[[rootClass alloc] initWithFrame:NSMakeRect(0, 0, 400, 400)] autorelease];
    NSView *parent = [[[parentClass alloc] initWithFrame:NSMakeRect(0, 0, 100, 100)] autorelease];
    NSView *manual = [[[NSView alloc] initWithFrame:NSMakeRect(5, 7, 10, 10)] autorelease];
    NSView *sibling = [[[NSView alloc] initWithFrame:NSZeroRect] autorelease];
    parent.translatesAutoresizingMaskIntoConstraints = NO;
    sibling.translatesAutoresizingMaskIntoConstraints = NO;
    [root addSubview:parent]; [parent addSubview:manual]; [parent addSubview:sibling];
    NSDictionary *views = [NSDictionary dictionaryWithObjectsAndKeys:parent, @"parent", sibling, @"sibling", nil];
    [root addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"H:|-40-[parent(100)]" options:0 metrics:nil views:views]];
    [root addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"V:|-50-[parent(100)]" options:0 metrics:nil views:views]];
    [parent addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"H:[sibling(10)]" options:0 metrics:nil views:views]];
    [parent addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"V:[sibling(10)]" options:0 metrics:nil views:views]];
    [parent addConstraint:[NSLayoutConstraint constraintWithItem:sibling attribute:NSLayoutAttributeLeft relatedBy:NSLayoutRelationEqual
        toItem:manual attribute:NSLayoutAttributeRight multiplier:1 constant:2]];
    [parent addConstraint:[NSLayoutConstraint constraintWithItem:sibling attribute:NSLayoutAttributeTop relatedBy:NSLayoutRelationEqual
        toItem:manual attribute:NSLayoutAttributeTop multiplier:1 constant:0]];
    [root layoutSubtreeIfNeeded];
    printf("parent-relative sibling %.3f %.3f\n", sibling.frame.origin.x, sibling.frame.origin.y);
    return fabs(sibling.frame.origin.x - 17) < 0.001 && fabs(sibling.frame.origin.y - 7) < 0.001;
}

int main(void) {
    @autoreleasepool {
        if (![NSColor controlAccentColor] || ![[NSColor controlAccentColor] isEqual:[NSColor selectedControlColor]]) return 11;
        NSView *root = [[[NSView alloc] initWithFrame:NSMakeRect(0, 0, 800, 600)] autorelease];
        FittingLeaf *leaf = [[[FittingLeaf alloc] initWithFrame:NSMakeRect(4, 5, 6, 7)] autorelease];
        leaf.translatesAutoresizingMaskIntoConstraints = NO;
        [root addSubview:leaf];
        NSDictionary *views = [NSDictionary dictionaryWithObject:leaf forKey:@"leaf"];
        [root addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"H:|-10-[leaf]-20-|" options:0 metrics:nil views:views]];
        [root addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"V:|-3-[leaf(40)]-7-|" options:0 metrics:nil views:views]];
        NSRect originalRoot = root.frame, originalLeaf = leaf.frame;
        NSSize measured = root.fittingSize;
        printf("constrained fitting %.3f %.3f\n", measured.width, measured.height);
        if (!closeSize(measured, 150, 50)) return 1;
        if (!NSEqualRects(originalRoot, root.frame) || !NSEqualRects(originalLeaf, leaf.frame)) return 2;
        // Repeat after changing the existing size: measurement must not depend on it.
        root.frame = NSMakeRect(0, 0, 900, 700);
        if (!closeSize(root.fittingSize, 150, 50)) return 3;
        FittingLeaf *partial = [[[FittingLeaf alloc] initWithFrame:NSMakeRect(0, 0, 300, 200)] autorelease];
        if (!closeSize(partial.fittingSize, 120, 0)) return 4;
        NSWindow *window = [[NSWindow alloc] initWithContentRect:NSMakeRect(0, 0, 800, 600)
            styleMask:NSBorderlessWindowMask backing:NSBackingStoreBuffered defer:YES];
        [window setReleasedWhenClosed:NO];
        NSViewController *controller = [[[NSViewController alloc] init] autorelease];
        root.translatesAutoresizingMaskIntoConstraints = NO;
        controller.view = root;
        window.contentViewController = controller;
        NSSize windowSize = [window contentRectForFrameRect:window.frame].size;
        printf("controller fitting %.3f %.3f\n", windowSize.width, windowSize.height);
        if (!closeSize(windowSize, 150, 50)) return 5;
        NSView *fixed = [[[NSView alloc] initWithFrame:NSMakeRect(0, 0, 320, 240)] autorelease];
        controller = [[[NSViewController alloc] init] autorelease];
        controller.view = fixed;
        window.contentViewController = controller;
        if (!closeSize([window contentRectForFrameRect:window.frame].size, 320, 240)) return 6;
        [window close];
        [window release];
        NSView *ancestor = [[[NSView alloc] initWithFrame:NSMakeRect(0, 0, 100, 100)] autorelease];
        LayoutCounter *descendant = [[[LayoutCounter alloc] initWithFrame:NSMakeRect(0, 0, 50, 50)] autorelease];
        [ancestor addSubview:descendant];
        ancestor.needsLayout = YES;
        descendant.needsLayout = YES;
        [ancestor layoutSubtreeIfNeeded];
        NSUInteger previous = descendant->calls;
        descendant.needsLayout = YES;
        [ancestor layoutSubtreeIfNeeded];
        printf("descendant layout callbacks %lu -> %lu\n", (unsigned long)previous, (unsigned long)descendant->calls);
        if (descendant->calls <= previous) return 7;
        previous = descendant->calls;
        [descendant invalidateIntrinsicContentSize];
        [ancestor layoutSubtreeIfNeeded];
        if (descendant->calls <= previous) return 8;
        previous = descendant->calls;
        [ancestor layoutSubtreeIfNeeded];
        if (descendant->calls != previous) return 9;
        for (int rootFlipped = 0; rootFlipped < 2; ++rootFlipped)
            for (int parentFlipped = 0; parentFlipped < 2; ++parentFlipped)
                if (!parentRelativeLayout(rootFlipped, parentFlipped)) return 10;
        NSView *plain = [[[NSView alloc] initWithFrame:NSZeroRect] autorelease];
        if (plain.firstBaselineOffsetFromTop != 0 || plain.lastBaselineOffsetFromBottom != 0) return 12;
        for (int flipped = 0; flipped < 2; ++flipped)
            for (int first = 0; first < 2; ++first)
                if (!baselineLayout(flipped, first)) return 13;
        if (!constraintAnimation()) return 14;
        puts("layout fitting PASS");
    }
    return 0;
}
