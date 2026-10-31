package moe.n4tsu.dextop.linux

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Light or dark Linux and Windows applications, following Android's theme or as chosen in
 * the Linux settings. A container gets /etc/dextop/theme.env, which every application launch
 * reads (GTK, libadwaita), GTK's own settings files, and the desktop's
 * color-scheme; dextop-windows turns Windows' dark mode on or off from DEXTOP_THEME.
 * Applications already open keep their theme until they start again.
 */
internal object LinuxTheme {
    private const val TAG = "DextopLinuxTheme"
    private const val PREFS = "linux_android_settings"
    private const val KEY = "theme"

    /** "auto" (follow Android), "light" or "dark". */
    fun mode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "auto") ?: "auto"

    fun setMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode).apply()
        applied.clear()
    }

    fun dark(context: Context): Boolean = when (mode(context)) {
        "dark" -> true
        "light" -> false
        else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    /** The theme each container was last given, so a launch only writes it after a change. */
    private val applied = ConcurrentHashMap<String, Boolean>()

    /** Gives [container] the current theme; blocking, for a worker thread. */
    fun apply(context: Context, linux: ILinuxContainerService, container: String) {
        val dark = dark(context)
        if (applied[container] == dark) return
        runCatching { linux.runCommand(container, script(dark), 60) }
            .onSuccess { applied[container] = dark }
            .onFailure { Log.w(TAG, "theme for $container not applied", it) }
    }

    private fun script(dark: Boolean): String {
        val name = if (dark) "dark" else "light"
        val gtk = if (dark) "1" else "0"
        val scheme = if (dark) "prefer-dark" else "default"
        val theme = if (dark) "Adwaita-dark" else "Adwaita"
        return """
            mkdir -p /etc/dextop /root/.config/gtk-3.0 /root/.config/gtk-4.0
            {
                echo '# Written by Dextop from its Linux theme setting.'
                echo 'export DEXTOP_THEME=$name'
                echo 'export GTK_THEME=${if (dark) "Adwaita:dark" else "Adwaita"}'
                echo 'export ADW_DEBUG_COLOR_SCHEME=${if (dark) "prefer-dark" else "prefer-light"}'
            } >/etc/dextop/theme.env
            for version in 3.0 4.0; do
                file=/root/.config/gtk-${'$'}version/settings.ini
                [ -f "${'$'}file" ] || printf '[Settings]\n' >"${'$'}file"
                grep -q '^gtk-application-prefer-dark-theme=' "${'$'}file" \
                    && sed -i 's/^gtk-application-prefer-dark-theme=.*/gtk-application-prefer-dark-theme=$gtk/' "${'$'}file" \
                    || sed -i '/^\[Settings\]/a gtk-application-prefer-dark-theme=$gtk' "${'$'}file"
            done
            # Applications that read the desktop's setting (GNOME, libadwaita).
            if command -v gsettings >/dev/null 2>&1; then
                buses=${'$'}(grep -o '@/tmp/dextop-dbus-[0-9][0-9]*' /proc/net/unix 2>/dev/null | sort -u)
                # The Android mode may not read /proc/net/unix: each session leaves /tmp/dbus-<display>.log,
                # and a bus that answers a ping is running.
                if [ -z "${'$'}buses" ]; then
                    for log in /tmp/dbus-[0-9]*.log; do
                        [ -f "${'$'}log" ] || continue
                        display=${'$'}{log#/tmp/dbus-}; display=${'$'}{display%.log}
                        dbus-send --bus=unix:abstract=/tmp/dextop-dbus-${'$'}display --dest=org.freedesktop.DBus \
                            /org/freedesktop/DBus org.freedesktop.DBus.Peer.Ping >/dev/null 2>&1 \
                            && buses="${'$'}buses @/tmp/dextop-dbus-${'$'}display"
                    done
                fi
                for bus in ${'$'}buses; do
                    DBUS_SESSION_BUS_ADDRESS=unix:abstract=${'$'}{bus#@} timeout 5 gsettings set org.gnome.desktop.interface color-scheme $scheme 2>/dev/null
                    DBUS_SESSION_BUS_ADDRESS=unix:abstract=${'$'}{bus#@} timeout 5 gsettings set org.gnome.desktop.interface gtk-theme $theme 2>/dev/null
                done
            fi
            exit 0
        """.trimIndent()
    }
}
