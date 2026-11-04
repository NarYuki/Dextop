@testable import os
import Darwin

var evaluations = 0
func evaluated() -> String { evaluations += 1; return "MUST_NOT_APPEAR" }
let disabled = Logger(.disabled)
disabled.info("disabled \(evaluated(), privacy: .public)")
precondition(evaluations == 0, "disabled log evaluated interpolation")
let logger = Logger(subsystem: "org.dextop.logger.test", category: "privacy")
let message: OSLogMessage = "public=\("visible", privacy: .public) secret=\(evaluated(), privacy: .private) automatic=\("SECRET_AUTO") number=\(42) fraction=\(2.5) flag=\(true)"
precondition(message.render() == "public=visible secret=<private> automatic=<private> number=42 fraction=2.5 flag=true", "privacy and scalar rendering differ")
precondition(evaluations == 0, "rendering evaluated private expression")
let hidden: OSLogMessage = "hidden=\(777, privacy: .private) sensitive=\(evaluated(), privacy: .sensitive)"
precondition(hidden.render() == "hidden=<private> sensitive=<private>")
precondition(evaluations == 0)
logger.notice("Logger literal 100% preserved")
logger.error("Logger public=\("visible", privacy: .public) secret=\(evaluated(), privacy: .private) automatic=\("SECRET_AUTO") number=\(42) fraction=\(2.5) flag=\(true)")
precondition(evaluations == 0, "private value evaluated before redaction")
logger.fault("Logger sensitive=\(evaluated(), privacy: .sensitive) integer=\(777, privacy: .private)")
precondition(evaluations == 0, "sensitive value evaluated before redaction")
logger.log(level: .default, "Logger explicit-level")
logger.log("Logger default")
logger.trace("trace"); logger.debug("debug"); logger.info("info")
logger.warning("warning"); logger.critical("critical")
os_log(.fault, log: .disabled, "disabled scoped \(evaluated(), privacy: .public)")
os_log(log: .disabled, "disabled default level \(evaluated(), privacy: .public)")
precondition(evaluations == 0, "os_log overloads evaluated disabled interpolations")
os_log("literal overload remains unambiguous")
os_log("legacy integer %d", type: .default, 42)
os_log(.error, log: logger.logObject, "modern \(42) \("visible", privacy: .public)")
print("Logger privacy and lazy evaluation OK")
