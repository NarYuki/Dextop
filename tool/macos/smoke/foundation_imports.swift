import Foundation

let manager: FileManager = FileManager.default
let defaults: UserDefaults = UserDefaults.standard
precondition(manager.fileExists(atPath: "/Applications"))
let key = "DextopFoundationImportProbe"
defaults.set("roundtrip", forKey: key)
precondition(defaults.object(forKey: key) as? String == "roundtrip")
defaults.removeObject(forKey: key)
let data = Data([0x31, 0x7f]) as NSData
let pointer: UnsafeRawPointer = data.bytes
precondition(pointer.load(as: UInt8.self) == 0x31 && pointer.load(fromByteOffset: 1, as: UInt8.self) == 0x7f)

extension NSMutableAttributedString {
    func testSynchronousEnumeration() {
        var lengthVisited = 0
        enumerateAttributes(in: NSRange(location: 0, length: length), options: []) { attributes, range, _ in
            // Public callbacks are synchronous, so this needs no escaping self capture.
            setAttributes(attributes, range: range)
            lengthVisited += range.length
        }
        precondition(lengthVisited == length)
    }
}
let text = NSMutableAttributedString(string: "abc")
text.testSynchronousEnumeration()
print("Foundation imports: singletons, data bytes and synchronous attributes OK")
