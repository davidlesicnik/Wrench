package com.lesicnik.wrench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.lesicnik.wrench.data.repository.CredentialsRepository
import com.lesicnik.wrench.data.repository.ExpenseRepository
import com.lesicnik.wrench.data.repository.VehicleRepository
import com.lesicnik.wrench.ui.navigation.WrenchNavGraph
import com.lesicnik.wrench.ui.theme.WrenchTheme

class MainActivity : ComponentActivity() {

    private val credentialsRepository by lazy { CredentialsRepository(applicationContext) }
    private val vehicleRepository by lazy { VehicleRepository() }
    private val expenseRepository by lazy { ExpenseRepository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WrenchTheme {
                val navController = rememberNavController()
                WrenchNavGraph(
                    navController = navController,
                    credentialsRepository = credentialsRepository,
                    vehicleRepository = vehicleRepository,
                    expenseRepository = expenseRepository
                )
            }
        }
    }
}
