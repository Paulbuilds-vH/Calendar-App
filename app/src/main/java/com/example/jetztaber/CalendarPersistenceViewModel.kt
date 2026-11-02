package com.example.jetztaber

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jetztaber.data.db.AppDatabase
import com.example.jetztaber.data.db.CalendarEventDao
import com.example.jetztaber.data.model.CalendarEvent
import com.example.jetztaber.data.model.EventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Calendar

class CalendarPersistenceViewModel(application: Application) : AndroidViewModel(application) {

    private val eventDao: CalendarEventDao
    private val LAST_SELECTED_DATE_ID = "app_last_selected_main_date"

    private val _persistedSelectedDate = MutableStateFlow<Calendar?>(null)
    val persistedSelectedDate: StateFlow<Calendar?> = _persistedSelectedDate.asStateFlow()

    // --- Flow for ALL user events ---
    private val _allUserEventsFlow = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val allUserEventsFlow: StateFlow<List<CalendarEvent>> = _allUserEventsFlow.asStateFlow()
    // --- End Flow for ALL user events ---

    init {
        val database = AppDatabase.getDatabase(application)
        eventDao = database.calendarEventDao()
        Log.d("ViewModel", "ViewModel INIT block started.")
        loadLastSelectedDate()
        loadAllUserEvents()
    }

    private fun loadLastSelectedDate() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("ViewModel", "Attempting to load last selected date...")
            val event = eventDao.getEventById(LAST_SELECTED_DATE_ID).firstOrNull()
            _persistedSelectedDate.value = event?.calendarInstance
            Log.d("ViewModel", "Loaded last selected date: ${event?.calendarInstance?.time}, Event found: $event")
        }
    }

    fun saveDateAsLastSelected(selectedDate: Calendar) {
        viewModelScope.launch(Dispatchers.IO) {
            val eventToSave = CalendarEvent(
                id = LAST_SELECTED_DATE_ID,
                type = EventType.APP_STATE,
                name = "Last Selected Date",
                description = null,
                calendarInstance = selectedDate.clone() as Calendar,
                isRecurringAnnually = false,
                recurringMonth = null,
                recurringDayOfMonth = null
            )
            Log.d("ViewModel", "Attempting to save event: $eventToSave")
            eventDao.insertEvent(eventToSave)
            _persistedSelectedDate.value = selectedDate
            Log.d("ViewModel", "Saved last selected date: ${selectedDate.time}")
        }
    }

    fun addUserEvent(event: CalendarEvent) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("ViewModel", "Attempting to insert user event: $event")
            try {
                eventDao.insertEvent(event) // This saves it to the database
                Log.d("ViewModel", "User event insert successful for ID: ${event.id}")
            } catch (e: Exception) {
                Log.e("ViewModel", "Error inserting user event: ${event.id}", e)
            }
        }
    }

    private fun loadAllUserEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("ViewModel", "Starting to collect all user events from DB...")
            eventDao.getAllEvents().collect { eventsFromDb ->
                val actualUserEvents = eventsFromDb.filter { it.id != LAST_SELECTED_DATE_ID }
                _allUserEventsFlow.value = actualUserEvents
                Log.d("ViewModel", "Loaded and emitting ${actualUserEvents.size} user events.")
            }
        }
    }

    fun deleteUserEvent(event: CalendarEvent) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("ViewModel", "Attempting to delete user event: ${event.id}")
            try {
                eventDao.deleteEvent(event)
                Log.d("ViewModel", "User event delete successful for ID: ${event.id}")
            } catch (e: Exception) {
                Log.e("ViewModel", "Error deleting user event: ${event.id}", e)
            }
        }
    }
    // --- End function to delete an event ---
}