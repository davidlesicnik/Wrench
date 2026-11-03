package com.lesicnik.wrench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import coil.request.ImageRequest
import com.lesicnik.wrench.data.preload.SessionPreloader
import com.lesicnik.wrench.data.remote.photoUrl
import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.repository.CredentialsRepository
import com.lesicnik.wrench.data.repository.ExpenseRepository
import com.lesicnik.wrench.data.repository.VehicleRepository
import com.lesicnik.wrench.data.sync.OfflineSyncEngine
import com.lesicnik.wrench.data.sync.SyncWorkScheduler
import com.lesicnik.wrench.ui.navigation.WrenchNavGraph
import com.lesicnik.wrench.ui.theme.WrenchTheme

class MainActivity : ComponentActivity() {

    private val credentialsRepository by lazy { CredentialsRepository(applicationContext) }
    private val database by lazy { WrenchDatabase.getInstance(applicationContext) }
    private val syncEngine by lazy { OfflineSyncEngine(database) }
    private val vehicleRepository by lazy { VehicleRepository(database, syncEngine) }
    private val expenseRepository by lazy {
        ExpenseRepository(
            appContext = applicationContext,
            database = database,
            syncEngine = syncEngine
        )
    }

    private val sessionPreloader by lazy {
        SessionPreloader(
            scope = lifecycleScope,
            preloadData = expenseRepository::preloadSessionData,
            preloadPhoto = { serverUrl, apiKey, vehicle ->
                vehicle.photoUrl(serverUrl)?.let { url ->
                    applicationContext.imageLoader.execute(
                        ImageRequest.Builder(applicationContext)
                            .data(url)
                            .addHeader("x-api-key", apiKey)
                            .size(1024)
                            .build()
                    )
                }
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncWorkScheduler.schedulePeriodicSync(applicationContext)

        enableEdgeToEdge()
        setContent {
            com.lesicnik.wrench.ui.settings.AppPreferencesProvider {
                WrenchTheme {
                    val navController = rememberNavController()
                    WrenchNavGraph(
                        navController = navController,
                        credentialsRepository = credentialsRepository,
                        vehicleRepository = vehicleRepository,
                        expenseRepository = expenseRepository,
                        sessionPreloader = sessionPreloader
                    )
                }
            }
        }
    }
}
