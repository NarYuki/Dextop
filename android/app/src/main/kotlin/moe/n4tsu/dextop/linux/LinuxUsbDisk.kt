package moe.n4tsu.dextop.linux

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.net.LocalSocket
import android.util.Log
import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * USB storage (memory sticks, card readers, USB disks) as Linux block devices.
 *
 * Android keeps USB storage for itself (vold mounts what it understands), and no app may open a
 * block device. Dextop takes the stick's mass-storage interface from Android's kernel driver
 * (claimInterface with force) and speaks USB Mass Storage Bulk-Only Transport with SCSI commands
 * itself, as Android apps that read sticks without root do. Linux then sees the whole disk as
 * /dev/sdX and each partition as /dev/sdXN (linux_android_disk.c), so fdisk, parted, mkfs, dd and
 * the like work on it: partitioning and formatting included. Nothing can be mounted (no kernel).
 *
 * The stick stays Dextop's while it is enabled for Linux; Android gets it back when it is
 * turned off for Linux or plugged in again.
 */
internal object LinuxUsbDisk {
    private const val TAG = "DextopLinuxDisk"

    /** Linux's major for SCSI disks; each disk has 16 minors (the disk and 15 partitions). */
    const val MAJOR = 8

    /** Sectors per SCSI command: Linux's usb-storage default (max_sectors 240), which every stick takes. */
    private const val MAX_SECTORS = 240

    /** The interface a device stores data on: mass storage, SCSI transparent, Bulk-Only Transport. */
    fun storageInterface(device: UsbDevice): UsbInterface? =
        (0 until device.interfaceCount).map(device::getInterface).firstOrNull { usb ->
            usb.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE && usb.interfaceSubclass == 6 && usb.interfaceProtocol == 0x50 &&
                (0 until usb.endpointCount).map(usb::getEndpoint).count { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK } >= 2
        }

    class Partition(val number: Int, val start: Long, val length: Long)

    /** A stick Dextop holds: its BOT pipes, geometry, and one SCSI command at a time. */
    class Disk(
        val name: String,
        private val connection: UsbDeviceConnection,
        private val usb: UsbInterface,
        private val bulkIn: UsbEndpoint,
        private val bulkOut: UsbEndpoint,
    ) {
        private val lock = Object()
        private var tag = 1
        var sectorSize = 512; private set
        var sectors = 0L; private set
        var readOnly = false; private set
        var model = ""; private set
        @Volatile private var partitionCache: List<Partition>? = null
        @Volatile var closed = false; private set

        val size get() = sectors * sectorSize

        fun close() {
            synchronized(lock) {
                if (closed) return
                closed = true
                runCatching { connection.releaseInterface(usb) }
                // Closing the connection gives the interface back: the kernel's usb-storage driver
                // takes the stick again and vold mounts what it can read.
                runCatching { connection.close() }
            }
        }

        /** Asks the stick what it is, waits for its medium and reads its size. */
        fun identify() = synchronized(lock) {
            val inquiry = ByteArray(36)
            if (command(byteArrayOf(0x12, 0, 0, 0, 36, 0), inquiry, 0, 36, true) == 0) {
                val vendor = String(inquiry, 8, 8, Charsets.US_ASCII).trim()
                val product = String(inquiry, 16, 16, Charsets.US_ASCII).trim()
                model = listOf(vendor, product).filter { it.isNotEmpty() }.joinToString(" ")
            }
            // A stick reports "unit attention" or "becoming ready" right after it is taken.
            var ready = false
            for (attempt in 0 until 20) {
                if (command(ByteArray(6), null, 0, 0, false) == 0) { ready = true; break }
                requestSense()
                Thread.sleep(150)
            }
            if (!ready) throw IOException("The USB storage is not ready (no medium?)")
            val capacity = ByteArray(8)
            check(command(byteArrayOf(0x25, 0, 0, 0, 0, 0, 0, 0, 0, 0), capacity, 0, 8, true) == 0) { "READ CAPACITY failed" }
            var last = ByteBuffer.wrap(capacity).int.toLong() and 0xffffffffL
            sectorSize = ByteBuffer.wrap(capacity, 4, 4).int
            if (last == 0xffffffffL) {
                val long = ByteArray(32)
                val cdb = ByteArray(16).apply { this[0] = 0x9e.toByte(); this[1] = 0x10; this[13] = 32 }
                check(command(cdb, long, 0, 32, true) == 0) { "READ CAPACITY(16) failed" }
                last = ByteBuffer.wrap(long).long
                sectorSize = ByteBuffer.wrap(long, 8, 4).int
            }
            if (sectorSize !in setOf(512, 1024, 2048, 4096)) sectorSize = 512
            sectors = last + 1
            // MODE SENSE(6): the write-protect switch of SD readers and some sticks.
            val mode = ByteArray(192)
            if (command(byteArrayOf(0x1a, 0, 0x3f, 0, 192.toByte(), 0), mode, 0, 192, true) == 0)
                readOnly = mode[2].toInt() and 0x80 != 0
            Log.i(TAG, "$name: $model, $sectors sectors of $sectorSize bytes, readOnly=$readOnly")
        }

        /** Reads [length] bytes at byte [offset] (any alignment) into [out] at [at]. */
        fun read(offset: Long, out: ByteArray, at: Int, length: Int) = synchronized(lock) {
            var done = 0
            while (done < length) {
                val position = offset + done
                val lba = position / sectorSize
                val skip = (position % sectorSize).toInt()
                val count = minOf(MAX_SECTORS.toLong(), (skip + (length - done) + sectorSize - 1L) / sectorSize).toInt()
                val bytes = count * sectorSize
                val take = minOf(bytes - skip, length - done)
                if (skip == 0 && take == bytes) {
                    transfer(false, lba, count, out, at + done)
                } else {
                    val buffer = ByteArray(bytes)
                    transfer(false, lba, count, buffer, 0)
                    System.arraycopy(buffer, skip, out, at + done, take)
                }
                done += take
            }
        }

        /** Writes [length] bytes from [data] at byte [offset]; partial sectors are read first. */
        fun write(offset: Long, data: ByteArray, at: Int, length: Int) = synchronized(lock) {
            if (readOnly) throw ErrnoException(30 /* EROFS */)
            partitionCache = null
            var done = 0
            while (done < length) {
                val position = offset + done
                val lba = position / sectorSize
                val skip = (position % sectorSize).toInt()
                val count = minOf(MAX_SECTORS.toLong(), (skip + (length - done) + sectorSize - 1L) / sectorSize).toInt()
                val bytes = count * sectorSize
                val take = minOf(bytes - skip, length - done)
                if (skip == 0 && take == bytes) {
                    transfer(true, lba, count, data, at + done)
                } else {
                    val buffer = ByteArray(bytes)
                    // Only the first and last sectors keep bytes this write does not cover.
                    transfer(false, lba, 1, buffer, 0)
                    if (count > 1) transfer(false, lba + count - 1, 1, buffer, bytes - sectorSize)
                    System.arraycopy(data, at + done, buffer, skip, take)
                    transfer(true, lba, count, buffer, 0)
                }
                done += take
            }
        }

        fun flush() = synchronized(lock) {
            command(byteArrayOf(0x35, 0, 0, 0, 0, 0, 0, 0, 0, 0), null, 0, 0, false)
            Unit
        }

        /** The partitions as Linux numbers them: MBR primaries 1-4 and logicals from 5, or GPT slots. */
        fun partitions(): List<Partition> = partitionCache ?: runCatching { readPartitions() }
            .onFailure { Log.w(TAG, "$name: cannot read the partition table", it) }
            .getOrDefault(emptyList()).also { partitionCache = it }

        fun forgetPartitions() { partitionCache = null }

        private fun readPartitions(): List<Partition> {
            val mbr = ByteArray(512).also { read(0, it, 0, 512) }
            if (mbr[510] != 0x55.toByte() || mbr[511] != 0xaa.toByte()) return emptyList()
            fun le32(buffer: ByteArray, at: Int) = ByteBuffer.wrap(buffer, at, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
            val types = (0 until 4).map { mbr[446 + it * 16 + 4].toInt() and 0xff }
            if (0xee in types) {
                val header = ByteArray(sectorSize).also { read(sectorSize.toLong(), it, 0, sectorSize) }
                if (String(header, 0, 8, Charsets.US_ASCII) == "EFI PART") {
                    val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                    val entriesLba = b.getLong(72)
                    val count = b.getInt(80).coerceIn(0, 256)
                    val entrySize = b.getInt(84).coerceIn(128, 1024)
                    val table = ByteArray(count * entrySize).also { read(entriesLba * sectorSize, it, 0, it.size) }
                    return (0 until count).mapNotNull { index ->
                        val e = ByteBuffer.wrap(table, index * entrySize, entrySize).order(ByteOrder.LITTLE_ENDIAN)
                        if ((0 until 16).all { table[index * entrySize + it] == 0.toByte() }) return@mapNotNull null
                        val first = e.getLong(index * entrySize + 32)
                        val last = e.getLong(index * entrySize + 40)
                        if (index + 1 > 15 || last < first) null
                        else Partition(index + 1, first * sectorSize, (last - first + 1) * sectorSize)
                    }
                }
            }
            val result = ArrayList<Partition>()
            var extended = -1L
            for (i in 0 until 4) {
                val at = 446 + i * 16
                val type = mbr[at + 4].toInt() and 0xff
                val start = le32(mbr, at + 8)
                val length = le32(mbr, at + 12)
                if (type == 0 || length == 0L) continue
                if (type == 0x05 || type == 0x0f || type == 0x85) {
                    extended = start
                    result += Partition(i + 1, start * sectorSize, 1024L.coerceAtMost(length * sectorSize))
                } else result += Partition(i + 1, start * sectorSize, length * sectorSize)
            }
            // Logical partitions: a chain of EBRs, each relative to the extended partition.
            var ebr = extended
            var number = 5
            val seen = HashSet<Long>()
            while (ebr > 0 && number <= 15 && seen.add(ebr)) {
                val sector = ByteArray(512).also { read(ebr * sectorSize, it, 0, 512) }
                if (sector[510] != 0x55.toByte() || sector[511] != 0xaa.toByte()) break
                val start = le32(sector, 446 + 8)
                val length = le32(sector, 446 + 12)
                if (length > 0) result += Partition(number++, (ebr + start) * sectorSize, length * sectorSize)
                val next = le32(sector, 462 + 8)
                ebr = if (next == 0L) 0 else extended + next
            }
            return result.sortedBy { it.number }
        }

        private fun transfer(write: Boolean, lba: Long, count: Int, buffer: ByteArray, at: Int) {
            val cdb = if (lba + count > 0xffffffffL) ByteBuffer.allocate(16).apply {
                put((if (write) 0x8a else 0x88).toByte()); put(0); putLong(lba); putInt(count); put(0); put(0)
            }.array() else ByteBuffer.allocate(10).apply {
                put((if (write) 0x2a else 0x28).toByte()); put(0); putInt(lba.toInt()); put(0); putShort(count.toShort()); put(0)
            }.array()
            var status = -1
            for (attempt in 0 until 3) {
                status = command(cdb, buffer, at, count * sectorSize, !write)
                if (status == 0) return
                val sense = requestSense()
                if (sense == 0x07) throw ErrnoException(30 /* EROFS */)
                // Not ready / unit attention: the stick settles, then the command is repeated.
                if (sense != 0x02 && sense != 0x06) break
            }
            throw ErrnoException(5 /* EIO */)
        }

        /** REQUEST SENSE: the sense key of the last failed command, -1 when unknown. */
        private fun requestSense(): Int {
            val sense = ByteArray(18)
            return if (command(byteArrayOf(0x03, 0, 0, 0, 18, 0), sense, 0, 18, true) == 0) sense[2].toInt() and 0x0f else -1
        }

        /** One BOT command: CBW, data, CSW. Returns the CSW status (0 passed, 1 failed). */
        private fun command(cdb: ByteArray, buffer: ByteArray?, at: Int, length: Int, dataIn: Boolean, timeout: Int = 20_000): Int {
            if (closed) throw ErrnoException(19 /* ENODEV */)
            val cbw = ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0x43425355); putInt(tag); putInt(length)
                put((if (dataIn) 0x80 else 0).toByte()); put(0); put(cdb.size.toByte()); put(cdb.copyOf(16))
            }.array()
            val sent = tag++
            if (connection.bulkTransfer(bulkOut, cbw, 31, 5000) != 31) { recover(); throw ErrnoException(5) }
            if (length > 0 && buffer != null) {
                val endpoint = if (dataIn) bulkIn else bulkOut
                var done = 0
                while (done < length) {
                    val chunk = minOf(length - done, 128 * 1024)
                    val n = connection.bulkTransfer(endpoint, buffer, at + done, chunk, timeout)
                    if (n < 0) { clearHalt(endpoint); break }
                    done += n
                    if (n < chunk) break // a short packet ends the data stage
                }
            }
            val csw = ByteArray(13)
            var n = connection.bulkTransfer(bulkIn, csw, 13, timeout)
            if (n < 0) { clearHalt(bulkIn); n = connection.bulkTransfer(bulkIn, csw, 13, timeout) }
            val b = ByteBuffer.wrap(csw).order(ByteOrder.LITTLE_ENDIAN)
            if (n != 13 || b.getInt(0) != 0x53425355 || b.getInt(4) != sent) { recover(); throw ErrnoException(5) }
            val status = csw[12].toInt()
            if (status == 2) { recover(); return 1 }
            return status
        }

        private fun clearHalt(endpoint: UsbEndpoint) {
            connection.controlTransfer(0x02, 0x01 /* CLEAR_FEATURE */, 0 /* ENDPOINT_HALT */, endpoint.address, null, 0, 2000)
        }

        /** Bulk-Only Mass Storage Reset, then both pipes are cleared (BOT 5.3.4). */
        private fun recover() {
            connection.controlTransfer(0x21, 0xff, 0, usb.id, null, 0, 2000)
            clearHalt(bulkIn)
            clearHalt(bulkOut)
        }
    }

    class ErrnoException(val errno: Int) : IOException("errno $errno")


    private val disks = java.util.concurrent.ConcurrentHashMap<String, Disk>()

    /** Linux descriptors open on each stick (by Android device name). */
    private val openCounts = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()

    /**
     * Sticks lent back to Android so that its kernel mounts them for Linux ("mount /dev/sdX1 ..."):
     * each partition's file-system UUID, which names the volume (/storage/<UUID>).
     */
    private val lent = java.util.concurrent.ConcurrentHashMap<String, Map<Int, String>>()

    /** When each stick was lent: giving it back makes some phones (Galaxy) enumerate it anew. */
    private val lentAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Lent sticks are known by vendor, product and serial ([LinuxUsb.key]), which survive re-enumeration. */
    fun isLent(device: UsbDevice) = lent.containsKey(LinuxUsb.key(device))

    /** The stick Dextop holds for [device], taken from Android the first time it is asked for. */
    @Synchronized
    fun disk(context: Context, device: UsbDevice): Disk? {
        if (isLent(device)) return null
        disks[device.deviceName]?.takeIf { !it.closed }?.let { return it }
        val usb = storageInterface(device) ?: return null
        val manager = context.getSystemService(UsbManager::class.java) ?: return null
        val connection = manager.openDevice(device) ?: return null
        val endpoints = (0 until usb.endpointCount).map(usb::getEndpoint).filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
        val bulkIn = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_IN }
        val bulkOut = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
        // Force: Android's usb-storage driver lets go (vold sees the disk leave and unmounts it).
        if (bulkIn == null || bulkOut == null || !connection.claimInterface(usb, true)) {
            connection.close()
            Log.w(TAG, "${device.deviceName}: cannot take the storage interface")
            return null
        }
        val disk = Disk(device.deviceName, connection, usb, bulkIn, bulkOut)
        return try {
            disk.identify()
            disks[device.deviceName] = disk
            disk
        } catch (error: Exception) {
            Log.w(TAG, "${device.deviceName}: cannot use the USB storage", error)
            disk.close()
            null
        }
    }

    /** Gives a stick back (turned off for Linux, or unplugged). */
    fun release(name: String) {
        disks.remove(name)?.close()
    }

    /**
     * Unplugged ([detached]) or turned off for Linux: the stick is not lent any more, except
     * right after it was lent, when the phone enumerating it anew looks like an unplug.
     */
    fun forget(device: UsbDevice, detached: Boolean) {
        val key = LinuxUsb.key(device)
        val recent = android.os.SystemClock.elapsedRealtime() - (lentAt[key] ?: 0L) < 60_000
        if (!detached || !recent) { lent.remove(key); lentAt.remove(key) }
        release(device.deviceName)
    }

    /**
     * The file-system UUID Android names a volume by (vold reads it with blkid): FAT and exFAT
     * "XXXX-XXXX", NTFS 16 hex digits. "ext" for ext2/3/4, which Android does not mount; null
     * for anything else.
     */
    private fun fileSystemId(disk: Disk, partition: Partition): String? {
        val boot = ByteArray(2048)
        disk.read(partition.start, boot, 0, boot.size)
        val b = ByteBuffer.wrap(boot).order(ByteOrder.LITTLE_ENDIAN)
        fun text(at: Int, length: Int) = String(boot, at, length, Charsets.US_ASCII)
        fun serial(at: Int) = (b.getInt(at).toLong() and 0xffffffffL).let { "%04X-%04X".format(it shr 16, it and 0xffff) }
        return when {
            text(3, 8) == "EXFAT   " -> serial(100)
            text(82, 8) == "FAT32   " -> serial(67)
            text(54, 4) == "FAT1" -> serial(39)
            text(3, 8) == "NTFS    " -> "%016X".format(b.getLong(0x48))
            (b.getShort(1080).toInt() and 0xffff) == 0xef53 -> "ext"
            else -> null
        }
    }

    class MountException(val errno: Int, message: String) : Exception(message)

    /**
     * Mounts partition [partition] (0: an unpartitioned stick) of [device] the only way Linux in
     * Dextop can: the stick goes back to Android, whose kernel mounts what it reads (FAT, exFAT,
     * NTFS) at /storage/<UUID>, the returned path. Linux reaches it below /media/storage.
     */
    fun mount(context: Context, device: UsbDevice, partition: Int): String {
        val name = LinuxUsb.key(device)
        val ids = lent[name] ?: synchronized(this) {
            lent[name] ?: run {
                if ((openCounts[device.deviceName]?.get() ?: 0) > 0)
                    throw MountException(16, "The disk is open in Linux; close the programs using it first")
                val disk = disk(context, device) ?: throw MountException(6, "The USB storage cannot be read")
                val parts = disk.partitions().ifEmpty { listOf(Partition(0, 0, disk.size)) }
                val found = parts.associate { it.number to (runCatching { fileSystemId(disk, it) }.getOrNull() ?: "") }
                // Closing the connection lets Android's usb-storage driver take the stick again.
                lent[name] = found
                lentAt[name] = android.os.SystemClock.elapsedRealtime()
                release(device.deviceName)
                found
            }
        }
        val uuid = ids[partition].orEmpty()
        if (uuid == "ext") throw MountException(19, "Android cannot mount ext2/3/4. Format the partition as FAT32, exFAT or NTFS to mount it; as /dev/sdX it stays usable as it is")
        if (uuid.isEmpty()) throw MountException(19, "This partition has no file system Android can mount (FAT32, exFAT or NTFS)")
        val storage = context.getSystemService(android.os.storage.StorageManager::class.java)
        val deadline = android.os.SystemClock.elapsedRealtime() + 60_000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            storage.storageVolumes.firstOrNull { it.uuid.equals(uuid, ignoreCase = true) && it.state == android.os.Environment.MEDIA_MOUNTED }
                ?.let { return "/storage/${it.uuid}" }
            Thread.sleep(500)
        }
        val locked = context.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
        throw MountException(110, if (locked) "Android mounts USB storage only while the screen is unlocked. Unlock it and try again"
            else "Android did not mount the volume $uuid")
    }

    /**
     * Unmounts the stick holding volume [uuid] and takes it back as /dev/sdX: Android unmounts
     * it where Dextop has privilege, otherwise everything is synced before the stick is taken.
     */
    fun unmount(context: Context, uuid: String) {
        val name = lent.entries.firstOrNull { (_, ids) -> ids.values.any { it.equals(uuid, ignoreCase = true) } }?.key
            ?: throw MountException(22, "$uuid is not mounted from Linux")
        val ids = lent[name].orEmpty().values.filter { it.length > 3 }
        val privileged = moe.n4tsu.dextop.PrivilegedAccess(TAG)
        if (!LinuxExecutionMode.android(context) && privileged.isAvailable()) {
            // "public:8,81 mounted 92E1-55C5": every volume of the stick is unmounted.
            privileged.execute("sm", "list-volumes", "public").output.lineSequence()
                .map { it.trim().split(' ').filter(String::isNotEmpty) }
                .filter { it.size >= 3 && it[1] == "mounted" && ids.any { id -> id.equals(it[2], ignoreCase = true) } }
                .forEach { privileged.execute("sm", "unmount", it[0]) }
        } else {
            runCatching { Runtime.getRuntime().exec(arrayOf("/system/bin/sync")).waitFor() }
        }
        lent.remove(name)
        lentAt.remove(name)
    }

    private const val OP_PREAD = 'p'.code
    private const val OP_PWRITE = 'w'.code
    private const val OP_READ = 'r'.code
    private const val OP_WRITE = 'W'.code
    private const val OP_SEEK = 's'.code
    private const val OP_SYNC = 'f'.code
    private const val OP_INFO = 'i'.code
    private const val OP_RESCAN = 'P'.code
    private const val MAX_REQUEST = 4 shl 20

    /**
     * One opened /dev/sdX or /dev/sdXN ("B <device> <partition>", 0 for the whole disk), served
     * until Linux closes it. Requests are an operation byte and two little-endian longs, write
     * data after them; every answer starts with a long (a byte count, a position or -errno).
     * The file position lives here, so it is shared by duplicated and inherited descriptors.
     */
    fun serve(context: Context, socket: LocalSocket, input: DataInputStream, device: UsbDevice, partition: Int, index: Int) {
        val output = java.io.BufferedOutputStream(socket.outputStream, 256 * 1024)
        // Mounted through Android: busy until Linux unmounts it.
        if (isLent(device)) { output.write(2); output.flush(); return }
        val disk = disk(context, device)
        val window = when {
            disk == null -> null
            partition == 0 -> Partition(0, 0, disk.size)
            else -> disk.partitions().firstOrNull { it.number == partition }
        }
        if (disk == null || window == null) { output.write(1); output.flush(); return }
        output.write(0)
        val answer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        fun reply(value: Long) { answer.clear(); answer.putLong(value); output.write(answer.array()) }
        fun info() {
            reply(0); reply(window.length); reply(disk.sectorSize.toLong()); reply(if (disk.readOnly) 1 else 0)
            reply(window.start / 512); reply((index * 16 + partition).toLong())
        }
        info()
        output.flush()
        val open = openCounts.getOrPut(device.deviceName) { java.util.concurrent.atomic.AtomicInteger() }
        open.incrementAndGet()
        try {
            exchange(input, output, disk, window, device, ::info, ::reply)
        } finally {
            open.decrementAndGet()
            // Whatever was written reaches the stick before Linux sees the descriptor closed.
            runCatching { disk.flush() }
        }
    }

    private fun exchange(input: DataInputStream, output: java.io.OutputStream, disk: Disk, window: Partition,
                         device: UsbDevice, info: () -> Unit, reply: (Long) -> Unit) {
        val header = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN)
        var position = 0L
        var buffer = ByteArray(0)
        val raw = ByteArray(17)
        while (true) {
            try { input.readFully(raw) } catch (_: java.io.EOFException) { break }
            header.clear(); header.put(raw); header.flip()
            val op = header.get().toInt() and 0xff
            val a = header.long
            val b = header.long
            when (op) {
                OP_PREAD, OP_READ, OP_PWRITE, OP_WRITE -> {
                    val writing = op == OP_PWRITE || op == OP_WRITE
                    val start = if (op == OP_READ || op == OP_WRITE) position else a
                    val wanted = b.coerceIn(0, MAX_REQUEST.toLong()).toInt()
                    if (buffer.size < wanted) buffer = ByteArray(wanted)
                    if (writing) input.readFully(buffer, 0, wanted)
                    val count = when {
                        start < 0 -> -22L
                        writing && start >= window.length -> if (wanted == 0) 0L else -28L /* ENOSPC */
                        else -> minOf(wanted.toLong(), (window.length - start).coerceAtLeast(0)).toLong()
                    }
                    val result = if (count <= 0) count else try {
                        if (writing) disk.write(window.start + start, buffer, 0, count.toInt())
                        else disk.read(window.start + start, buffer, 0, count.toInt())
                        count
                    } catch (error: ErrnoException) { -error.errno.toLong() }
                    catch (error: Exception) { Log.w(TAG, "${device.deviceName}: I/O failed", error); -5L }
                    if (result > 0 && (op == OP_READ || op == OP_WRITE)) position = start + result
                    reply(result)
                    if (!writing && result > 0) output.write(buffer, 0, result.toInt())
                }
                OP_SEEK -> {
                    val base = when (b.toInt()) { 0 -> 0L; 1 -> position; 2 -> window.length; else -> -1L }
                    val target = base + a
                    if (base < 0 || target < 0) reply(-22) else { position = target; reply(target) }
                }
                OP_SYNC -> reply(runCatching { disk.flush(); 0L }.getOrDefault(-5L))
                OP_RESCAN -> { disk.forgetPartitions(); reply(0) }
                OP_INFO -> info()
                else -> reply(-22)
            }
            output.flush()
        }
    }
}
