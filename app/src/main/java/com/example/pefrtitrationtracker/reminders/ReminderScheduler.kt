package com.example.pefrtitrationtracker.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.Calendar
import java.util.TimeZone
import com.example.pefrtitrationtracker.network.SessionManager

class ReminderScheduler(private val context: Context) {

    private val REQUEST_CODE = 1001
    private val TAG = "ReminderScheduler"

    private fun getPendingIntent(targetPefr: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        intent.putExtra("target_pefr", targetPefr)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            flags
        )
    }

    /**
     * Schedule an exact alarm for the next occurrence of the requested time.
     * Uses device timezone and prioritizes exact alarms for on-time delivery.
     */
    fun schedule(hour: Int, minute: Int, frequency: String, targetPefr: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Use device default timezone explicitly
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the time has already passed today, schedule for next occurrence
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            when (frequency.uppercase()) {
                "DAILY" -> cal.add(Calendar.DAY_OF_MONTH, 1)
                "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                "MONTHLY" -> cal.add(Calendar.MONTH, 1)
                else -> cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val pendingIntent = getPendingIntent(targetPefr)
        val triggerTime = cal.timeInMillis

        Log.d(TAG, "Scheduling reminder for ${cal.time} (${triggerTime}) - Frequency: $frequency | SDK=${android.os.Build.VERSION.SDK_INT}")

        // If the trigger time is effectively now (within a small threshold), fire immediately
        val now = System.currentTimeMillis()
        val thresholdMs = 1500L // 1.5 seconds tolerance
        if (triggerTime <= now + thresholdMs) {
            Log.d(TAG, "Trigger time is immediate (within ${thresholdMs}ms). Firing reminder now.")
            try {
                val b = Intent(context, ReminderReceiver::class.java).apply { putExtra("target_pefr", targetPefr) }
                context.sendBroadcast(b)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to broadcast immediate reminder: ${t.message}")
            }
            return
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val canExact = alarmManager.canScheduleExactAlarms()
                Log.d(TAG, "canScheduleExactAlarms() => $canExact")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not query exact alarm capability: ${t.message}")
        }

        try {
            when {
                // Android 12+ (API 31+) - Use setAlarmClock for highest priority
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    Log.d(TAG, "Using API31 exact alarm path (setAlarmClock)")
                    scheduleExactAlarmAPI31(alarmManager, triggerTime, pendingIntent)
                }
                // Android 6+ (API 23+) - Try exact alarm with fallback
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    scheduleExactAlarmAPI23(alarmManager, triggerTime, pendingIntent)
                }
                // Older versions - Use setExact
                else -> {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            }

            Log.d(TAG, "Reminder scheduled successfully for ${cal.time}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact alarm, using inexact fallback", e)
            // Final fallback - inexact alarm (may be delayed by system)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun scheduleExactAlarmAPI31(alarmManager: AlarmManager, triggerTime: Long, pendingIntent: PendingIntent) {
        val canExact = try { alarmManager.canScheduleExactAlarms() } catch (t: Throwable) { false }
        Log.d(TAG, "scheduleExactAlarmAPI31 - canScheduleExactAlarms: $canExact")
        if (canExact) {
            // Use AlarmManager.setAlarmClock for highest priority delivery
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, null)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Scheduled with setAlarmClock (highest priority)")
        } else {
            Log.w(TAG, "Exact alarms not permitted, falling back to setExactAndAllowWhileIdle")
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun scheduleExactAlarmAPI23(alarmManager: AlarmManager, triggerTime: Long, pendingIntent: PendingIntent) {
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            Log.d(TAG, "Scheduled with setExactAndAllowWhileIdle")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException with exact alarm, falling back to setExact")
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    /**
     * Called by the receiver to schedule the next occurrence after firing.
     * It reads the stored reminder and schedules the next exact alarm.
     */
    fun scheduleNextOccurrence(targetPefr: Int) {
        val ownerEmail = SessionManager(context).fetchUserEmail()
        val saved = ReminderStore.load(context, ownerEmail)
        if (!saved.enabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = getPendingIntent(targetPefr)

        // Use device default timezone explicitly
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
            set(Calendar.HOUR_OF_DAY, saved.hour)
            set(Calendar.MINUTE, saved.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Move to next occurrence
        when (saved.frequency.uppercase()) {
            "DAILY" -> cal.add(Calendar.DAY_OF_MONTH, 1)
            "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            "MONTHLY" -> cal.add(Calendar.MONTH, 1)
            else -> cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        val triggerTime = cal.timeInMillis
        Log.d(TAG, "Scheduling next occurrence for ${cal.time} (${triggerTime})")

        try {
            when {
                // Android 12+ (API 31+) - Use setAlarmClock for highest priority
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    scheduleExactAlarmAPI31(alarmManager, triggerTime, pending)
                }
                // Android 6+ (API 23+) - Try exact alarm with fallback
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    scheduleExactAlarmAPI23(alarmManager, triggerTime, pending)
                }
                // Older versions - Use setExact
                else -> {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pending)
                }
            }

            Log.d(TAG, "Next occurrence scheduled successfully for ${cal.time}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule next occurrence, using inexact fallback", e)
            // Final fallback - inexact alarm (may be delayed by system)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pending)
        }
    }

    fun cancel() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = getPendingIntent(-1)
        alarmManager.cancel(pending)
        pending.cancel()
        Log.d(TAG, "Reminder cancelled")
    }

    /**
     * Check if exact alarms can be scheduled
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Older versions don't have this restriction
        }
    }
}
