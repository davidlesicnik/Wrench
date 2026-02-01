package com.lesicnik.wrench.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class BottomTab {
    HOME,
    EXPENSES,
    STATISTICS,
    CALENDAR
}

val FabRadius = 28.dp
val BottomBarHeight = 80.dp

private class BottomBarCutoutShape(
    private val fabRadiusPx: Float,
    private val cutoutMarginPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val cutoutRadius = fabRadiusPx + cutoutMarginPx
            val centerX = size.width / 2
            val cutoutDepth = fabRadiusPx + cutoutMarginPx / 2

            moveTo(0f, 0f)
            lineTo(centerX - cutoutRadius - cutoutMarginPx, 0f)

            cubicTo(
                centerX - cutoutRadius, 0f,
                centerX - cutoutRadius, cutoutDepth,
                centerX, cutoutDepth
            )

            cubicTo(
                centerX + cutoutRadius, cutoutDepth,
                centerX + cutoutRadius, 0f,
                centerX + cutoutRadius + cutoutMarginPx, 0f
            )

            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun BottomBarWithFab(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fabRadiusPx = with(LocalDensity.current) { FabRadius.toPx() }
    val cutoutMarginPx = with(LocalDensity.current) { 8.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BottomBarHeight)
            .graphicsLayer { clip = false }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = BottomBarCutoutShape(fabRadiusPx, cutoutMarginPx)
                )
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarIcon(
                    icon = Icons.Default.Home,
                    label = "Home",
                    selected = selectedTab == BottomTab.HOME,
                    onClick = { onTabSelected(BottomTab.HOME) }
                )

                BottomBarIcon(
                    icon = Icons.Default.Receipt,
                    label = "Expenses",
                    selected = selectedTab == BottomTab.EXPENSES,
                    onClick = { onTabSelected(BottomTab.EXPENSES) }
                )

                Spacer(modifier = Modifier.width(FabRadius * 2 + 24.dp))

                BottomBarIcon(
                    icon = Icons.Default.BarChart,
                    label = "Stats",
                    selected = selectedTab == BottomTab.STATISTICS,
                    onClick = { onTabSelected(BottomTab.STATISTICS) }
                )

                BottomBarIcon(
                    icon = Icons.Default.CalendarMonth,
                    label = "Calendar",
                    selected = selectedTab == BottomTab.CALENDAR,
                    onClick = { onTabSelected(BottomTab.CALENDAR) }
                )
            }
        }

        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = -FabRadius.toPx() + cutoutMarginPx / 2
                }
                .size(FabRadius * 2),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Expense"
            )
        }
    }
}

@Composable
private fun BottomBarIcon(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(4.dp)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = CircleShape
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}
