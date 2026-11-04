"""Animate NSLayoutConstraint constants on the AppKit run loop."""

import argparse
from pathlib import Path

IMPLEMENTATION = r'''
#import <AppKit/NSAnimationContext.h>
#import <AppKit/NSWindow.h>
#import <Foundation/NSTimer.h>
#import <Foundation/NSRunLoop.h>
#import <QuartzCore/CAMediaTiming.h>
#import <objc/runtime.h>
#include <math.h>

static char DarlingConstraintAnimationKey;

@interface DarlingConstraintAnimator : NSObject {
    NSLayoutConstraint *_constraint;
    NSTimer *_timer;
    double _from, _to, _start, _duration;
}
- (id)initWithConstraint:(NSLayoutConstraint *)constraint;
- (void)setConstant:(CGFloat)constant;
- (void)cancel;
@end

@implementation DarlingConstraintAnimator
- (id)initWithConstraint:(NSLayoutConstraint *)constraint {
    if ((self = [super init])) _constraint = [constraint retain];
    return self;
}
- (void)dealloc {
    [_timer invalidate];
    [_timer release];
    [_constraint release];
    [super dealloc];
}
- (NSMethodSignature *)methodSignatureForSelector:(SEL)selector {
    return [_constraint methodSignatureForSelector:selector];
}
- (void)forwardInvocation:(NSInvocation *)invocation {
    [invocation invokeWithTarget:_constraint];
}
- (void)cancel {
    [[self retain] autorelease];
    [_timer invalidate]; [_timer release]; _timer = nil;
    if (objc_getAssociatedObject(_constraint, &DarlingConstraintAnimationKey) == self)
        objc_setAssociatedObject(_constraint, &DarlingConstraintAnimationKey, nil, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}
- (void)applyConstant:(CGFloat)value {
    [_constraint setConstant:value];
    id item = [_constraint firstItem];
    if (![item isKindOfClass:[NSView class]]) return;
    NSView *root = item;
    while ([root superview]) root = [root superview];
    [root setNeedsLayout:YES];
    [root layoutSubtreeIfNeeded];
    [root setNeedsDisplay:YES];
    if ([[root window] isVisible]) [[root window] displayIfNeeded];
}
- (void)tick:(NSTimer *)timer {
    double fraction = fmin(1.0, fmax(0.0, (CACurrentMediaTime() - _start) / _duration));
    // Smooth acceleration/deceleration; custom context timing curves are pending.
    double eased = fraction * fraction * (3.0 - 2.0 * fraction);
    [self applyConstant:fraction == 1.0 ? _to : _from + (_to - _from) * eased];
    if (fraction == 1.0) [self cancel];
}
- (void)setConstant:(CGFloat)constant {
    [objc_getAssociatedObject(_constraint, &DarlingConstraintAnimationKey) cancel];
    _from = [_constraint constant]; _to = constant;
    _duration = [[NSAnimationContext currentContext] duration];
    if (!isfinite(_duration) || _duration <= 0 || _from == _to || !isfinite(_from) || !isfinite(_to)) {
        [self applyConstant:constant];
        return;
    }
    _start = CACurrentMediaTime();
    objc_setAssociatedObject(_constraint, &DarlingConstraintAnimationKey, self, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    _timer = [[NSTimer timerWithTimeInterval:1.0/60.0 target:self selector:@selector(tick:) userInfo:nil repeats:YES] retain];
    [[NSRunLoop currentRunLoop] addTimer:_timer forMode:NSRunLoopCommonModes];
}
@end

@implementation NSLayoutConstraint (DarlingConstraintAnimation)
- (id)animator {
    return [[[DarlingConstraintAnimator alloc] initWithConstraint:self] autorelease];
}
@end
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    base = parser.parse_args().source / "src/external/cocotron/AppKit"
    path = base / "NSLayoutConstraint.m"
    text = path.read_text()
    marker = "#import <AppKit/NSAnimationContext.h>"
    if marker in text:
        text = text[:text.index(marker)].rstrip() + "\n"
    path.write_text(text + IMPLEMENTATION)
    path = base / "include/AppKit/NSLayoutConstraint.h"
    text = path.read_text()
    if "@interface NSLayoutConstraint (DarlingConstraintAnimation)" not in text:
        text += "\n@interface NSLayoutConstraint (DarlingConstraintAnimation)\n- (id)animator;\n@end\n"
        path.write_text(text)


if __name__ == "__main__":
    main()
