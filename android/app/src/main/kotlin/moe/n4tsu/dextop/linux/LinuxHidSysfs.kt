package moe.n4tsu.dextop.linux

import java.io.File

/**
 * The sysfs entries of security keys relayed as /dev/hidrawN (linux_android_usb.c). Browsers
 * and libfido2 find hidraw devices through udev, which reads sysfs: each key gets a USB device,
 * its interface, the HID device (with HID_ID, HID_NAME and its report_descriptor) and the hidraw
 * node, under /sys/devices/dextop, listed in /sys/class/hidraw. Both directories are bound into
 * every session ([binds]); their contents follow /tmp/dextop-devices.conf ([update]).
 */
internal object LinuxHidSysfs {
    /** The major of relayed hidraw nodes (DEXTOP_HIDRAW_MAJOR in linux_android_usb.c). */
    const val MAJOR = 241
    private const val GUEST_DEVICES = "/sys/devices/dextop"

    private fun base(id: String) = File(LinuxPaths.container(id), "runtime/hid")
    private fun devices(id: String) = File(base(id), "devices")
    private fun classDir(id: String) = File(base(id), "class")

    /** proot binds for container [id]; the directories exist from the first session on. */
    fun binds(id: String): List<String> {
        val devices = devices(id).apply { mkdirs() }
        val classes = classDir(id).apply { mkdirs() }
        return listOf("${devices.absolutePath}:$GUEST_DEVICES", "${classes.absolutePath}:/sys/class/hidraw")
    }

    private data class Key(val node: String, val vendor: Int, val product: Int, val descriptor: ByteArray, val name: String)

    /** Rewrites the entries of container [id] from the manifest's "hid" lines. */
    @Synchronized
    fun update(id: String, manifest: String) {
        val keys = manifest.lineSequence().filter { it.startsWith("hid ") }.mapNotNull { line ->
            val parts = line.split(' ')
            if (parts.size != 7) return@mapNotNull null
            runCatching {
                Key(parts[1], parts[3].toInt(16), parts[4].toInt(16),
                    parts[5].chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
                    String(android.util.Base64.decode(parts[6], android.util.Base64.NO_WRAP)))
            }.getOrNull()?.takeIf { it.node.matches(Regex("hidraw[0-9]+")) }
        }.toList()
        val devices = devices(id)
        val classes = classDir(id)
        if (!devices.isDirectory && !devices.mkdirs()) return
        if (!classes.isDirectory && !classes.mkdirs()) return
        // Unchanged keys keep their entries; the others are rebuilt.
        val signature = keys.joinToString("\n") { "${it.node} ${it.vendor} ${it.product} ${it.name} ${it.descriptor.contentHashCode()}" }
        val stamp = File(base(id), "signature")
        if (stamp.isFile && stamp.readText() == signature) return
        devices.listFiles().orEmpty().forEach { it.deleteRecursively() }
        classes.listFiles().orEmpty().forEach { it.delete() }
        keys.forEachIndexed { index, key -> write(devices, classes, index, key) }
        stamp.writeText(signature)
    }

    private fun write(devices: File, classes: File, index: Int, key: Key) {
        val minor = key.node.removePrefix("hidraw").toInt()
        val port = "1-${index + 1}"
        val busDevice = File(devices, "usb1/$port")
        val usbInterface = File(busDevice, "$port:1.0")
        val hidName = "0003:%04X:%04X.%04X".format(key.vendor, key.product, index + 1)
        val hid = File(usbInterface, hidName)
        val hidraw = File(hid, "hidraw/${key.node}")
        check(hidraw.mkdirs()) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiCannotCreate0"), hidraw.path) }
        val guestBusDevice = "$GUEST_DEVICES/usb1/$port"
        val guestHidraw = "$guestBusDevice/$port:1.0/$hidName/hidraw/${key.node}"
        val devnum = index + 2
        File(devices, "usb1/uevent").writeText("DEVTYPE=usb_device\nDRIVER=usb\nBUSNUM=001\nDEVNUM=001\n")
        link("/sys/bus/usb", File(devices, "usb1/subsystem"))
        File(busDevice, "uevent").writeText(
            "MAJOR=189\nMINOR=$devnum\nDEVNAME=bus/usb/001/%03d\nDEVTYPE=usb_device\nDRIVER=usb\n".format(devnum) +
                "PRODUCT=%x/%x/0\nTYPE=0/0/0\nBUSNUM=001\nDEVNUM=%03d\n".format(key.vendor, key.product, devnum))
        mapOf(
            "idVendor" to "%04x".format(key.vendor), "idProduct" to "%04x".format(key.product),
            "manufacturer" to key.name.substringBefore(' '), "product" to key.name,
            "busnum" to "1", "devnum" to "$devnum", "serial" to "",
        ).forEach { (name, value) -> File(busDevice, name).writeText("$value\n") }
        link("/sys/bus/usb", File(busDevice, "subsystem"))
        File(usbInterface, "uevent").writeText(
            "DEVTYPE=usb_interface\nDRIVER=usbhid\nPRODUCT=%x/%x/0\nTYPE=0/0/0\nINTERFACE=3/0/0\n".format(key.vendor, key.product))
        mapOf("bInterfaceNumber" to "00", "bInterfaceClass" to "03", "bInterfaceSubClass" to "00", "bInterfaceProtocol" to "00")
            .forEach { (name, value) -> File(usbInterface, name).writeText("$value\n") }
        link("/sys/bus/usb", File(usbInterface, "subsystem"))
        File(hid, "uevent").writeText(
            "DRIVER=hid-generic\nHID_ID=0003:%08X:%08X\nHID_NAME=%s\nHID_PHYS=usb-dextop-$port/input0\nHID_UNIQ=\n".format(key.vendor, key.product, key.name) +
                "MODALIAS=hid:b0003g0001v%08Xp%08X\n".format(key.vendor, key.product))
        File(hid, "report_descriptor").writeBytes(key.descriptor)
        link("/sys/bus/hid", File(hid, "subsystem"))
        File(hidraw, "uevent").writeText("MAJOR=$MAJOR\nMINOR=$minor\nDEVNAME=${key.node}\n")
        File(hidraw, "dev").writeText("$MAJOR:$minor\n")
        link("/sys/class/hidraw", File(hidraw, "subsystem"))
        link("$guestBusDevice/$port:1.0/$hidName", File(hidraw, "device"))
        link(guestHidraw, File(classes, key.node))
    }

    private fun link(target: String, file: File) {
        if (java.nio.file.Files.isSymbolicLink(file.toPath()) || file.exists()) file.delete()
        android.system.Os.symlink(target, file.path)
    }
}
