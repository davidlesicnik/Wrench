package com.lesicnik.wrench.ui.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.data.repository.CredentialsRepository
import com.lesicnik.wrench.data.repository.ExpenseRepository
import com.lesicnik.wrench.data.repository.VehicleRepository
import com.lesicnik.wrench.ui.expenses.AddEditExpenseScreen
import com.lesicnik.wrench.ui.expenses.AddEditExpenseViewModel
import com.lesicnik.wrench.ui.expenses.ExpensesScreen
import com.lesicnik.wrench.ui.expenses.ExpensesViewModel
import com.lesicnik.wrench.ui.home.HomeScreen
import com.lesicnik.wrench.ui.home.HomeViewModel
import com.lesicnik.wrench.ui.login.LoginScreen
import com.lesicnik.wrench.ui.login.LoginViewModel
import com.lesicnik.wrench.ui.statistics.StatisticsScreen
import com.lesicnik.wrench.ui.statistics.StatisticsViewModel
import com.lesicnik.wrench.ui.vehicles.VehiclesScreen
import com.lesicnik.wrench.ui.vehicles.VehiclesViewModel

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Vehicles : Screen("vehicles")
    data object Home : Screen("home/{vehicleId}/{vehicleName}/{odometerUnit}") {
        fun createRoute(vehicleId: Int, vehicleName: String, odometerUnit: String): String {
            return "home/$vehicleId/${Uri.encode(vehicleName)}/${Uri.encode(odometerUnit)}"
        }
    }
    data object Expenses : Screen("expenses/{vehicleId}/{vehicleName}/{odometerUnit}") {
        fun createRoute(vehicleId: Int, vehicleName: String, odometerUnit: String): String {
            return "expenses/$vehicleId/${Uri.encode(vehicleName)}/${Uri.encode(odometerUnit)}"
        }
    }
    data object AddExpense : Screen("addExpense/{vehicleId}/{odometerUnit}/{lastOdometer}") {
        fun createRoute(vehicleId: Int, odometerUnit: String, lastOdometer: Int?): String {
            return "addExpense/$vehicleId/${Uri.encode(odometerUnit)}/${lastOdometer ?: -1}"
        }
    }
    data object EditExpense : Screen("editExpense/{vehicleId}/{odometerUnit}/{expenseId}/{expenseType}") {
        fun createRoute(vehicleId: Int, odometerUnit: String, expenseId: Int, expenseType: ExpenseType): String {
            return "editExpense/$vehicleId/${Uri.encode(odometerUnit)}/$expenseId/${expenseType.name}"
        }
    }
    data object Statistics : Screen("statistics/{vehicleId}/{vehicleName}/{odometerUnit}") {
        fun createRoute(vehicleId: Int, vehicleName: String, odometerUnit: String): String {
            return "statistics/$vehicleId/${Uri.encode(vehicleName)}/${Uri.encode(odometerUnit)}"
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

        composable(
            route = Screen.Vehicles.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            val viewModel: VehiclesViewModel = viewModel(
                factory = VehiclesViewModel.Factory(credentialsRepository, vehicleRepository, expenseRepository)
            )
            VehiclesScreen(
                viewModel = viewModel,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Vehicles.route) { inclusive = true }
                    }
                },
                onVehicleClick = { vehicle ->
                    navController.navigate(Screen.Home.createRoute(vehicle.id, vehicle.displayName, vehicle.odometerUnit ?: "km"))
                }
            )
        }

        composable(
            route = Screen.Home.route,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.IntType },
                navArgument("vehicleName") { type = NavType.StringType },
                navArgument("odometerUnit") { type = NavType.StringType }
            ),
            enterTransition = { EnterTransition.None },
            exitTransition = {
                // Keep screen visible while add expense screen slides in on top
                fadeOut(animationSpec = tween(200), targetAlpha = 0.99f)
            },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getInt("vehicleId") ?: return@composable
            val vehicleName = backStackEntry.arguments?.getString("vehicleName") ?: ""
            val odometerUnit = backStackEntry.arguments?.getString("odometerUnit") ?: "km"

            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(
                    credentialsRepository = credentialsRepository,
                    expenseRepository = expenseRepository,
                    vehicleId = vehicleId
                )
            )

            HomeScreen(
                viewModel = viewModel,
                vehicleName = vehicleName,
                odometerUnit = odometerUnit,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToExpenses = {
                    navController.navigate(Screen.Expenses.createRoute(vehicleId, vehicleName, odometerUnit))
                },
                onNavigateToStatistics = {
                    navController.navigate(Screen.Statistics.createRoute(vehicleId, vehicleName, odometerUnit))
                },
                onAddExpense = { lastOdometer ->
                    navController.navigate(Screen.AddExpense.createRoute(vehicleId, odometerUnit, lastOdometer))
                }
            )
        }

        composable(
            route = Screen.Expenses.route,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.IntType },
                navArgument("vehicleName") { type = NavType.StringType },
                navArgument("odometerUnit") { type = NavType.StringType }
            ),
            enterTransition = { EnterTransition.None },
            exitTransition = {
                // Keep screen visible while edit screen slides in on top
                fadeOut(animationSpec = tween(200), targetAlpha = 0.99f)
            },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getInt("vehicleId") ?: return@composable
            val vehicleName = backStackEntry.arguments?.getString("vehicleName") ?: ""
            val odometerUnit = backStackEntry.arguments?.getString("odometerUnit") ?: "km"

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
                odometerUnit = odometerUnit,
                onNavigateBack = {
                    navController.navigate(Screen.Vehicles.route) {
                        popUpTo(Screen.Vehicles.route) { inclusive = true }
                    }
                },
                onNavigateToHome = { navController.popBackStack() },
                onNavigateToStatistics = {
                    navController.navigate(Screen.Statistics.createRoute(vehicleId, vehicleName, odometerUnit)) {
                        popUpTo(Screen.Expenses.route) { inclusive = true }
                    }
                },
                onAddExpense = { lastOdometer ->
                    navController.navigate(Screen.AddExpense.createRoute(vehicleId, odometerUnit, lastOdometer))
                },
                onEditExpense = { expense ->
                    navController.navigate(Screen.EditExpense.createRoute(vehicleId, odometerUnit, expense.id, expense.type))
                }
            )
        }

        composable(
            route = Screen.AddExpense.route,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.IntType },
                navArgument("odometerUnit") { type = NavType.StringType },
                navArgument("lastOdometer") { type = NavType.IntType }
            ),
            enterTransition = { slideInHorizontally(animationSpec = tween(200)) { it } },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(200)) { it } }
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getInt("vehicleId") ?: return@composable
            val odometerUnit = backStackEntry.arguments?.getString("odometerUnit") ?: "km"
            val lastOdometer = backStackEntry.arguments?.getInt("lastOdometer")?.takeIf { it >= 0 }

            val viewModel: AddEditExpenseViewModel = viewModel(
                factory = AddEditExpenseViewModel.Factory(
                    credentialsRepository = credentialsRepository,
                    expenseRepository = expenseRepository,
                    vehicleId = vehicleId
                )
            )

            AddEditExpenseScreen(
                viewModel = viewModel,
                odometerUnit = odometerUnit,
                lastOdometer = lastOdometer,
                onNavigateBack = { navController.popBackStack() },
                onExpenseSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.EditExpense.route,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.IntType },
                navArgument("odometerUnit") { type = NavType.StringType },
                navArgument("expenseId") { type = NavType.IntType },
                navArgument("expenseType") { type = NavType.StringType }
            ),
            enterTransition = { slideInHorizontally(animationSpec = tween(200)) { it } },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(200)) { it } }
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getInt("vehicleId") ?: return@composable
            val odometerUnit = backStackEntry.arguments?.getString("odometerUnit") ?: "km"
            val expenseId = backStackEntry.arguments?.getInt("expenseId") ?: return@composable
            val expenseTypeStr = backStackEntry.arguments?.getString("expenseType") ?: return@composable
            val expenseType = ExpenseType.valueOf(expenseTypeStr)

            val viewModel: AddEditExpenseViewModel = viewModel(
                factory = AddEditExpenseViewModel.Factory(
                    credentialsRepository = credentialsRepository,
                    expenseRepository = expenseRepository,
                    vehicleId = vehicleId
                )
            )

            // Initialize for edit mode
            LaunchedEffect(Unit) {
                viewModel.initializeForEdit(expenseId, expenseType)
            }

            AddEditExpenseScreen(
                viewModel = viewModel,
                odometerUnit = odometerUnit,
                onNavigateBack = { navController.popBackStack() },
                onExpenseSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Statistics.route,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.IntType },
                navArgument("vehicleName") { type = NavType.StringType },
                navArgument("odometerUnit") { type = NavType.StringType }
            ),
            enterTransition = { EnterTransition.None },
            exitTransition = {
                fadeOut(animationSpec = tween(200), targetAlpha = 0.99f)
            },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getInt("vehicleId") ?: return@composable
            val vehicleName = backStackEntry.arguments?.getString("vehicleName") ?: ""
            val odometerUnit = backStackEntry.arguments?.getString("odometerUnit") ?: "km"

            val viewModel: StatisticsViewModel = viewModel(
                factory = StatisticsViewModel.Factory(
                    credentialsRepository = credentialsRepository,
                    expenseRepository = expenseRepository,
                    vehicleId = vehicleId
                )
            )

            // Get last odometer from cached expenses for add expense navigation
            val lastOdometer = expenseRepository.getCachedExpenses(vehicleId)
                ?.firstNotNullOfOrNull { it.odometer }

            StatisticsScreen(
                viewModel = viewModel,
                vehicleName = vehicleName,
                odometerUnit = odometerUnit,
                onNavigateBack = {
                    navController.navigate(Screen.Vehicles.route) {
                        popUpTo(Screen.Vehicles.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.createRoute(vehicleId, vehicleName, odometerUnit)) {
                        popUpTo(Screen.Statistics.route) { inclusive = true }
                    }
                },
                onNavigateToExpenses = {
                    navController.navigate(Screen.Expenses.createRoute(vehicleId, vehicleName, odometerUnit)) {
                        popUpTo(Screen.Statistics.route) { inclusive = true }
                    }
                },
                onAddExpense = {
                    navController.navigate(Screen.AddExpense.createRoute(vehicleId, odometerUnit, lastOdometer))
                }
            )
        }
    }
}
