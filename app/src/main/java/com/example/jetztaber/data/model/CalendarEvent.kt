package com.example.jetztaber.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.util.Calendar
import java.util.UUID
import com.example.jetztaber.Converters

// --- Event Types ---
enum class EventType(val displayName: String) {
    APPOINTMENT("Termin"),
    BIRTHDAY("Geburtstag"),


    HOLIDAY("Feiertag"),
    USER_EVENT("Benutzerdefiniert"),
    OTHER("Sonstiges"),
    GENERAL("Allgemein"),
    APP_STATE("App-Status");

    companion object {
        // This function returns only the types you want to offer for user selection
        fun userSelectableTypes(): List<EventType> = listOf(APPOINTMENT, BIRTHDAY)

    }
}

@Entity(tableName = "calendar_events")
@TypeConverters(Converters::class)
data class CalendarEvent(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    var type: EventType, // This will be handled by a TypeConverter
    var name: String,
    var description: String? = null,
    var calendarInstance: Calendar, // This will be handled by a TypeConverter
    val isRecurringAnnually: Boolean = false,
    val recurringMonth: Int? = null,
    val recurringDayOfMonth: Int? = null
) {
    fun occursOnDate(dateToDisplay: Calendar): Boolean {
        return if (isRecurringAnnually && recurringMonth != null && recurringDayOfMonth != null) {
            dateToDisplay.get(Calendar.MONTH) == recurringMonth &&
                    dateToDisplay.get(Calendar.DAY_OF_MONTH) == recurringDayOfMonth
        } else {
            calendarInstance.get(Calendar.YEAR) == dateToDisplay.get(Calendar.YEAR) &&
                    calendarInstance.get(Calendar.MONTH) == dateToDisplay.get(Calendar.MONTH) &&
                    calendarInstance.get(Calendar.DAY_OF_MONTH) == dateToDisplay.get(Calendar.DAY_OF_MONTH)
        }
    }
}