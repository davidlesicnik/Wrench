package com.lesicnik.wrench.data.preload

import com.lesicnik.wrench.data.remote.Vehicle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Keeps session warm-up alive across screen navigation, with bounded photo requests. */
class SessionPreloader(
    private val scope: CoroutineScope,
    private val preloadData: suspend (String, String, List<Int>) -> Unit,
    private val preloadPhoto: suspend (String, String, Vehicle) -> Unit
) {
    private var job: Job? = null

    fun start(serverUrl: String, apiKey: String, vehicles: List<Vehicle>) {
        val previous = job
        previous?.cancel()
        val ordered = vehicles.distinctBy { it.id }.sortedBy { it.isSold }
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            previous?.join()
            supervisorScope {
                // Register the shared data refresh before navigation can request the same data.
                launch(start = CoroutineStart.UNDISPATCHED) {
                    bestEffort { preloadData(serverUrl, apiKey, ordered.map { it.id }) }
                }
                launch {
                    val permits = Semaphore(2)
                    ordered.map { vehicle ->
                        async {
                            permits.withPermit {
                                bestEffort { preloadPhoto(serverUrl, apiKey, vehicle) }
                            }
                        }
                    }.awaitAll()
                }
            }
        }
    }

    suspend fun cancelAndJoin() {
        job?.cancelAndJoin()
        job = null
    }

    private suspend fun bestEffort(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Warm-up is optional; normal screen loading still provides errors and retries.
        }
    }
}
