package com.example.pefrtitrationtracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pefrtitrationtracker.R
import com.example.pefrtitrationtracker.network.RetrofitClient

class NotificationPollWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val prefs: SharedPreferences = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        try {
            val resp = RetrofitClient.apiService.getNotifications()
            if (resp.isSuccessful) {
                val list = resp.body() ?: emptyList()
                // find latest id
                val latestId = list.maxOfOrNull { it.id } ?: 0
                val lastSeen = prefs.getInt("last_notification_id", 0)
                if (latestId > lastSeen) {
                    // show simple notification for the newest
                    val newest = list.maxByOrNull { it.id }!!
                    showNotification(newest.message)
                    prefs.edit().putInt("last_notification_id", latestId).apply()
                }
            }
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    private fun showNotification(message: String) {
        val channelId = "asthma_notifications"
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "App Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("Asthma Manager")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        NotificationManagerCompat.from(applicationContext).notify(1001, builder.build())
    }
}
