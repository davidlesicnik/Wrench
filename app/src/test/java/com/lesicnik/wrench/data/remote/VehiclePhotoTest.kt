package com.lesicnik.wrench.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehiclePhotoTest {
    private val server = "https://server.example/lubelogger"

    @Test
    fun `relative and same origin absolute photos remain supported`() {
        assertEquals("$server/images/car.png", vehicle("images/car.png").photoUrl(server))
        assertEquals("$server/images/car.png", vehicle("/images/car.png").photoUrl(server))
        assertEquals("https://server.example/images/car.png", vehicle("https://SERVER.example:443/images/car.png").photoUrl(server))
    }

    @Test
    fun `external photos and origin changes are rejected before attaching credentials`() {
        for (location in listOf(
            "https://other.example/car.png", "//other.example/car.png",
            "https://server.example.attacker.example/car.png", "https://server.example:8443/car.png",
            "http://server.example/car.png", "https://server.example@other.example/car.png",
            "https://user:password@server.example/car.png", "file:///tmp/car.png",
            "https://other.example\\car.png"
        )) assertNull(location, vehicle(location).photoUrl(server))
    }

    @Test
    fun `missing and placeholder photos do not create requests`() {
        for (location in listOf(null, "", "/defaults/noimage.png", "https://server.example/defaults/noimage.png?x=1")) {
            assertNull(vehicle(location).photoUrl(server))
        }
        assertNull(vehicle("images/car.png").photoUrl("invalid server"))
    }

    private fun vehicle(location: String?) = Vehicle(1, location, 2020, "Test", "Car", null, null, false, false, "km")
}
