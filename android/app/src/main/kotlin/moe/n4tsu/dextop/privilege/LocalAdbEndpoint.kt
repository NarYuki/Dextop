package moe.n4tsu.dextop.privilege

internal object LocalAdbEndpoint {
    fun accepts(host: String?, localAddresses: Collection<String>): Boolean {
        val address = host?.substringBefore('%')?.takeIf { it.isNotBlank() } ?: return false
        return address == "127.0.0.1" || address == "::1" || address == "0:0:0:0:0:0:0:1" ||
            localAddresses.any { it.substringBefore('%') == address }
    }
}
