import CoreGraphics
import CoreFoundation

extension CGContext {
    public func drawLinearGradient(_ gradient: CGGradient, start: CGPoint, end: CGPoint, options: CGGradientDrawingOptions = []) {
        CGContextDrawLinearGradient(self, gradient, start, end, options)
    }
    public func drawRadialGradient(_ gradient: CGGradient, startCenter: CGPoint, startRadius: CGFloat, endCenter: CGPoint, endRadius: CGFloat, options: CGGradientDrawingOptions = []) {
        CGContextDrawRadialGradient(self, gradient, startCenter, startRadius, endCenter, endRadius, options)
    }
}
