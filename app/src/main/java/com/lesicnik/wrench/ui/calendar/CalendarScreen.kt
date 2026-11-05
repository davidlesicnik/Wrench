package com.lesicnik.wrench.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lesicnik.wrench.data.repository.*
import com.lesicnik.wrench.ui.components.*
import com.lesicnik.wrench.ui.utils.RefreshOnResumeEffect
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    vehicleName: String,
    odometerUnit: String,
    onNavigateBack: () -> Unit,
    onTabSelected: (BottomTab) -> Unit,
    onAddReminder: (LocalDate, ReminderMetric) -> Unit,
    createdLocation: String? = null,
    onCreationShown: () -> Unit = {},
    onRecordService: (Reminder) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    RefreshOnResumeEffect(viewModel::refresh)
    CalendarContent(state, vehicleName, odometerUnit, viewModel::refresh, onNavigateBack, onTabSelected, onAddReminder,
        createdLocation = createdLocation, onCreationShown = {
            viewModel.refreshAfterCreation()
            onCreationShown()
        }, onManage = state.serverUrl?.let { url -> { uriHandler.openUri(url) } }, onRecordService = onRecordService)
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
    onManage: (() -> Unit)? = null,
    onRecordService: (Reminder) -> Unit = {}
) {
    var view by rememberSaveable { mutableIntStateOf(0) }
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedValue by rememberSaveable { mutableStateOf<String?>(null) }
    var attention by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<Int?>(null) }
    val month = YearMonth.parse(monthValue)
    val selectedDate = selectedValue?.let(LocalDate::parse)
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var focusCreated by rememberSaveable { mutableStateOf(false) }
    var refreshBaseline by remember { mutableStateOf<Long?>(null) }
    val agenda = remember(state.reminders) { maintenanceAgenda(state.reminders) }
    val overdue = agenda.filter { it.urgency == ReminderUrgency.PAST_DUE }
    val scheduled = agenda.filter { it.dueDate != null }
    val visible = when {
        attention -> overdue
        view == 1 -> scheduled.filter { YearMonth.from(it.dueDate) == month && (selectedDate == null || it.dueDate == selectedDate) }.sortedBy { it.dueDate }
        view == 2 -> agenda.filter { it.dueOdometer != null }
        else -> agenda
    }
    val add = {
        val date = selectedDate ?: if (month == YearMonth.from(today)) today else month.atDay(1)
        onAddReminder(if (view == 1) date else today, if (view == 1) ReminderMetric.DATE else ReminderMetric.ODOMETER)
    }
    LaunchedEffect(createdLocation) {
        createdLocation?.let {
            refreshBaseline = state.fetchedAt
            focusCreated = true
            attention = false
            view = if (it == "mileage") 2 else 1
            if (it != "mileage") {
                selectedValue = it
                monthValue = YearMonth.from(LocalDate.parse(it)).toString()
            }
            scope.launch { snackbar.showSnackbar("Reminder added") }
            onCreationShown()
        }
    }
    LaunchedEffect(view, attention, selectedValue) { if (!focusCreated) listState.scrollToItem(0) }
    LaunchedEffect(focusCreated, state.fetchedAt, state.isLoading, state.errorMessage) {
        if (focusCreated && !state.isLoading && (state.fetchedAt != refreshBaseline || state.errorMessage != null)) {
            val headingIndex = 1 + (if (state.errorMessage != null) 1 else 0) +
                (if (overdue.isNotEmpty() && !attention) 1 else 0) + (if (view == 1 && !attention) 1 else 0)
            listState.animateScrollToItem(if (state.errorMessage != null) 0 else headingIndex)
            focusCreated = false
        }
    }
    val back = {
        when {
            attention -> attention = false
            selectedDate != null -> selectedValue = null
            else -> onNavigateBack()
        }
    }
    BackHandler(onBack = back)
    Scaffold(
        topBar = { VehicleTopBar("Maintenance", vehicleName, back) {
            if (view == 1) TextButton(onClick = {
                monthValue = YearMonth.from(today).toString(); selectedValue = today.toString(); attention = false
            }) { Text("Today") }
            IconButton(onClick = onRefresh, enabled = !state.isLoading) { Icon(Icons.Default.Refresh, "Refresh reminders") }
        } },
        bottomBar = { VehicleNavigationBar(BottomTab.CALENDAR, onTabSelected) },
        floatingActionButton = { AddRecordButton("Add reminder", add) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.isLoading && state.fetchedAt != null, onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize().testTag("calendar-list"), state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Agenda", "Calendar", "Mileage").forEachIndexed { index, label ->
                            SegmentedButton(selected = view == index, onClick = { view = index; attention = false; selectedValue = null },
                                shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(label) }
                        }
                    }
                }
                if (state.isLoading && state.fetchedAt == null) item {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Loading maintenance…", style = MaterialTheme.typography.bodyMedium)
                }
                if (state.errorMessage != null) item {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (state.fetchedAt != null) "Showing saved reminders" else "Couldn't load maintenance", style = MaterialTheme.typography.titleSmall)
                            Text(state.errorMessage, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onRefresh, enabled = !state.isLoading) { Text("Try again") }
                        }
                    }
                }
                if (overdue.isNotEmpty() && !attention) item {
                    TextButton(onClick = { attention = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("${overdue.size} overdue · Review", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (view == 1 && !attention) item {
                    MonthCalendar(month, selectedDate, today, scheduled,
                        onMonthChange = { monthValue = it.toString(); selectedValue = null },
                        onSelect = { selectedValue = if (selectedDate == it) null else it.toString() })
                }
                item {
                    Row(Modifier.fillMaxWidth()) {
                        Text(when {
                            attention -> "Overdue reminders"
                            view == 2 -> "Mileage reminders"
                            view == 1 && selectedDate != null -> selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                            view == 1 -> month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                            else -> "Your maintenance plan"
                        }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        if (attention) TextButton(onClick = { attention = false }) { Text("Show all") }
                        else if (selectedDate != null) TextButton(onClick = { selectedValue = null }) { Text("Show month") }
                    }
                    Text(if (view == 2) "Based on your recorded odometer" else "${visible.size} ${if (visible.size == 1) "reminder" else "reminders"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (visible.isEmpty() && state.fetchedAt != null) item {
                    Card(colors = screenCardColors(), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (attention) "You're all caught up" else "Nothing scheduled", style = MaterialTheme.typography.titleMedium)
                            Text(if (attention) "No overdue reminders." else "Plan your next service, inspection, or renewal.", style = MaterialTheme.typography.bodyMedium)
                            if (!attention) TextButton(onClick = add) { Text("Plan maintenance") }
                            val next = scheduled.firstOrNull { it.dueDate!! > month.atEndOfMonth() }
                            if (view == 1 && selectedDate == null && next != null) TextButton(onClick = {
                                monthValue = YearMonth.from(next.dueDate).toString()
                            }) { Text("Next reminder · ${next.dueDate!!.format(DateTimeFormatter.ofPattern("d MMM"))}") }
                        }
                    }
                }
                val groups = if (view == 0 && !attention) visible.groupBy {
                    when (it.urgency) {
                        ReminderUrgency.PAST_DUE -> "Overdue"
                        ReminderUrgency.URGENT, ReminderUrgency.VERY_URGENT -> "Due soon"
                        else -> "Upcoming"
                    }
                } else mapOf("" to visible)
                groups.forEach { (label, reminders) ->
                    if (label.isNotEmpty()) item(key = label) { Text(label, style = MaterialTheme.typography.titleSmall,
                        color = if (label == "Overdue") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(reminders, key = { it.id }) { reminder ->
                        ReminderCard(reminder, odometerUnit, view == 2 || reminder.dueDate == null, today) { detailId = reminder.id }
                    }
                }
                if (state.fetchedAt != null) item {
                    Text("Updated ${Instant.ofEpochMilli(state.fetchedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    state.reminders.firstOrNull { it.id == detailId }?.let { reminder ->
        ReminderDetailSheet(reminder, odometerUnit, today, { detailId = null }, onManage,
            onRecordService = { detailId = null; onRecordService(reminder) })
    }
}
