/* Own-source readback test for layer replacement and image uploads. */
#define GL_GLEXT_PROTOTYPES 1
#import <AppKit/AppKit.h>
#import <Foundation/Foundation.h>
#import <QuartzCore/QuartzCore.h>
#import <OpenGL/OpenGL.h>
#include <OpenGL/glext.h>
#include <stdio.h>
#include <string.h>

@interface CALayer (DextopRenderProbe)
- (void)_setTextureId:(NSNumber *)identifier;
@end

static CGImageRef solidImage(unsigned char r, unsigned char g, unsigned char b) {
    CGColorSpaceRef space = CGColorSpaceCreateDeviceRGB();
    CGContextRef context = CGBitmapContextCreate(NULL, 8, 8, 8, 32, space,
        kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Host);
    CGColorSpaceRelease(space);
    CGContextSetRGBFillColor(context, r/255.0, g/255.0, b/255.0, 1);
    CGContextFillRect(context, CGRectMake(0,0,8,8));
    CGImageRef image = CGBitmapContextCreateImage(context);
    CGContextRelease(context);
    return image;
}

static BOOL pixel(int x, int y, unsigned char r, unsigned char g, unsigned char b, unsigned char a) {
    unsigned char bytes[4] = {0};
    glReadPixels(x,y,1,1,GL_RGBA,GL_UNSIGNED_BYTE,bytes);
    GLenum error = glGetError();
    if (error || bytes[0] != r || bytes[1] != g || bytes[2] != b || bytes[3] != a) {
        printf("layer pixel %d,%d: %u %u %u %u expected %u %u %u %u GL=%x\n",
            x,y,bytes[0],bytes[1],bytes[2],bytes[3],r,g,b,a,error);
        return NO;
    }
    return YES;
}

static CGImageRef providerImage(const unsigned char *sample, size_t components,
                               CGColorSpaceRef space, CGBitmapInfo info) {
    unsigned char bytes[8 * 8 * 4];
    for (size_t i = 0; i < 64; ++i) memcpy(bytes + i * components, sample, components);
    CFDataRef data = CFDataCreate(NULL, bytes, 64 * components);
    CGDataProviderRef provider = CGDataProviderCreateWithCFData(data);
    CGImageRef image = CGImageCreate(8,8,8,components * 8,8 * components,space,
        info,provider,NULL,NO,kCGRenderingIntentDefault);
    CGDataProviderRelease(provider); CFRelease(data);
    return image;
}

int main(void) {
    @autoreleasepool {
        NSWindow *window = [[NSWindow alloc] initWithContentRect:NSMakeRect(0,0,64,64)
            styleMask:NSBorderlessWindowMask backing:NSBackingStoreBuffered defer:NO];
        [window setReleasedWhenClosed:NO];
        [window orderFront:nil];
        CGLPixelFormatAttribute attributes[] = {0};
        CGLPixelFormatObj format = NULL; CGLContextObj context = NULL; GLint count = 0;
        CGLError error = CGLChoosePixelFormat(attributes,&format,&count);
        if (!error) error = CGLCreateContext(format,NULL,&context);
        if (!error) error = CGLSetCurrentContext(context);
        if (error) { printf("layer CGL setup failed: %d (%s)\n", error, CGLErrorString(error)); return 1; }
        const GLubyte *rendererName = glGetString(GL_RENDERER);
        if (!rendererName) return 2;
        printf("layer GL renderer: %s\n", rendererName);
        GLuint target = 0, framebuffer = 0;
        glGenTextures(1,&target); glBindTexture(GL_TEXTURE_2D,target);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,64,64,0,GL_RGBA,GL_UNSIGNED_BYTE,NULL);
        glGenFramebuffersEXT(1,&framebuffer); glBindFramebufferEXT(GL_FRAMEBUFFER_EXT,framebuffer);
        glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT,GL_COLOR_ATTACHMENT0_EXT,GL_TEXTURE_2D,target,0);
        if (glCheckFramebufferStatusEXT(GL_FRAMEBUFFER_EXT) != GL_FRAMEBUFFER_COMPLETE_EXT) return 3;
        glViewport(0,0,64,64); glMatrixMode(GL_PROJECTION); glLoadIdentity(); glOrtho(0,64,0,64,-1,1);
        CALayer *root = [CALayer layer]; root.bounds = CGRectMake(0,0,64,64);
        root.anchorPoint = CGPointZero; root.position = CGPointZero;
        CALayer *old = [CALayer layer]; old.bounds = CGRectMake(0,0,32,32);
        old.anchorPoint = CGPointZero; old.position = CGPointZero;
        CGImageRef image = solidImage(255,0,0); old.contents = (id)image; CGImageRelease(image);
        GLuint texture; glGenTextures(1,&texture); [old _setTextureId:[NSNumber numberWithUnsignedInt:texture]];
        [root addSublayer:old];
        CARenderer *renderer = [CARenderer rendererWithCGLContext:context options:nil]; renderer.layer = root;
        [renderer render];
        if (!pixel(16,16,255,0,0,255) || !pixel(48,48,0,0,0,0)) return 4;
        [old removeFromSuperlayer]; glDeleteTextures(1,&texture);
        CALayer *replacement = [CALayer layer]; replacement.bounds = CGRectMake(0,0,32,32);
        replacement.anchorPoint = CGPointZero; replacement.position = CGPointMake(32,32);
        image = solidImage(0,0,255); replacement.contents = (id)image; CGImageRelease(image);
        glGenTextures(1,&texture); [replacement _setTextureId:[NSNumber numberWithUnsignedInt:texture]];
        [root addSublayer:replacement]; [renderer render];
        if (!pixel(16,16,0,0,0,0) || !pixel(48,48,0,0,255,255)) return 5;
        image = solidImage(0,255,0); replacement.contents = (id)image; CGImageRelease(image);
        [renderer render];
        if (!pixel(48,48,0,255,0,255)) return 6;
        // Image providers may pad each row beyond width * bytesPerPixel.
        CGColorSpaceRef paddedSpace = CGColorSpaceCreateDeviceRGB();
        CGContextRef padded = CGBitmapContextCreate(NULL,8,8,8,64,paddedSpace,
            kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Host);
        CGColorSpaceRelease(paddedSpace);
        CGContextSetRGBFillColor(padded,0,1,0,1);
        CGContextFillRect(padded,CGRectMake(0,0,8,8));
        image = CGBitmapContextCreateImage(padded); replacement.contents = (id)image;
        CGImageRelease(image); CGContextRelease(padded);
        glPixelStorei(GL_UNPACK_ALIGNMENT,8);
        glPixelStorei(GL_UNPACK_ROW_LENGTH,13);
        glPixelStorei(GL_UNPACK_SKIP_ROWS,2);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS,3);
        glPixelStorei(GL_UNPACK_SWAP_BYTES,GL_TRUE);
        [renderer render];
        GLint unpack;
        glGetIntegerv(GL_UNPACK_ALIGNMENT,&unpack); if (unpack != 8) return 9;
        glGetIntegerv(GL_UNPACK_ROW_LENGTH,&unpack); if (unpack != 13) return 9;
        glGetIntegerv(GL_UNPACK_SKIP_ROWS,&unpack); if (unpack != 2) return 9;
        glGetIntegerv(GL_UNPACK_SKIP_PIXELS,&unpack); if (unpack != 3) return 9;
        glGetIntegerv(GL_UNPACK_SWAP_BYTES,&unpack); if (unpack != GL_TRUE) return 9;
        glPixelStorei(GL_UNPACK_ALIGNMENT,4); glPixelStorei(GL_UNPACK_ROW_LENGTH,0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS,0); glPixelStorei(GL_UNPACK_SKIP_PIXELS,0);
        glPixelStorei(GL_UNPACK_SWAP_BYTES,GL_FALSE);
        for (int y = 34; y < 62; ++y)
            if (!pixel(48,y,0,255,0,255)) return 8;
        CGColorSpaceRef rgb = CGColorSpaceCreateDeviceRGB();
        CGColorSpaceRef gray = CGColorSpaceCreateDeviceGray();
        const unsigned char samples[][4] = {{255,0,0,255}, {255,0,0,128}, {255,0,0,0}, {96,0,0,0}};
        const size_t components[] = {4,4,3,1};
        const CGBitmapInfo formats[] = {
            kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big,
            kCGImageAlphaLast | kCGBitmapByteOrder32Big,
            kCGImageAlphaNone, kCGImageAlphaNone};
        const unsigned char expected[][4] = {{255,0,0,255}, {128,0,0,128}, {255,0,0,255}, {96,96,96,255}};
        for (size_t i = 0; i < 4; ++i) {
            image = providerImage(samples[i],components[i],i == 3 ? gray : rgb,formats[i]);
            if (!image) return 10;
            replacement.contents = (id)image; CGImageRelease(image);
            [renderer render];
            if (!pixel(48,48,expected[i][0],expected[i][1],expected[i][2],expected[i][3])) {
                printf("layer image format failed: %lu\n",(unsigned long)i);
                return 11;
            }
        }
        // Converting a non-native image must preserve the top-to-bottom row order.
        for (size_t channels = 4; channels >= 3; --channels) {
            unsigned char rows[8 * 8 * 4];
            for (size_t y = 0; y < 8; ++y) for (size_t x = 0; x < 8; ++x) {
                unsigned char *p = rows + (y * 8 + x) * channels;
                p[0] = y < 4 ? 255 : 0; p[1] = 0; p[2] = y < 4 ? 0 : 255;
                if (channels == 4) p[3] = 255;
            }
            CFDataRef data = CFDataCreate(NULL,rows,8 * 8 * channels);
            CGDataProviderRef provider = CGDataProviderCreateWithCFData(data);
            image = CGImageCreate(8,8,8,channels * 8,8 * channels,rgb,
                channels == 4 ? formats[0] : kCGImageAlphaNone,provider,NULL,NO,kCGRenderingIntentDefault);
            CGDataProviderRelease(provider); CFRelease(data);
            if (!image) return 12;
            replacement.contents = (id)image; CGImageRelease(image); [renderer render];
            if (!pixel(48,60,255,0,0,255) || !pixel(48,36,0,0,255,255)) return 13;
        }
        CGColorSpaceRelease(rgb); CGColorSpaceRelease(gray);
        [replacement removeFromSuperlayer]; [renderer render];
        if (!pixel(48,48,0,0,0,0)) return 7;
        renderer.layer = nil;
        glDeleteTextures(1,&texture); glDeleteFramebuffersEXT(1,&framebuffer); glDeleteTextures(1,&target);
        CGLSetCurrentContext(NULL); CGLReleaseContext(context); CGLReleasePixelFormat(format);
        [window close]; [window release];
        puts("layer render: replacement, contents update and removal readback OK");
    }
    return 0;
}
