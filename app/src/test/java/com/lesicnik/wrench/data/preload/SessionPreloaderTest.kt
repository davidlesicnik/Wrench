package com.lesicnik.wrench.data.preload

import com.lesicnik.wrench.data.remote.Vehicle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionPreloaderTest {
    @Test
    fun `starts data immediately without waiting and includes offscreen sold vehicles`() = runTest {
        val release = CompletableDeferred<Unit>()
        var order: List<Int>? = null
        var finished = false
        val photos = mutableListOf<Int>()
        val loader = SessionPreloader(this,
            preloadData = { _, _, ids -> order = ids; release.await(); finished = true },
            preloadPhoto = { _, _, vehicle -> photos += vehicle.id })

        loader.start("https://example.test", "test-key", listOf(vehicle(1, sold = true), vehicle(2), vehicle(3)))
        assertEquals(listOf(2, 3, 1), order)
        assertFalse(finished)
        runCurrent()
        assertEquals(listOf(2, 3, 1), photos)
        release.complete(Unit)
        advanceUntilIdle()
        assertTrue(finished)
    }

    @Test
    fun `photo downloads are limited to two concurrent requests`() = runTest {
        var active = 0
        var peak = 0
        val completed = mutableListOf<Int>()
        val loader = SessionPreloader(this,
            preloadData = { _, _, _ -> },
            preloadPhoto = { _, _, vehicle ->
                active++
                peak = maxOf(peak, active)
                delay(100)
                active--
                completed += vehicle.id
            })
        loader.start("https://example.test", "test-key", (1..7).map { vehicle(it) })
        advanceUntilIdle()
        assertEquals(2, peak)
        assertEquals((1..7).toList(), completed)
    }

    @Test
    fun `failed optional warmups do not stop other photos`() = runTest {
        val completed = mutableListOf<Int>()
        val loader = SessionPreloader(this,
            preloadData = { _, _, _ -> error("Offline") },
            preloadPhoto = { _, _, vehicle ->
                if (vehicle.id == 1) error("Photo missing")
                completed += vehicle.id
            })
        loader.start("https://example.test", "test-key", listOf(vehicle(1), vehicle(2), vehicle(3)))
        advanceUntilIdle()
        assertEquals(listOf(2, 3), completed)
    }

    @Test
    fun `logout cancels both data loading and active photo requests before returning`() = runTest {
        var dataCancelled = false
        var photoCancelled = false
        var completed = false
        val loader = SessionPreloader(this,
            preloadData = { _, _, _ ->
                try { delay(10_000); completed = true } finally { dataCancelled = true }
            },
            preloadPhoto = { _, _, _ ->
                try { delay(10_000); completed = true } finally { photoCancelled = true }
            })
        loader.start("https://example.test", "test-key", listOf(vehicle(1)))
        runCurrent()
        loader.cancelAndJoin()
        assertTrue(dataCancelled)
        assertTrue(photoCancelled)
        assertFalse(completed)
        advanceUntilIdle()
        assertFalse(completed)
    }

    @Test
    fun `a new login cancels the old session before loading the next one`() = runTest {
        val events = mutableListOf<String>()
        val loader = SessionPreloader(this,
            preloadData = { server, _, _ ->
                events += "start:$server"
                try { delay(100) } finally { events += "end:$server" }
            },
            preloadPhoto = { _, _, _ -> })
        loader.start("first", "test-key", listOf(vehicle(1)))
        loader.start("second", "test-key", listOf(vehicle(2)))
        advanceUntilIdle()
        assertEquals(listOf("start:first", "end:first", "start:second", "end:second"), events)
    }

    private fun vehicle(id: Int, sold: Boolean = false) = Vehicle(
        id = id, imageLocation = null, year = 2020, make = "Test", model = "Car", licensePlate = null,
        soldDate = if (sold) "2025-01-01" else null, isElectric = false, useHours = false, odometerUnit = "km"
    )
}
