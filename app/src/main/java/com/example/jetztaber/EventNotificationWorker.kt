package com.example.jetztaber

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.jetztaber.data.db.AppDatabase // Corrected import for AppDatabase
import com.example.jetztaber.data.model.CalendarEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.jetztaber.data.model.EventType // Ensure EventType is imported
import java.util.concurrent.TimeUnit


class EventNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val eventDao = AppDatabase.getDatabase(applicationContext).calendarEventDao()

    companion object {
        const val WORK_NAME = "com.example.jetztaber.worker.EventNotificationWorker"
        private const val NOTIFICATION_CHANNEL_ID = "event_reminders_channel"
        private const val TAG = "EventNotificationWorker"
        // Define constants for notification time
        private const val NOTIFICATION_HOUR_OF_DAY = 12 // Noon
        private const val NOTIFICATION_MINUTE = 0
        private const val NOON_CHECK_WINDOW_MINUTES = 30 // +/- 30 minutes around noon (e.g., 11:30 - 12:30)
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker started. Current time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Calendar.getInstance().time)}")
        try {
            // --- Permission Check (Good to have early) ---
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Worker stopping.")
                    return Result.success() // Success, so it doesn't retry immediately just for permission
                }
            }

            val now = Calendar.getInstance()

            // --- 1. Check if it's the right time of day to send "day before" notifications ---
            val targetNotificationTimeToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, NOTIFICATION_HOUR_OF_DAY)
                set(Calendar.MINUTE, NOTIFICATION_MINUTE)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val windowMillis = TimeUnit.MINUTES.toMillis(NOON_CHECK_WINDOW_MINUTES.toLong())
            val isWithinNotificationWindow = now.timeInMillis >= (targetNotificationTimeToday.timeInMillis - windowMillis) &&
                    now.timeInMillis <= (targetNotificationTimeToday.timeInMillis + windowMillis)

            if (!isWithinNotificationWindow) {
                Log.d(TAG, "Not within the noon notification window. Skipping 'day before' event check. Current: ${now.get(Calendar.HOUR_OF_DAY)}:${now.get(Calendar.MINUTE)}")
                return Result.success() // Not time to send these specific notifications
            }

            Log.d(TAG, "Within noon notification window. Checking for tomorrow's events.")
            createNotificationChannel() // Ensure channel exists before trying to notify

            // --- 2. Define "Tomorrow" ---
            val tomorrowCalendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1) // Move to tomorrow
                // Normalize to the start of tomorrow for date comparisons
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // Define the range for "tomorrow" (from start of tomorrow to end of tomorrow)
            val tomorrowStartMillis = tomorrowCalendar.timeInMillis
            val tomorrowEndMillis = (tomorrowCalendar.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1) // Start of the day after tomorrow
                add(Calendar.MILLISECOND, -1) // End of tomorrow
            }.timeInMillis

            Log.d(TAG, "Checking for events on date: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(tomorrowCalendar.time)}")

            val eventsForTomorrowNotification = mutableListOf<CalendarEvent>()

            // --- 3. Get Non-Recurring Events for Tomorrow ---
            val nonRecurringForTomorrow = eventDao.getNonRecurringEventsBetween(tomorrowStartMillis, tomorrowEndMillis)
            nonRecurringForTomorrow.forEach { event ->
                Log.d(TAG, "Found non-recurring event for tomorrow: ${event.name} on ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(event.calendarInstance.time)}")
                eventsForTomorrowNotification.add(event)
            }

            // --- 4. Get Recurring Events for Tomorrow ---
            val allRecurringAnnualEvents = eventDao.getAllRecurringAnnualEvents()
            allRecurringAnnualEvents.forEach { event ->
                if (event.isRecurringAnnually && event.recurringMonth != null && event.recurringDayOfMonth != null) {
                    // Check if tomorrow matches the recurring month and day
                    if (tomorrowCalendar.get(Calendar.MONTH) == event.recurringMonth &&
                        tomorrowCalendar.get(Calendar.DAY_OF_MONTH) == event.recurringDayOfMonth
                    ) {
                        Log.d(TAG, "Found recurring event for tomorrow: ${event.name} (Original date: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(event.calendarInstance.time)})")
                        // Add the original event; the notification text will clarify it's for "tomorrow"
                        // Ensure we don't add duplicates if an event was already added from non-recurring list (though unlikely with current logic)
                        if (eventsForTomorrowNotification.none { it.id == event.id }) {
                            eventsForTomorrowNotification.add(event)
                        }
                    }
                }
            }

            // Remove duplicates (e.g., if an event was somehow fetched by both queries, though unlikely with current DAO structure)
            val distinctEventsForTomorrow = eventsForTomorrowNotification.distinctBy { it.id }

            Log.d(TAG, "Total ${distinctEventsForTomorrow.size} distinct events found for tomorrow to notify at noon.")

            if (distinctEventsForTomorrow.isNotEmpty()) {
                distinctEventsForTomorrow.forEach { event ->
                    // Final permission check before sending individual notification
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        Log.w(TAG, "Notification permission revoked before sending for ${event.name}. Skipping.")
                        return@forEach // Skip this notification using labeled return for forEach
                    }
                    showNotificationForTomorrow(event) // Use a specific notification function
                }
            } else {
                Log.d(TAG, "No events found for tomorrow to notify about at noon.")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in EventNotificationWorker doWork", e)
            return Result.retry() // Or Result.failure() if errors are persistent and not recoverable by retry
        }
    } // End of doWork method


    private fun createNotificationChannel() {
        // This method is fine as is. No changes needed here.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = applicationContext.getString(R.string.notification_channel_name)
            val descriptionText = applicationContext.getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created/ensured.")
        }
    }

    // Renamed and modified for "tomorrow's" event notification
    private fun showNotificationForTomorrow(event: CalendarEvent) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EVENT_ID_FROM_NOTIFICATION", event.id)
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext,
            event.id.hashCode(), // Unique request code
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val eventTimeFormatted = timeFormat.format(event.calendarInstance.time) // Original time of the event

        // --- MODIFIED CONTENT STRINGS FOR "TOMORROW" ---
        val contentTitle = if (event.isRecurringAnnually && event.type == EventType.BIRTHDAY) {
            applicationContext.getString(R.string.notification_title_birthday_tomorrow, event.name)
        } else {
            applicationContext.getString(R.string.notification_title_event_tomorrow, event.name)
        }

        val contentText = event.description?.takeIf { it.isNotBlank() }
            ?: applicationContext.getString(R.string.notification_default_description_tomorrow, eventTimeFormatted)


        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_event_note_24)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        with(NotificationManagerCompat.from(applicationContext)) {
            val notificationId = event.id.hashCode() // Unique ID for this notification
            Log.d(TAG, "Attempting to show 'day before' notification with ID: $notificationId for event: ${event.name}")

            // Permission check (already done in doWork, but good as a final safeguard)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(TAG, "Missing POST_NOTIFICATIONS permission before showing notification for ${event.name}")
                    return // Do not proceed to notify if permission is missing
                }
            }
            notify(notificationId, builder.build())
            Log.d(TAG, "'Day before' notification dispatched with ID: $notificationId for event: ${event.name}")
        }
    } // End of showNotificationForTomorrow method

} // End of EventNotificationWorker class