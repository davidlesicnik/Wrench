package com.example.wrench.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.wrench.data.repository.CredentialsRepository
import com.example.wrench.data.repository.VehicleRepository
import com.example.wrench.ui.login.LoginScreen
import com.example.wrench.ui.login.LoginViewModel
import com.example.wrench.ui.vehicles.VehiclesScreen
import com.example.wrench.ui.vehicles.VehiclesViewModel

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Vehicles : Screen("vehicles")
}

@Composable
fun WrenchNavGraph(
    navController: NavHostController,
    credentialsRepository: CredentialsRepository,
    vehicleRepository: VehicleRepository
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            val viewModel: LoginViewModel = viewModel(
                factory = LoginViewModel.Factory(credentialsRepository, vehicleRepository)
            )
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Vehicles.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Vehicles.route) {
            val viewModel: VehiclesViewModel = viewModel(
                factory = VehiclesViewModel.Factory(credentialsRepository, vehicleRepository)
            )
            VehiclesScreen(
                viewModel = viewModel,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Vehicles.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
