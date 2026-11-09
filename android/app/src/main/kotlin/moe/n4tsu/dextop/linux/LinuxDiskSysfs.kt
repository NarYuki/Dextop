package moe.n4tsu.dextop.linux

import java.io.File

/**
 * The sysfs and /proc/partitions entries of USB storage served as /dev/sdX (linux_android_disk.c,
 * LinuxUsbDisk). libblkid (wipefs, mkfs, blkid), lsblk, fdisk -l and parted find disks, tell a
 * whole disk from a partition and read sector sizes there: each disk gets /sys/block/sdX with its
 * queue limits and partitions, /sys/dev/block/MAJ:MIN links, and a line in /proc/partitions.
 * The directories are bound into every session ([binds]); their contents follow the "disk" lines
 * of /tmp/dextop-devices.conf ([update]).
 */
internal object LinuxDiskSysfs {
    private fun base(id: String) = File(LinuxPaths.container(id), "runtime/block")
    private fun blocks(id: String) = File(base(id), "block")
    private fun numbers(id: String) = File(base(id), "dev")
    private fun partitions(id: String) = File(base(id), "partitions")

    /** proot binds for container [id]; the entries exist from the first session on. */
    fun binds(id: String): List<String> {
        val blocks = blocks(id).apply { mkdirs() }
        val numbers = numbers(id).apply { mkdirs() }
        val partitions = partitions(id).apply { if (!isFile) writeText(HEADER) }
        return listOf("${blocks.absolutePath}:/sys/block", "${numbers.absolutePath}:/sys/dev/block",
            "${partitions.absolutePath}:/proc/partitions")
    }

    private const val HEADER = "major minor  #blocks  name\n\n"

    private class Part(val number: Int, val start: Long, val length: Long)
    private class Disk(val node: String, val size: Long, val sector: Int, val readOnly: Boolean, val parts: List<Part>, val model: String)

    /** Rewrites the entries of container [id] from the manifest's "disk" lines. */
    @Synchronized
    fun update(id: String, manifest: String) {
        val disks = manifest.lineSequence().filter { it.startsWith("disk ") }.mapNotNull { line ->
            val p = line.split(' ')
            if (p.size != 8 || !p[1].matches(Regex("sd[a-z]"))) return@mapNotNull null
            runCatching {
                val parts = if (p[6] == "-") emptyList() else p[6].split(',').map {
                    val (n, start, length) = it.split(':'); Part(n.toInt(), start.toLong(), length.toLong())
                }
                Disk(p[1], p[3].toLong(), p[4].toInt(), p[5] == "1", parts,
                    String(android.util.Base64.decode(p[7], android.util.Base64.NO_WRAP)))
            }.getOrNull()
        }.toList()
        val signature = disks.joinToString("\n") { d -> "${d.node} ${d.size} ${d.sector} ${d.readOnly} ${d.model} " +
            d.parts.joinToString(",") { "${it.number}:${it.start}:${it.length}" } }
        val stamp = File(base(id), "signature")
        if (stamp.isFile && stamp.readText() == signature) return
        val blocks = blocks(id).apply { mkdirs() }
        val numbers = numbers(id).apply { mkdirs() }
        blocks.listFiles().orEmpty().forEach { it.deleteRecursively() }
        numbers.listFiles().orEmpty().forEach { it.delete() }
        val table = StringBuilder(HEADER)
        disks.forEach { disk -> write(blocks, numbers, table, disk) }
        partitions(id).writeText(table.toString())
        stamp.writeText(signature)
    }

    private fun write(blocks: File, numbers: File, table: StringBuilder, disk: Disk) {
        val index = disk.node[2] - 'a'
        val major = LinuxUsbDisk.MAJOR
        val minor = index * 16
        val root = File(blocks, disk.node).apply { mkdirs() }
        val sectors512 = disk.size / 512
        fun put(dir: File, values: Map<String, String>) = values.forEach { (name, value) -> File(dir, name).writeText("$value\n") }
        put(root, mapOf(
            "dev" to "$major:$minor", "size" to "$sectors512", "removable" to "1", "ro" to if (disk.readOnly) "1" else "0",
            "range" to "16", "ext_range" to "256", "capability" to "51", "alignment_offset" to "0", "discard_alignment" to "0",
            "uevent" to "MAJOR=$major\nMINOR=$minor\nDEVNAME=${disk.node}\nDEVTYPE=disk",
        ))
        val queue = File(root, "queue").apply { mkdirs() }
        put(queue, mapOf(
            "logical_block_size" to "${disk.sector}", "physical_block_size" to "${disk.sector}",
            "hw_sector_size" to "${disk.sector}", "minimum_io_size" to "${disk.sector}", "optimal_io_size" to "0",
            "rotational" to "0", "max_sectors_kb" to "120", "max_hw_sectors_kb" to "120", "discard_granularity" to "0",
            "discard_max_bytes" to "0", "nr_requests" to "64", "read_ahead_kb" to "128", "scheduler" to "[none]", "zoned" to "none",
        ))
        val device = File(root, "device").apply { mkdirs() }
        val vendor = disk.model.substringBefore(' ')
        put(device, mapOf("vendor" to vendor, "model" to disk.model.removePrefix(vendor).trim().ifEmpty { disk.model },
            "rev" to "", "type" to "0", "state" to "running"))
        link("/sys/block/${disk.node}", File(numbers, "$major:$minor"))
        table.append("%4d  %7d %10d %s\n".format(major, minor, sectors512 / 2, disk.node))
        disk.parts.filter { it.number in 1..15 }.forEach { part ->
            val name = "${disk.node}${part.number}"
            val dir = File(root, name).apply { mkdirs() }
            put(dir, mapOf(
                "dev" to "$major:${minor + part.number}", "partition" to "${part.number}", "start" to "${part.start / 512}",
                "size" to "${part.length / 512}", "ro" to if (disk.readOnly) "1" else "0", "alignment_offset" to "0",
                "discard_alignment" to "0", "uevent" to "MAJOR=$major\nMINOR=${minor + part.number}\nDEVNAME=$name\nDEVTYPE=partition\nPARTN=${part.number}",
            ))
            link("/sys/block/${disk.node}/$name", File(numbers, "$major:${minor + part.number}"))
            table.append("%4d  %7d %10d %s\n".format(major, minor + part.number, part.length / 1024, name))
        }
    }

    private fun link(target: String, file: File) {
        if (java.nio.file.Files.isSymbolicLink(file.toPath()) || file.exists()) file.delete()
        android.system.Os.symlink(target, file.path)
    }
}
