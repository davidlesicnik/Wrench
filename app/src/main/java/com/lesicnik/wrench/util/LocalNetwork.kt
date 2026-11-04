package com.lesicnik.wrench.util

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

object LocalNetwork {

    fun isLocalHttpUrl(url: String): Boolean {
        val uri = try {
            URI(url.trim())
        } catch (_: Exception) {
            return false
        }

        if (uri.scheme?.lowercase() != "http") return false
        val host = uri.host ?: return false
        return isLocalHost(host)
    }

    fun isLocalHost(host: String): Boolean {
        val normalized = host
            .trim()
            .lowercase()
            .removePrefix("[")
            .removeSuffix("]")
            .substringBefore('%') // strip IPv6 zone id if present (e.g., fe80::1%wlan0)

        if (normalized == "localhost") return true

        return isLocalIpv4(normalized) || isLocalIpv6(normalized)
    }

    private fun isLocalIpv4(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false

        val first = octets[0]
        val second = octets[1]

        return when {
            first == 127 -> true // Loopback 127.0.0.0/8
            first == 10 -> true // 10.0.0.0/8
            first == 192 && second == 168 -> true // 192.168.0.0/16
            first == 172 && second in 16..31 -> true // 172.16.0.0/12
            first == 169 && second == 254 -> true // Link-local 169.254.0.0/16
            else -> false
        }
    }

    private fun isLocalIpv6(host: String): Boolean {
        // Only parse numeric literals; never resolve a hostname to decide whether HTTP is safe.
        if (':' !in host || !host.matches(Regex("[0-9a-f:.]+"))) return false
        val address = try {
            InetAddress.getByName(host) as? Inet6Address ?: return false
        } catch (_: Exception) {
            return false
        }
        return address.isLoopbackAddress || address.isLinkLocalAddress ||
            (address.address[0].toInt() and 0xfe) == 0xfc // Unique local fc00::/7
    }
}

