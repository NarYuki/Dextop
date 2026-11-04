"""Deliver balanced NSViewController appearance events for actual view trees."""

import argparse
from pathlib import Path

HELPERS = r'''
#import "DarlingViewAppearance.h"
#import <AppKit/NSView.h>
#import <AppKit/NSWindow.h>
#import <objc/runtime.h>

static char DarlingViewOwnerKey, DarlingAppearanceStateKey;

static NSViewController *DarlingViewOwner(NSView *view) {
    return [objc_getAssociatedObject(view, &DarlingViewOwnerKey) pointerValue];
}

static NSInteger DarlingAppearanceState(NSViewController *controller) {
    return [objc_getAssociatedObject(controller, &DarlingAppearanceStateKey) integerValue];
}

static void DarlingSetAppearanceState(NSViewController *controller, NSInteger state) {
    objc_setAssociatedObject(controller, &DarlingAppearanceStateKey,
        [NSNumber numberWithInteger:state], OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

static void DarlingCollectAppearance(NSView *view, BOOL appearing, BOOL ignoreHidden,
                                     NSMutableArray *controllers) {
    if (!view || (appearing && [view isHidden] && !ignoreHidden)) return;
    NSViewController *controller = DarlingViewOwner(view);
    if (controller && DarlingAppearanceState(controller) == (appearing ? 0 : 1))
        [controllers addObject:controller];
    for (NSView *child in [view subviews])
        DarlingCollectAppearance(child, appearing, NO, controllers);
}

NSArray *DarlingBeginViewAppearance(NSView *root, BOOL appearing, BOOL ignoreRootHidden) {
    NSMutableArray *controllers = [NSMutableArray array];
    DarlingCollectAppearance(root, appearing, ignoreRootHidden, controllers);
    // Mark the whole snapshot first: callbacks may change the hierarchy.
    for (NSViewController *controller in controllers)
        DarlingSetAppearanceState(controller, appearing ? 2 : 3);
    @try {
        for (NSViewController *controller in controllers) {
            if (appearing) [controller viewWillAppear];
            else [controller viewWillDisappear];
        }
    } @catch (id exception) {
        for (NSViewController *controller in controllers)
            if (DarlingAppearanceState(controller) == (appearing ? 2 : 3))
                DarlingSetAppearanceState(controller, appearing ? 0 : 1);
        @throw;
    }
    return controllers;
}

void DarlingFinishViewAppearance(NSArray *controllers, BOOL appearing) {
    for (NSViewController *controller in controllers) {
        if (DarlingAppearanceState(controller) != (appearing ? 2 : 3)) continue;
        DarlingSetAppearanceState(controller, appearing ? 1 : 0);
        if (appearing) [controller viewDidAppear];
        else [controller viewDidDisappear];
    }
}
'''


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError("Pinned controller appearance anchor changed: " + old[:70])
    return text.replace(old, new)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    base = parser.parse_args().source / "src/external/cocotron/AppKit"
    (base / "DarlingViewAppearance.h").write_text('''#import <Foundation/NSArray.h>
@class NSView;
NSArray *DarlingBeginViewAppearance(NSView *root, BOOL appearing, BOOL ignoreRootHidden);
void DarlingFinishViewAppearance(NSArray *controllers, BOOL appearing);
''')
    path = base / "NSViewController.m"
    text = path.read_text()
    if "static char DarlingViewOwnerKey" not in text:
        text = replace_once(text, "@implementation NSViewController", HELPERS + "\n@implementation NSViewController")
        text = replace_once(text, "- (void) dealloc {", """- (void) dealloc {
    if (DarlingViewOwner(_view) == self)
        objc_setAssociatedObject(_view, &DarlingViewOwnerKey, nil, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    [_view release];""")
        text = replace_once(text, "- (void) viewDidLoad {\n}", """- (void) viewDidLoad {
}
- (void) viewWillAppear { }
- (void) viewDidAppear { }
- (void) viewWillDisappear { }
- (void) viewDidDisappear { }""")
        text = replace_once(text, """- (void) setView: (NSView *) value {
    value = [value retain];
    [_view release];
    _view = value;
}""", """- (void) setView: (NSView *) value {
    if (_view == value) return;
    value = [value retain];
    NSArray *disappearing = DarlingBeginViewAppearance(_view, NO, NO);
    if (DarlingViewOwner(_view) == self)
        objc_setAssociatedObject(_view, &DarlingViewOwnerKey, nil, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    [_view release];
    _view = value;
    DarlingFinishViewAppearance(disappearing, NO);
    if (_view) {
        objc_setAssociatedObject(_view, &DarlingViewOwnerKey,
            [NSValue valueWithPointer:self], OBJC_ASSOCIATION_RETAIN_NONATOMIC);
        if ([[_view window] isVisible] && ![_view isHiddenOrHasHiddenAncestor])
            DarlingFinishViewAppearance(DarlingBeginViewAppearance(_view, YES, NO), YES);
    }
}""")
        path.write_text(text)
    path = base / "include/AppKit/NSViewController.h"
    text = path.read_text()
    if "- (void) viewWillAppear;" not in text:
        text = replace_once(text, "- (void) viewDidLoad;", """- (void) viewDidLoad;
- (void) viewWillAppear;
- (void) viewDidAppear;
- (void) viewWillDisappear;
- (void) viewDidDisappear;""")
        path.write_text(text)
    path = base / "NSView.m"
    text = path.read_text()
    if '#import "DarlingViewAppearance.h"' not in text:
        text = '#import "DarlingViewAppearance.h"\n' + text
        text = replace_once(text, """    if (_isHidden != flag) {
        invalidateTransform(self);""", """    if (_isHidden != flag) {
        BOOL appearing = !flag && [_window isVisible] && ![_superview isHiddenOrHasHiddenAncestor];
        NSArray *appearance = (flag || appearing) ? DarlingBeginViewAppearance(self, appearing, YES) : nil;
        invalidateTransform(self);""")
        text = replace_once(text, """        else
            [self viewDidUnhide];
    }
}""", """        else
            [self viewDidUnhide];
        DarlingFinishViewAppearance(appearance, appearing);
    }
}""")
        text = replace_once(text, """- (void) _insertSubview: (NSView *) view atIndex: (NSInteger) index {

    [view retain];""", """- (void) _insertSubview: (NSView *) view atIndex: (NSInteger) index {
    NSArray *appearance = nil;
    [view retain];""")
        text = replace_once(text, """        [view removeFromSuperview];

        [view _setWindow: _window];""", """        [view removeFromSuperview];
        if ([_window isVisible] && ![self isHiddenOrHasHiddenAncestor])
            appearance = DarlingBeginViewAppearance(view, YES, NO);

        [view _setWindow: _window];""")
        text = replace_once(text, """    if (_wantsLayer)
        [view setWantsLayer: YES];
}""", """    if (_wantsLayer)
        [view setWantsLayer: YES];
    DarlingFinishViewAppearance(appearance, YES);
}""")
        text = replace_once(text, """- (void) removeFromSuperviewWithoutNeedingDisplay {
    [[self retain] autorelease];""", """- (void) removeFromSuperviewWithoutNeedingDisplay {
    [[self retain] autorelease];
    NSArray *appearance = DarlingBeginViewAppearance(self, NO, NO);""")
        text = replace_once(text, """    [window release];
    [removeFrom release];
}""", """    [window release];
    [removeFrom release];
    DarlingFinishViewAppearance(appearance, NO);
}""")
        path.write_text(text)
    path = base / "NSWindow.m"
    text = path.read_text()
    if '#import "DarlingViewAppearance.h"' not in text:
        text = '#import "DarlingViewAppearance.h"\n' + text
        text = replace_once(text, """    [self postNotificationName: NSWindowWillMoveNotification];

    switch (place) {""", """    BOOL appearing = place != NSWindowOut;
    NSArray *appearance = (appearing != _isVisible) ? DarlingBeginViewAppearance(_backgroundView, appearing, NO) : nil;
    [self postNotificationName: NSWindowWillMoveNotification];

    switch (place) {""")
        text = replace_once(text, """    [self postNotificationName: NSWindowDidMoveNotification];
}

// Like macOS""", """    DarlingFinishViewAppearance(appearance, appearing);
    [self postNotificationName: NSWindowDidMoveNotification];
}

// Like macOS""")
        text = replace_once(text, """    _isVisible = visible;
    if (visible) {
        [[self platformWindow] showWindowWithoutActivation];
    } else {
        [[self platformWindow] hideWindow];
    }
}""", """    NSArray *appearance = DarlingBeginViewAppearance(_backgroundView, visible, NO);
    _isVisible = visible;
    if (visible) {
        [[self platformWindow] showWindowWithoutActivation];
    } else {
        [[self platformWindow] hideWindow];
    }
    DarlingFinishViewAppearance(appearance, visible);
}""")
        text = replace_once(text, """- (void) _setVisible: (BOOL) visible {
    _isVisible = visible;
}""", """- (void) _setVisible: (BOOL) visible {
    if (_isVisible == visible) return;
    NSArray *appearance = DarlingBeginViewAppearance(_backgroundView, visible, NO);
    _isVisible = visible;
    DarlingFinishViewAppearance(appearance, visible);
}""")
        path.write_text(text)


    path = base / "NSWindow.m"
    text = path.read_text()
    if "NSViewController *previousController = _contentViewController;" not in text:
        start = text.index("- (void) setContentViewController:")
        end = text.index("\n- (void) setContentView:", start)
        method = text[start:end]
        method = replace_once(method, "    [_contentViewController release];",
                              "    NSViewController *previousController = _contentViewController;")
        method = replace_once(method, "    if (controller != nil) {", "    @try {\n    if (controller != nil) {")
        closing = method.rfind("\n}")
        method = method[:closing] + "\n    } @finally {\n        [previousController release];\n    }" + method[closing:]
        path.write_text(text[:start] + method + text[end:])


if __name__ == "__main__":
    main()
