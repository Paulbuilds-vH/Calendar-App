package com.example.jetztaber
import com.example.jetztaber.data.model.EventType
import androidx.room.TypeConverter
import java.util.Calendar

class Converters {

    // --- Calendar Converters ---

    @TypeConverter
    fun fromTimestamp(value: Long?): Calendar? {
        return value?.let { timestamp ->
            Calendar.getInstance().apply {
                timeInMillis = timestamp
            }
        }
    }

    @TypeConverter
    fun calendarToTimestamp(calendar: Calendar?): Long? {
        return calendar?.timeInMillis
    }

    // --- EventType Converters ---

    @TypeConverter
    fun fromStringToEventType(value: String?): EventType? {
        return value?.let { enumName ->
            try {
                EventType.valueOf(enumName)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    @TypeConverter
    fun eventTypeToString(eventType: EventType?): String? {
        return eventType?.name
    }
}