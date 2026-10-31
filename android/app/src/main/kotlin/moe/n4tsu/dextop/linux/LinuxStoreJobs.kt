package moe.n4tsu.dextop.linux

import java.util.concurrent.ConcurrentHashMap
import android.content.Context

/**
 * Package installs, removals and upgrades the Store runs in a container: Linux packages with
 * apt, Windows applications with dextop-windows. Each job runs on a thread of its own, so the
 * Linux client's queue stays free while it works.
 */
internal object LinuxStoreJobs {
    fun startSource(context: android.content.Context, container: String, source: String,
                    action: String, packageId: String): Boolean {
        if (source !in setOf("deb-get", "flathub") ||
            action !in setOf("prepare", "install", "remove", "upgrade") ||
            (action != "prepare" && !windowsId.matches(packageId))) return false
        LinuxStoreSources.requireEnabled(context, source)
        val name = "$source:${if (action == "prepare") "@prepare" else packageId}"
        val log = logFile(name)
        val command = if (action == "prepare") "prepare ${quote(source)}"
            else "${quote(source)} ${quote(action)} ${quote(packageId)}"
        val python = if (action == "prepare") "command -v python3 >/dev/null 2>&1 || " +
            "{ export DEBIAN_FRONTEND=noninteractive; apt-get update && apt-get install -y --no-install-recommends python3; }; " else ""
        val script = "set -e; $python" + LinuxStoreSources.helper() +
            "mkdir -p $LOG_DIR; /usr/local/bin/dextop-store $command > $log 2>&1; "
        // Preserve failure diagnostics instead of exiting before tail.
        val logged = script.replace("set -e; ", "") + "status=\$?; tail -n 45 $log; exit \$status"
        return run(Job(container, action, listOf(name)), name, logged,
            "tail -n 1 $log 2>/dev/null", { job, line -> job.line = line }, before = {
                LinuxStoreSources.requireEnabled(context, source)
                LinuxStoreSources.requireApt(checkNotNull(LinuxContainerClient.service()), container)
            })
    }

    class Job(val container: String, val action: String, val packages: List<String>) {
        /** "queued" until the jobs before it in its container finish, then "running", "done" or "failed". */
        val notificationId = notificationIds.incrementAndGet()
        @Volatile var state = "queued"
        @Volatile var queuedAt = System.currentTimeMillis()
        @Volatile var output = ""
        @Volatile var line = ""
        /** 0–100 from the job's progress reports, or -1. */
        @Volatile var percent = -1
    }

    private val notificationIds = java.util.concurrent.atomic.AtomicInteger(42000)
    private val labels = ConcurrentHashMap<String, String>()
    @Volatile private var notificationContext: Context? = null

    fun attach(context: Context) { notificationContext = context.applicationContext }

    fun metadata(container: String, apps: List<Map<String, Any?>>) {
        for (app in apps) {
            val name = (app["name"] as? String)?.take(120) ?: continue
            val pkg = app["package"] as? String ?: continue
            if (pkg.length <= 256) labels[key(container, pkg)] = name
        }
    }

    private fun notify(job: Job) {
        val context = notificationContext ?: return
        if (job.action == "prepare") return
        runCatching {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            val japanese = context.resources.configuration.locales[0].language == "ja"
            fun text(ja: String, en: String) = moe.n4tsu.dextop.InlineStrings.pick(context.resources.configuration.locales[0], ja, en)
            val channel = "linux_store_installs"
            manager.createNotificationChannel(android.app.NotificationChannel(channel,
                text("アプリのインストール", "App installations"), android.app.NotificationManager.IMPORTANCE_LOW))
            if (job.state == "cancelled") { manager.cancel(job.notificationId); return }
            val active = job.state == "running" || job.state == "queued"
            val title = job.packages.joinToString(", ") { labels[key(job.container, it)] ?: it }.take(180)
                .ifEmpty { text("アプリの更新", "App updates") }
            val status = when (job.state) {
                "queued" -> text("順番を待っています", "Waiting in queue")
                "done" -> when (job.action) {
                    "remove" -> text("アンインストール完了", "Uninstalled")
                    "upgrade" -> text("更新完了", "Updated")
                    else -> text("インストール完了", "Installed")
                }
                "failed" -> text("処理に失敗しました。ストアで確認してください", "Failed. Check the Store for details")
                else -> when (job.action) {
                    "remove" -> text("アンインストール中", "Uninstalling")
                    "upgrade" -> text("更新中", "Updating")
                    else -> text("インストール中", "Installing")
                } + if (job.percent >= 0) " ${job.percent.coerceIn(0, 100)}%" else ""
            }
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pending = intent?.let { android.app.PendingIntent.getActivity(context, job.notificationId, it,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE) }
            val notification = android.app.Notification.Builder(context, channel)
                .setSmallIcon(if (active) android.R.drawable.stat_sys_download else if (job.state == "done")
                    android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                .setContentTitle(title).setContentText(status).setContentIntent(pending)
                .setCategory(android.app.Notification.CATEGORY_PROGRESS).setOnlyAlertOnce(true)
                .setOngoing(active).setAutoCancel(!active)
                .setGroup("dextop.store.${job.container}")
            if (active) notification.setProgress(100, job.percent.coerceIn(0, 100),
                job.state == "queued" || job.percent < 0)
            manager.notify(job.notificationId, notification.build())
        }
    }

    private val jobs = ConcurrentHashMap<String, Job>()
    fun hasActiveJobs(): Boolean = jobs.values.any { it.state == "queued" || it.state == "running" }
    private val packageName = Regex("^[a-z0-9][a-z0-9+.-]+$")
    private val windowsId = Regex("^[A-Za-z0-9][A-Za-z0-9.+_-]{0,127}$")
    private const val LOG_DIR = "/var/tmp/dextop-store"
    /** Windows applications' job names, beside Debian package names. */
    const val WINDOWS_PREFIX = "win:"

    private fun key(container: String, name: String) = "$container\u0000$name"

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    /** [action]: "install", "remove" or "upgrade"; an empty [packages] upgrades everything. */
    fun start(container: String, action: String, packages: List<String>, repair: Boolean = false,
              before: () -> Unit = {}): Boolean {
        if (action !in setOf("install", "remove", "upgrade")) return false
        if (packages.any { !packageName.matches(it) }) return false
        val name = packages.joinToString(" ").ifEmpty { "*" }
        val list = packages.joinToString(" ")
        val log = logFile(name)
        // APT::Status-Fd makes apt report dlstatus:/pmstatus: progress lines in the log.
        val apt = "apt-get -o APT::Status-Fd=1 " + when (action) {
            "remove" -> "remove -y --no-auto-remove $list"
            "upgrade" -> if (packages.isEmpty()) "upgrade -y" else "install -y --only-upgrade $list"
            else -> "install -y $list"
        }
        // Package lists older than 12 hours are refreshed first. With [repair] (the user agreed
        // to it after [diagnose] found a problem), an interrupted dpkg and broken dependencies
        // are fixed first.
        val fix = if (!repair) "" else stageHelper("dextop-apt-heal", "/usr/local/sbin/dextop-apt-heal") +
            "/usr/local/sbin/dextop-apt-heal; dpkg --configure -a --force-confdef --force-confold; " +
            "apt-get -o APT::Status-Fd=1 -f install -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold; "
        val script = "mkdir -p $LOG_DIR; export DEBIAN_FRONTEND=noninteractive; " +
            "{ $fix" +
            "if [ -z \"\$(find /var/lib/apt/lists -maxdepth 1 -name '*Packages*' -mmin -720 2>/dev/null | head -n 1)\" ]; " +
            "then apt-get update; fi; $apt; } > $log 2>&1; status=\$?; tail -n 40 $log; exit \$status"
        return run(Job(container, action, packages), name, script, "grep -E '^(dl|pm)status:' $log 2>/dev/null | tail -n 1", { job, status ->
            // dlstatus:<n>:<percent>:<text> / pmstatus:<package>:<percent>:<text>
            val parts = status.split(':', limit = 4)
            if (parts.size == 4) {
                val value = parts[2].toFloatOrNull() ?: 0f
                job.percent = if (parts[0] == "dlstatus") (value / 2).toInt()
                    else if (action == "remove") value.toInt() else 50 + (value / 2).toInt()
                job.line = parts[3]
            }
        }, before = before)
    }

    /**
     * Installs ([spec] from [WindowsCatalog.details]) or removes Windows application [id] with
     * dextop-windows, which is put in the container first.
     */
    fun startWindows(container: String, action: String, id: String, spec: String?): Boolean {
        if (!windowsId.matches(id) || (action == "install" && spec == null)) return false
        val helper = stageHelper("dextop-windows", "/usr/local/bin/dextop-windows")
        if (helper.isEmpty()) return false
        val name = WINDOWS_PREFIX + id
        val log = logFile(name)
        val command = if (action == "remove") "dextop-windows remove ${quote(id)}" else "dextop-windows install ${quote(spec!!)}"
        val script = "mkdir -p $LOG_DIR /usr/local/bin; $helper" +
            "$command > $log 2>&1; status=\$?; grep -v '^dextop-progress:' $log | tail -n 40; exit \$status"
        return run(Job(container, action, listOf(name)), name, script, "grep '^dextop-progress:' $log 2>/dev/null | tail -n 1", { job, status ->
            // dextop-progress:<percent>:<text>
            val parts = status.split(':', limit = 3)
            if (parts.size == 3) {
                job.percent = parts[1].toIntOrNull() ?: job.percent
                job.line = parts[2]
            }
        })
    }

    /**
     * What keeps apt from working in [container], one line each: "interrupted|<packages>" (a
     * dpkg run was cut off), "broken|<details>" (unmet dependencies), "locked|" (another
     * package manager is working; apt waits for it).
     */
    const val DIAGNOSE = "free() { [ -e \"\$1\" ] || return 0; python3 -c 'import fcntl, sys\n" +
        "f = open(sys.argv[1], \"a\")\nfcntl.lockf(f, fcntl.LOCK_EX | fcntl.LOCK_NB)' \"\$1\" 2>/dev/null; }; " +
        "if [ -n \"\$(ls -A /var/lib/dpkg/updates 2>/dev/null)\" ] || [ -n \"\$(dpkg --audit 2>/dev/null)\" ]; then " +
        "echo \"interrupted|\$(dpkg --audit 2>/dev/null | grep -E '^ [a-z0-9]' | awk '{print \$1}' | sort -u | head -n 6 | tr '\\n' ' ')\"; " +
        "elif ! apt-get check >/dev/null 2>&1; then " +
        "echo \"broken|\$(apt-get check 2>&1 | grep -E '^ ' | head -n 3 | tr -s ' ' | tr '\\n' ' ')\"; fi; " +
        "free /var/lib/dpkg/lock-frontend || echo 'locked|'; exit 0"

    /** Shell commands writing Dextop's script [name] to [path] in the container (empty if missing). */
    private fun stageHelper(name: String, path: String): String {
        val bytes = LinuxSessionCompatibility.script(name) ?: return ""
        val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        return "mkdir -p ${path.substringBeforeLast('/')}; printf '%s' '$encoded' | base64 -d > $path; chmod 755 $path; "
    }

    private fun logFile(name: String) = "$LOG_DIR/${name.replace(Regex("[^A-Za-z0-9+.-]"), "_")}.log"

    /**
     * Jobs waiting in each container, run one at a time in order: package managers share one
     * lock, so parallel jobs would only wait for each other or fail on it.
     */
    private val queues = HashMap<String, ArrayDeque<Runnable>>()
    private val busy = HashSet<String>()

    private fun enqueue(container: String, work: Runnable) = synchronized(queues) {
        queues.getOrPut(container) { ArrayDeque() }.addLast(work)
        if (container !in busy) next(container)
    }

    private fun next(container: String) = synchronized(queues) {
        val work = queues[container]?.removeFirstOrNull()
        if (work == null) busy -= container else {
            busy += container
            Thread(work, "DextopStore").start()
        }
    }

    /** Takes [name] out of [container]'s queue while it still waits; false once it runs. */
    fun cancel(container: String, name: String): Boolean {
        val job = jobs[key(container, name)] ?: return false
        if (job.state != "queued") return false
        job.state = "cancelled"
        notify(job)
        return true
    }

    /** Queues [script] as [job] named [name], reading its progress with [progressCommand] while it runs. */
    private fun run(job: Job, name: String, script: String, progressCommand: String,
                    progress: (Job, String) -> Unit, before: () -> Unit = {}): Boolean {
        if (jobs[key(job.container, name)]?.state.let { it == "running" || it == "queued" }) return false
        jobs[key(job.container, name)] = job
        notify(job)
        enqueue(job.container, Runnable {
            try {
                if (job.state == "cancelled") return@Runnable
                before()
                job.state = "running"
                notify(job)
                execute(job, script, progressCommand, progress)
            } catch (error: Exception) {
                job.state = "failed"
                job.output = error.message ?: error.toString()
            } finally {
                notify(job)
                next(job.container)
            }
        })
        return true
    }

    private fun execute(job: Job, script: String, progressCommand: String, progress: (Job, String) -> Unit) {
        val service = LinuxContainerClient.service()
        if (service == null) { job.state = "failed"; job.output = "Linux is not connected"; return }
        val poller = Thread({
            while (job.state == "running") {
                runCatching {
                    val result = org.json.JSONObject(service.runCommand(job.container, progressCommand, 10))
                    val status = result.optString("output").trim().lines().lastOrNull().orEmpty()
                    progress(job, status)
                    if (job.state == "running") notify(job)
                }
                try { Thread.sleep(2500) } catch (_: InterruptedException) { return@Thread }
            }
        }, "DextopStorePoll").apply { isDaemon = true; start() }
        runCatching {
            val result = org.json.JSONObject(service.runCommand(job.container, script, 3600))
            job.output = result.optString("output")
            job.state = if (result.optInt("exitCode", 1) == 0) "done" else "failed"
        }.onFailure { error ->
            job.output = error.message ?: error.toString()
            job.state = "failed"
        }
        poller.interrupt()
        // Failures are diagnosed from logcat; the container's own log is private to the app.
        if (job.state == "failed") android.util.Log.w("DextopStore", "${job.action} ${job.packages} failed:\n${job.output.takeLast(4000)}")
    }

    /** Running and finished jobs of [container]; finished ones are reported once and forgotten. */
    fun poll(container: String, preparations: Boolean = false, consume: Boolean = true): List<Map<String, Any?>> {
        val result = jobs.filterValues { it.container == container &&
            it.packages.any { name -> name.endsWith(":@prepare") } == preparations
        }.entries.sortedBy { it.value.queuedAt }.map { (key, job) ->
            if (consume && job.state != "running" && job.state != "queued") jobs.remove(key, job)
            mapOf("action" to job.action, "packages" to job.packages, "state" to job.state,
                "line" to job.line, "percent" to job.percent, "output" to job.output.takeLast(4000))
        }
        return result
    }
}
