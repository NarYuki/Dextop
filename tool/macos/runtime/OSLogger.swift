// Dextop's source implementation of Logger messages and privacy interpolation.
// Uses the existing Darling trace transport; this is not Apple's log-store ABI.
public struct OSLogPrivacy {
    fileprivate enum Visibility { case automatic, visible, hidden }
    fileprivate let visibility: Visibility
    public static var auto: Self { Self(visibility: .automatic) }
    public static var `public`: Self { Self(visibility: .visible) }
    public static var `private`: Self { Self(visibility: .hidden) }
    public static var sensitive: Self { Self(visibility: .hidden) }
}

public struct OSLogInterpolation: StringInterpolationProtocol {
    fileprivate var fragments: [() -> String] = []
    public init(literalCapacity: Int, interpolationCount: Int) {
        fragments.reserveCapacity(interpolationCount * 2 + 1)
    }
    public mutating func appendLiteral(_ literal: String) {
        fragments.append { literal }
    }
    private mutating func append<T>(_ value: @escaping () -> T, privacy: OSLogPrivacy, scalar: Bool) {
        let visible = privacy.visibility == .visible || (privacy.visibility == .automatic && scalar)
        // Do not evaluate or convert a private value just to redact it.
        fragments.append { visible ? String(describing: value()) : "<private>" }
    }
    public mutating func appendInterpolation<T>(_ value: @autoclosure @escaping () -> T, privacy: OSLogPrivacy = .auto) {
        append(value, privacy: privacy, scalar: false)
    }
    public mutating func appendInterpolation<T: BinaryInteger>(_ value: @autoclosure @escaping () -> T, privacy: OSLogPrivacy = .auto) {
        append(value, privacy: privacy, scalar: true)
    }
    public mutating func appendInterpolation<T: BinaryFloatingPoint>(_ value: @autoclosure @escaping () -> T, privacy: OSLogPrivacy = .auto) {
        append(value, privacy: privacy, scalar: true)
    }
    public mutating func appendInterpolation(_ value: @autoclosure @escaping () -> Bool, privacy: OSLogPrivacy = .auto) {
        append(value, privacy: privacy, scalar: true)
    }
}

public struct OSLogMessage: ExpressibleByStringInterpolation {
    public let interpolation: OSLogInterpolation
    public init(stringLiteral value: String) {
        var interpolation = OSLogInterpolation(literalCapacity: value.utf8.count, interpolationCount: 0)
        interpolation.appendLiteral(value)
        self.interpolation = interpolation
    }
    public init(stringInterpolation: OSLogInterpolation) { interpolation = stringInterpolation }
    internal func render() -> String { interpolation.fragments.map { $0() }.joined() }
}

extension Logger {
    public func log(level: OSLogType, _ message: OSLogMessage) {
        guard logObject.isEnabled(type: level) else { return }
        message.render().withCString { pointer in
            _emitLog("%{public}s", dso: #dsohandle, log: logObject, type: level, arguments: [pointer])
        }
    }
    public func log(_ message: OSLogMessage) { log(level: .default, message) }
    public func trace(_ message: OSLogMessage) { log(level: .debug, message) }
    public func debug(_ message: OSLogMessage) { log(level: .debug, message) }
    public func info(_ message: OSLogMessage) { log(level: .info, message) }
    public func notice(_ message: OSLogMessage) { log(level: .default, message) }
    public func warning(_ message: OSLogMessage) { log(level: .error, message) }
    public func error(_ message: OSLogMessage) { log(level: .error, message) }
    public func critical(_ message: OSLogMessage) { log(level: .fault, message) }
    public func fault(_ message: OSLogMessage) { log(level: .fault, message) }
}

// Prefer the existing printf/StaticString overloads when those apply. Dynamic
// interpolation uses the same typed privacy and lazy evaluation as Logger.
@_disfavoredOverload
public func os_log(_ message: OSLogMessage) { Logger().log(message) }

@_disfavoredOverload
public func os_log(_ logLevel: OSLogType = .default, log logObject: OSLog = .default, _ message: OSLogMessage) {
    Logger(logObject).log(level: logLevel, message)
}
