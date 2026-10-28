package moe.n4tsu.dextop.linux

internal object LinuxLaunchFailure {
    fun chromiumGpu(code: Int, output: CharSequence): Boolean = code != 0 &&
        output.contains("GPU process isn't usable", ignoreCase = true)

    fun gpu(code: Int, output: CharSequence): Boolean =
        code == 139 || code == 134 || chromiumGpu(code, output)
}
