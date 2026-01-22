package com.example.pefrtitrationtracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.pefrtitrationtracker.network.SessionManager
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import com.example.pefrtitrationtracker.R
import java.util.Date

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "reminder_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {

        val pefrValue = intent.getIntExtra("target_pefr", -1)

        // Debug log
        android.util.Log.d("ReminderReceiver", "Triggered at ${Date()} | PEFR: $pefrValue")

        // Permission check for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            if (perm != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        // Build a notification with a full-screen intent so it appears as a pop-up/heads-up
        val activityIntent = Intent(context, ReminderPopupActivity::class.java).apply {
            putExtra("target_pefr", pefrValue)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val activityPending = PendingIntent.getActivity(
            context,
            pefrValue.hashCode(),
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("PEFR Reminder")
            .setContentText(
                if (pefrValue > 0) "Check your PEFR now! Target: $pefrValue"
                else "Check your PEFR now!"
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(activityPending, true)
            .build()

        NotificationManagerCompat.from(context).notify(999, notification)

        // Persist a notification entry so the UI can show a history
        try {
            val session = SessionManager(context)
            val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault()
            }
            val msg = if (pefrValue > 0) "Reminder fired: Target $pefrValue" else "Reminder fired"
            session.addNotification(msg)
        } catch (_: Exception) {}

        // After firing, schedule the next occurrence for repeating reminders
        try {
            ReminderScheduler(context).scheduleNextOccurrence(pefrValue)
        } catch (t: Throwable) {
            android.util.Log.w("ReminderReceiver", "Failed to schedule next occurrence", t)
        }
    }
}
