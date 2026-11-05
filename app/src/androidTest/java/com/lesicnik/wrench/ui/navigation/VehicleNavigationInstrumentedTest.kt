package com.lesicnik.wrench.ui.navigation

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.lesicnik.wrench.ui.theme.WrenchTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VehicleNavigationInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController
    private val home = Screen.Home.createRoute(1, "First car", "km")
    private val expenses = Screen.Expenses.createRoute(1, "First car", "km")
    private val statistics = Screen.Statistics.createRoute(1, "First car", "km")
    private val calendar = Screen.Calendar.createRoute(1, "First car", "km")

    private fun show() {
        compose.setContent { WrenchTheme {
            nav = rememberNavController()
            val arguments = listOf(navArgument("vehicleId") { type = NavType.IntType },
                navArgument("vehicleName") { type = NavType.StringType }, navArgument("odometerUnit") { type = NavType.StringType })
            NavHost(nav, startDestination = Screen.Vehicles.route) {
                composable(Screen.Vehicles.route) { Text("Garage") }
                composable(Screen.Home.route, arguments) { Text("Home") }
                composable(Screen.Expenses.route, arguments + navArgument("thisMonth") { type = NavType.BoolType; defaultValue = false }) {
                    var value by rememberSaveable { mutableStateOf("") }
                    OutlinedTextField(value, { value = it }, label = { Text("Search expenses") })
                }
                composable(Screen.Statistics.route, arguments) { Text("Statistics") }
                composable(Screen.Calendar.route, arguments) { Text("Maintenance") }
                composable(Screen.AddExpense.route, listOf(navArgument("vehicleId") { type = NavType.IntType },
                    navArgument("odometerUnit") { type = NavType.StringType }, navArgument("lastOdometer") { type = NavType.IntType },
                    navArgument("service") { type = NavType.StringType; defaultValue = "" })) { Text("Service draft") }
            }
        } }
        compose.runOnIdle { nav.navigate(home) }
    }

    @Test fun switchingTabsRestoresInputAndHomeDoesNotStackDuplicates() {
        show()
        compose.runOnIdle { nav.navigateVehicleTab(expenses) }
        compose.onNodeWithText("Search expenses").performTextInput("filter")
        compose.runOnIdle { nav.navigateVehicleTab(statistics) }
        compose.runOnIdle { nav.navigateVehicleTab(calendar) }
        compose.runOnIdle { nav.navigateVehicleTab(expenses) }
        compose.onNodeWithText("Search expenses").assertTextContains("filter")
        repeat(3) {
            compose.runOnIdle { nav.navigateVehicleTab(home) }
            compose.onNodeWithText("Home", substring = false).assertIsDisplayed()
        }
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithText("Garage").assertIsDisplayed()
    }

    @Test fun switchingVehiclesCannotRestoreThePreviousVehicleTab() {
        show()
        compose.runOnIdle { nav.navigateVehicleTab(expenses) }
        compose.onNodeWithText("Search expenses").performTextInput("First car search")
        compose.runOnIdle { nav.navigateVehicleTab(statistics) }
        compose.runOnIdle {
            nav.leaveVehicle()
            nav.navigate(Screen.Home.createRoute(2, "Second car", "mi"))
            nav.navigateVehicleTab(Screen.Expenses.createRoute(2, "Second car", "mi"))
        }
        compose.onNodeWithText("Search expenses").assertTextEquals("", "Search expenses")
        compose.runOnIdle {
            assertEquals(2, nav.currentBackStackEntry?.arguments?.getInt("vehicleId"))
            assertEquals("mi", nav.currentBackStackEntry?.arguments?.getString("odometerUnit"))
        }
    }

    @Test fun reminderDescriptionsAreDecodedWithoutChangingNavigationArguments() {
        show()
        val description = "Oil & filter / inspection? #1 – Škoda"
        compose.runOnIdle { nav.navigate(Screen.AddExpense.createRoute(1, "km", 146848, description)) }
        compose.onNodeWithText("Service draft").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(description, nav.currentBackStackEntry?.arguments?.getString("service"))
            assertEquals(146848, nav.currentBackStackEntry?.arguments?.getInt("lastOdometer"))
        }
    }
}
