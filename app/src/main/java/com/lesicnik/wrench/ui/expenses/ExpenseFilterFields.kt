package com.lesicnik.wrench.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ExpenseFilterFields(
    query: String,
    onQueryChanged: (String) -> Unit,
    dateFrom: LocalDate?,
    dateTo: LocalDate?,
    onDateClick: () -> Unit,
    onClearDates: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(18.dp)
    val fieldColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextField(
            value = query,
            onValueChange = onQueryChanged,
            placeholder = { Text("Search expenses") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { onQueryChanged("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                } }
            } else null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            singleLine = true,
            shape = shape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = fieldColor,
                unfocusedContainerColor = fieldColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )
        Surface(
            onClick = { focusManager.clearFocus(); onDateClick() },
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = fieldColor
        ) {
            Row(
                modifier = Modifier.heightIn(min = 64.dp).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Date range", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(dateRangeLabel(dateFrom, dateTo), style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (dateFrom != null || dateTo != null) {
                    IconButton(onClick = onClearDates) {
                        Icon(Icons.Default.Close, contentDescription = "Clear date range",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
        }
    }
}

private fun dateRangeLabel(from: LocalDate?, to: LocalDate?): String {
    if (from == null || to == null) return "Any date"
    val full = DateTimeFormatter.ofPattern("d MMM yyyy")
    return when {
        from == to -> from.format(full)
        from.year == to.year && from.month == to.month -> "${from.dayOfMonth}–${to.format(full)}"
        from.year == to.year -> "${from.format(DateTimeFormatter.ofPattern("d MMM"))} – ${to.format(full)}"
        else -> "${from.format(full)} – ${to.format(full)}"
    }
}
