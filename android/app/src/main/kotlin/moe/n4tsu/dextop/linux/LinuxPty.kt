package moe.n4tsu.dextop.linux

/** JNI (libdextop_linux.so) for the Linux terminal's pseudo-terminals. */
internal object LinuxPty {
    init {
        val library = System.getProperty(LinuxServiceHost.LIBRARY_PROPERTY)
        if (library != null) System.load(library) else System.loadLibrary("dextop_linux")
    }

    /** Starts [argv] on a new PTY; returns the master fd (negative errno on failure), its pid in [pidOut]. */
    @JvmStatic external fun nativeSpawn(argv: Array<String>, envp: Array<String>, cwd: String, rows: Int, cols: Int, pidOut: IntArray): Int
    @JvmStatic external fun nativeWaitFor(pid: Int): Int
    @JvmStatic external fun nativeSpawnPipe(argv: Array<String>, envp: Array<String>, pidOut: IntArray): Int
    @JvmStatic external fun nativeResize(fd: Int, rows: Int, cols: Int): Boolean
}
