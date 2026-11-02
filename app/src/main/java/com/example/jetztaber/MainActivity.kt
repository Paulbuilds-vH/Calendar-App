package com.example.jetztaber


import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jetztaber.ui.theme.JetztAberTheme
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
import kotlin.text.isNotBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.jetztaber.data.model.CalendarEvent
import com.example.jetztaber.data.model.EventType
import android.Manifest
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.TimeUnit


// --- Screen Navigation ---
enum class Screen {
    CALENDAR_OVERVIEW,
    DAY_DETAIL
}


class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                scheduleEventWorker()
            } else {
                Log.w("MainActivity", "POST_NOTIFICATIONS permission denied by user.")
                try {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        "Notification permission denied. Reminders will not be shown.",
                        Snackbar.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error showing Snackbar, likely no root view for findViewById: $e")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JetztAberTheme {
                CalendarApp()
            }
        }

        askNotificationPermission()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            scheduleEventWorker()
        }


        intent?.let { handleNotificationIntent(it) }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                   // Permission is already granted. Worker can be scheduled.
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    try {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            "This app needs notification permission to send you event reminders.",
                            Snackbar.LENGTH_INDEFINITE
                        ).setAction("OK") {
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }.show()
                    } catch (e: Exception) {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                else -> {
                     requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }


    private fun scheduleEventWorker() {
        val eventCheckWorkRequest =
            PeriodicWorkRequestBuilder<EventNotificationWorker>(15, TimeUnit.MINUTES)
                .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            EventNotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            eventCheckWorkRequest
        )
        Log.d("MainActivity", "EventNotificationWorker scheduled or kept if already running.")
    }


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let { handleNotificationIntent(it) }
    }

    private fun handleNotificationIntent(intent: Intent) {
        val eventId = intent.getStringExtra("EVENT_ID_FROM_NOTIFICATION")
        if (eventId != null) {
            intent.removeExtra("EVENT_ID_FROM_NOTIFICATION")
        }
    }

    override fun onResume() {
        super.onResume()
        intent?.let {
            if (it.hasExtra("EVENT_ID_FROM_NOTIFICATION")) {
                handleNotificationIntent(it)
            }
        }
    }
}


@Composable
fun CalendarApp(persistenceViewModel: CalendarPersistenceViewModel = viewModel()) {
    val loadedPersistedDate by persistenceViewModel.persistedSelectedDate.collectAsState()
    val userEventsFromVm by persistenceViewModel.allUserEventsFlow.collectAsState()
    var currentScreen by remember { mutableStateOf(Screen.CALENDAR_OVERVIEW) }
    var dayDetailSelectedCalendar by remember { mutableStateOf<Calendar?>(null) }
    var showAddEventDialog by remember { mutableStateOf(false) }
    var newEventName by remember { mutableStateOf("") }
    var newEventDescription by remember { mutableStateOf("") }
    var newEventType by remember { mutableStateOf(EventType.APPOINTMENT) }
    var newEventDate by remember { mutableStateOf<Calendar?>(null) }


    val initialCalendarDisplay = remember(loadedPersistedDate) {
        loadedPersistedDate ?: Calendar.getInstance()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (currentScreen) {
            Screen.CALENDAR_OVERVIEW -> {
                NavigableCalendarView(
                    initialYear = initialCalendarDisplay.get(Calendar.YEAR),
                    initialMonth = initialCalendarDisplay.get(Calendar.MONTH),
                    userEvents = userEventsFromVm,
                    onDateSelectedForDetail = { selectedCalendar, _ ->
                        dayDetailSelectedCalendar = selectedCalendar
                        currentScreen = Screen.DAY_DETAIL
                        persistenceViewModel.saveDateAsLastSelected(selectedCalendar)
                    },
                    modifier = Modifier.padding(8.dp)
                )
            }
            Screen.DAY_DETAIL -> {
                dayDetailSelectedCalendar?.let { currentSelectedCal ->
                    // 1. Get user-defined events from the ViewModel for the selected date
                    val userDefinedEvents = userEventsFromVm.filter { event ->
                        event.occursOnDate(currentSelectedCal)
                    }

                    val holidayEventsForDetail = mutableListOf<CalendarEvent>()
                    val holidayInfo = GermanHolidayProviderNRW.getHolidayInfo(currentSelectedCal)
                    if (holidayInfo.first) {
                        holidayEventsForDetail.add(
                            CalendarEvent(
                                id = UUID.randomUUID().toString(),
                                type = EventType.HOLIDAY,
                                name = holidayInfo.second ?: "Holiday",
                                description = null,
                                calendarInstance = currentSelectedCal.clone() as Calendar,
                                isRecurringAnnually = true,
                                recurringMonth = currentSelectedCal.get(Calendar.MONTH),
                                recurringDayOfMonth = currentSelectedCal.get(Calendar.DAY_OF_MONTH)
                            )
                        )
                    }

                    // 3. Combine both lists
                    val allEventsForDayDetail = (userDefinedEvents + holidayEventsForDetail)
                        .distinctBy { it.id }
                        .sortedBy { it.name }

                    Log.d("DayDetailScreen", "Displaying ${allEventsForDayDetail.size} events for ${currentSelectedCal.time}. User: ${userDefinedEvents.size}, Holiday: ${holidayEventsForDetail.size}")

                    DayDetailScreen(
                        selectedDateCalendar = currentSelectedCal,
                        eventsForDay = allEventsForDayDetail, // Pass the combined and sorted list
                        onNavigateBack = {
                            currentScreen = Screen.CALENDAR_OVERVIEW
                            dayDetailSelectedCalendar = null
                        },
                        onAddEventClicked = { selectedCal ->
                            newEventDate = selectedCal.clone() as Calendar
                            newEventName = ""
                            newEventDescription = ""
                            newEventType = EventType.APPOINTMENT
                            showAddEventDialog = true
                        },
                        onDeleteEventClicked = { eventToDelete ->
                            if (userEventsFromVm.any { it.id == eventToDelete.id }) {
                                persistenceViewModel.deleteUserEvent(eventToDelete)
                            } else {
                                Log.w("DayDetailScreen", "Attempted to delete a non-user event (e.g., provider holiday): ${eventToDelete.name}")
                            }
                        }
                    )
                } ?: run {
                    LaunchedEffect(Unit) {
                        currentScreen = Screen.CALENDAR_OVERVIEW
                    }
                }
            }
        }

        if (showAddEventDialog) {
            AddEventDialog(
                eventName = newEventName,
                onEventNameChange = { newEventName = it },
                eventDescription = newEventDescription,
                onEventDescriptionChange = { newEventDescription = it },
                eventType = newEventType,
                onEventTypeChange = { newEventType = it },
                eventDate = newEventDate ?: Calendar.getInstance(),
                onDateChange = { selectedCalendar ->
                    newEventDate = selectedCalendar
                },
                onDismissRequest = { showAddEventDialog = false },
                onConfirm = {
                    val dateForEvent = newEventDate
                    if (newEventName.isNotBlank() && dateForEvent != null) {
                        val isBirthday = newEventType == EventType.BIRTHDAY
                        val eventRecurringMonth = if (isBirthday) dateForEvent.get(Calendar.MONTH) else null
                        val eventRecurringDay = if (isBirthday) dateForEvent.get(Calendar.DAY_OF_MONTH) else null

                        val eventToAdd = CalendarEvent(
                            type = newEventType,
                            name = newEventName,
                            description = newEventDescription.takeIf { it.isNotBlank() },
                            calendarInstance = dateForEvent.clone() as Calendar,
                            isRecurringAnnually = isBirthday,
                            recurringMonth = eventRecurringMonth,
                            recurringDayOfMonth = eventRecurringDay
                        )
                        Log.d("CalendarApp", "Adding event: ${eventToAdd.name}")
                        persistenceViewModel.addUserEvent(eventToAdd)
                        showAddEventDialog = false
                        // Reset dialog fields
                        newEventName = ""
                        newEventDescription = ""
                        newEventType = EventType.APPOINTMENT
                        newEventDate = null
                    }
                }
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    eventName: String,
    onEventNameChange: (String) -> Unit,
    eventDescription: String,
    onEventDescriptionChange: (String) -> Unit,
    eventType: EventType, // Current selected EventType
    onEventTypeChange: (EventType) -> Unit, // Callback for when EventType changes
    eventDate: Calendar, // The current date for the event
    onDateChange: (newDate: Calendar) -> Unit, // Callback when a new date is confirmed
    showDatePickerButton: Boolean = true,
    onDismissRequest: () C:~$ snap list | grep android
android-studio             2026.2.2.8-rabbit2              248       latest/stable    snapcrafters*  classic
-> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    var showMaterialDatePicker by remember { mutableStateOf(false) }

    val currentLocale = Locale.getDefault()

    val datePickerState = remember(eventDate.timeInMillis, currentLocale) {
        DatePickerState(
            initialSelectedDateMillis = eventDate.timeInMillis,
            initialDisplayMode = DisplayMode.Picker,
            locale = currentLocale,
            yearRange = (1900..2100)
        )
    }

    if (showMaterialDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showMaterialDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMaterialDatePicker = false
                        datePickerState.selectedDateMillis?.let { millis ->
                            val newSelectedCalendar = Calendar.getInstance().apply {
                                timeInMillis = millis
                            }
                            onDateChange(newSelectedCalendar)
                        }
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showMaterialDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Add New Event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Date: ${SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(eventDate.time)}")

                if (showDatePickerButton) {
                    Button(onClick = {
                        showMaterialDatePicker = true
                    }) {
                        Text("Change Date")
                    }
                }

                OutlinedTextField(
                    value = eventName,
                    onValueChange = onEventNameChange,
                    label = { Text("Event Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = eventDescription,
                    onValueChange = onEventDescriptionChange,
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // --- REFACTORED EVENT TYPE SELECTION using ExposedDropdownMenuBox ---
                val selectableEventTypes = remember { EventType.userSelectableTypes() }
                var isEventTypeDropdownExpanded by remember { mutableStateOf(false) } // State for the dropdown's expansion

                ExposedDropdownMenuBox(
                    expanded = isEventTypeDropdownExpanded,
                    onExpandedChange = {
                        isEventTypeDropdownExpanded = !isEventTypeDropdownExpanded
                        Log.d("AddEventDialog", "ExposedDropdownMenuBox onExpandedChange: $isEventTypeDropdownExpanded")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField( // This is the anchor
                        value = eventType.displayName,
                        onValueChange = {}, // Not user-editable text
                        readOnly = true,
                        label = { Text("Event Type") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isEventTypeDropdownExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isEventTypeDropdownExpanded,
                        onDismissRequest = {
                            isEventTypeDropdownExpanded = false
                            Log.d("AddEventDialog", "ExposedDropdownMenu onDismissRequest")
                        }
                    ) {
                        selectableEventTypes.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption.displayName) },
                                onClick = {
                                    onEventTypeChange(selectionOption)
                                    isEventTypeDropdownExpanded = false // Close dropdown after selection
                                    Log.d("AddEventDialog", "DropdownMenuItem '${selectionOption.displayName}' clicked")
                                }
                            )
                        }
                    }
                }
                // --- END OF REFACTORED EVENT TYPE SELECTION ---
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (eventName.isNotBlank()) {
                        onConfirm()
                    } else {
                        Toast.makeText(context, "Event name cannot be empty", Toast.LENGTH_SHORT).show()
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
        }
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    selectedDateCalendar: Calendar,
    eventsForDay: List<CalendarEvent>,
    onNavigateBack: () -> Unit,
    onAddEventClicked: (Calendar) -> Unit,
    onDeleteEventClicked: (CalendarEvent) -> Unit, // Passed from CalendarApp
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()) }
    var eventToDeleteConfirmation by remember { mutableStateOf<CalendarEvent?>(null) } // To trigger confirmation dialog

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Day Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Calendar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddEventClicked(selectedDateCalendar.clone() as Calendar) },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add New Event")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = dateFormatter.format(selectedDateCalendar.time),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (eventsForDay.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No events scheduled for this day.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = "Events:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(eventsForDay, key = { it.id }) { event ->
                        EventItem(
                            event = event,
                            onDeleteClicked = {
                                eventToDeleteConfirmation = event
                            }
                        )
                    }
                }
            }
        }

        // Confirmation Dialog for Deleting Event
        eventToDeleteConfirmation?.let { event ->
            AlertDialog(
                onDismissRequest = { eventToDeleteConfirmation = null }, // Dismiss dialog
                title = { Text("Confirm Delete") },
                text = { Text("Wirklisch löschen? \"${event.name}\"?") },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteEventClicked(event) // Call the actual delete function
                            eventToDeleteConfirmation = null // Dismiss dialog
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { eventToDeleteConfirmation = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun EventItem(
    event: CalendarEvent,
    onDeleteClicked: () -> Unit, // Callback for when delete is clicked
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row( // Use Row to place content and delete icon side-by-side
            modifier = Modifier
                .padding(
                    start = 12.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                    end = 4.dp
                ) // Adjust padding for icon
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically // Align items vertically
        ) {
            Column(
                modifier = Modifier.weight(1f) // Column takes available space
            ) {
                Text(
                    text = event.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Type: ${event.type}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                event.description?.let {
                    if (it.isNotBlank()) {
                        Text(
                            text = "Description: $it",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            // Delete Button
            IconButton(
                onClick = onDeleteClicked, // Trigger the callback
                enabled = event.type != EventType.HOLIDAY,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete event ${event.name}",
                    tint = MaterialTheme.colorScheme.error // Use error color for delete
                )
            }
        }
    }
}



data class WeekData(
    val weekOfYear: Int,
    val days: List<DayOfMonth>
)


// --- Day Representation ---
data class DayOfMonth(
    val day: Int,
    val isCurrentMonth: Boolean,
    val calendarInstance: Calendar,
    val events: MutableList<CalendarEvent> = mutableListOf()
)
@Composable
fun NavigableCalendarView(
    modifier: Modifier = Modifier,
    initialYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    initialMonth: Int = Calendar.getInstance().get(Calendar.MONTH), // 0-11
    userEvents: List<CalendarEvent>, // Stays
    onDateSelectedForDetail: (selectedDayCal: Calendar, events: List<CalendarEvent>) -> Unit // Stays
) {
    val desiredFirstDayOfWeek = Calendar.MONDAY
    var currentYear by remember(initialYear) { mutableStateOf(initialYear) }
    var currentMonth by remember(initialMonth) { mutableStateOf(initialMonth) }

    // This state is for visual highlighting of the selected cell
    var highlightedDateCalendar by remember { mutableStateOf<Calendar?>(null) }

    Log.d("NavigableCV", "Composing/Recomposing: initialY=$initialYear, initialM=$initialMonth. currentY=$currentYear, currentM=$currentMonth")
    val weeksInMonthData = remember(currentYear, currentMonth, desiredFirstDayOfWeek, userEvents) { // ADD userEvents to remember keys
        generateWeeksForMonth(currentYear, currentMonth, desiredFirstDayOfWeek, userEvents) // PASS userEvents
    }

    val headerCalendar = Calendar.getInstance().apply {
        clear()
        set(Calendar.YEAR, currentYear)
        set(Calendar.MONTH, currentMonth)
    }

    // Calendar instances for checking if a clicked day is in the previous/next month
    val firstDayOfMonthCalendar = remember(currentYear, currentMonth) {
        Calendar.getInstance().apply {
            clear()
            set(currentYear, currentMonth, 1)
        }
    }
    val lastDayOfMonthCalendar = remember(currentYear, currentMonth) {
        Calendar.getInstance().apply {
            clear()
            set(currentYear, currentMonth, 1)
            add(Calendar.MONTH, 1)
            add(Calendar.DAY_OF_MONTH, -1)
        }
    }

    Column(modifier = modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
        // Header: Month/Year and Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, currentYear)
                    set(Calendar.MONTH, currentMonth)
                    add(Calendar.MONTH, -1)
                }
                currentMonth = cal.get(Calendar.MONTH)
                currentYear = cal.get(Calendar.YEAR)
                highlightedDateCalendar = null
            }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Previous Month", tint = MaterialTheme.colorScheme.primary)
            }

            Text(
                text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(headerCalendar.time),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            IconButton(onClick = {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, currentYear)
                    set(Calendar.MONTH, currentMonth)
                    add(Calendar.MONTH, 1)
                }
                currentMonth = cal.get(Calendar.MONTH)
                currentYear = cal.get(Calendar.YEAR)
                highlightedDateCalendar = null
            }) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "Next Month", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Days of the week header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "KW",
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(0.6f),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = Color.Gray
            )
            val daysOfWeek = getSortedDaysOfWeek(desiredFirstDayOfWeek)
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Calendar Rows
        if (weeksInMonthData.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(weeksInMonthData, key = { week -> week.weekOfYear * 1000 + (week.days.firstOrNull()?.calendarInstance?.timeInMillis ?: 0) }) { weekData ->
                    WeekRow(
                        weekData = weekData,
                        highlightedDate = highlightedDateCalendar,
                        onDayClick = { clickedDayOfMonthObject ->
                            highlightedDateCalendar = clickedDayOfMonthObject.calendarInstance

                            // *** CALL THE CALLBACK ***
                            onDateSelectedForDetail(
                                clickedDayOfMonthObject.calendarInstance,
                                clickedDayOfMonthObject.events
                            )

                            if (!clickedDayOfMonthObject.isCurrentMonth) {
                                val clickedCal = clickedDayOfMonthObject.calendarInstance
                                if (clickedCal.before(firstDayOfMonthCalendar)) {
                                    currentMonth = clickedCal.get(Calendar.MONTH)
                                    currentYear = clickedCal.get(Calendar.YEAR)
                                    highlightedDateCalendar = null
                                } else if (clickedCal.after(lastDayOfMonthCalendar)) {
                                    currentMonth = clickedCal.get(Calendar.MONTH)
                                    currentYear = clickedCal.get(Calendar.YEAR)
                                    highlightedDateCalendar = null
                                }
                            }
                        }
                    )
                }
            }
        } else {
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Loading calendar...")
            }
        }
        Button(
            onClick = {
                val today = Calendar.getInstance()
                currentYear = today.get(Calendar.YEAR)
                currentMonth = today.get(Calendar.MONTH)
                highlightedDateCalendar = today


                val todayEvents = mutableListOf<CalendarEvent>()
                val holidayInfo = GermanHolidayProviderNRW.getHolidayInfo(today)
                if (holidayInfo.first) {
                    todayEvents.add(CalendarEvent(type = EventType.HOLIDAY, name = holidayInfo.second ?: "Holiday", calendarInstance = today.clone() as Calendar))
                }
                userEvents.filter { it.calendarInstance.isSameDay(today) }.forEach { todayEvents.add(it) }
                onDateSelectedForDetail(today, todayEvents)

            },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 16.dp)
        ) {
            Text("Go to Today")
        }
    }
}
object GermanHolidayProviderNRW { // Name angepasst zur Verdeutlichung

    // Feste Feiertage für NRW (bundesweite + Allerheiligen)
    private val fixedHolidaysNRW = mapOf(
        Pair(Calendar.JANUARY, 1) to "Neujahr",
        Pair(Calendar.MAY, 1) to "Tag der Arbeit",
        Pair(Calendar.OCTOBER, 3) to "Tag der Deutschen Einheit",
        Pair(Calendar.NOVEMBER, 1) to "Allerheiligen", // NRW-spezifisch
        Pair(Calendar.DECEMBER, 24)to "Weihnachten",
        Pair(Calendar.DECEMBER, 25) to "1. Weihnachtstag",
        Pair(Calendar.DECEMBER, 26) to "2. Weihnachtstag"
    )

    private fun getEasterSunday(year: Int): Calendar {
        val cal = Calendar.getInstance()
        cal.clear() // Wichtig, um alte Werte zu löschen

        if (year < 1583) {
            Log.w("HolidayCalcNRW", "Easter calculation for year $year might be inaccurate (pre-Gregorian).")
        }
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31 // Month (3 = March, 4 = April)
        val day = ((h + l - 7 * m + 114) % 31) + 1 // Day of the month

        cal.set(year, month - 1, day) // Calendar month is 0-indexed
        return cal
    }


    fun getHolidayInfo(dateCal: Calendar): Pair<Boolean, String?> {
        val year = dateCal.get(Calendar.YEAR)
        val month = dateCal.get(Calendar.MONTH) // 0-indexed
        val day = dateCal.get(Calendar.DAY_OF_MONTH)

        // 1. Feste Feiertage für NRW prüfen
        fixedHolidaysNRW[Pair(month, day)]?.let { name ->
            return Pair(true, name)
        }

        // 2. Bewegliche Feiertage (Ostern-abhängig) für NRW prüfen
        val easterSunday = getEasterSunday(year)

        // Karfreitag (Good Friday): 2 Tage vor Ostersonntag
        val goodFriday = easterSunday.clone() as Calendar
        goodFriday.add(Calendar.DAY_OF_MONTH, -2)
        if (goodFriday.isSameDay(dateCal)) return Pair(true, "Karfreitag")

        // Ostersonntag (Easter Sunday) - explizit gewünscht
        if (easterSunday.isSameDay(dateCal)) return Pair(true, "Ostersonntag")

        // Ostermontag (Easter Monday): 1 Tag nach Ostersonntag
        val easterMonday = easterSunday.clone() as Calendar
        easterMonday.add(Calendar.DAY_OF_MONTH, 1)
        if (easterMonday.isSameDay(dateCal)) return Pair(true, "Ostermontag")

        // Christi Himmelfahrt (Ascension Day): 39 Tage nach Ostersonntag
        val ascensionDay = easterSunday.clone() as Calendar
        ascensionDay.add(Calendar.DAY_OF_MONTH, 39)
        if (ascensionDay.isSameDay(dateCal)) return Pair(true, "Christi Himmelfahrt")

        // Pfingstsonntag (Pentecost Sunday) - explizit gewünscht
        val pentecostSunday = easterSunday.clone() as Calendar
        pentecostSunday.add(Calendar.DAY_OF_MONTH, 49)
        if (pentecostSunday.isSameDay(dateCal)) return Pair(true, "Pfingstsonntag")

        // Pfingstmontag (Whit Monday): 50 Tage nach Ostersonntag
        val whitMonday = easterSunday.clone() as Calendar
        whitMonday.add(Calendar.DAY_OF_MONTH, 50)
        if (whitMonday.isSameDay(dateCal)) return Pair(true, "Pfingstmontag")

        // Fronleichnam (Corpus Christi): 60 Tage nach Ostersonntag - Feiertag in NRW
        val corpusChristi = easterSunday.clone() as Calendar
        corpusChristi.add(Calendar.DAY_OF_MONTH, 60)
        if (corpusChristi.isSameDay(dateCal)) return Pair(true, "Fronleichnam")

        return Pair(false, null) // Kein Feiertag in NRW an diesem Datum
    }
}


@Composable
fun WeekRow(
    weekData: WeekData,
    highlightedDate: Calendar?,
    onDayClick: (dayOfMonth: DayOfMonth) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Week Number Cell
        Box(
            modifier = Modifier
                .weight(0.6f)
                .aspectRatio(1f)
                .padding(horizontal = 2.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = weekData.weekOfYear.toString(),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, // Use theme color
                textAlign = TextAlign.Center
            )
        }

        weekData.days.forEach { dayOfMonth ->
            DayCell(
                day = dayOfMonth.day,
                isSelected = highlightedDate?.isSameDay(dayOfMonth.calendarInstance) == true,
                isCurrentMonth = dayOfMonth.isCurrentMonth,
                isToday = Calendar.getInstance().isSameDay(dayOfMonth.calendarInstance),
                events = dayOfMonth.events,
                onDateClick = { onDayClick(dayOfMonth) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
@Composable
fun DayCell(
    day: Int,
    isSelected: Boolean,
    isCurrentMonth: Boolean,
    isToday: Boolean,
    events: List<CalendarEvent>,
    onDateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        isToday && isCurrentMonth -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        isCurrentMonth -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isCurrentMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }


    val hasHoliday = events.any { it.type == EventType.HOLIDAY }
    val hasAppointment = events.any { it.type == EventType.APPOINTMENT }
    val hasBirthday = events.any { it.type == EventType.BIRTHDAY }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .clickable(enabled = isCurrentMonth) { onDateClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day.toString(),
                color = textColor,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                fontWeight = if (isToday && isCurrentMonth) FontWeight.Bold else FontWeight.Normal
            )

            if (isCurrentMonth) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.height(5.dp)
                ) {
                    if (hasHoliday) {
                        EventIndicatorDot(color = MaterialTheme.colorScheme.tertiary) // Example color
                    }
                    if (hasAppointment) {
                        EventIndicatorDot(color = MaterialTheme.colorScheme.primary) // Example color
                    }
                    if (hasBirthday) {
                        EventIndicatorDot(color = Color(0xFFFFA726)) // Example orange for Birthday
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(7.dp))
            }
        }
    }
}

@Composable
private fun EventIndicatorDot(color: Color) {
    Box(
        modifier = Modifier
            .size(5.dp)
            .background(color, CircleShape)
    )
}
fun generateWeeksForMonth(
    year: Int,
    month: Int,
    firstDayOfWeekPreference: Int,
    userEvents: List<CalendarEvent>
): List<WeekData> {
    val weeksList = mutableListOf<WeekData>()
    val currentMonthCalendar = Calendar.getInstance().apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
        this.firstDayOfWeek = firstDayOfWeekPreference
    }

    val daysInMonth = currentMonthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayActualWeekDay = currentMonthCalendar.get(Calendar.DAY_OF_WEEK)

    var leadingCells = firstDayActualWeekDay - firstDayOfWeekPreference
    if (leadingCells < 0) {
        leadingCells += 7
    }

    val lastDayOfMonthCal = Calendar.getInstance().apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, daysInMonth)
        this.firstDayOfWeek = firstDayOfWeekPreference
    }
    val lastDayActualWeekDay = lastDayOfMonthCal.get(Calendar.DAY_OF_WEEK)
    var trailingCells = (firstDayOfWeekPreference + 6 - lastDayActualWeekDay) % 7
    if (trailingCells < 0) trailingCells += 7 // Ensure positive for total calculation

    val totalDaysToDisplay = leadingCells + daysInMonth + trailingCells
    val numWeeksToDisplay = if (totalDaysToDisplay > 0) (totalDaysToDisplay + 6) / 7 else 0


    val dayIteratorCalendar = Calendar.getInstance().apply {
        clear()
        timeInMillis = currentMonthCalendar.timeInMillis
        add(Calendar.DAY_OF_MONTH, -leadingCells)
    }

    for (weekIndex in 0 until numWeeksToDisplay) {
        val currentDaysInRow = mutableListOf<DayOfMonth>()
        var representativeDateForRow: Calendar? = null

        for (dayIndexInWeek in 0 until 7) {
            val currentCellCalendar = dayIteratorCalendar.clone() as Calendar
            if (representativeDateForRow == null) {
                representativeDateForRow = currentCellCalendar.clone() as Calendar
            }

            val dayEvents = mutableListOf<CalendarEvent>()

            val holidayInfo = GermanHolidayProviderNRW.getHolidayInfo(currentCellCalendar)
            if (holidayInfo.first) {
                dayEvents.add(
                    CalendarEvent(
                        type = EventType.HOLIDAY,
                        name = holidayInfo.second ?: "Holiday",
                        description = null,
                        calendarInstance = currentCellCalendar.clone() as Calendar,
                        isRecurringAnnually = false,
                        recurringMonth = null,
                        recurringDayOfMonth = null
                    )
                )
            }


            userEvents
                .filter { event -> event.occursOnDate(currentCellCalendar) }
                .forEach { event ->
                    if (event.isRecurringAnnually) {
                        val displayEventInstance = event.copy(calendarInstance = (event.calendarInstance.clone() as Calendar).apply {
                                set(Calendar.YEAR, currentCellCalendar.get(Calendar.YEAR))
                                event.recurringMonth?.let { monthValue -> set(Calendar.MONTH, monthValue) }
                                event.recurringDayOfMonth?.let { dayValue -> set(Calendar.DAY_OF_MONTH, dayValue) }
                            }
                        )
                        dayEvents.add(displayEventInstance)
                    } else {
                        // For non-recurring events
                        dayEvents.add(event)
                    }
                }
            // --- End of event collection ---

            currentDaysInRow.add(
                DayOfMonth(
                    day = currentCellCalendar.get(Calendar.DAY_OF_MONTH),
                    isCurrentMonth = (currentCellCalendar.get(Calendar.YEAR) == year &&
                            currentCellCalendar.get(Calendar.MONTH) == month),
                    calendarInstance = currentCellCalendar.clone() as Calendar,
                    events = ArrayList(dayEvents.distinctBy { it.id }) // Ensure distinct events and pass as ArrayList if WeekData expects it
                )
            )
            dayIteratorCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        if (representativeDateForRow != null) {
            val (weekNumberFromCustomRule, yearOfWeekFromCustomRule) = getWeekAndYear_Custom(representativeDateForRow!!)
            var weekNumberToDisplay = weekNumberFromCustomRule
            val viewingMonth = month // current month being generated
            if (viewingMonth == Calendar.DECEMBER && yearOfWeekFromCustomRule > year) {
                val dec31Cal = Calendar.getInstance().apply { clear(); set(year, Calendar.DECEMBER, 31) }
                val (lastWeekOfCurrentYear, _) = getWeekAndYear_Custom(dec31Cal)
                weekNumberToDisplay = lastWeekOfCurrentYear
            }
            weeksList.add(WeekData(weekNumberToDisplay, ArrayList(currentDaysInRow)))
        }
    }
    return weeksList
}


fun getWeekAndYear_Custom(dateCalInput: Calendar): Pair<Int, Int> {
    val dateCal = dateCalInput.clone() as Calendar
    dateCal.firstDayOfWeek = Calendar.MONDAY
    dateCal.minimalDaysInFirstWeek = 4

    val week = dateCal.get(Calendar.WEEK_OF_YEAR)
    var year = dateCal.get(Calendar.YEAR)

    if (week >= 52 && dateCal.get(Calendar.MONTH) == Calendar.JANUARY) {
        year--
    }
    else if (week == 1 && dateCal.get(Calendar.MONTH) == Calendar.DECEMBER) {
        year++
    }

    return Pair(week, year)
}



fun Calendar.isSameDay(other: Calendar): Boolean {
    return this.get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
            this.get(Calendar.MONTH) == other.get(Calendar.MONTH) &&
            this.get(Calendar.DAY_OF_MONTH) == other.get(Calendar.DAY_OF_MONTH)
}


fun getSortedDaysOfWeek(firstDayOfWeek: Int): List<String> {
    val sdf = SimpleDateFormat("E", Locale.getDefault())
    val days = mutableListOf<String>()
    val cal = Calendar.getInstance()
    cal.firstDayOfWeek = firstDayOfWeek

    cal.set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
    for (i in 0..6) {
        days.add(sdf.format(cal.time).take(3))
        cal.add(Calendar.DAY_OF_WEEK, 1)
    }
    return days
}


// --- Preview ---
// --- Preview ---
@Preview(showBackground = true, widthDp = 380)
@Composable
fun CalendarWithWeekNumbersPreview() {
    Box(modifier = Modifier.background(Color.White)) {
        NavigableCalendarView(
            userEvents = emptyList(), // Provide an empty list or sample events for preview
            onDateSelectedForDetail = { calendar, events ->
                val sdf = SimpleDateFormat("yyyy-MM-dd (EEE)", Locale.getDefault())
                println("Preview: Date selected: ${sdf.format(calendar.time)}, Events: ${events.size}")
            }
        )
    }
    // }
}

