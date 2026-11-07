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
                    action: String, packageId: String, onJob: (Job) -> Unit = {}): Boolean {
        if (source !in setOf("deb-get", "flathub") ||
            action !in setOf("prepare", "install", "remove", "upgrade") ||
            (action != "prepare" && !windowsId.matches(packageId))) return false
        if (action != "remove") LinuxStoreSources.requireEnabled(context, source)
        val name = "$source:${if (action == "prepare") "@prepare" else packageId}"
        val log = logFile(name)
        val command = if (action == "prepare") "prepare ${quote(source)}"
            else "${quote(source)} ${quote(action)} ${quote(packageId)}"
        val python = if (action == "prepare") "command -v python3 >/dev/null 2>&1 || " +
            "{ export DEBIAN_FRONTEND=noninteractive; apt-get update && apt-get install -y --no-install-recommends python3; }; " else ""
        val logged = "mkdir -p $LOG_DIR; { $python" + LinuxStoreSources.helper() +
            "/usr/local/bin/dextop-store $command; } > $log 2>&1; " +
            "status=\$?; tail -n 45 $log; exit \$status"
        return run(Job(container, action, listOf(name)), name, logged,
            log, ::sourceProgress, before = {
                if (action != "remove") LinuxStoreSources.requireEnabled(context, source)
                LinuxStoreSources.requireApt(checkNotNull(LinuxContainerClient.service()), container)
            }, onJob = onJob)
    }

    /** Holds the exact job while awaiting completion, even if Store polls consume its result. */
    fun uninstall(context: Context, container: String, source: String, packageId: String) {
        var owned: Job? = null
        val capture: (Job) -> Unit = { owned = it }
        val started = when (source) {
            "apt" -> start(container, "remove", listOf(packageId), before = {
                LinuxStoreSources.requireApt(checkNotNull(LinuxContainerClient.service()), container)
            }, onJob = capture)
            "flathub", "deb-get" -> startSource(context, container, source, "remove", packageId, onJob = capture)
            "pacman" -> {
                require(packageName.matches(packageId))
                val name = "pacman:$packageId"
                val log = logFile(name)
                val script = "mkdir -p $LOG_DIR; pacman -R --noconfirm -- ${quote(packageId)} > $log 2>&1; status=\$?; tail -n 40 $log; exit \$status"
                run(Job(container, "remove", listOf(name)), name, script, log, ::sourceProgress, onJob = capture)
            }
            else -> error("This application's uninstall method is not supported")
        }
        check(started) { "Another operation for this application is already in progress" }
        val job = checkNotNull(owned)
        while (job.state in setOf("queued", "running")) Thread.sleep(200)
        check(job.state == "done") { job.output.takeLast(4000).ifBlank { "Uninstall failed: ${job.state}" } }
    }

    class Job(val container: String, val action: String, val packages: List<String>) {
        /** "queued" until the jobs before it in its container finish, then "running", "done" or "failed". */
        val notificationId = notificationIds.incrementAndGet()
        @Volatile var state = "queued"
        @Volatile var queuedAt = System.currentTimeMillis()
        @Volatile var output = ""
        @Volatile var line = ""
        /** 0–100 from the job's progress reports, or -1. */
        @Volatile var percent = -1.0
        @Volatile var phase = ""
        @Volatile var estimating = false
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
                } + if (job.percent >= 0) " ${"%.1f".format(java.util.Locale.ROOT, job.percent.coerceIn(0.0, 100.0))}%" else ""
            }
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pending = intent?.let { android.app.PendingIntent.getActivity(context, job.notificationId, it,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE) }
            val notification = android.app.Notification.Builder(context, channel)
                .setSmallIcon(if (active) android.R.drawable.stat_sys_download else if (job.state == "done")
                    android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                .setContentTitle(title).setContentText(status).setContentIntent(pending)
                .setStyle(android.app.Notification.BigTextStyle().bigText(if (job.line.isEmpty()) status else "$status\n${job.line}"))
                .setCategory(android.app.Notification.CATEGORY_PROGRESS).setOnlyAlertOnce(true)
                .setOngoing(active).setAutoCancel(!active)
                .setGroup("dextop.store.${job.container}")
            if (active) notification.setProgress(1000, (job.percent.coerceIn(0.0, 100.0) * 10).toInt(),
                job.state == "queued" || job.percent < 0)
            manager.notify(job.notificationId, notification.build())
        }
    }

    private val jobs = ConcurrentHashMap<String, Job>()
    fun hasActiveJobs(): Boolean = jobs.values.any { it.state == "queued" || it.state == "running" }
    private val packageName = Regex("^[a-z0-9][a-z0-9+.-]+$")
    private val windowsId = Regex("^[A-Za-z0-9][A-Za-z0-9.+_-]{0,127}$")
    private const val LOG_DIR = "/tmp"
    /** Windows applications' job names, beside Debian package names. */
    const val WINDOWS_PREFIX = "win:"

    private fun key(container: String, name: String) = "$container\u0000$name"

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    /** [action]: "install", "remove" or "upgrade"; an empty [packages] upgrades everything. */
    fun start(container: String, action: String, packages: List<String>, repair: Boolean = false,
              before: () -> Unit = {}, onJob: (Job) -> Unit = {}): Boolean {
        if (action !in setOf("install", "remove", "upgrade")) return false
        if (packages.any { !packageName.matches(it) }) return false
        val name = packages.joinToString(" ").ifEmpty { "*" }
        val installedPackages = if (action == "install" && packages.any {
            it.substringBefore(':') in setOf("libreoffice", "libreoffice-core", "libreoffice-writer",
                "libreoffice-calc", "libreoffice-impress", "libreoffice-draw", "libreoffice-base", "libreoffice-math")
        }) (packages + "libreoffice-gtk3").distinct() else packages
        val list = installedPackages.joinToString(" ")
        val log = logFile(name)
        // APT::Status-Fd makes apt report dlstatus:/pmstatus: progress lines in the log.
        val apt = "apt-get -o APT::Status-Fd=1 -o Dpkg::Use-Pty=0 " + when (action) {
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
            "then echo 'dextop-stage:update:Updating package lists'; apt-get -o APT::Status-Fd=1 update; fi; echo 'dextop-stage:resolve:Resolving packages'; $apt; } > $log 2>&1; status=\$?; tail -n 40 $log; exit \$status"
        return run(Job(container, action, packages), name, script, log, ::aptProgress, before = before, onJob = onJob)
    }

    private fun aptProgress(job: Job, tail: String) {
        val lines = tail.replace('\r', '\n').lineSequence().map(String::trim).filter { it.isNotEmpty() }.toList()
        for (line in lines) {
            if (line.startsWith("dextop-stage:")) {
                val parts = line.split(':', limit = 3)
                if (parts.size == 3) { job.phase = parts[1]; job.line = parts[2]; job.percent = -1.0 }
            } else LinuxStoreProgress.apt(line)?.let {
                job.percent = it.percent
                // dlstatus during index refresh is a package-list download, not an install.
                if (job.phase != "update" || it.phase != "download") job.phase =
                    if (it.phase == "install" && job.action == "remove") "remove" else it.phase
                job.line = it.line
                job.estimating = false
            }
        }
        if (job.line.isEmpty()) job.line = lines.lastOrNull().orEmpty().take(400)
    }

    private fun sourceProgress(job: Job, tail: String) {
        aptProgress(job, tail)
        val line = tail.replace('\r', '\n').lineSequence().lastOrNull { it.startsWith("dextop-progress-json:") } ?: return
        runCatching {
            val value = org.json.JSONObject(line.substringAfter(':'))
            job.percent = value.optDouble("percent", -1.0).takeIf { it.isFinite() }?.coerceIn(-1.0, 100.0) ?: -1.0
            job.line = value.optString("line").take(500)
            job.phase = value.optString("phase")
            job.estimating = value.optBoolean("estimating")
        }
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
        return run(Job(container, action, listOf(name)), name, script, log, { job, status ->
            // dextop-progress:<percent>:<text>
            val parts = status.replace('\r', '\n').lineSequence().lastOrNull { it.startsWith("dextop-progress:") }.orEmpty().split(':', limit = 3)
            if (parts.size == 3) {
                job.percent = parts[1].toDoubleOrNull() ?: job.percent
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

    private fun logFile(name: String) = "$LOG_DIR/dextop-store-${name.replace(Regex("[^A-Za-z0-9+.-]"), "_").take(80)}-${java.util.UUID.randomUUID()}.log"

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
                    progress: (Job, String) -> Unit, before: () -> Unit = {}, onJob: (Job) -> Unit = {}): Boolean {
        if (jobs[key(job.container, name)]?.state.let { it == "running" || it == "queued" }) return false
        jobs[key(job.container, name)] = job
        onJob(job)
        notify(job)
        enqueue(job.container, Runnable {
            try {
                if (job.state == "cancelled") return@Runnable
                before()
                job.state = "running"
                notify(job)
                val before = if (job.action == "install") appIds(job.container) else null
                execute(job, script, progressCommand, progress)
                if (before != null && job.state == "done") addLaunchers(job.container, before)
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

    private fun apps(container: String): org.json.JSONArray? = runCatching {
        org.json.JSONArray(checkNotNull(LinuxContainerClient.service())
            .listApps(container, java.util.Locale.getDefault().toLanguageTag()))
    }.getOrNull()

    private fun appIds(container: String): Set<String>? = apps(container)?.let { list ->
        (0 until list.length()).mapTo(HashSet()) { list.getJSONObject(it).optString("id") }
    }

    /**
     * With launcher installs set to automatic, every desktop entry a finished install added gets
     * its home-screen launcher without the user asking for it.
     */
    private fun addLaunchers(container: String, before: Set<String>) {
        val context = notificationContext ?: return
        if (LinuxShortcutInstaller.mode(context) == "manual" && !LinuxShortcutInstaller.play) return
        if (!LinuxShortcutInstaller.silentAvailable(context)) return
        val list = apps(container) ?: return
        for (i in 0 until list.length()) {
            val app = list.getJSONObject(i)
            val id = app.optString("id")
            if (id.isEmpty() || id in before || LinuxShortcutInstaller.installed(context, container, id)) continue
            val icon = app.optString("icon").takeIf { it.isNotEmpty() }
                ?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }.getOrNull() }
            runCatching {
                LinuxShortcutInstaller.install(context, container, id, app.optString("name"), icon, silent = true)
            }.onFailure { android.util.Log.w("DextopStore", "Launcher for $id not added", it) }
        }
    }

    private fun execute(job: Job, script: String, progressCommand: String, progress: (Job, String) -> Unit) {
        val service = LinuxContainerClient.service()
        if (service == null) { job.state = "failed"; job.output = "Linux is not connected"; return }
        val poller = Thread({
            while (job.state == "running") {
                runCatching {
                    val tail = service.guestLog(job.container, progressCommand.substringAfterLast('/'), 16384, false)
                    val previous = listOf(job.line, job.percent, job.phase, job.estimating)
                    if (job.state == "running") progress(job, tail)
                    if (job.state == "running" && previous != listOf(job.line, job.percent, job.phase, job.estimating)) notify(job)
                }
                try { Thread.sleep(350) } catch (_: InterruptedException) { return@Thread }
            }
        }, "DextopStorePoll").apply { isDaemon = true; start() }
        runCatching {
            val result = org.json.JSONObject(service.runCommand(job.container, script, 3600))
            job.output = result.optString("output")
            job.state = if (result.optInt("exitCode", 1) == 0) "done" else "failed"
            if (job.state == "done") job.percent = 100.0
        }.onFailure { error ->
            job.output = error.message ?: error.toString()
            job.state = "failed"
        }
        poller.interrupt()
        runCatching { service.refreshContainerSize(job.container, true) }
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
                "line" to job.line, "percent" to job.percent, "phase" to job.phase, "estimating" to job.estimating, "output" to job.output.takeLast(4000))
        }
        return result
    }
}
