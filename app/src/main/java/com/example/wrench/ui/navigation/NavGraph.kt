package com.example.wrench.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.wrench.data.repository.CredentialsRepository
import com.example.wrench.data.repository.ExpenseRepository
import com.example.wrench.data.repository.VehicleRepository
import com.example.wrench.ui.expenses.ExpensesScreen
import com.example.wrench.ui.expenses.ExpensesViewModel
import com.example.wrench.ui.login.LoginScreen
import com.example.wrench.ui.login.LoginViewModel
import com.example.wrench.ui.vehicles.VehiclesScreen
import com.example.wrench.ui.vehicles.VehiclesViewModel

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Vehicles : Screen("vehicles")
    data object Expenses : Screen("expenses/{vehicleId}/{vehicleName}") {
        fun createRoute(vehicleId: Int, vehicleName: String): String {
            return "expenses/$vehicleId/${Uri.encode(vehicleName)}"
        }
    }
}

@Composable
fun WrenchNavGraph(
    navController: NavHostController,
    credentialsRepository: CredentialsRepository,
    vehicleRepository: VehicleRepository,
    expenseRepository: ExpenseRepository
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
                },
                onVehicleClick = { vehicle ->
                    navController.navigate(Screen.Expenses.createRoute(vehicle.id, vehicle.displayName))
                }
            )
        }

        composable(
            route = Screen.Expenses.route,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.IntType },
                navArgument("vehicleName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getInt("vehicleId") ?: return@composable
            val vehicleName = backStackEntry.arguments?.getString("vehicleName") ?: ""

            val viewModel: ExpensesViewModel = viewModel(
                factory = ExpensesViewModel.Factory(
                    credentialsRepository = credentialsRepository,
                    expenseRepository = expenseRepository,
                    vehicleId = vehicleId
                )
            )

            ExpensesScreen(
                viewModel = viewModel,
                vehicleName = vehicleName,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
