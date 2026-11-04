import CoreGraphics

let path = CGMutablePath()
let transform = CGAffineTransform(translationX: 10, y: 20)
path.move(to: CGPoint(x: 1, y: 2), transform: transform)
path.addLine(to: CGPoint(x: 3, y: 4), transform: transform)
path.addQuadCurve(to: CGPoint(x: 5, y: 6), control: CGPoint(x: 4, y: 5), transform: transform)
path.addCurve(to: CGPoint(x: 8, y: 9), control1: CGPoint(x: 6, y: 7), control2: CGPoint(x: 7, y: 8), transform: transform)
precondition(path.currentPoint.x == 18 && path.currentPoint.y == 29)
path.closeSubpath()
var kinds: [Int32] = []
var firstPoints: [CGPoint] = []
var nestedCount = 0
path.applyWithBlock { element in
    kinds.append(element.pointee.type.rawValue)
    if element.pointee.type.rawValue != 4 { firstPoints.append(element.pointee.points[0]) }
    if kinds.count == 1 { path.applyWithBlock { _ in nestedCount += 1 } }
}
precondition(kinds == [0, 1, 2, 3, 4] && nestedCount == 5)
precondition(firstPoints[0].x == 11 && firstPoints[0].y == 22)
precondition(firstPoints[2].x == 14 && firstPoints[2].y == 25)
let appended = CGMutablePath()
appended.addPath(path, transform: CGAffineTransform(translationX: -10, y: -20))
var pointCount = 0
appended.applyWithBlock { element in
    if pointCount == 0 { precondition(element.pointee.points[0].x == 1 && element.pointee.points[0].y == 2) }
    pointCount += 1
}
precondition(pointCount == 5)
var copyingTransform = CGAffineTransform(translationX: 30, y: -10)
let transformed = path.copy(using: &copyingTransform)!
var renamedKinds: [CGPathElementType] = []
transformed.applyWithBlock { element in
    renamedKinds.append(element.pointee.type)
    if renamedKinds.count == 1 {
        precondition(element.pointee.points[0].x == 41 && element.pointee.points[0].y == 12)
    }
}
precondition(renamedKinds == [.moveToPoint, .addLineToPoint, .addQuadCurveToPoint, .addCurveToPoint, .closeSubpath])
precondition(MemoryLayout<CGPathElementType>.size == 4)
let unchanged = path.copy(using: nil)!
precondition(unchanged.currentPoint.x == path.currentPoint.x && unchanged.currentPoint.y == path.currentPoint.y)
let empty = CGMutablePath()
empty.addLines(between: []); empty.addRects([])
precondition(empty.isEmpty)
empty.addRect(CGRect(x: 2, y: 3, width: 4, height: 5), transform: transform)
precondition(empty.boundingBox.origin.x == 12 && empty.boundingBox.origin.y == 23 && empty.boundingBox.size.width == 4)
let shapes = CGMutablePath()
shapes.addEllipse(in: CGRect(x: 0, y: 0, width: 10, height: 10))
shapes.addRoundedRect(in: CGRect(x: 20, y: 20, width: 10, height: 10), cornerWidth: 2, cornerHeight: 3)
shapes.addLines(between: [CGPoint(x: 40, y: 40), CGPoint(x: 41, y: 42)])
precondition(!shapes.isEmpty && shapes.currentPoint.x == 41 && shapes.currentPoint.y == 42)
print("Swift paths: transforms, curves, append, nested traversal and shapes OK")
