package moe.n4tsu.dextop.linux

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.android.apksig.ApkSigner
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal

/**
 * Home-screen launchers for Linux applications: a copy of the bundled template APK with the
 * application's package name, label and icon, signed with a key kept in Android's keystore
 * and installed through the system installer. Opening it opens the application in Dextop.
 */
internal object LinuxShortcutInstaller {
    private const val TAG = "DextopLinuxShortcut"
    private const val TEMPLATE = "linux-shortcut-template.apk"
    private const val KEY_ALIAS = "dextop_linux_launchers"
    private const val PREFS = "linux_shortcuts"
    const val PACKAGE_PREFIX = "moe.n4tsu.dextop.lx."
    private const val TEMPLATE_PACKAGE = "moe.n4tsu.dextop.lxstub"
    /** The template's versionCode; older launchers cannot host windows yet and are rebuilt. */
    const val VERSION = 2
    /** The launcher's activity that roots each window's task. */
    const val WINDOW_ACTIVITY = "moe.n4tsu.dextop.lxstub.Window"
    private const val MODE_KEY = "install_mode"

    /**
     * How launchers are installed: "auto" through Shizuku/Stellar or Dextop's built-in access,
     * with no confirmation; "manual" through Android's installer; "ask" until the user chose.
     */
    fun mode(context: Context): String =
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).getString(MODE_KEY, "ask") ?: "ask"

    fun setMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).edit().putString(MODE_KEY, mode).apply()
    }

    private const val PREFS_SETTINGS = "linux_shortcut_settings"

    /** Whether launchers can be installed without Android asking (Shizuku, Stellar or built-in access). */
    fun silentAvailable(): Boolean = LinuxContainerClient.service() != null || moe.n4tsu.dextop.PrivilegedAccess(TAG).isAvailable()

    /** The launcher package for [desktopId] of [container]; stable, so a new build replaces the old one. */
    fun packageName(container: String, desktopId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$container\u0000$desktopId".toByteArray())
        return PACKAGE_PREFIX + "a" + digest.take(10).joinToString("") { "%02x".format(it) }
    }

    fun installed(context: Context, container: String, desktopId: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName(container, desktopId), 0); true
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun installedVersion(context: Context, target: String): Long? = runCatching {
        context.packageManager.getPackageInfo(target, 0).longVersionCode
    }.getOrNull()

    /** The launcher package that can host [desktopId]'s windows in its own tasks, if installed and current. */
    fun hostPackage(context: Context, container: String, desktopId: String): String? {
        val target = packageName(container, desktopId)
        return target.takeIf { (installedVersion(context, target) ?: 0) >= VERSION &&
            trustedHost(context, target, container, desktopId) }
    }

    /** Package names and version codes are public, so only our existing signing key grants trust. */
    fun trustedHost(context: Context, caller: String?, container: String?, desktop: String?): Boolean = runCatching {
        if (caller == null || container.isNullOrBlank() || desktop.isNullOrBlank() ||
            !container.matches(Regex("c[a-zA-Z0-9]+")) || '/' in desktop || '\\' in desktop ||
            caller != packageName(container, desktop)) return false
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val expected = store.getCertificate(KEY_ALIAS)?.encoded ?: return false
        val info = context.packageManager.getPackageInfo(caller, PackageManager.GET_SIGNING_CERTIFICATES)
        val signers = info.signingInfo?.apkContentsSigners ?: return false
        if (signers.size != 1 || !MessageDigest.isEqual(expected, signers.single().toByteArray())) return false
        val metadata = context.packageManager.getApplicationInfo(caller, PackageManager.GET_META_DATA).metaData
        metadata?.getString("dextop.target") == context.packageName &&
            metadata.getString("dextop.container") == container && metadata.getString("dextop.desktop") == desktop
    }.getOrDefault(false)

    private val upgrading = HashSet<String>()

    /**
     * Rebuilds an installed launcher made from an older template, keeping its name and icon.
     * Silent when the user chose automatic installs and privileged access is available.
     */
    fun upgradeIfOld(context: Context, container: String, desktopId: String) {
        val target = packageName(container, desktopId)
        val version = installedVersion(context, target) ?: return
        if (version >= VERSION || !upgrading.add(target)) return
        val app = context.applicationContext
        Thread {
            runCatching {
                val pm = app.packageManager
                val label = pm.getApplicationLabel(pm.getApplicationInfo(target, 0)).toString()
                val drawable = pm.getApplicationIcon(target)
                val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, 192, 192)
                drawable.draw(Canvas(bitmap))
                val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                install(app, container, desktopId, label, png, silent = mode(app) == "auto", iconReady = true)
            }.onFailure { Log.w(TAG, "Launcher $target not upgraded", it) }
        }.start()
    }

    /** Every launcher Dextop made and that is still installed, as "container:desktopId" keys. */
    fun installedKeys(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.keys.filter { key ->
            val (container, desktop) = key.split(':', limit = 2).takeIf { it.size == 2 } ?: return@filter false
            installed(context, container, desktop)
        }
    }

    /**
     * Builds and installs the launcher. Returns "permission" when Dextop may not install apps
     * yet (the settings page is opened), otherwise "pending": Android asks the user next.
     */
    fun install(context: Context, container: String, desktopId: String, label: String, iconPng: ByteArray?,
                silent: Boolean = false, iconReady: Boolean = false): String {
        val quiet = silent || mode(context) == "auto"
        check(!quiet || silentAvailable()) { "Automatic installation requires a running Stellar, Shizuku or built-in connection" }
        if (!quiet && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "permission"
        }
        val target = packageName(container, desktopId)
        val work = File(context.cacheDir, "linux-shortcuts").apply { mkdirs() }
        val template = File(work, TEMPLATE)
        context.assets.open(TEMPLATE).use { input -> template.outputStream().use { input.copyTo(it) } }
        val unsigned = File(work, "$target-unsigned.apk")
        val signed = File(work, "$target.apk")
        rewrite(template, unsigned, mapOf(
            TEMPLATE_PACKAGE to target,
            "LXSTUB_LABEL_PLACEHOLDER_0000000000" to label.ifBlank { desktopId.removeSuffix(".desktop") },
            "LXSTUB_TARGET_PLACEHOLDER" to context.packageName,
            "LXSTUB_CONTAINER_PLACEHOLDER" to container,
            "LXSTUB_DESKTOP_PLACEHOLDER" to desktopId,
        ), if (iconReady && iconPng != null) iconPng else iconFor(iconPng))
        val (key, certificate) = signingKey()
        ApkSigner.Builder(listOf(ApkSigner.SignerConfig.Builder("DEXTOPLX", key, listOf(certificate)).build()))
            .setInputApk(unsigned)
            .setOutputApk(signed)
            .setMinSdkVersion(26)
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)
            .build()
            .sign()
        unsigned.delete()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("$container:$desktopId", target).apply()
        if (quiet) {
            installQuietly(context, signed, target)
            return "installed"
        }
        commit(context, signed, target)
        return "pending"
    }

    /** Asks Android to remove the launcher of [desktopId]. */
    fun uninstall(context: Context, container: String, desktopId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("$container:$desktopId").apply()
        val target = packageName(container, desktopId)
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$target")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Installs [apk] as the shell user (Shizuku, Stellar or Dextop's built-in access), which
     * needs no confirmation. The shell reads it from Dextop's external files directory.
     */
    private fun installQuietly(context: Context, apk: File, target: String) {
        val linux = LinuxContainerClient.service()
        if (linux != null) {
            android.os.ParcelFileDescriptor.open(apk, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                linux.installLauncher(fd, apk.length(), context.packageName)
            }
            apk.delete()
            Log.i(TAG, "Installed launcher $target through Linux privilege service")
            return
        }
        val shared = File(checkNotNull(context.getExternalFilesDir("launchers")), "$target.apk")
        try {
            apk.copyTo(shared, overwrite = true)
            fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
            val command = "pm install -r -i ${quote(context.packageName)} --user current -S ${shared.length()} < ${quote(shared.absolutePath)}"
            val result = moe.n4tsu.dextop.PrivilegedAccess(TAG)
                .execute("/system/bin/sh", "-c", command)
            check(result.succeeded && result.output.lineSequence().any { it.trim() == "Success" }) {
                "Automatic launcher install failed: ${result.output} ${result.error}"
            }
            apk.delete()
            Log.i(TAG, "Installed launcher $target quietly")
        } finally {
            shared.delete()
        }
    }

    private fun commit(context: Context, apk: File, target: String) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(target)
            setSize(apk.length())
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("launcher.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, Status::class.java).putExtra("package", target)
            val pending = PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
        apk.delete()
    }

    /** Shows Android's confirmation when the installation needs one, and logs the outcome. */
    class Status : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "Installed launcher ${intent.getStringExtra("package")}")
                else -> Log.w(TAG, "Launcher ${intent.getStringExtra("package")} not installed: $status " +
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
            }
        }
    }

    // region APK contents

    /** Copies [template] to [output] with [strings] replaced in the manifest and [icon] as the launcher icon. */
    private fun rewrite(template: File, output: File, strings: Map<String, String>, icon: ByteArray) {
        ZipFile(template).use { zip ->
            ZipOutputStream(output.outputStream().buffered()).use { out ->
                for (entry in zip.entries()) {
                    if (entry.name.startsWith("META-INF/")) continue
                    var data = zip.getInputStream(entry).use { it.readBytes() }
                    when {
                        entry.name == "AndroidManifest.xml" -> data = replaceStrings(data, strings)
                        entry.name.startsWith("res/") && entry.name.endsWith(".png") -> data = icon
                    }
                    val copy = ZipEntry(entry.name)
                    if (entry.name == "resources.arsc") {
                        copy.method = ZipEntry.STORED
                        copy.size = data.size.toLong()
                        copy.compressedSize = data.size.toLong()
                        copy.crc = CRC32().apply { update(data) }.value
                    }
                    out.putNextEntry(copy)
                    out.write(data)
                    out.closeEntry()
                }
            }
        }
    }

    /** A square launcher icon from the application's icon, or a plain penguin-green tile. */
    private fun iconFor(png: ByteArray?): ByteArray {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val source = png?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (source != null) {
            canvas.drawBitmap(source, null, RectF(8f, 8f, size - 8f, size - 8f), paint)
        } else {
            paint.color = Color.rgb(0x2E, 0x7D, 0x32)
            canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 48f, 48f, paint)
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /**
     * Replaces whole strings in a binary XML document's string pool. References into the pool
     * are by index, so only the pool chunk and the document size change.
     */
    private fun replaceStrings(xml: ByteArray, replacements: Map<String, String>): ByteArray {
        val input = ByteBuffer.wrap(xml).order(ByteOrder.LITTLE_ENDIAN)
        val poolStart = input.getShort(2).toInt() and 0xFFFF
        check(input.getShort(poolStart).toInt() == 0x0001) { "No string pool in the template manifest" }
        val poolHeader = input.getShort(poolStart + 2).toInt() and 0xFFFF
        val poolSize = input.getInt(poolStart + 4)
        val count = input.getInt(poolStart + 8)
        val styles = input.getInt(poolStart + 12)
        val flags = input.getInt(poolStart + 16)
        val stringsStart = input.getInt(poolStart + 20)
        val stylesStart = input.getInt(poolStart + 24)
        val utf8 = flags and (1 shl 8) != 0

        val strings = (0 until count).map { index ->
            val at = poolStart + stringsStart + input.getInt(poolStart + poolHeader + index * 4)
            if (utf8) {
                var p = at
                if (xml[p].toInt() and 0x80 != 0) p += 2 else p += 1
                val length: Int
                if (xml[p].toInt() and 0x80 != 0) {
                    length = ((xml[p].toInt() and 0x7F) shl 8) or (xml[p + 1].toInt() and 0xFF); p += 2
                } else { length = xml[p].toInt() and 0xFF; p += 1 }
                String(xml, p, length, Charsets.UTF_8)
            } else {
                var p = at
                var length = input.getShort(p).toInt() and 0xFFFF; p += 2
                if (length and 0x8000 != 0) { length = ((length and 0x7FFF) shl 16) or (input.getShort(p).toInt() and 0xFFFF); p += 2 }
                String(xml, p, length * 2, Charsets.UTF_16LE)
            }
        }.map { replacements[it] ?: it }

        val data = ByteArrayOutputStream()
        val offsets = IntArray(count)
        strings.forEachIndexed { index, value ->
            offsets[index] = data.size()
            if (utf8) {
                val bytes = value.toByteArray(Charsets.UTF_8)
                fun length8(n: Int) { if (n > 0x7F) { data.write(0x80 or (n shr 8)); data.write(n and 0xFF) } else data.write(n) }
                length8(value.length)
                length8(bytes.size)
                data.write(bytes)
                data.write(0)
            } else {
                val n = value.length
                fun word(v: Int) { data.write(v and 0xFF); data.write((v shr 8) and 0xFF) }
                if (n > 0x7FFF) { word(0x8000 or (n shr 16)); word(n and 0xFFFF) } else word(n)
                data.write(value.toByteArray(Charsets.UTF_16LE))
                data.write(0); data.write(0)
            }
        }
        while (data.size() % 4 != 0) data.write(0)
        val styleOffsets = ByteArray(styles * 4).also { System.arraycopy(xml, poolStart + poolHeader + count * 4, it, 0, it.size) }
        val styleData = if (styles > 0) xml.copyOfRange(poolStart + stylesStart, poolStart + poolSize) else ByteArray(0)

        val newStringsStart = poolHeader + count * 4 + styles * 4
        val newPoolSize = newStringsStart + data.size() + styleData.size
        val pool = ByteBuffer.allocate(newPoolSize).order(ByteOrder.LITTLE_ENDIAN)
        pool.put(xml, poolStart, poolHeader)
        pool.putInt(4, newPoolSize)
        pool.putInt(20, newStringsStart)
        pool.putInt(24, if (styles > 0) newStringsStart + data.size() else 0)
        pool.position(poolHeader)
        offsets.forEach(pool::putInt)
        pool.put(styleOffsets)
        pool.put(data.toByteArray())
        pool.put(styleData)

        val rest = xml.copyOfRange(poolStart + poolSize, xml.size)
        val result = ByteBuffer.allocate(poolStart + newPoolSize + rest.size).order(ByteOrder.LITTLE_ENDIAN)
        result.put(xml, 0, poolStart)
        result.put(pool.array())
        result.put(rest)
        result.putInt(4, result.capacity())
        return result.array()
    }
    // endregion

    /** The launchers' signing key, created once in Android's keystore so updates keep the same signer. */
    private fun signingKey(): Pair<PrivateKey, X509Certificate> {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(KEY_ALIAS)) {
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }.time
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSubject(X500Principal("CN=Dextop Linux launchers"))
                    .setCertificateNotAfter(end)
                    .build())
            }.generateKeyPair()
        }
        val key = store.getKey(KEY_ALIAS, null) as PrivateKey
        val certificate = store.getCertificate(KEY_ALIAS) as X509Certificate
        return key to certificate
    }
}
