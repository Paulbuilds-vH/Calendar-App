package com.example.jetztaber.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.jetztaber.data.model.CalendarEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CalendarEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<CalendarEvent>)

    @Update
    suspend fun updateEvent(event: CalendarEvent)

    @Delete
    suspend fun deleteEvent(event: CalendarEvent)

    @Query("SELECT * FROM calendar_events WHERE id = :eventId")
    fun getEventById(eventId: String): Flow<CalendarEvent?>

    @Query("SELECT * FROM calendar_events ORDER BY calendarInstance ASC")
    fun getAllEvents(): Flow<List<CalendarEvent>>


    @Query("DELETE FROM calendar_events")
    suspend fun deleteAllEvents()

    @Query("SELECT * FROM calendar_events WHERE isRecurringAnnually = 0 AND calendarInstance BETWEEN :startTimeMillis AND :endTimeMillis ORDER BY calendarInstance ASC")
    suspend fun getNonRecurringEventsBetween(startTimeMillis: Long, endTimeMillis: Long): List<CalendarEvent>

    @Query("SELECT * FROM calendar_events WHERE isRecurringAnnually = 1 ORDER BY recurringMonth, recurringDayOfMonth")
    suspend fun getAllRecurringAnnualEvents(): List<CalendarEvent>

}