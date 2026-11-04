// Shared Swift path operations, backed by the existing CoreGraphics geometry.
import CoreGraphics
import CoreFoundation
private final class DextopPathCallback {
    let body: (UnsafePointer<CGPathElement>) -> Void
    init(_ body: @escaping (UnsafePointer<CGPathElement>) -> Void) { self.body = body }
}

extension CGPath {
    public var currentPoint: CGPoint { CGPathGetCurrentPoint(self) }
    public func copy(using transform: UnsafePointer<CGAffineTransform>?) -> CGPath? {
        CGPathCreateCopyByTransformingPath(self, transform)
    }
    public func applyWithBlock(_ body: @escaping (UnsafePointer<CGPathElement>) -> Void) {
        let callback = DextopPathCallback(body)
        withExtendedLifetime(callback) {
            CGPathApply(self, Unmanaged.passUnretained(callback).toOpaque()) { info, element in
                guard let info = info, let element = element else { return }
                Unmanaged<DextopPathCallback>.fromOpaque(info).takeUnretainedValue().body(element)
            }
        }
    }
}

extension CGMutablePath {
    public func move(to point: CGPoint, transform: CGAffineTransform = .identity) {
        var transform = transform
        CGPathMoveToPoint(self, &transform, point.x, point.y)
    }
    public func addLine(to point: CGPoint, transform: CGAffineTransform = .identity) {
        var transform = transform
        CGPathAddLineToPoint(self, &transform, point.x, point.y)
    }
    public func addQuadCurve(to point: CGPoint, control: CGPoint, transform: CGAffineTransform = .identity) {
        var transform = transform
        CGPathAddQuadCurveToPoint(self, &transform, control.x, control.y, point.x, point.y)
    }
    public func addCurve(to point: CGPoint, control1: CGPoint, control2: CGPoint, transform: CGAffineTransform = .identity) {
        var transform = transform
        CGPathAddCurveToPoint(self, &transform, control1.x, control1.y, control2.x, control2.y, point.x, point.y)
    }
    public func closeSubpath() { CGPathCloseSubpath(self) }
    public func addPath(_ path: CGPath, transform: CGAffineTransform = .identity) {
        var transform = transform
        CGPathAddPath(self, &transform, path)
    }
    public func addRect(_ rect: CGRect, transform: CGAffineTransform = .identity) {
        var transform = transform
        CGPathAddRect(self, &transform, rect)
    }
    public func addEllipse(in rect: CGRect, transform: CGAffineTransform = .identity) {
        var transform = transform
        CGPathAddEllipseInRect(self, &transform, rect)
    }
    public func addRoundedRect(in rect: CGRect, cornerWidth: CGFloat, cornerHeight: CGFloat, transform: CGAffineTransform = .identity) {
        var transform = transform
        CGPathAddRoundedRect(self, &transform, rect, cornerWidth, cornerHeight)
    }
    public func addLines(between points: [CGPoint], transform: CGAffineTransform = .identity) {
        var transform = transform
        points.withUnsafeBufferPointer { buffer in
            if let start = buffer.baseAddress { CGPathAddLines(self, &transform, start, buffer.count) }
        }
    }
    public func addRects(_ rects: [CGRect], transform: CGAffineTransform = .identity) {
        var transform = transform
        rects.withUnsafeBufferPointer { buffer in
            if let start = buffer.baseAddress { CGPathAddRects(self, &transform, start, buffer.count) }
        }
    }
    public func addArc(center: CGPoint, radius: CGFloat, startAngle: CGFloat, endAngle: CGFloat, clockwise: Bool, transform: CGAffineTransform = .identity) {
        var transform = transform
        CGPathAddArc(self, &transform, center.x, center.y, radius, startAngle, endAngle, clockwise)
    }
    public func addArc(tangent1End: CGPoint, tangent2End: CGPoint, radius: CGFloat, transform: CGAffineTransform = .identity) {
        var transform = transform
        CGPathAddArcToPoint(self, &transform, tangent1End.x, tangent1End.y, tangent2End.x, tangent2End.y, radius)
    }
}
