package moe.n4tsu.dextop.linux

import java.io.File
import java.security.MessageDigest

/** Shared GLX/EGL runtime for KGSL clients, including Flatpak runtimes. */
internal object LinuxGpuRuntime {
    const val SHA256 = "e561f2694267bda6fe89ae5d8901c24b5e07e0f2028606e85592b028d515bd22"
    const val PREFIX = "/opt/dextop-gpu/$SHA256"
    const val LIB = "$PREFIX/lib/aarch64-linux-gnu"
    private const val RESOURCE = "/moe/n4tsu/dextop/linux/gpu/mesa-direct.tar.xz"

    fun installed(root: File) = File(root, "$PREFIX/.ready".removePrefix("/")).let {
        it.isFile && it.readText().trim() == SHA256 &&
            File(root, "$LIB/libEGL_mesa.so.0.0.0".removePrefix("/")).isFile
    }

    fun environment(root: File): List<String> = if (installed(root)) listOf(
        "LD_LIBRARY_PATH=$LIB",
        "LIBGL_DRIVERS_PATH=$LIB/dri",
        "__EGL_VENDOR_LIBRARY_FILENAMES=$PREFIX/share/glvnd/egl_vendor.d/50_mesa.json",
        "GBM_BACKENDS_PATH=$LIB/gbm",
    ) else emptyList()

    @Synchronized
    fun prepare(root: File, execute: (String) -> Unit) {
        if (installed(root)) return
        val archive = File(root, "var/cache/dextop/mesa-$SHA256.tar")
        archive.parentFile?.mkdirs()
        val bytes = requireNotNull(javaClass.getResourceAsStream(RESOURCE)).use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(digest == SHA256) { "Packaged Mesa runtime checksum mismatch" }
        // Decompressed here: a fresh image has no xz for tar -J (the Android mode sets up new ones).
        org.tukaani.xz.XZInputStream(bytes.inputStream()).use { input -> archive.outputStream().use { input.copyTo(it) } }
        execute(installScript())
    }

    internal fun installScript() = """
        set -eu
        version=${'$'}(getconf GNU_LIBC_VERSION | cut -d' ' -f2)
        if ! dpkg --compare-versions "${'$'}version" ge 2.38; then
            echo 'Native KGSL Wayland runtime requires glibc 2.38 or later'
            exit 0
        fi
        mkdir -p '$PREFIX'
        tar -xf '/var/cache/dextop/mesa-$SHA256.tar' -C '$PREFIX'
        rm -f '/var/cache/dextop/mesa-$SHA256.tar'
        test -f '$LIB/libEGL_mesa.so.0.0.0'
        test -f '$LIB/libgallium-27.0.0-devel.so'
        test -f '$LIB/libgbm.so.1.0.0'
        test -f '$LIB/libdextop_kgsl_gbm.so'
        printf '%s\n' '$SHA256' >'$PREFIX/.ready.staged'
        mv '$PREFIX/.ready.staged' '$PREFIX/.ready'
    """.trimIndent()
}
