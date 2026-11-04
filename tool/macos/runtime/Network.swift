// Initial Network overlay: asynchronous TCP clients backed by POSIX sockets.
// TLS, UDP, listeners, browsing and Network.framework's C ABI remain unfinished.
import Foundation
import Dispatch
#if canImport(Darwin)
import Darwin
#else
import Glibc
#endif

public enum NWError: Error, Equatable {
    case posix(POSIXErrorCode)
    case dns(Int32)
    case tls(Int32)
}

public struct IPv4Address: Hashable {
    public let rawValue: Data
    public init?(_ string: String) {
        var address = in_addr()
        guard string.withCString({ inet_pton(AF_INET, $0, &address) }) == 1 else { return nil }
        rawValue = withUnsafeBytes(of: address) { Data($0) }
    }
    fileprivate var string: String { rawValue.withUnsafeBytes { bytes in
        var buffer = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
        _ = inet_ntop(AF_INET, bytes.baseAddress!, &buffer, socklen_t(buffer.count))
        return String(cString: buffer)
    } }
}

public struct IPv6Address: Hashable {
    public let rawValue: Data
    public init?(_ string: String) {
        var address = in6_addr()
        guard string.withCString({ inet_pton(AF_INET6, $0, &address) }) == 1 else { return nil }
        rawValue = withUnsafeBytes(of: address) { Data($0) }
    }
    fileprivate var string: String { rawValue.withUnsafeBytes { bytes in
        var buffer = [CChar](repeating: 0, count: Int(INET6_ADDRSTRLEN))
        _ = inet_ntop(AF_INET6, bytes.baseAddress!, &buffer, socklen_t(buffer.count))
        return String(cString: buffer)
    } }
}

public final class NWInterface: Hashable {
    public enum InterfaceType { case other, wifi, cellular, wiredEthernet, loopback }
    public let name: String
    public let index: Int
    public let type: InterfaceType
    private init(name: String, index: Int, type: InterfaceType) {
        self.name = name; self.index = index; self.type = type
    }
    public static func == (lhs: NWInterface, rhs: NWInterface) -> Bool { lhs.index == rhs.index }
    public func hash(into hasher: inout Hasher) { hasher.combine(index) }
}

public enum NWEndpoint {
    public enum Host: ExpressibleByStringLiteral {
        case name(String, interface: NWInterface?)
        case ipv4(IPv4Address)
        case ipv6(IPv6Address)
        public init(_ string: String) {
            if let v4 = IPv4Address(string) { self = .ipv4(v4) }
            else if let v6 = IPv6Address(string) { self = .ipv6(v6) }
            else { self = .name(string, interface: nil) }
        }
        public init(stringLiteral value: String) { self.init(value) }
        fileprivate var string: String {
            switch self {
            case .name(let name, _): return name
            case .ipv4(let address): return address.string
            case .ipv6(let address): return address.string
            }
        }
    }
    public struct Port: RawRepresentable, ExpressibleByIntegerLiteral, Hashable {
        public let rawValue: UInt16
        public init?(rawValue: UInt16) { self.rawValue = rawValue }
        public init(integerLiteral value: UInt16) { rawValue = value }
    }
    case hostPort(host: Host, port: Port)
}

public final class NWParameters {
    public static var tcp: NWParameters { NWParameters() }
    private init() {}
}

public final class NWConnection {
    public enum State: Equatable {
        case setup, waiting(NWError), preparing, ready, failed(NWError), cancelled
    }
    public final class ContentContext {
        public let identifier: String
        public let isFinal: Bool
        public init(identifier: String, isFinal: Bool = false) {
            self.identifier = identifier; self.isFinal = isFinal
        }
        public static let defaultMessage = ContentContext(identifier: "defaultMessage")
        public static let defaultStream = ContentContext(identifier: "defaultStream", isFinal: true)
        public static let finalMessage = ContentContext(identifier: "finalMessage", isFinal: true)
    }
    public enum SendCompletion {
        case contentProcessed((NWError?) -> Void)
        case idempotent
    }
    public let endpoint: NWEndpoint
    public let parameters: NWParameters
    private let lock = NSLock()
    private var currentState: State = .setup
    private var handler: ((State) -> Void)?
    public var state: State { lock.lock(); defer { lock.unlock() }; return currentState }
    public var stateUpdateHandler: ((State) -> Void)? {
        get { lock.lock(); defer { lock.unlock() }; return handler }
        set { lock.lock(); handler = newValue; lock.unlock() }
    }
    private let worker = DispatchQueue(label: "moe.n4tsu.dextop.network.tcp")
    private var callbackQueue: DispatchQueue?
    private var started = false, finished = false, ready = false, eof = false, writeClosed = false
    private var terminalError: NWError?
    private var fd: Int32 = -1
    private var socketGeneration: UInt64 = 0
    private var readSource: DispatchSourceRead?
    private var writeSource: DispatchSourceWrite?
    private struct Address { let family: Int32; let bytes: Data }
    private var addresses: [Address] = []
    private struct Send {
        let data: Data; var offset = 0
        let final: Bool; let completion: SendCompletion
    }
    private struct Receive {
        let minimum: Int; let maximum: Int
        var data = Data()
        let completion: (Data?, ContentContext?, Bool, NWError?) -> Void
    }
    private var sends: [Send] = [], receives: [Receive] = []

    public convenience init(host: NWEndpoint.Host, port: NWEndpoint.Port, using: NWParameters) {
        self.init(to: .hostPort(host: host, port: port), using: using)
    }
    public init(to endpoint: NWEndpoint, using parameters: NWParameters) {
        self.endpoint = endpoint; self.parameters = parameters
    }
    deinit { readSource?.cancel(); writeSource?.cancel(); if fd >= 0 { _ = close(fd) } }
    private func callback(_ body: @escaping () -> Void) {
        (callbackQueue ?? DispatchQueue.global()).async(execute: body)
    }
    private func transition(_ state: State) {
        lock.lock(); currentState = state; let update = handler; lock.unlock()
        if let update = update { callback { update(state) } }
    }
    private func error(_ code: Int32 = errno) -> NWError {
        .posix(POSIXErrorCode(rawValue: code) ?? .EIO)
    }
    public func start(queue: DispatchQueue) {
        worker.async {
            guard !self.started, !self.finished else { return }
            self.started = true
            self.callbackQueue = DispatchQueue(label: "moe.n4tsu.dextop.network.callbacks", target: queue)
            self.transition(.preparing)
            guard case .hostPort(let host, let port) = self.endpoint else { return }
            if case .name(_, let interface) = host, interface != nil {
                let failure = self.error(ENOTSUP)
                self.finish(.failed(failure),error: failure); return
            }
            // Name resolution may block. Keep it off both the caller's queue and
            // the state queue so cancellation remains responsive during DNS.
            DispatchQueue.global().async {
                var hints = addrinfo()
                hints.ai_family = AF_UNSPEC
                #if canImport(Darwin)
                hints.ai_socktype = SOCK_STREAM
                #else
                hints.ai_socktype = Int32(SOCK_STREAM.rawValue)
                #endif
                hints.ai_protocol = Int32(IPPROTO_TCP)
                var first: UnsafeMutablePointer<addrinfo>?
                let status = getaddrinfo(host.string, String(port.rawValue), &hints, &first)
                var result: [Address] = []
                var cursor = first
                while let address = cursor {
                    let value = address.pointee
                    if let bytes = value.ai_addr, value.ai_family == AF_INET || value.ai_family == AF_INET6 {
                        result.append(Address(family: value.ai_family, bytes: Data(bytes: bytes, count: Int(value.ai_addrlen))))
                    }
                    cursor = value.ai_next
                }
                if let first = first { freeaddrinfo(first) }
                let resolved = result
                self.worker.async {
                    guard !self.finished else { return }
                    guard status == 0 else { self.finish(.failed(.dns(status)), error: .dns(status)); return }
                    self.addresses = resolved
                    self.connectNext(lastError: self.error(EHOSTUNREACH))
                }
            }
        }
    }
    private func closeSocket() {
        socketGeneration &+= 1
        readSource?.cancel(); readSource = nil
        writeSource?.cancel(); writeSource = nil
        if fd >= 0 { _ = close(fd); fd = -1 }
    }
    private func connectNext(lastError: NWError) {
        closeSocket()
        guard !addresses.isEmpty else { finish(.failed(lastError), error: lastError); return }
        let address = addresses.removeFirst()
        #if canImport(Darwin)
        fd = socket(address.family, SOCK_STREAM, 0)
        #else
        fd = socket(address.family, Int32(SOCK_STREAM.rawValue), 0)
        #endif
        guard fd >= 0 else { connectNext(lastError: error()); return }
        guard fcntl(fd, F_SETFL, O_NONBLOCK) == 0 else { connectNext(lastError: error()); return }
        #if canImport(Darwin)
        var enabled: Int32 = 1
        _ = setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &enabled, socklen_t(MemoryLayout<Int32>.size))
        #endif
        let result = address.bytes.withUnsafeBytes { bytes in
            connect(fd, bytes.baseAddress!.assumingMemoryBound(to: sockaddr.self), socklen_t(bytes.count))
        }
        if result == 0 { connected(); return }
        guard errno == EINPROGRESS else { connectNext(lastError: error()); return }
        watchWrite()
    }
    private func watchWrite() {
        guard writeSource == nil else { return }
        let descriptor = fd
        let generation = socketGeneration
        let source = DispatchSource.makeWriteSource(fileDescriptor: descriptor, queue: worker)
        source.setEventHandler { [weak self] in
            guard let self = self, !self.finished, self.fd == descriptor,
                  self.socketGeneration == generation else { return }
            if !self.ready {
                var code: Int32 = 0, length = socklen_t(MemoryLayout<Int32>.size)
                if getsockopt(descriptor,SOL_SOCKET,SO_ERROR,&code,&length) != 0 { code = errno }
                if code != 0 { self.connectNext(lastError: self.error(code)); return }
                self.connected()
            } else { self.drainSends() }
        }
        writeSource = source; source.resume()
    }
    private func connected() {
        ready = true; writeSource?.cancel(); writeSource = nil
        transition(.ready); drainSends(); drainReceives()
    }
    public func send(content: Data?, contentContext: ContentContext = .defaultMessage,
                     isComplete: Bool = true, completion: SendCompletion) {
        worker.async {
            if self.finished || self.writeClosed {
                self.complete(completion, self.terminalError ?? self.error(EPIPE)); return
            }
            self.sends.append(Send(data: content ?? Data(), final: isComplete && contentContext.isFinal,
                                   completion: completion))
            self.drainSends()
        }
    }
    private func complete(_ completion: SendCompletion, _ error: NWError?) {
        if case .contentProcessed(let body) = completion { callback { body(error) } }
    }
    private func drainSends() {
        guard ready, !finished else { return }
        while !sends.isEmpty {
            if writeClosed {
                let request = sends.removeFirst(); complete(request.completion, error(EPIPE)); continue
            }
            if sends[0].offset < sends[0].data.count {
                let request = sends[0]
                let count = request.data.withUnsafeBytes { bytes -> Int in
                    #if canImport(Darwin)
                    return Darwin.send(fd,bytes.baseAddress!.advanced(by: request.offset),bytes.count - request.offset,0)
                    #else
                    return Glibc.send(fd,bytes.baseAddress!.advanced(by: request.offset),bytes.count - request.offset,Int32(MSG_NOSIGNAL))
                    #endif
                }
                if count > 0 { sends[0].offset += count; continue }
                if count < 0 && errno == EINTR { continue }
                if count < 0 && (errno == EAGAIN || errno == EWOULDBLOCK) { watchWrite(); return }
                let failure = error(count == 0 ? EPIPE : errno)
                finish(.failed(failure), error: failure); return
            }
            let request = sends.removeFirst()
            if request.final { _ = shutdown(fd,Int32(SHUT_WR)); writeClosed = true }
            complete(request.completion,nil)
        }
        writeSource?.cancel(); writeSource = nil
    }
    public func receive(minimumIncompleteLength: Int, maximumLength: Int,
                        completion: @escaping (Data?, ContentContext?, Bool, NWError?) -> Void) {
        worker.async {
            guard minimumIncompleteLength >= 0, maximumLength > 0,
                  minimumIncompleteLength <= maximumLength else {
                self.callback { completion(nil,nil,false,self.error(EINVAL)) }; return
            }
            if self.finished {
                self.callback { completion(nil,nil,false,self.terminalError ?? self.error(ECANCELED)) }; return
            }
            self.receives.append(Receive(minimum: minimumIncompleteLength, maximum: maximumLength, completion: completion))
            self.drainReceives()
        }
    }
    private func drainReceives() {
        guard ready, !finished else { return }
        while !receives.isEmpty {
            var request = receives.removeFirst()
            if !eof {
                var bytes = [UInt8](repeating: 0, count: min(65536,request.maximum - request.data.count))
                let count = recv(fd,&bytes,bytes.count,0)
                if count > 0 { request.data.append(contentsOf: bytes.prefix(count)) }
                else if count == 0 { eof = true }
                else if errno == EINTR { receives.insert(request,at: 0); continue }
                else if errno == EAGAIN || errno == EWOULDBLOCK {
                    receives.insert(request,at: 0)
                    if readSource == nil {
                        let descriptor = fd
                        let generation = socketGeneration
                        let source = DispatchSource.makeReadSource(fileDescriptor: descriptor,queue: worker)
                        source.setEventHandler { [weak self] in
                            guard let self = self, self.fd == descriptor,
                                  self.socketGeneration == generation else { return }
                            self.drainReceives()
                        }
                        readSource = source; source.resume()
                    }
                    return
                } else {
                    receives.insert(request,at: 0); let failure = error()
                    finish(.failed(failure),error: failure); return
                }
            }
            if eof || request.data.count >= max(1,request.minimum) {
                let data = request.data.isEmpty ? nil : request.data
                let complete = eof
                callback { request.completion(data,.defaultStream,complete,nil) }
            } else { receives.insert(request,at: 0) }
        }
        readSource?.cancel(); readSource = nil
    }
    public func cancel() {
        worker.async {
            guard self.state != .cancelled else { return }
            let error = self.error(ECANCELED)
            if self.finished {
                self.terminalError = error
                self.transition(.cancelled)
            } else { self.finish(.cancelled,error: error) }
        }
    }
    private func finish(_ state: State, error: NWError) {
        guard !finished else { return }
        finished = true; terminalError = error; closeSocket()
        for request in sends { complete(request.completion,error) }
        sends.removeAll()
        for request in receives {
            let data = request.data.isEmpty ? nil : request.data
            callback { request.completion(data,nil,false,error) }
        }
        receives.removeAll(); transition(state)
    }
}
