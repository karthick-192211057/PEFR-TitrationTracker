package com.example.pefrtitrationtracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.pefrtitrationtracker.network.RetrofitClient
import com.example.pefrtitrationtracker.network.SessionManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "FCM token: $token")
        // Send this token to backend if user is logged in
        try {
            val session = SessionManager(applicationContext)
            val auth = session.fetchAuthToken()
            if (!auth.isNullOrBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val resp = RetrofitClient.apiService.registerDeviceToken(token)
                        if (resp.isSuccessful) {
                            Log.d("FCM", "Device token registered with backend")
                        } else {
                            Log.d("FCM", "Failed to register token: ${resp.code()}")
                        }
                    } catch (e: Exception) {
                        Log.d("FCM", "Exception while registering token: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("FCM", "Error in onNewToken registration: ${e.message}")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: "Notification"
        val body = remoteMessage.notification?.body ?: "You have a new message"

        showNotification(title, body)
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "fcm_default"

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "FCM Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
