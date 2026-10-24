package moe.n4tsu.dextop.linux

import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Publishes Android's actual active link, including its policy-table gateway. */
internal object LinuxNetworkLink {
    fun describe(json: String): String {
        require(json.length <= 16_384)
        val value = JSONObject(json)
        val name = value.optString("name")
        if (name.isEmpty()) return ""
        val addresses = value.getJSONArray("addresses")
        val gateways = value.getJSONArray("gateways")
        require(addresses.length() <= 16 && gateways.length() <= 2)
        return describe(name, value.getInt("index"), value.optInt("mtu", 1500),
            (0 until addresses.length()).map { addresses.getJSONObject(it).let { a -> a.getString("address") to a.getInt("prefix") } },
            (0 until gateways.length()).map(gateways::getString))
    }

    fun describe(name: String, index: Int, requestedMtu: Int, addresses: List<Pair<String, Int>>, gateways: List<String>): String {
        if (name.isEmpty()) return ""
        require(name.matches(Regex("[a-zA-Z0-9_.:-]{1,15}")))
        require(index > 1 && addresses.size <= 16 && gateways.size <= 2)
        val mtu = requestedMtu.takeIf { it > 0 } ?: 1500
        fun numeric(text: String): String {
            require(text.isNotEmpty() && text.matches(Regex("[0-9a-fA-F:.]+")))
            return requireNotNull(InetAddress.getByName(text).hostAddress).substringBefore('%')
        }
        return buildString {
            append("if $name $index $mtu\nmac 02:00:00:00:00:01\n")
            for ((address, prefix) in addresses) {
                val ip = numeric(address)
                require(prefix in 0..if (':' in ip) 128 else 32)
                append("addr $ip $prefix\n")
            }
            gateways.forEach { append("gw ${numeric(it)}\n") }
        }
    }

    fun stage(root: File, description: String) {
        write(root, "etc/droiddeck-net", description)
    }

    fun stageSnapshot(root: File, json: String) {
        require(json.length <= 16_384)
        val value = JSONObject(json)
        require(value.has("connected"))
        write(root, "etc/droiddeck-network.json", value.toString())
    }

    fun resolver(json: String): String? {
        val dns = JSONObject(json).optJSONArray("dns") ?: return null
        require(dns.length() <= 16)
        return resolver((0 until dns.length()).map { dns.getString(it) })
    }

    fun resolver(addresses: List<String>): String? {
        require(addresses.size <= 16)
        val servers = addresses.distinct().map {
            require(it.isNotEmpty() && it.matches(Regex("[0-9a-fA-F:.]+")))
            val ip = InetAddress.getByName(it)
            require(!ip.isAnyLocalAddress && !ip.isLinkLocalAddress)
            "nameserver ${ip.hostAddress}\n"
        }
        return servers.joinToString("").takeIf { it.isNotEmpty() }
    }

    fun stageResolver(root: File, resolver: String) = write(root, "etc/resolv.conf", resolver)

    private fun write(root: File, relative: String, description: String) {
        val file = File(root, relative)
        if (file.isFile && file.readText() == description) return
        file.parentFile.mkdirs()
        val staged = File.createTempFile(".dextop-net-", ".staged", file.parentFile)
        try {
            staged.writeText(description)
            check(staged.setReadable(true, false))
            Files.move(staged.toPath(), file.toPath(), REPLACE_EXISTING)
        } finally { staged.delete() }
    }
}
