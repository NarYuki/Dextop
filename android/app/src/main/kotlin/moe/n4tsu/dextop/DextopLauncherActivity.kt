package moe.n4tsu.dextop

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.Display

/** Routes launcher opens to display-specific activity components. */
class DextopLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        val desktopLaunch = MirrorService.isActive() && launchDisplayId != Display.DEFAULT_DISPLAY
        val destination = if (desktopLaunch) DesktopActivity::class.java else MainActivity::class.java
        val flags = if (desktopLaunch) {
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        } else {
            // Its own task (the ".phone" affinity), never this trampoline's: DeX decorates a
            // window from its task's root, and this translucent, untitled one left the first
            // open without a proper window frame until Dextop was opened a second time.
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(launchDisplayId)
        startActivity(Intent(this, destination).addFlags(flags), options.toBundle())
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
