package com.lesicnik.wrench.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class BottomTab { HOME, EXPENSES, STATISTICS, CALENDAR }

@Composable
fun VehicleNavigationBar(selectedTab: BottomTab, onTabSelected: (BottomTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        BottomTab.entries.forEach { tab ->
            val (icon, label) = when (tab) {
                BottomTab.HOME -> Icons.Default.Home to "Home"
                BottomTab.EXPENSES -> Icons.Default.Receipt to "Expenses"
                BottomTab.STATISTICS -> Icons.Default.BarChart to "Stats"
                BottomTab.CALENDAR -> Icons.Default.Build to "Maintenance"
            }
            NavigationBarItem(selected = tab == selectedTab, onClick = { onTabSelected(tab) },
                icon = { Icon(icon, null) }, label = { Text(label, maxLines = 1) })
        }
    }
}

@Composable
fun AddRecordButton(label: String = "Add expense", onClick: () -> Unit) {
    ExtendedFloatingActionButton(onClick = onClick,
        icon = { Icon(Icons.Default.Add, null) }, text = { Text(label) },
        modifier = Modifier.semantics { contentDescription = label },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
}
