import java.io.File
object BluetoothQueueProbe {
 @JvmStatic fun main(args: Array<String>) {
  val directory=File(args[0])
  try {
   val selected=directory.listFiles().orEmpty().filter { it.name.matches(Regex("[a-f0-9]{32}[.]json")) }.take(32)
   println("selected="+selected.size)
   error("Expected unbounded enumeration to exceed test heap")
  } catch(error: OutOfMemoryError) {
   println("CONFIRMED take(32) still enumerates all files and exhausts 16MiB heap")
  }
 }
}
