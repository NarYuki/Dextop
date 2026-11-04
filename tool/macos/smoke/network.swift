// Own-source TCP regression. The independent host server validates payload bytes.
import Foundation
import Dispatch
import Network
import Darwin

func require(_ condition: Bool, _ reason: String) {
    if !condition { print("network FAIL: \(reason)"); exit(1) }
}
func wait(_ semaphore: DispatchSemaphore, _ reason: String) {
    require(semaphore.wait(timeout: .now() + 20) == .success, reason)
}
let environment = ProcessInfo.processInfo.environment
guard let portString = environment["DEXTOP_NETWORK_TEST_PORT"], let port = UInt16(portString),
      let refusedString = environment["DEXTOP_NETWORK_REFUSED_PORT"], let refusedPort = UInt16(refusedString) else {
    print("network requires the independent loopback server harness"); exit(2)
}
let queue = DispatchQueue(label: "dextop.network.probe")
let sent = DispatchSemaphore(value: 0), response = DispatchSemaphore(value: 0), ended = DispatchSemaphore(value: 0)
let connection = NWConnection(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: port)!, using: .tcp)
connection.send(content: Data(repeating: 97, count: 4 * 1024 * 1024),contentContext: .finalMessage,
    completion: .contentProcessed { error in require(error == nil,"sending data and FIN"); sent.signal() })
// Both operations are queued before start. Fragmented response must meet the minimum.
connection.receive(minimumIncompleteLength: 5,maximumLength: 64) { data, _, complete, error in
    require(error == nil && data == Data("hello".utf8),"fragmented minimum-length receive")
    response.signal()
    if complete { ended.signal() }
    else {
        connection.receive(minimumIncompleteLength: 1,maximumLength: 64) { data, _, complete, error in
            require(error == nil && data == nil && complete,"EOF without spurious error")
            ended.signal()
        }
    }
}
connection.start(queue: queue)
wait(sent,"send timeout"); wait(response,"response timeout"); wait(ended,"EOF timeout")
connection.cancel()

let cancelled = DispatchSemaphore(value: 0), pending = DispatchSemaphore(value: 0)
let second = NWConnection(host: "127.0.0.1",port: NWEndpoint.Port(rawValue: port)!,using: .tcp)
var cancellations = 0, receiveCompletions = 0
second.stateUpdateHandler = { state in
    if state == .ready {
        second.receive(minimumIncompleteLength: 4,maximumLength: 16) { data, _, _, error in
            receiveCompletions += 1
            require(data == nil && error == .posix(.ECANCELED),"cancel pending receive")
            pending.signal()
        }
        queue.asyncAfter(deadline: .now() + 0.1) { second.cancel(); second.cancel() }
    }
    if state == .cancelled { cancellations += 1; cancelled.signal() }
}
second.start(queue: queue)
wait(pending,"pending receive cancellation timeout"); wait(cancelled,"cancel state timeout")
queue.sync { require(cancellations == 1 && receiveCompletions == 1,"exactly-once cancellation") }

let rejected = DispatchSemaphore(value: 0)
let rejectedCancelled = DispatchSemaphore(value: 0)
let third = NWConnection(host: "127.0.0.1",port: NWEndpoint.Port(rawValue: refusedPort)!,using: .tcp)
third.stateUpdateHandler = { state in
    if case .failed(let error) = state {
        require(error == .posix(.ECONNREFUSED),"connection refusal error")
        rejected.signal()
    }
    if state == .cancelled { rejectedCancelled.signal() }
}
third.start(queue: queue)
wait(rejected,"refused connection timeout")
third.cancel(); third.cancel()
wait(rejectedCancelled,"cancelling failed connection timeout")
print("network: TCP payload, fragmented receive, EOF, cancellation and refusal OK")
