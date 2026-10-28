package moe.n4tsu.dextop.linux

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.ZipFile

/**
 * Windows applications for the Store, from the winget community catalogue: the same index
 * winget itself downloads (source.msix, an SQLite database), and each package's merged
 * manifest from Microsoft's CDN. Installing goes through dextop-windows in the container.
 */
internal object WindowsCatalog {
    private const val SOURCE = "https://cdn.winget.microsoft.com/cache/source.msix"
    private const val MANIFESTS = "https://cdn.winget.microsoft.com/cache/"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    /** Well-known applications that run under Wine, shown before a search. */
    val FEATURED = listOf(
        "Notepad++.Notepad++", "7zip.7zip", "VideoLAN.VLC", "IrfanSkiljan.IrfanView", "Audacity.Audacity",
        "GIMP.GIMP.3", "Inkscape.Inkscape", "KeePassXCTeam.KeePassXC", "WinMerge.WinMerge", "XnSoft.XnViewMP",
        "HandBrake.HandBrake", "OBSProject.OBSStudio", "Mozilla.Firefox", "TheDocumentFoundation.LibreOffice",
        "PuTTY.PuTTY", "WinSCP.WinSCP", "SumatraPDF.SumatraPDF", "Foobar2000.Foobar2000",
    )

    private val lock = Any()
    private var database: SQLiteDatabase? = null

    /** The index, downloaded again once a day. */
    private fun index(context: Context): SQLiteDatabase = synchronized(lock) {
        val directory = File(context.cacheDir, "winget").apply { mkdirs() }
        val file = File(directory, "index.db")
        val stale = !file.isFile || System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS
        if (stale) {
            val downloaded = runCatching { download(directory) }
            if (downloaded.isSuccess) {
                database?.close()
                database = null
                File(directory, "index.db.new").renameTo(file)
            } else if (!file.isFile) {
                throw downloaded.exceptionOrNull()!!
            }
        }
        database ?: SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).also { database = it }
    }

    /**
     * source.msix is a zip archive; the index is its Public/index.db. It is read through the
     * archive's central directory: the index is stored uncompressed with a data descriptor,
     * which a streaming reader cannot size.
     */
    private fun download(directory: File) {
        val archive = File(directory, "source.msix.part")
        val target = File(directory, "index.db.new")
        try {
            val connection = URL(SOURCE).openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.inputStream.use { stream -> archive.outputStream().use { stream.copyTo(it) } }
            ZipFile(archive).use { zip ->
                val entry = zip.getEntry("Public/index.db") ?: error("The winget catalogue has no index")
                zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        } finally {
            archive.delete()
        }
    }

    data class Package(val id: String, val name: String, val version: String, val path: String) {
        val publisher get() = id.substringBefore('.')
        fun toMap() = mapOf("id" to id, "name" to name, "version" to version, "publisher" to publisher)
    }

    private val LATEST = """
        SELECT i.id, n.name, v.version, m.pathpart, MAX(m.rowid) FROM manifest m
        JOIN ids i ON i.rowid = m.id JOIN names n ON n.rowid = m.name JOIN versions v ON v.rowid = m.version
    """.trimIndent()

    fun search(context: Context, query: String, limit: Int = 80): List<Package> {
        val db = index(context)
        val text = query.trim()
        val rows = if (text.isEmpty()) {
            val marks = FEATURED.joinToString(",") { "?" }
            db.rawQuery("$LATEST WHERE i.id IN ($marks) GROUP BY m.id", FEATURED.toTypedArray())
        } else {
            val like = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            db.rawQuery("$LATEST WHERE n.name LIKE ? ESCAPE '\\' OR i.id LIKE ? ESCAPE '\\' GROUP BY m.id " +
                "ORDER BY (n.name LIKE ? ESCAPE '\\') DESC, length(n.name) LIMIT $limit",
                arrayOf(like, like, text.replace("%", "") + "%"))
        }
        val packages = rows.use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Package(cursor.getString(0), cursor.getString(1), cursor.getString(2), path(db, cursor.getLong(3))))
            }
        }
        return if (text.isEmpty()) packages.sortedBy { FEATURED.indexOf(it.id) } else packages
    }

    fun find(context: Context, id: String): Package? {
        val db = index(context)
        return db.rawQuery("$LATEST WHERE i.id = ? GROUP BY m.id", arrayOf(id)).use { cursor ->
            if (cursor.moveToNext()) Package(cursor.getString(0), cursor.getString(1), cursor.getString(2), path(db, cursor.getLong(3))) else null
        }
    }

    /** The manifest's path on the CDN, from the index's chain of path parts. */
    private fun path(db: SQLiteDatabase, part: Long): String {
        val parts = ArrayList<String>()
        var current = part
        while (current != 0L) {
            db.rawQuery("SELECT parent, pathpart FROM pathparts WHERE rowid = ?", arrayOf(current.toString())).use { cursor ->
                if (!cursor.moveToNext()) return parts.asReversed().joinToString("/")
                parts += cursor.getString(1)
                current = if (cursor.isNull(0)) 0L else cursor.getLong(0)
            }
        }
        return parts.asReversed().joinToString("/")
    }

    private fun manifest(path: String): Map<String, Any?> {
        val url = MANIFESTS + path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        val text = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        @Suppress("UNCHECKED_CAST")
        return Load(LoadSettings.builder().build()).loadFromString(text) as? Map<String, Any?> ?: emptyMap()
    }

    /** Installer types Wine can run without the Windows app deployment services. */
    private val TYPE_SCORE = mapOf("portable" to 5, "zip" to 5, "nullsoft" to 4, "inno" to 4,
        "msi" to 3, "wix" to 3, "burn" to 2, "exe" to 2)
    // ARM64 builds run natively on this device; x64 and x86 run through Proton's emulation.
    private val ARCH_SCORE = mapOf("arm64" to 40, "x64" to 30, "x86" to 20, "neutral" to 10)

    /**
     * The package's details for the Store, with the installer Dextop would use ("spec", for
     * dextop-windows) or why none fits.
     */
    fun details(context: Context, id: String): Map<String, Any?> {
        val pkg = find(context, id) ?: error("$id is not in the winget catalogue")
        val m = manifest(pkg.path)
        @Suppress("UNCHECKED_CAST")
        val installers = (m["Installers"] as? List<Map<String, Any?>>).orEmpty().map { installer ->
            // Values at the top of a manifest are defaults for every installer.
            m.filterKeys { it in listOf("InstallerType", "InstallerSwitches", "NestedInstallerType", "NestedInstallerFiles", "Scope") } + installer
        }
        fun typeOf(installer: Map<String, Any?>): String? {
            val type = (installer["InstallerType"] as? String)?.lowercase() ?: return null
            if (type != "zip") return type.takeIf { it in TYPE_SCORE }
            // An archive works when it holds the program itself.
            return "zip".takeIf { (installer["NestedInstallerType"] as? String)?.lowercase() == "portable" }
        }
        val chosen = installers.mapNotNull { installer ->
            val type = typeOf(installer) ?: return@mapNotNull null
            val arch = (installer["Architecture"] as? String)?.lowercase() ?: "neutral"
            val switches = installer["InstallerSwitches"] as? Map<*, *>
            // A plain exe can only be installed quietly with switches the manifest gives.
            if (type == "exe" && switches?.get("Silent") == null && switches?.get("SilentWithProgress") == null) return@mapNotNull null
            Triple(installer, type, (ARCH_SCORE[arch] ?: 0) + (TYPE_SCORE[type] ?: 0))
        }.maxByOrNull { it.third }
        @Suppress("UNCHECKED_CAST")
        val icons = (m["Icons"] as? List<Map<String, Any?>>).orEmpty()
        val icon = icons.firstOrNull { (it["IconFileType"] as? String).equals("png", true) } ?: icons.firstOrNull()
        val name = m["PackageName"] as? String ?: pkg.name
        val spec = chosen?.let { (installer, type, _) ->
            val hash = installer["InstallerSha256"] as? String
            require(hash != null && hash.matches(Regex("[A-Fa-f0-9]{64}"))) { "Missing or invalid installer SHA-256" }
            require((installer["InstallerUrl"] as? String)?.startsWith("https://") == true) { "Installer requires HTTPS" }
            val nested = (installer["NestedInstallerFiles"] as? List<*>)?.firstOrNull() as? Map<*, *>
            JSONObject()
                .put("id", pkg.id).put("name", name).put("version", m["PackageVersion"] as? String ?: pkg.version)
                .put("url", installer["InstallerUrl"]).put("sha256", hash).put("type", type)
                .put("switches", JSONObject((installer["InstallerSwitches"] as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap<String, Any?>()))
                .put("nested", nested?.get("RelativeFilePath") as? String ?: "")
                .put("productCode", installer["ProductCode"] as? String ?: m["ProductCode"] as? String ?: "")
                .put("iconUrl", icon?.get("IconUrl") as? String ?: "")
                .put("iconSha256", icon?.get("IconSha256") as? String ?: "")
                .toString()
        }
        return mapOf(
            "id" to pkg.id,
            "name" to name,
            "publisher" to (m["Publisher"] as? String ?: pkg.publisher),
            "version" to (m["PackageVersion"] as? String ?: pkg.version),
            "summary" to (m["ShortDescription"] as? String ?: ""),
            "description" to (m["Description"] as? String ?: ""),
            "license" to (m["License"] as? String ?: ""),
            "homepage" to (m["PackageUrl"] as? String ?: m["PublisherUrl"] as? String ?: ""),
            "iconUrl" to (icon?.get("IconUrl") as? String ?: ""),
            "installer" to chosen?.let { "${(it.first["Architecture"] as? String) ?: "neutral"} · ${it.second}" },
            "spec" to spec,
        )
    }
}
