package moe.n4tsu.dextop.linux

/** Linux interpreters must never receive a checkout's Windows line endings. */
internal object LinuxScript {
    fun text(script: String): String = script.removePrefix("\uFEFF").replace("\r\n", "\n")

    /** Shared resource collections also contain archives and native libraries. */
    fun resource(bytes: ByteArray): ByteArray =
        if (bytes.size >= 2 && bytes[0] == '#'.code.toByte() && bytes[1] == '!'.code.toByte())
            text(bytes.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
        else bytes
}
