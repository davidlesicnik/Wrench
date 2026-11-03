package com.lesicnik.wrench.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

data class AppPreferences(val currencyCode: String, val compactVehicles: Boolean = false)
val LocalAppPreferences = staticCompositionLocalOf { AppPreferences("EUR") }
private val LocalUpdatePreferences = staticCompositionLocalOf<(AppPreferences) -> Unit> { {} }

@Composable
fun AppPreferencesProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val storage = remember { context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE) }
    var preferences by remember {
        mutableStateOf(AppPreferences(
            storage.getString("currency", null) ?: runCatching {
                Currency.getInstance(Locale.getDefault()).currencyCode
            }.getOrDefault("EUR"),
            storage.getBoolean("compact_vehicles", false)
        ))
    }
    CompositionLocalProvider(
        LocalAppPreferences provides preferences,
        LocalUpdatePreferences provides { value ->
            preferences = value
            storage.edit().putString("currency", value.currencyCode)
                .putBoolean("compact_vehicles", value.compactVehicles).apply()
        },
        content = content
    )
}

@Composable
fun rememberCurrencyFormatter(): NumberFormat {
    val code = LocalAppPreferences.current.currencyCode
    val locale = LocalContext.current.resources.configuration.locales[0]
    return remember(code, locale) {
        NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance(code) }
    }
}

@Composable
fun DisplaySettingsButton() {
    val preferences = LocalAppPreferences.current
    val update = LocalUpdatePreferences.current
    var open by remember { mutableStateOf(false) }
    var code by remember(preferences) { mutableStateOf(preferences.currencyCode) }
    var compact by remember(preferences) { mutableStateOf(preferences.compactVehicles) }
    val valid = code.length == 3 && runCatching { Currency.getInstance(code) }.isSuccess
    IconButton(onClick = {
        code = preferences.currencyCode
        compact = preferences.compactVehicles
        open = true
    }) { Icon(Icons.Default.Settings, contentDescription = "Display settings") }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Display settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose the currency used by your LubeLogger records. This changes formatting; amounts are not converted.")
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase(Locale.ROOT).filter(Char::isLetter).take(3) },
                    label = { Text("Currency code") },
                    supportingText = { Text("For example EUR, USD or GBP") },
                    isError = !valid,
                    singleLine = true
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = compact, onCheckedChange = { compact = it })
                    Text("Compact vehicle list")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                update(AppPreferences(code, compact))
                open = false
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
    )
}
