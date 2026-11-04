package com.lesicnik.wrench.data.remote

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.repository.ApiErrorType
import com.lesicnik.wrench.data.repository.ApiResult
import com.lesicnik.wrench.data.repository.VehicleRepository
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworkSecurityTest {
    @Test
    fun `API redirects never forward credentials or reach another origin`() = runBlocking<Unit> {
        val received = AtomicInteger()
        val sourceToken = AtomicReference<String>()
        val target = server()
        val source = server()
        target.createContext("/") { exchange ->
            received.incrementAndGet()
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        source.createContext("/") { exchange ->
            sourceToken.set(exchange.requestHeaders.getFirst("x-api-key"))
            exchange.responseHeaders.add("Location", "http://localhost:${target.address.port}/capture")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        target.start()
        source.start()
        try {
            val response = NetworkModule.getApi("http://127.0.0.1:${source.address.port}").getVehicles("synthetic-test-key")
            assertEquals(302, response.code())
            assertEquals("synthetic-test-key", sourceToken.get())
            assertEquals(0, received.get())
            response.errorBody()?.close()
        } finally {
            source.stop(0)
            target.stop(0)
            NetworkModule.clearCache()
        }
    }

    @Test
    fun `401 and 403 are authentication failures while other HTTP errors are not outages`() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, WrenchDatabase::class.java).build()
        val repository = VehicleRepository(database)
        val source = server()
        val status = AtomicInteger(401)
        source.createContext("/") { exchange ->
            exchange.sendResponseHeaders(status.get(), -1)
            exchange.close()
        }
        source.start()
        try {
            for (code in listOf(302, 401, 403, 404, 500, 503)) {
                status.set(code)
                val result = repository.fetchVehiclesRemote("http://127.0.0.1:${source.address.port}", "synthetic-test-key")
                assertTrue(result is ApiResult.Error)
                val expected = if (code in listOf(401, 403)) ApiErrorType.AUTHENTICATION else ApiErrorType.OTHER
                assertEquals(expected, (result as ApiResult.Error).type)
            }
        } finally {
            source.stop(0)
            database.close()
            NetworkModule.clearCache()
        }
    }

    @Test
    fun `connection refused is eligible for offline fallback but insecure domains are not`() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, WrenchDatabase::class.java).build()
        val repository = VehicleRepository(database)
        val source = server()
        val closedPort = source.address.port
        source.start()
        source.stop(0)
        try {
            val disconnected = repository.fetchVehiclesRemote("http://127.0.0.1:$closedPort", "synthetic-test-key")
            assertEquals(ApiErrorType.NETWORK, (disconnected as ApiResult.Error).type)
            val blocked = repository.fetchVehiclesRemote("http://fc-attacker.example", "synthetic-test-key")
            assertEquals(ApiErrorType.OTHER, (blocked as ApiResult.Error).type)
            assertEquals("Insecure HTTP is only allowed for local network addresses", blocked.message)
        } finally {
            database.close()
            NetworkModule.clearCache()
        }
    }

    private fun server(): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
}
