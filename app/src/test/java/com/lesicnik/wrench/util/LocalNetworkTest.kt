package com.lesicnik.wrench.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkTest {

    @Test
    fun isLocalHost_allows_localhost() {
        assertTrue(LocalNetwork.isLocalHost("localhost"))
        assertTrue(LocalNetwork.isLocalHost("LOCALHOST"))
    }

    @Test
    fun isLocalHost_allows_private_ipv4_ranges() {
        assertTrue(LocalNetwork.isLocalHost("127.0.0.1"))
        assertTrue(LocalNetwork.isLocalHost("10.0.2.2"))
        assertTrue(LocalNetwork.isLocalHost("192.168.1.20"))
        assertTrue(LocalNetwork.isLocalHost("172.16.0.1"))
        assertTrue(LocalNetwork.isLocalHost("172.31.255.254"))
        assertTrue(LocalNetwork.isLocalHost("169.254.10.10"))
    }

    @Test
    fun isLocalHost_rejects_public_ipv4_and_domains() {
        assertFalse(LocalNetwork.isLocalHost("8.8.8.8"))
        assertFalse(LocalNetwork.isLocalHost("1.1.1.1"))
        assertFalse(LocalNetwork.isLocalHost("example.com"))
        assertFalse(LocalNetwork.isLocalHost("100.64.0.1"))
        assertFalse(LocalNetwork.isLocalHost("172.32.0.1"))
    }

    @Test
    fun isLocalHost_allows_local_ipv6() {
        assertTrue(LocalNetwork.isLocalHost("::1"))
        assertTrue(LocalNetwork.isLocalHost("[::1]"))
        assertTrue(LocalNetwork.isLocalHost("fe80::1"))
        assertTrue(LocalNetwork.isLocalHost("fd00::1"))
    }

    @Test
    fun isLocalHttpUrl_allows_local_http_urls() {
        assertTrue(LocalNetwork.isLocalHttpUrl("http://192.168.1.2:8080"))
        assertTrue(LocalNetwork.isLocalHttpUrl("http://localhost:8080"))
        assertTrue(LocalNetwork.isLocalHttpUrl("http://[::1]:8080"))
    }

    @Test
    fun isLocalHttpUrl_rejects_non_local_http_urls_and_non_http() {
        assertFalse(LocalNetwork.isLocalHttpUrl("http://example.com"))
        assertFalse(LocalNetwork.isLocalHttpUrl("http://8.8.8.8"))
        assertFalse(LocalNetwork.isLocalHttpUrl("https://192.168.1.2"))
    }
}

