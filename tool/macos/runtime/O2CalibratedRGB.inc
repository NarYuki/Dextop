// Relative-colorimetric CalRGB conversion. ICC intents and black-point
// compensation are separate work; preserve black-point metadata for them.
#include <math.h>
#include <stdint.h>
#include <string.h>

static O2Float DextopRGBClamp(O2Float value) { return fmin(1, fmax(0, value)); }

static void DextopRGBMultiply(const O2Float m[9], const O2Float v[3], O2Float out[3]) {
    O2Float result[3];
    for (int row=0; row<3; ++row)
        result[row]=m[row]*v[0]+m[row+3]*v[1]+m[row+6]*v[2];
    memcpy(out,result,sizeof(result));
}

static BOOL DextopRGBInverse(const O2Float m[9], O2Float out[9]) {
    O2Float d=m[0]*(m[4]*m[8]-m[7]*m[5])-m[3]*(m[1]*m[8]-m[7]*m[2])+m[6]*(m[1]*m[5]-m[4]*m[2]);
    if (!isfinite(d) || fabs(d)<1e-12) return NO;
    out[0]=(m[4]*m[8]-m[7]*m[5])/d; out[1]=(m[7]*m[2]-m[1]*m[8])/d; out[2]=(m[1]*m[5]-m[4]*m[2])/d;
    out[3]=(m[6]*m[5]-m[3]*m[8])/d; out[4]=(m[0]*m[8]-m[6]*m[2])/d; out[5]=(m[3]*m[2]-m[0]*m[5])/d;
    out[6]=(m[3]*m[7]-m[6]*m[4])/d; out[7]=(m[6]*m[1]-m[0]*m[7])/d; out[8]=(m[0]*m[4]-m[3]*m[1])/d;
    for (int i=0;i<9;++i) if (!isfinite(out[i])) return NO;
    return YES;
}

static const O2Float DextopD65[3]={0.95047,1,1.08883};
static const O2Float DextopSRGBToXYZ[9]={0.4124564,0.2126729,0.0193339,0.3575761,0.7151522,0.1191920,0.1804375,0.0721750,0.9503041};

// Bradford cone-response chromatic adaptation; matrices are column-major.
static void DextopAdaptRGBWhite(O2Float xyz[3],const O2Float from[3],const O2Float to[3]) {
    static const O2Float bradford[9]={0.8951,-0.7502,0.0389,0.2664,1.7135,-0.0685,-0.1614,0.0367,1.0296};
    static const O2Float inverse[9]={0.9869929,0.4323053,-0.0085287,-0.1470543,0.5183603,0.0400428,0.1599627,0.0492912,0.9684867};
    if (from[0]==to[0] && from[1]==to[1] && from[2]==to[2]) return;
    O2Float source[3],target[3],cone[3];
    DextopRGBMultiply(bradford,from,source); DextopRGBMultiply(bradford,to,target);
    DextopRGBMultiply(bradford,xyz,cone);
    for (int i=0;i<3;++i) {
        if (fabs(source[i])<1e-12) {
            for (int j=0;j<3;++j) xyz[j]*=to[j]/from[j];
            return;
        }
        cone[i]*=target[i]/source[i];
    }
    DextopRGBMultiply(inverse,cone,xyz);
}

@implementation O2CalibratedRGBColorSpace
- (BOOL)isEqualToColorSpace:(O2ColorSpaceRef)other {
    if (![other isKindOfClass:[O2CalibratedRGBColorSpace class]]) return NO;
    O2CalibratedRGBColorSpace *rhs=(O2CalibratedRGBColorSpace *)other;
    for (int i=0;i<3;++i)
        if (_calWhite[i]!=rhs->_calWhite[i] || _calBlack[i]!=rhs->_calBlack[i] || _calGamma[i]!=rhs->_calGamma[i]) return NO;
    for (int i=0;i<9;++i) if (_calMatrix[i]!=rhs->_calMatrix[i]) return NO;
    return YES;
}
- (BOOL)isEqual:(id)other { return [self isEqualToColorSpace:other]; }
- (NSUInteger)hash {
    NSUInteger result=5381;
    const O2Float *arrays[]={_calWhite,_calBlack,_calGamma,_calMatrix};
    for (int a=0;a<4;++a) for (int i=0;i<(a==3?9:3);++i) {
        double value=arrays[a][i]; if (value==0) value=0; // Equal signed zeros hash alike.
        uint64_t bits; memcpy(&bits,&value,sizeof(bits));
        result=result*33+(NSUInteger)(bits^(bits>>32));
    }
    return result;
}
@end

O2ColorSpaceRef O2ColorSpaceCreateCalibratedRGB(const O2Float *white,const O2Float *black,const O2Float *gamma,const O2Float *matrix) {
    static const O2Float identity[9]={1,0,0,0,1,0,0,0,1};
    O2Float inverse[9];
    if (!white) return nil;
    for (int i=0;i<3;++i)
        if (!isfinite(white[i]) || white[i]<=0 ||
            (black && (!isfinite(black[i]) || black[i]<0)) ||
            (gamma && (!isfinite(gamma[i]) || gamma[i]<=0))) return nil;
    if (!matrix) matrix=identity;
    for (int i=0;i<9;++i) if (!isfinite(matrix[i])) return nil;
    if (!DextopRGBInverse(matrix,inverse)) return nil;
    O2CalibratedRGBColorSpace *space=[[O2CalibratedRGBColorSpace alloc] initWithDeviceRGB];
    for (int i=0;i<3;++i) {
        space->_calWhite[i]=white[i]; space->_calBlack[i]=black?black[i]:0;
        space->_calGamma[i]=gamma?gamma[i]:1;
    }
    memcpy(space->_calMatrix,matrix,sizeof(space->_calMatrix));
    memcpy(space->_calInverse,inverse,sizeof(space->_calInverse));
    return space;
}

BOOL O2ColorSpaceConvertRGB(O2ColorSpaceRef source,O2ColorSpaceRef destination,const O2Float in[3],O2Float out[3]) {
    BOOL sourceCal=[source isKindOfClass:[O2CalibratedRGBColorSpace class]];
    BOOL destinationCal=[destination isKindOfClass:[O2CalibratedRGBColorSpace class]];
    if (!sourceCal && !destinationCal) { memcpy(out,in,3*sizeof(*out)); return YES; }
    if (O2ColorSpaceGetModel(source)!=kO2ColorSpaceModelRGB || O2ColorSpaceGetModel(destination)!=kO2ColorSpaceModelRGB) return NO;
    if ([source isEqualToColorSpace:destination]) { memcpy(out,in,3*sizeof(*out)); return YES; }
    O2Float linear[3],xyz[3];
    if (sourceCal) {
        O2CalibratedRGBColorSpace *cal=(O2CalibratedRGBColorSpace *)source;
        for (int i=0;i<3;++i) linear[i]=pow(DextopRGBClamp(in[i]),cal->_calGamma[i]);
        DextopRGBMultiply(cal->_calMatrix,linear,xyz);
        DextopAdaptRGBWhite(xyz,cal->_calWhite,DextopD65);
    } else {
        for (int i=0;i<3;++i) {
            O2Float v=DextopRGBClamp(in[i]);
            linear[i]=v<=0.04045?v/12.92:pow((v+0.055)/1.055,2.4);
        }
        DextopRGBMultiply(DextopSRGBToXYZ,linear,xyz);
    }
    if (destinationCal) {
        O2CalibratedRGBColorSpace *cal=(O2CalibratedRGBColorSpace *)destination;
        DextopAdaptRGBWhite(xyz,DextopD65,cal->_calWhite);
        DextopRGBMultiply(cal->_calInverse,xyz,linear);
        for (int i=0;i<3;++i) out[i]=pow(DextopRGBClamp(linear[i]),1/cal->_calGamma[i]);
    } else {
        O2Float inverse[9]; DextopRGBInverse(DextopSRGBToXYZ,inverse);
        DextopRGBMultiply(inverse,xyz,linear);
        for (int i=0;i<3;++i) {
            O2Float v=DextopRGBClamp(linear[i]);
            out[i]=v<=0.0031308?12.92*v:1.055*pow(v,1/2.4)-0.055;
        }
    }
    return YES;
}
