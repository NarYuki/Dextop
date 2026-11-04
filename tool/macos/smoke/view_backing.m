#import <AppKit/AppKit.h>
#import <QuartzCore/QuartzCore.h>
#include <stdio.h>

@interface BackingPaintView : NSView {
@public
    NSColor *paint;
}
@end
@implementation BackingPaintView
- (void)drawRect:(NSRect)rect { [paint setFill]; NSRectFill(rect); }
@end

@interface FlippedBackingPaintView : BackingPaintView
@end
@implementation FlippedBackingPaintView
- (BOOL)isFlipped { return YES; }
@end

static BOOL pixel(CGImageRef image, size_t x, size_t y, int r, int g, int b) {
    if (!image || x >= CGImageGetWidth(image) || y >= CGImageGetHeight(image)) return NO;
    size_t width = CGImageGetWidth(image), height = CGImageGetHeight(image);
    CGColorSpaceRef space = CGColorSpaceCreateDeviceRGB();
    CGContextRef context = CGBitmapContextCreate(NULL, width, height, 8, width * 4, space,
        kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
    CGColorSpaceRelease(space);
    if (!context) return NO;
    CGContextDrawImage(context, CGRectMake(0, 0, width, height), image);
    unsigned char *p = (unsigned char *)CGBitmapContextGetData(context) + y * width * 4 + x * 4;
    BOOL result = p[0] == r && p[1] == g && p[2] == b && p[3] == 255;
    if (!result) printf("pixel (%lu,%lu) actual %u %u %u %u expected %d %d %d 255\n",
        x, y, p[0], p[1], p[2], p[3], r, g, b);
    CGContextRelease(context);
    return result;
}

int main(void) {
    @autoreleasepool {
        NSWindow *window = [[NSWindow alloc] initWithContentRect:NSMakeRect(100, 100, 64, 48)
            styleMask:NSBorderlessWindowMask backing:NSBackingStoreBuffered defer:NO];
        [window setReleasedWhenClosed:NO];
        BackingPaintView *root = [[[BackingPaintView alloc] initWithFrame:NSMakeRect(0, 0, 64, 48)] autorelease];
        root->paint = [NSColor redColor];
        BackingPaintView *child = [[[BackingPaintView alloc] initWithFrame:NSMakeRect(10, 12, 16, 18)] autorelease];
        child->paint = [NSColor blueColor];
        [root addSubview:child];
        window.contentView = root;
        root.wantsLayer = YES;
        if (!CGPointEqualToPoint(child.layer.position, CGPointMake(10, 12))) return 7;
        [window orderFront:nil];
        [root display];
        CGImageRef image = (CGImageRef)root.layer.contents;
        if (!image || CGImageGetWidth(image) != 64 || CGImageGetHeight(image) != 48) return 1;
        if (!pixel(image, 2, 2, 255, 0, 0) || !pixel((CGImageRef)child.layer.contents, 2, 2, 0, 0, 255)) return 2;
        root->paint = [NSColor greenColor];
        [root displayRectIgnoringOpacity:NSMakeRect(0, 0, 8, 8)];
        image = (CGImageRef)root.layer.contents;
        // Pixel storage is top-down: a small bottom-left invalidation changes the last rows.
        if (!pixel(image, 2, 2, 255, 0, 0) || !pixel(image, 2, 46, 0, 255, 0)) return 3;
        [window setContentSize:NSMakeSize(80, 60)];
        [root display];
        image = (CGImageRef)root.layer.contents;
        if (!image || CGImageGetWidth(image) != 80 || CGImageGetHeight(image) != 60 ||
            !pixel(image, 2, 2, 0, 255, 0)) return 4;
        CALayer *hosted = [CALayer layer];
        [CATransaction begin]; [CATransaction setDisableActions:YES];
        hosted.backgroundColor = [[NSColor blueColor] CGColor];
        child.layer = hosted;
        [CATransaction commit];
        [root display];
        if (child.layer != hosted || hosted.contents != nil) return 5;
        [window close];
        [window release];
        window = [[NSWindow alloc] initWithContentRect:NSMakeRect(100, 100, 32, 24)
            styleMask:NSBorderlessWindowMask backing:NSBackingStoreBuffered defer:NO];
        [window setReleasedWhenClosed:NO];
        window.backgroundColor = [NSColor yellowColor];
        NSView *plain = [[[NSView alloc] initWithFrame:NSMakeRect(0, 0, 32, 24)] autorelease];
        window.contentView = plain;
        plain.wantsLayer = YES;
        [window orderFront:nil];
        [plain display];
        if (!pixel((CGImageRef)plain.layer.contents, 2, 2, 255, 255, 0)) return 6;
        [window close];
        [window release];
        window = [[NSWindow alloc] initWithContentRect:NSMakeRect(100, 100, 64, 48)
            styleMask:NSBorderlessWindowMask backing:NSBackingStoreBuffered defer:NO];
        [window setReleasedWhenClosed:NO];
        root = [[[FlippedBackingPaintView alloc] initWithFrame:NSMakeRect(0, 0, 64, 48)] autorelease];
        root->paint = [NSColor redColor];
        child = [[[BackingPaintView alloc] initWithFrame:NSMakeRect(10, 12, 16, 18)] autorelease];
        child->paint = [NSColor blueColor];
        [root addSubview:child];
        window.contentView = root;
        root.wantsLayer = YES;
        if (!CGPointEqualToPoint(child.layer.position, CGPointMake(10, 18))) return 8;
        [window orderFront:nil];
        [root display];
        if (!pixel((CGImageRef)child.layer.contents, 2, 2, 0, 0, 255)) return 9;
        [window close];
        [window release];
        puts("view backing: nested drawing, partial invalidation, resize and hosted layers OK");
    }
    return 0;
}
