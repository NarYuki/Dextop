/* Own-source CalRGB object/solid-paint tests; no claim of full ICC management. */
#import <Foundation/Foundation.h>
#import <CoreGraphics/CoreGraphics.h>
#include <math.h>
#include <stdio.h>
#include <string.h>

static const CGFloat white[3]={0.95047,1,1.08883};
static const CGFloat srgb[9]={0.4124564,0.2126729,0.0193339,0.3575761,0.7151522,0.1191920,0.1804375,0.0721750,0.9503041};

static BOOL paint(CGColorSpaceRef source,CGColorSpaceRef destination,const CGFloat rgba[4],
                  int red,int green,int blue,int alpha) {
    CGContextRef context=CGBitmapContextCreate(NULL,1,1,8,4,destination,
        kCGImageAlphaPremultipliedLast|kCGBitmapByteOrder32Big);
    if (!context) return NO;
    memset(CGBitmapContextGetData(context),0,4);
    CGColorRef color=CGColorCreate(source,rgba);
    CGContextSetFillColorWithColor(context,color);
    CGContextFillRect(context,CGRectMake(0,0,1,1));
    unsigned char *pixel=CGBitmapContextGetData(context);
    BOOL result=abs(pixel[0]-red)<=2 && abs(pixel[1]-green)<=2 && abs(pixel[2]-blue)<=2 && abs(pixel[3]-alpha)<=1;
    if (!result) printf("CalRGB pixel %u,%u,%u,%u expected %d,%d,%d,%d\n",pixel[0],pixel[1],pixel[2],pixel[3],red,green,blue,alpha);
    CGColorRelease(color); CGContextRelease(context);
    return result;
}

int main(void) {
    @autoreleasepool {
        CGColorSpaceRef device=CGColorSpaceCreateDeviceRGB();
        CGColorSpaceRef linear=CGColorSpaceCreateCalibratedRGB(white,NULL,NULL,srgb);
        if (!linear || CGColorSpaceGetModel(linear)!=kCGColorSpaceModelRGB || CGColorSpaceGetNumberOfComponents(linear)!=3) return 1;
        const CGFloat gray[4]={0.5,0.5,0.5,1}, transparent[4]={0.5,0.5,0.5,0.5};
        if (!paint(linear,device,gray,188,188,188,255)) return 2;
        if (!paint(linear,device,transparent,94,94,94,128)) return 3;
        if (!paint(device,linear,gray,55,55,55,255)) return 4;
        if (!paint(linear,linear,gray,128,128,128,255)) return 5;
        CGColorSpaceRef graySpace=CGColorSpaceCreateDeviceGray();
        const CGFloat grayColor[2]={0.5,1};
        if (!paint(graySpace,linear,grayColor,55,55,55,255) ||
            !paint(graySpace,device,grayColor,128,128,128,255)) return 13;
        CGColorSpaceRelease(graySpace);
        const CGFloat gamma2[3]={2,2,2};
        CGColorSpaceRef squared=CGColorSpaceCreateCalibratedRGB(white,NULL,gamma2,srgb);
        if (!squared || !paint(squared,device,gray,137,137,137,255)) return 6;
        CGFloat permutation[9]; memcpy(permutation,srgb,sizeof(permutation));
        for (int i=0;i<3;++i) { permutation[i]=srgb[i+6]; permutation[i+6]=srgb[i]; }
        CGColorSpaceRef swapped=CGColorSpaceCreateCalibratedRGB(white,NULL,NULL,permutation);
        const CGFloat red[4]={1,0,0,1};
        if (!swapped || !paint(swapped,device,red,0,0,255,255)) return 7;
        const CGFloat d50[3]={0.96422,1,0.82521};
        const CGFloat d50matrix[9]={0.4360747,0.2225045,0.0139322,0.3850649,0.7168786,0.0971045,0.1430804,0.0606169,0.7141733};
        CGColorSpaceRef adapted=CGColorSpaceCreateCalibratedRGB(d50,NULL,NULL,d50matrix);
        if (!adapted || !paint(adapted,device,gray,188,188,188,255)) return 8;
        CGFloat mutableWhite[3],mutableGamma[3]={1,1,1},mutableMatrix[9];
        memcpy(mutableWhite,white,sizeof(white)); memcpy(mutableMatrix,srgb,sizeof(srgb));
        CGColorSpaceRef copied=CGColorSpaceCreateCalibratedRGB(mutableWhite,NULL,mutableGamma,mutableMatrix);
        mutableWhite[0]=0.5; mutableGamma[0]=4; mutableMatrix[0]=99;
        if (!copied || !paint(copied,device,gray,188,188,188,255)) return 9;
        CGColorRef a=CGColorCreate(linear,gray),b=CGColorCreate(copied,gray),c=CGColorCreate(device,gray);
        if (!CGColorEqualToColor(a,b) || CGColorEqualToColor(a,c) || CGColorEqualToColor(c,a)) return 10;
        CGColorRelease(a); CGColorRelease(b); CGColorRelease(c);
        const CGFloat badGamma[3]={0,1,1},singular[9]={0};
        if (CGColorSpaceCreateCalibratedRGB(white,NULL,badGamma,srgb) ||
            CGColorSpaceCreateCalibratedRGB(white,NULL,NULL,singular)) return 11;
        CGColorSpaceRef defaults=CGColorSpaceCreateCalibratedRGB(white,NULL,NULL,NULL);
        if (!defaults) return 12;
        CGColorSpaceRelease(defaults); CGColorSpaceRelease(copied); CGColorSpaceRelease(adapted);
        CGColorSpaceRelease(swapped); CGColorSpaceRelease(squared); CGColorSpaceRelease(linear); CGColorSpaceRelease(device);
        puts("CalRGB: solid paints, gamma, matrix, white adaptation, alpha, copying and invalid inputs OK");
    }
    return 0;
}
