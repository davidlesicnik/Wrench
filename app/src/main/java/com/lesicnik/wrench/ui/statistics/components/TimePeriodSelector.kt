package com.lesicnik.wrench.ui.statistics.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.ui.statistics.TimePeriod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePeriodSelector(
    selectedPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        TimePeriod.entries.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selectedPeriod,
                onClick = { onPeriodSelected(period) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = TimePeriod.entries.size
                )
            ) {
                Text(
                    text = when (period) {
                        TimePeriod.LAST_30_DAYS -> "30d"
                        TimePeriod.LAST_90_DAYS -> "90d"
                        TimePeriod.LAST_12_MONTHS -> "12mo"
                        TimePeriod.ALL_TIME -> "All"
                    }
                )
            }
        }
    }
}
