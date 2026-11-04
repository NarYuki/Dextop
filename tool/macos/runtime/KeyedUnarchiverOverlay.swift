import Foundation

extension NSKeyedUnarchiver {
    // Apple's Swift overlay retains this spelling instead of the Clang-imported
    // unarchiveTopLevelObject(with:). Keep the actual throwing decoder beneath it.
    @nonobjc
    public class func unarchiveTopLevelObjectWithData(_ data: Data) throws -> Any? {
        try self.__unarchiveTopLevelObjectWithData(data)
    }
}
