package com.lesicnik.wrench.ui.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lesicnik.wrench.data.preload.SessionPreloader
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.data.repository.CredentialsRepository
import com.lesicnik.wrench.data.repository.ExpenseRepository
import com.lesicnik.wrench.data.repository.ReminderMetric
import com.lesicnik.wrench.data.repository.ReminderRepository
import com.lesicnik.wrench.data.repository.VehicleRepository
import com.lesicnik.wrench.ui.calendar.AddReminderScreen
import com.lesicnik.wrench.ui.calendar.AddReminderViewModel
import com.lesicnik.wrench.ui.calendar.CalendarScreen
import com.lesicnik.wrench.ui.calendar.CalendarViewModel
import com.lesicnik.wrench.ui.components.BottomTab
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
import java.time.LocalDate

/**
 * Data class to hold common vehicle navigation arguments.
 */
data class VehicleNavArgs(
    val vehicleId: Int,
    val vehicleName: String,
    val odometerUnit: String
)

/**
 * Common nav arguments for screens that display vehicle data.
 */
private fun vehicleNavArguments() = listOf(
    navArgument("vehicleId") { type = NavType.IntType },
    navArgument("vehicleName") { type = NavType.StringType },
    navArgument("odometerUnit") { type = NavType.StringType }
)

/**
 * Extract vehicle arguments from a BackStackEntry.
 */
private fun androidx.navigation.NavBackStackEntry.extractVehicleArgs(): VehicleNavArgs? {
    val vehicleId = arguments?.getInt("vehicleId") ?: return null
    val vehicleName = arguments?.getString("vehicleName") ?: ""
    val odometerUnit = arguments?.getString("odometerUnit") ?: "km"
    return VehicleNavArgs(vehicleId, vehicleName, odometerUnit)
}

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Vehicles : Screen("vehicles")
    data object Home : Screen("home/{vehicleId}/{vehicleName}/{odometerUnit}") {
        fun createRoute(vehicleId: Int, vehicleName: String, odometerUnit: String): String {
            return "home/$vehicleId/${Uri.encode(vehicleName)}/${Uri.encode(odometerUnit)}"
        }
    }
    data object Expenses : Screen("expenses/{vehicleId}/{vehicleName}/{odometerUnit}?thisMonth={thisMonth}") {
        fun createRoute(vehicleId: Int, vehicleName: String, odometerUnit: String, thisMonth: Boolean = false): String {
            return "expenses/$vehicleId/${Uri.encode(vehicleName)}/${Uri.encode(odometerUnit)}?thisMonth=$thisMonth"
        }
    }
    data object Calendar : Screen("calendar/{vehicleId}/{vehicleName}/{odometerUnit}") {
        fun createRoute(vehicleId: Int, vehicleName: String, odometerUnit: String): String =
            "calendar/$vehicleId/${Uri.encode(vehicleName)}/${Uri.encode(odometerUnit)}"
    }
    data object AddReminder : Screen("addReminder/{vehicleId}/{vehicleName}/{odometerUnit}/{initialDate}?metric={metric}") {
        fun createRoute(vehicleId: Int, vehicleName: String, odometerUnit: String, initialDate: LocalDate, metric: ReminderMetric): String =
            "addReminder/$vehicleId/${Uri.encode(vehicleName)}/${Uri.encode(odometerUnit)}/$initialDate?metric=${metric.name}"
    }
    data object AddExpense : Screen("addExpense/{vehicleId}/{odometerUnit}/{lastOdometer}?service={service}&reminderId={reminderId}") {
        fun createRoute(vehicleId: Int, odometerUnit: String, lastOdometer: Int?, service: String = "", reminderId: Int = -1): String {
            return "addExpense/$vehicleId/${Uri.encode(odometerUnit)}/${lastOdometer ?: -1}?service=${Uri.encode(service)}&reminderId=$reminderId"
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
    expenseRepository: ExpenseRepository,
    sessionPreloader: SessionPreloader,
    reminderRepository: ReminderRepository
) {
    NavHost(
        navController = navController,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            val viewModel: LoginViewModel = viewModel(
                factory = LoginViewModel.Factory(credentialsRepository, vehicleRepository)
            )
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    val session = viewModel.uiState.value
                    sessionPreloader.start(session.serverUrl, session.apiKey, session.vehicles)
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
                factory = VehiclesViewModel.Factory(credentialsRepository, vehicleRepository, expenseRepository, sessionPreloader)
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
            arguments = vehicleNavArguments(),
            enterTransition = { EnterTransition.None },
            exitTransition = {
                if (targetState.destination.route == Screen.Vehicles.route) ExitTransition.None
                else fadeOut(animationSpec = tween(200), targetAlpha = 0.99f)
            },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val args = backStackEntry.extractVehicleArgs() ?: return@composable
            val (vehicleId, vehicleName, odometerUnit) = args

            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(
                    credentialsRepository = credentialsRepository,
                    expenseRepository = expenseRepository,
                    vehicleId = vehicleId,
                    reminderRepository = reminderRepository
                )
            )

            HomeScreen(
                viewModel = viewModel,
                vehicleName = vehicleName,
                odometerUnit = odometerUnit,
                onNavigateToCalendar = {
                    navController.navigateVehicleTab(Screen.Calendar.createRoute(vehicleId, vehicleName, odometerUnit))
                },
                animateEntry = navController.previousBackStackEntry?.destination?.route == Screen.Vehicles.route,
                onNavigateBack = { navController.leaveVehicle() },
                onNavigateToExpenses = {
                    navController.navigateVehicleTab(Screen.Expenses.createRoute(vehicleId, vehicleName, odometerUnit))
                },
                onNavigateToStatistics = {
                    navController.navigateVehicleTab(Screen.Statistics.createRoute(vehicleId, vehicleName, odometerUnit))
                },
                onMonthlyExpenses = {
                    navController.navigate(Screen.Expenses.createRoute(vehicleId, vehicleName, odometerUnit, thisMonth = true))
                },
                onEditExpense = { expense ->
                    navController.navigate(Screen.EditExpense.createRoute(vehicleId, odometerUnit, expense.id, expense.type))
                },
                onAddExpense = { lastOdometer ->
                    navController.navigate(Screen.AddExpense.createRoute(vehicleId, odometerUnit, lastOdometer))
                }
            )
        }

        composable(
            route = Screen.Expenses.route,
            arguments = vehicleNavArguments() + navArgument("thisMonth") {
                type = NavType.BoolType
                defaultValue = false
            },
            enterTransition = { EnterTransition.None },
            exitTransition = {
                if (targetState.destination.route == Screen.Vehicles.route)
                    ExitTransition.None
                else fadeOut(animationSpec = tween(200), targetAlpha = 0.99f)
            },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val args = backStackEntry.extractVehicleArgs() ?: return@composable
            val (vehicleId, vehicleName, odometerUnit) = args

            val viewModel: ExpensesViewModel = viewModel(
                factory = ExpensesViewModel.Factory(
                    credentialsRepository = credentialsRepository,
                    expenseRepository = expenseRepository,
                    vehicleId = vehicleId
                )
            )

            val thisMonth = backStackEntry.arguments?.getBoolean("thisMonth") == true
            LaunchedEffect(viewModel, thisMonth) {
                if (thisMonth) {
                    val today = LocalDate.now()
                    viewModel.setDateRange(today.withDayOfMonth(1), today)
                }
            }

            ExpensesScreen(
                viewModel = viewModel,
                vehicleName = vehicleName,
                odometerUnit = odometerUnit,
                onNavigateToCalendar = {
                    navController.navigateVehicleTab(Screen.Calendar.createRoute(vehicleId, vehicleName, odometerUnit))
                },
                onNavigateBack = {
                    navController.leaveVehicle()
                },
                onNavigateToHome = {
                    navController.navigateVehicleTab(Screen.Home.createRoute(vehicleId, vehicleName, odometerUnit))
                },
                onNavigateToStatistics = {
                    navController.navigateVehicleTab(Screen.Statistics.createRoute(vehicleId, vehicleName, odometerUnit))
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
            route = Screen.Calendar.route,
            arguments = vehicleNavArguments(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val (vehicleId, vehicleName, odometerUnit) = backStackEntry.extractVehicleArgs() ?: return@composable
            val viewModel: CalendarViewModel = viewModel(
                factory = CalendarViewModel.Factory(credentialsRepository, reminderRepository, vehicleId)
            )
            CalendarScreen(
                viewModel = viewModel,
                vehicleName = vehicleName,
                odometerUnit = odometerUnit,
                onNavigateBack = {
                    navController.leaveVehicle()
                },
                onTabSelected = { tab ->
                    val route = when (tab) {
                        BottomTab.HOME -> Screen.Home.createRoute(vehicleId, vehicleName, odometerUnit)
                        BottomTab.EXPENSES -> Screen.Expenses.createRoute(vehicleId, vehicleName, odometerUnit)
                        BottomTab.STATISTICS -> Screen.Statistics.createRoute(vehicleId, vehicleName, odometerUnit)
                        BottomTab.CALENDAR -> null
                    }
                    route?.let {
                        navController.navigateVehicleTab(it)
                    }
                },
                createdLocation = backStackEntry.savedStateHandle.getStateFlow<String?>("createdReminder", null).collectAsState().value,
                onCreationShown = { backStackEntry.savedStateHandle.set<String?>("createdReminder", null) },
                onAddReminder = { date, metric ->
                    navController.navigate(Screen.AddReminder.createRoute(vehicleId, vehicleName, odometerUnit, date, metric))
                },
                onRecordService = { reminder ->
                    val lastOdometer = expenseRepository.getCachedExpenses(vehicleId)?.mapNotNull { it.odometer }?.maxOrNull()
                    navController.navigate(Screen.AddExpense.createRoute(vehicleId, odometerUnit, lastOdometer, reminder.description, reminder.id))
                }
            )
        }

        composable(
            route = Screen.AddReminder.route,
            arguments = vehicleNavArguments() + listOf(
                navArgument("initialDate") { type = NavType.StringType },
                navArgument("metric") { type = NavType.StringType; defaultValue = ReminderMetric.ODOMETER.name }
            ),
            enterTransition = { slideInHorizontally(animationSpec = tween(200)) { it } },
            exitTransition = { ExitTransition.None },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(200)) { it } }
        ) { backStackEntry ->
            val (vehicleId, vehicleName, odometerUnit) = backStackEntry.extractVehicleArgs() ?: return@composable
            val initialDate = backStackEntry.arguments?.getString("initialDate")?.let(LocalDate::parse) ?: LocalDate.now()
            val viewModel: AddReminderViewModel = viewModel(
                factory = AddReminderViewModel.Factory(credentialsRepository, reminderRepository, vehicleId, initialDate,
                    ReminderMetric.valueOf(backStackEntry.arguments?.getString("metric") ?: ReminderMetric.ODOMETER.name))
            )
            AddReminderScreen(
                viewModel, vehicleName, odometerUnit,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { location ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("createdReminder", location)
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.AddExpense.route,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.IntType },
                navArgument("odometerUnit") { type = NavType.StringType },
                navArgument("lastOdometer") { type = NavType.IntType },
                navArgument("service") { type = NavType.StringType; defaultValue = "" },
                navArgument("reminderId") { type = NavType.IntType; defaultValue = -1 }
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
                    vehicleId = vehicleId,
                    serviceDescription = backStackEntry.arguments?.getString("service").orEmpty(),
                    reminderId = backStackEntry.arguments?.getInt("reminderId")?.takeIf { it > 0 }
                )
            )

            AddEditExpenseScreen(
                viewModel = viewModel,
                odometerUnit = odometerUnit,
                vehicleName = navController.previousBackStackEntry?.arguments?.getString("vehicleName").orEmpty(),
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
                vehicleName = navController.previousBackStackEntry?.arguments?.getString("vehicleName").orEmpty(),
                onNavigateBack = { navController.popBackStack() },
                onExpenseSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Statistics.route,
            arguments = vehicleNavArguments(),
            enterTransition = { EnterTransition.None },
            exitTransition = {
                if (targetState.destination.route == Screen.Vehicles.route)
                    ExitTransition.None
                else fadeOut(animationSpec = tween(200), targetAlpha = 0.99f)
            },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val args = backStackEntry.extractVehicleArgs() ?: return@composable
            val (vehicleId, vehicleName, odometerUnit) = args

            val viewModel: StatisticsViewModel = viewModel(
                factory = StatisticsViewModel.Factory(
                    credentialsRepository = credentialsRepository,
                    expenseRepository = expenseRepository,
                    vehicleId = vehicleId
                )
            )

            // Get last odometer from cached expenses for add expense navigation
            val lastOdometer = expenseRepository.getCachedExpenses(vehicleId)
                ?.mapNotNull { it.odometer }?.maxOrNull()

            StatisticsScreen(
                viewModel = viewModel,
                vehicleName = vehicleName,
                odometerUnit = odometerUnit,
                onNavigateToCalendar = {
                    navController.navigateVehicleTab(Screen.Calendar.createRoute(vehicleId, vehicleName, odometerUnit))
                },
                onNavigateBack = {
                    navController.leaveVehicle()
                },
                onNavigateToHome = {
                    navController.navigateVehicleTab(Screen.Home.createRoute(vehicleId, vehicleName, odometerUnit))
                },
                onNavigateToExpenses = {
                    navController.navigateVehicleTab(Screen.Expenses.createRoute(vehicleId, vehicleName, odometerUnit))
                },
                onAddExpense = {
                    navController.navigate(Screen.AddExpense.createRoute(vehicleId, odometerUnit, lastOdometer))
                }
            )
        }
    }
}

internal fun NavHostController.navigateVehicleTab(route: String) {
    navigate(route) {
        popUpTo(Screen.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

internal fun NavHostController.clearVehicleTabState() {
    listOf(Screen.Expenses.route, Screen.Statistics.route, Screen.Calendar.route).forEach { route ->
        graph.findNode(route)?.let { clearBackStack(it.id) }
    }
}

internal fun NavHostController.leaveVehicle() {
    // Drop saved vehicle tabs before returning to the garage.
    clearVehicleTabState()
    popBackStack(Screen.Vehicles.route, false)
}
