import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
object BluetoothReadProbe {
    fun write(file: File, value: String) {
        if (file.isFile && file.readText() == value) return
        file.parentFile?.mkdirs()
        val temporary = File.createTempFile(".dextop-bt-", ".staged", file.parentFile)
        try {
            temporary.writeText(value)
            check(temporary.setReadable(true, false))
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }

@JvmStatic fun main(args: Array<String>) {
 val target=File(args[0])
 java.io.RandomAccessFile(target,"rw").use { it.setLength(64L*1024*1024) }
 try { write(target,"safe snapshot"); error("Expected unbounded read") }
 catch (error: OutOfMemoryError) { println("CONFIRMED exact Bluetooth write() exhausts 16MiB heap reading 64MiB guest file") }
}
}
