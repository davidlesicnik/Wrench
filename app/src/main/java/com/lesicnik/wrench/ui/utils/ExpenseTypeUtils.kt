package com.lesicnik.wrench.ui.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.lesicnik.wrench.data.remote.records.ExpenseType
import com.lesicnik.wrench.ui.theme.FuelGreen
import com.lesicnik.wrench.ui.theme.RepairRed
import com.lesicnik.wrench.ui.theme.ServiceBlue
import com.lesicnik.wrench.ui.theme.TaxOrange
import com.lesicnik.wrench.ui.theme.UpgradePurple

data class ExpenseTypeStyle(
    val icon: ImageVector,
    val color: Color,
    val label: String
)

fun ExpenseType.getStyle(): ExpenseTypeStyle = when (this) {
    ExpenseType.SERVICE -> ExpenseTypeStyle(Icons.Default.Build, ServiceBlue, "Service")
    ExpenseType.REPAIR -> ExpenseTypeStyle(Icons.Default.Handyman, RepairRed, "Repair")
    ExpenseType.UPGRADE -> ExpenseTypeStyle(Icons.AutoMirrored.Filled.TrendingUp, UpgradePurple, "Upgrade")
    ExpenseType.FUEL -> ExpenseTypeStyle(Icons.Default.LocalGasStation, FuelGreen, "Fuel")
    ExpenseType.TAX -> ExpenseTypeStyle(Icons.Default.Receipt, TaxOrange, "Tax")
}

val ExpenseType.icon: ImageVector
    get() = getStyle().icon

val ExpenseType.color: Color
    get() = getStyle().color

val ExpenseType.label: String
    get() = getStyle().label
