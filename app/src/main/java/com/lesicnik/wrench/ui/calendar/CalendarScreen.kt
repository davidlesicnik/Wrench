package com.lesicnik.wrench.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.data.repository.Reminder
import com.lesicnik.wrench.data.repository.ReminderMetric
import com.lesicnik.wrench.data.repository.ReminderUrgency
import com.lesicnik.wrench.ui.components.BottomBarWithFab
import com.lesicnik.wrench.ui.components.BottomTab
import com.lesicnik.wrench.ui.components.FabRadius
import com.lesicnik.wrench.ui.utils.RefreshOnResumeEffect
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    vehicleName: String,
    odometerUnit: String,
    onNavigateBack: () -> Unit,
    onTabSelected: (BottomTab) -> Unit,
    onAddReminder: (LocalDate, ReminderMetric) -> Unit,
    createdLocation: String? = null,
    onCreationShown: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    RefreshOnResumeEffect(viewModel::refresh)
    CalendarContent(state, vehicleName, odometerUnit, viewModel::refresh, onNavigateBack, onTabSelected, onAddReminder,
        createdLocation = createdLocation, onCreationShown = {
            viewModel.refreshAfterCreation()
            onCreationShown()
        }, onManage = state.serverUrl?.let { url -> { uriHandler.openUri(url) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarContent(
    state: CalendarUiState,
    vehicleName: String,
    odometerUnit: String,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit,
    onTabSelected: (BottomTab) -> Unit,
    onAddReminder: (LocalDate, ReminderMetric) -> Unit,
    today: LocalDate = LocalDate.now(),
    createdLocation: String? = null,
    onCreationShown: () -> Unit = {},
    onManage: (() -> Unit)? = null
) {
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthValue)
    var selectedValue by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDate = selectedValue?.let(LocalDate::parse)
    var mileage by rememberSaveable { mutableStateOf(false) }
    var attention by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<Int?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var focusCreated by rememberSaveable { mutableStateOf(false) }
    var refreshBaseline by remember { mutableStateOf<Long?>(null) }
    val addDate = selectedDate ?: if (month == YearMonth.from(today)) today else month.atDay(1)
    val add = { onAddReminder(addDate, if (mileage || selectedDate == null) ReminderMetric.ODOMETER else ReminderMetric.DATE) }
    LaunchedEffect(createdLocation) {
        if (createdLocation != null) {
            refreshBaseline = state.fetchedAt
            focusCreated = true
            attention = false
            mileage = createdLocation == "mileage"
            if (!mileage) {
                val date = LocalDate.parse(createdLocation)
                monthValue = YearMonth.from(date).toString()
                selectedValue = date.toString()
            }
            scope.launch { snackbar.showSnackbar("Reminder added") }
            onCreationShown()
        }
    }
    val scheduled = remember(state.reminders) { state.reminders.filter { it.dueDate != null }.sortedBy { it.dueDate } }
    val mileageReminders = remember(state.reminders) {
        state.reminders.filter { it.dueOdometer != null }
            .sortedWith(compareByDescending<Reminder> { it.urgency.rank }.thenBy { it.dueDistance ?: Int.MAX_VALUE })
    }
    val monthReminders = scheduled.filter { YearMonth.from(it.dueDate) == month }
    val overdue = state.reminders.filter { it.urgency == ReminderUrgency.PAST_DUE }
    val visible = when {
        attention -> overdue.sortedWith(compareBy<Reminder> { it.dueDate ?: LocalDate.MAX }.thenBy { it.dueDistance })
        mileage -> mileageReminders
        else -> monthReminders.filter { selectedDate == null || it.dueDate == selectedDate }
    }
    LaunchedEffect(focusCreated, state.fetchedAt, state.isLoading, state.errorMessage) {
        if (focusCreated && !state.isLoading && (state.fetchedAt != refreshBaseline || state.errorMessage != null)) {
            // Scroll only after the fresh snapshot is ready, so the new reminder is actually visible.
            val agendaIndex = 1 + (if (overdue.isNotEmpty() && !attention) 1 else 0) + (if (!mileage && !attention) 1 else 0)
            listState.animateScrollToItem(if (state.errorMessage != null) 0 else agendaIndex)
            focusCreated = false
        }
    }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val back = {
        when {
            attention -> attention = false
            selectedDate != null -> selectedValue = null
            else -> onNavigateBack()
        }
    }
    BackHandler(onBack = back)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("Calendar", style = MaterialTheme.typography.titleLarge)
                    Text(vehicleName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                } },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = {
                        monthValue = YearMonth.from(today).toString(); selectedValue = today.toString(); mileage = false; attention = false; focusCreated = false
                    }) { Text("Today") }
                    IconButton(onClick = onRefresh, enabled = !state.isLoading) { Icon(Icons.Default.Refresh, "Refresh reminders") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = { Box(Modifier.padding(top = FabRadius)) {
            BottomBarWithFab(BottomTab.CALENDAR, onTabSelected, add, addContentDescription = "Add reminder")
        } }
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.isLoading && state.fetchedAt != null, onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize().testTag("calendar-list"), state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { CalendarViewSwitch(mileage, scheduled.size, mileageReminders.size) { mileage = it; attention = false; focusCreated = false } }
                if (state.isLoading && state.fetchedAt == null) item {
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Loading your reminders…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                if (state.errorMessage != null) item {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (state.fetchedAt != null) "Showing saved reminders" else "Couldn't load reminders", style = MaterialTheme.typography.titleSmall)
                            Text(state.errorMessage, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onRefresh, enabled = !state.isLoading) { Text("Try again") }
                        }
                    }
                }
                if (overdue.isNotEmpty() && !attention) item {
                    Surface(onClick = { attention = true; mileage = false; selectedValue = null }, shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.semantics { contentDescription = "View overdue reminders" },
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.WarningAmber, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Text("${overdue.size} overdue", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Review", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (!mileage && !attention) item {
                    MonthCalendar(month, selectedDate, today, scheduled,
                        onMonthChange = { monthValue = it.toString(); selectedValue = null; focusCreated = false },
                        onSelect = { selectedValue = if (selectedDate == it) null else it.toString(); focusCreated = false })
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(when {
                                attention -> "Overdue reminders"
                                mileage -> "Mileage reminders"
                                selectedDate != null -> selectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
                                month == YearMonth.from(today) -> "This month"
                                else -> month.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
                            }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(if (mileage && !attention) "Based on your recorded odometer" else
                                "${visible.size} ${if (visible.size == 1) "reminder" else "reminders"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (attention) TextButton(onClick = { attention = false; mileage = false }) { Text("Show calendar") }
                        else if (!mileage && selectedDate != null) TextButton(onClick = { selectedValue = null }) { Text("Show month") }
                    }
                }
                if (visible.isEmpty() && state.fetchedAt != null) item {
                    Surface(shape = RoundedCornerShape(24.dp), color = calendarPanelColor(), border = calendarPanelBorder()) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                                Icon(if (mileage) Icons.Default.Speed else Icons.Default.EventAvailable, null, Modifier.padding(16.dp).size(28.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(when { attention -> "You're all caught up"; mileage -> "No mileage reminders"; else -> "Nothing scheduled" },
                                style = MaterialTheme.typography.titleMedium)
                            Text(when {
                                attention -> "No overdue reminders."
                                mileage -> "Keep track of oil changes, tyres, and other maintenance by mileage."
                                selectedDate != null -> "A clear day. Add a reminder for ${selectedDate.format(dateFormat)}."
                                else -> "Plan your next service, inspection, or renewal."
                            }, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!attention) FilledTonalButton(onClick = add) {
                                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add reminder")
                            }
                            val next = scheduled.firstOrNull { YearMonth.from(it.dueDate) > month } ?: scheduled.lastOrNull { YearMonth.from(it.dueDate) < month }
                            if (!mileage && !attention && selectedDate == null && next != null) TextButton(onClick = { monthValue = YearMonth.from(next.dueDate).toString() }) {
                                Text("${if (next.dueDate!! >= today) "Next reminder" else "Previous reminder"} · ${next.dueDate.format(dateFormat)}")
                            }
                        }
                    }
                }
                // Grouping dated entries makes a busy day readable without repeating the date on every row.
                visible.groupBy { if (mileage && !attention) null else it.dueDate }.forEach { (date, reminders) ->
                    if (date != null && selectedDate == null && !mileage) item(key = "date-$date") {
                        Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())), Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(reminders, key = { it.id }) { reminder ->
                        ReminderCard(reminder, odometerUnit, (mileage && !attention) || reminder.dueDate == null, today) { detailId = reminder.id }
                    }
                }
                if (state.fetchedAt != null) item {
                    val updated = Instant.ofEpochMilli(state.fetchedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
                    Text("Updated $updated", Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    state.reminders.firstOrNull { it.id == detailId }?.let { ReminderDetailSheet(it, odometerUnit, today, { detailId = null }, onManage) }
}
