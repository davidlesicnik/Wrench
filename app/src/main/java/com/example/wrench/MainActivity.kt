package com.example.wrench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.wrench.data.local.AppDatabase
import com.example.wrench.data.repository.CredentialsRepository
import com.example.wrench.data.repository.VehicleRepository
import com.example.wrench.ui.navigation.WrenchNavGraph
import com.example.wrench.ui.theme.WrenchTheme

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private val credentialsRepository by lazy { CredentialsRepository(database.credentialsDao()) }
    private val vehicleRepository by lazy { VehicleRepository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WrenchTheme {
                val navController = rememberNavController()
                WrenchNavGraph(
                    navController = navController,
                    credentialsRepository = credentialsRepository,
                    vehicleRepository = vehicleRepository
                )
            }
        }
    }
}
