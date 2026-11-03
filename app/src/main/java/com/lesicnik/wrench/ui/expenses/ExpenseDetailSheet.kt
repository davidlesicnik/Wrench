package com.lesicnik.wrench.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.sync.ExpenseSyncState
import com.lesicnik.wrench.ui.settings.rememberCurrencyFormatter
import com.lesicnik.wrench.ui.utils.getStyle
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailSheet(expense: Expense, odometerUnit: String, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val currency = rememberCurrencyFormatter()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!expense.description.equals(expense.type.getStyle().label, ignoreCase = true)) {
                Text(expense.type.getStyle().label, style = MaterialTheme.typography.labelLarge)
            }
            Text(expense.description, style = MaterialTheme.typography.headlineSmall)
            Text(currency.format(expense.cost), style = MaterialTheme.typography.headlineMedium)
            Text(expense.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")))
            expense.odometer?.let { Text("Odometer: ${java.text.NumberFormat.getNumberInstance().format(it)} $odometerUnit") }
            expense.liters?.let { Text("Fuel: $it L") }
            expense.fuelEconomy?.let { Text("Consumption: ${String.format("%.1f", it)} L/100km") }
            if (expense.isFillToFull == true) Text("Filled to full")
            if (expense.isMissedFuelUp == true) Text("Previous fill-up not recorded")
            if (expense.isRecurring == true) Text("Recurring expense")
            expense.notes?.takeIf { it.isNotBlank() }?.let {
                HorizontalDivider()
                Text("Notes", style = MaterialTheme.typography.titleSmall)
                Text(it)
            }
            Text(when (expense.syncState) {
                ExpenseSyncState.SYNCED -> "Synced with server"
                ExpenseSyncState.CONFLICT -> "Conflict · Open Edit to resolve"
                ExpenseSyncState.SYNC_ERROR -> "Sync failed · Retry from expenses"
                else -> "Saved on device · Pending sync"
            }, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Edit expense") }
        }
    }
}
