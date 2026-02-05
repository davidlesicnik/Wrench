package com.lesicnik.wrench.util

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
        if (host == "::1") return true // Loopback

        // Link-local fe80::/10
        if (host.startsWith("fe8") || host.startsWith("fe9") || host.startsWith("fea") || host.startsWith("feb")) {
            return true
        }

        // Unique local fc00::/7
        if (host.startsWith("fc") || host.startsWith("fd")) {
            return true
        }

        return false
    }
}

