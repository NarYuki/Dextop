package moe.n4tsu.dextop.linux

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Same APK, ordinary Android UID. It never starts or attaches a privilege provider. */
class LinuxRuntimeService : Service() {
    private lateinit var runtime: LinuxContainerService
    override fun onCreate() {
        super.onCreate()
        check(android.os.Process.myUid() !in setOf(0, 2000))
        LinuxPaths.initializeAndroid(this)
        // Linux runs as this app's child processes here, which Android otherwise limits to 32.
        LinuxPhantomProcesses.ensureLifted(this)
        runtime = LinuxContainerService(applicationContext)
    }
    override fun onBind(intent: Intent?): IBinder = runtime
    override fun onDestroy() {
        runtime.close()
        super.onDestroy()
    }
}
