import Foundation

let original = ["count": 42, "other": -7]
let archive = NSKeyedArchiver.archivedData(withRootObject: original)!
let decoded = try NSKeyedUnarchiver.unarchiveTopLevelObjectWithData(archive) as? [String: Int]
precondition(decoded == original)
let nilArchive = NSKeyedArchiver.archivedData(withRootObject: nil)!
let nilRoot = try NSKeyedUnarchiver.unarchiveTopLevelObjectWithData(nilArchive)
precondition(nilRoot == nil, "a valid nil root must not become a decoding error")
var threw = false
do {
    _ = try NSKeyedUnarchiver.unarchiveTopLevelObjectWithData(Data([0x00, 0x01, 0x02]))
} catch {
    threw = true
    let failure = error as NSError
    precondition(failure.domain() == NSCocoaErrorDomain && failure.code() == 4864)
}
precondition(threw, "invalid archive must throw through the public Swift wrapper")
let value = NSNumber(value: 12.375)
precondition(value.doubleValue == 12.375 && value.stringValue == "12.375")
print("Swift keyed unarchiver: public API, object roundtrip and thrown decoder errors OK")
