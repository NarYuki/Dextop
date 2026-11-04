import CoreGraphics
import Darwin

let space = CGColorSpaceCreateDeviceRGB()!
let components: [CGFloat] = [1, 0, 0, 1, 0, 0, 1, 1]
let gradient = CGGradient(colorSpace: space, colorComponents: components, locations: nil, count: 2)!
let info = CGBitmapInfo(0x4001)
let context = CGBitmapContextCreate(nil, 100, 1, 8, 400, space, info)!.takeRetainedValue()
let pixels = CGBitmapContextGetData(context)!.assumingMemoryBound(to: UInt8.self)
for index in 0..<400 { pixels[index] = 0 }
context.drawLinearGradient(gradient, start: CGPoint(x: 0, y: 0.5), end: CGPoint(x: 100, y: 0.5))
func near(_ actual: UInt8, _ expected: Int) -> Bool { abs(Int(actual) - expected) <= 4 }
precondition(near(pixels[0], 254) && near(pixels[2], 1) && pixels[3] == 255)
precondition(near(pixels[200], 126) && near(pixels[202], 129) && pixels[203] == 255)
precondition(near(pixels[396], 1) && near(pixels[398], 254) && pixels[399] == 255)
let located = CGGradient(colorSpace: space, colorComponents: components, locations: [0.25, 0.75], count: 2)!
for index in 0..<400 { pixels[index] = 0 }
context.drawLinearGradient(located, start: CGPoint(x: 25, y: 0.5), end: CGPoint(x: 75, y: 0.5), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
precondition(pixels[0] > 250 && pixels[2] < 5 && pixels[398] > 250 && pixels[396] < 5)
for index in 0..<400 { pixels[index] = 0 }
context.drawRadialGradient(gradient, startCenter: CGPoint(x: 50.5, y: 0.5), startRadius: 0, endCenter: CGPoint(x: 50.5, y: 0.5), endRadius: 50, options: [.drawsAfterEndLocation])
print("Radial left", pixels[0], pixels[1], pixels[2], pixels[3], "center", pixels[200], pixels[201], pixels[202], pixels[203])
fflush(stdout)
precondition(pixels[200] > 250 && pixels[202] < 5 && pixels[2] > 250)
for x in 0..<100 { precondition(pixels[x * 4 + 3] == 255, "radial gradient has a transparent hole") }
let empty = CGGradient(colorSpace: space, colorComponents: components, locations: nil, count: 0)
precondition(empty == nil)
print("Swift gradients: initializers, linear/radial pixels, stops and options OK")
