package moe.n4tsu.dextop.linux

import java.io.File

/**
 * GPU windows for X11 programs: an application's "GPU window (gamescope)" choice.
 *
 * On Xvfb an X11 program drawing with Turnip has no DRI3, so every frame is read back from the
 * GPU and copied into the X server, a cost that grows with the window. In a GPU window the
 * program runs on gamescope's own Xwayland, which takes its frames as GPU buffers (DRI3), and
 * gamescope hands them to Dextop's Wayland compositor as dma-bufs: no copy through the CPU.
 * It uses DroidDeck's gamescope that the Steam mode uses, staged on demand.
 */
internal object LinuxGpuWindow {
    const val MODE = "gamescope"
    const val LAUNCHER = "/usr/local/bin/dextop-gpu-window"

    private val script = """
        #!/bin/sh
        # Dextop: one X11 program in a gamescope window on Dextop's Wayland compositor.
        # Usage: dextop-gpu-window WIDTH HEIGHT PROGRAM [ARGUMENTS...]
        width=${'$'}1 height=${'$'}2
        shift 2
        # Xvfb's CPU presentation must not reach gamescope's DRI3-capable Xwayland.
        unset DISPLAY MESA_VK_WSI_DEBUG LIBGL_ALWAYS_SOFTWARE
        export XDG_RUNTIME_DIR=${'$'}{XDG_RUNTIME_DIR:-/tmp/runtime-root} XDG_SESSION_TYPE=wayland WAYLAND_DISPLAY=${'$'}{WAYLAND_DISPLAY:-wayland-0}
        mkdir -p "${'$'}XDG_RUNTIME_DIR" && chmod 700 "${'$'}XDG_RUNTIME_DIR"
        if [ -f ${LinuxCatalog.TURNIP_ICD} ]; then
            export VK_ICD_FILENAMES=${LinuxCatalog.TURNIP_ICD} VK_DRIVER_FILES=${LinuxCatalog.TURNIP_ICD}
        fi
        export MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink LIBGL_KOPPER_DRI2=true
        export GAMESCOPE_FORCE_GENERAL_QUEUE=1
        exec /usr/local/bin/dextop-deck-gamescope --backend wayland -W "${'$'}width" -H "${'$'}height" \
            -w "${'$'}width" -h "${'$'}height" -- "${'$'}@"
    """.trimIndent() + "\n"

    /**
     * Makes [root] ready for a GPU window, or says what is missing: Turnip with its Wayland
     * driver ([waylandGpu]), the session's Wayland compositor, Python and Xwayland.
     */
    fun prepare(root: File, waylandGpu: Boolean, compositor: Boolean) {
        check(waylandGpu) { "GPU windows need Turnip with its Wayland driver; select Turnip and run the container's setup again" }
        check(compositor) { "GPU windows need Dextop's Wayland compositor; restart the Linux application session" }
        check(File(root, "usr/bin/python3").exists()) { "GPU windows need python3 in the container (apt install python3)" }
        check(File(root, "usr/bin/Xwayland").exists()) { "GPU windows need Xwayland in the container (apt install xwayland)" }
        LinuxSteam.stageGamescope(root)
        File(root, LAUNCHER.removePrefix("/")).apply {
            parentFile?.mkdirs()
            if (!isFile || readText() != script) {
                val staged = File(parentFile, "$name.staged")
                staged.writeText(script)
                check(staged.setExecutable(true, false) && staged.renameTo(this)) { "Cannot write $LAUNCHER" }
            }
            check(setExecutable(true, false)) { "Cannot make $LAUNCHER executable" }
        }
    }

    /** The launch command for [command] in a [width] x [height] GPU window. */
    fun command(width: Int, height: Int, command: String) =
        "$LAUNCHER ${width.coerceIn(320, 3840)} ${height.coerceIn(240, 2160)} $command"
}
