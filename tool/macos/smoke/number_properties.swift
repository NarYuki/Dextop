import Foundation

let number = NSNumber(value: 12.375)
let scalar: Double = number.doubleValue
let text: String = number.stringValue
precondition(scalar == 12.375 && text == "12.375")
let formatter = NumberFormatter()
formatter.locale = Locale(identifier: "en_US_POSIX")
formatter.minimumFractionDigits = 1
formatter.maximumFractionDigits = 3
precondition(formatter.minimumFractionDigits == 1 && formatter.maximumFractionDigits == 3)
precondition(formatter.string(from: NSNumber(value: 2)) == "2.0")
precondition(formatter.string(from: number) == "12.375")
precondition(formatter.string(from: NSNumber(value: 12.3751)) == "12.375")
print("Number properties and formatting OK")
