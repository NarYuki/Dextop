package moe.n4tsu.dextop.linux

import java.util.concurrent.ConcurrentHashMap

/**
 * Package installs, removals and upgrades the Store runs in a container with apt. Each job
 * runs on a thread of its own, so the Linux client's queue stays free while apt works.
 */
internal object LinuxStoreJobs {
    class Job(val container: String, val action: String, val packages: List<String>) {
        @Volatile var state = "running"
        @Volatile var output = ""
        @Volatile var line = ""
        /** 0–100 from apt's status reports (download first half, unpacking second), or -1. */
        @Volatile var percent = -1
    }

    private val jobs = ConcurrentHashMap<String, Job>()
    private val packageName = Regex("^[a-z0-9][a-z0-9+.-]+$")
    private const val LOG_DIR = "/var/tmp/dextop-store"

    private fun key(container: String, name: String) = "$container\u0000$name"

    /** [action]: "install", "remove" or "upgrade"; an empty [packages] upgrades everything. */
    fun start(container: String, action: String, packages: List<String>): Boolean {
        if (packages.any { !packageName.matches(it) }) return false
        val name = packages.joinToString(" ").ifEmpty { "*" }
        if (jobs[key(container, name)]?.state == "running") return false
        val job = Job(container, action, packages)
        jobs[key(container, name)] = job
        val list = packages.joinToString(" ")
        val log = "$LOG_DIR/${name.replace(Regex("[^A-Za-z0-9+.-]"), "_")}.log"
        // APT::Status-Fd makes apt report dlstatus:/pmstatus: progress lines in the log.
        val apt = "apt-get -o APT::Status-Fd=1 " + when (action) {
            "remove" -> "remove -y $list"
            "upgrade" -> if (packages.isEmpty()) "upgrade -y" else "install -y --only-upgrade $list"
            else -> "install -y $list"
        }
        // Package lists older than 12 hours are refreshed first.
        val script = "mkdir -p $LOG_DIR; export DEBIAN_FRONTEND=noninteractive; " +
            "{ if [ -z \"\$(find /var/lib/apt/lists -maxdepth 1 -name '*Packages*' -mmin -720 2>/dev/null | head -n 1)\" ]; " +
            "then apt-get update; fi; $apt; } > $log 2>&1; status=\$?; tail -n 40 $log; exit \$status"
        Thread({
            val service = LinuxContainerClient.service()
            if (service == null) { job.state = "failed"; job.output = "Linux is not connected"; return@Thread }
            val poller = Thread({
                while (job.state == "running") {
                    runCatching {
                        val result = org.json.JSONObject(service.runCommand(container,
                            "grep -E '^(dl|pm)status:' $log 2>/dev/null | tail -n 1", 10))
                        val status = result.optString("output").trim().lines().lastOrNull().orEmpty()
                        // dlstatus:<n>:<percent>:<text> / pmstatus:<package>:<percent>:<text>
                        val parts = status.split(':', limit = 4)
                        if (parts.size == 4) {
                            val value = parts[2].toFloatOrNull() ?: 0f
                            job.percent = if (parts[0] == "dlstatus") (value / 2).toInt()
                                else if (action == "remove") value.toInt() else 50 + (value / 2).toInt()
                            job.line = parts[3]
                        }
                    }
                    try { Thread.sleep(2500) } catch (_: InterruptedException) { return@Thread }
                }
            }, "DextopStorePoll").apply { isDaemon = true; start() }
            runCatching {
                val result = org.json.JSONObject(service.runCommand(container, script, 3600))
                job.output = result.optString("output")
                job.state = if (result.optInt("exitCode", 1) == 0) "done" else "failed"
            }.onFailure { error ->
                job.output = error.message ?: error.toString()
                job.state = "failed"
            }
            poller.interrupt()
        }, "DextopStore").start()
        return true
    }

    /** Running and finished jobs of [container]; finished ones are reported once and forgotten. */
    fun poll(container: String): List<Map<String, Any?>> {
        val result = jobs.filterValues { it.container == container }.map { (key, job) ->
            if (job.state != "running") jobs.remove(key)
            mapOf("action" to job.action, "packages" to job.packages, "state" to job.state,
                "line" to job.line, "percent" to job.percent, "output" to job.output.takeLast(4000))
        }
        return result
    }
}
