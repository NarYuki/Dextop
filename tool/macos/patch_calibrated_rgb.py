"""Add calibrated RGB spaces and relative RGB conversion to Onyx/CoreGraphics."""
import argparse
from pathlib import Path
import shutil

HERE=Path(__file__).resolve().parent

def write_changed(path,text):
    if path.read_text()!=text:path.write_text(text)


def once(text,old,new):
    if new in text:return text
    if text.count(old)!=1:raise RuntimeError('Pinned CalRGB source anchor changed: '+old[:70])
    return text.replace(old,new)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',required=True,type=Path)
    base=parser.parse_args().source/'src/external/cocotron'
    onyx=base/'Onyx2D'
    included=onyx/'O2CalibratedRGB.inc'
    content=(HERE/'runtime/O2CalibratedRGB.inc').read_bytes()
    if not included.exists() or included.read_bytes()!=content:
        shutil.copy2(HERE/'runtime/O2CalibratedRGB.inc',included)
    header=onyx/'include/Onyx2D/O2ColorSpace.h'
    text=header.read_text()
    anchor='@interface O2ColorSpace_indexed : O2ColorSpace {'
    declarations='''@interface O2CalibratedRGBColorSpace : O2ColorSpace {
@public
    O2Float _calWhite[3], _calBlack[3], _calGamma[3], _calMatrix[9], _calInverse[9];
}
@end
O2ColorSpaceRef O2ColorSpaceCreateCalibratedRGB(const O2Float *white,const O2Float *black,const O2Float *gamma,const O2Float *matrix);
BOOL O2ColorSpaceConvertRGB(O2ColorSpaceRef source,O2ColorSpaceRef destination,const O2Float in[3],O2Float out[3]);

'''
    if declarations not in text:text=once(text,anchor,declarations+anchor)
    write_changed(header,text)
    path=onyx/'O2ColorSpace.m';text=path.read_text()
    text=once(text,'''- (BOOL) isEqualToColorSpace: (O2ColorSpaceRef) other {
    if (self->_type != other->_type)''','''- (BOOL) isEqualToColorSpace: (O2ColorSpaceRef) other {
    if (!other || [other isKindOfClass:[O2CalibratedRGBColorSpace class]]) return NO;
    if (self->_type != other->_type)''')
    if '#include "O2CalibratedRGB.inc"' not in text:text+='\n#include "O2CalibratedRGB.inc"\n'
    write_changed(path,text)
    path=base/'CoreGraphics/include/CoreGraphics/CGColorSpace.h';text=path.read_text()
    declaration='''COREGRAPHICS_EXPORT CGColorSpaceRef _Nullable CGColorSpaceCreateCalibratedRGB(const CGFloat * _Nonnull whitePoint, const CGFloat * _Nullable blackPoint, const CGFloat * _Nullable gamma, const CGFloat * _Nullable matrix)
    CF_SWIFT_NAME(CGColorSpace.init(calibratedRGBWhitePoint:blackPoint:gamma:matrix:));
'''
    if declaration not in text:
        anchor='COREGRAPHICS_EXPORT CGColorSpaceRef CGColorSpaceCreateDeviceRGB();'
        text=once(text,anchor,declaration+'\n'+anchor)
    write_changed(path,text)
    path=base/'CoreGraphics/CGColorSpace.m';text=path.read_text()
    implementation='''CGColorSpaceRef CGColorSpaceCreateCalibratedRGB(const CGFloat *whitePoint,const CGFloat *blackPoint,const CGFloat *gamma,const CGFloat *matrix) {
    return (CGColorSpaceRef)O2ColorSpaceCreateCalibratedRGB(whitePoint,blackPoint,gamma,matrix);
}

'''
    if implementation not in text:
        anchor='CGColorSpaceRef CGColorSpaceCreateDeviceRGB() {'
        text=once(text,anchor,implementation+anchor)
    write_changed(path,text)
    path=onyx/'O2Color.m';text=path.read_text()
    anchor='''    O2ColorSpaceModel model = O2ColorSpaceGetModel(inputSpace);

    switch (model)'''
    replacement='''    O2ColorSpaceModel model = O2ColorSpaceGetModel(inputSpace);
    if ([inputSpace isKindOfClass:[O2CalibratedRGBColorSpace class]]) {
        O2ColorSpaceRef device=O2ColorSpaceCreateDeviceRGB();
        BOOL converted=O2ColorSpaceConvertRGB(inputSpace,device,components,rgbComponents);
        O2ColorSpaceRelease(device);
        rgbComponents[3]=components[3];
        return converted;
    }

    switch (model)'''
    write_changed(path,once(text,anchor,replacement))
    path=onyx/'O2Context_builtin.m';text=path.read_text()
    anchor='''        const O2Float *components = O2ColorGetComponents(color);

        if (count == 2)'''
    replacement='''        const O2Float *components = O2ColorGetComponents(color);
        O2Float calibrated[4];
        O2ColorSpaceRef source=O2ColorGetColorSpace(color);
        O2ColorSpaceRef destination=O2ImageGetColorSpace([self surface]);
        if (count==4 && O2ColorSpaceGetModel(source)==kO2ColorSpaceModelRGB &&
            ([source isKindOfClass:[O2CalibratedRGBColorSpace class]] ||
             [destination isKindOfClass:[O2CalibratedRGBColorSpace class]])) {
            O2ColorSpaceRef device=NULL;
            if (!destination || O2ColorSpaceGetModel(destination)!=kO2ColorSpaceModelRGB)
                destination=device=O2ColorSpaceCreateDeviceRGB();
            if (O2ColorSpaceConvertRGB(source,destination,components,calibrated)) {
                calibrated[3]=components[3]; components=calibrated;
            }
            O2ColorSpaceRelease(device);
        }

        if (count == 2)'''
    if '        O2Float calibrated[4];' not in text:
        text=once(text,anchor,replacement)
    anchor='''        if (count == 2)
            result = [[O2Paint_color alloc] initWithGray: components[0]'''
    replacement='''        if (count==2 && [destination isKindOfClass:[O2CalibratedRGBColorSpace class]]) {
            O2Float grayRGB[3]={components[0],components[0],components[0]};
            O2Float alpha=components[1];
            O2ColorSpaceRef device=O2ColorSpaceCreateDeviceRGB();
            if (O2ColorSpaceConvertRGB(device,destination,grayRGB,calibrated)) {
                calibrated[3]=alpha; components=calibrated; count=4;
            }
            O2ColorSpaceRelease(device);
        }
        if (count == 2)
            result = [[O2Paint_color alloc] initWithGray: components[0]'''
    write_changed(path,once(text,anchor,replacement))


if __name__=='__main__':main()
