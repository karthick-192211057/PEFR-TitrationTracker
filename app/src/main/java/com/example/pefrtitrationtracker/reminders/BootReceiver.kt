package com.example.pefrtitrationtracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val ownerEmail = com.example.pefrtitrationtracker.network.SessionManager(context).fetchUserEmail()
        val saved = ReminderStore.load(context, ownerEmail)

        if (saved.enabled) {
            ReminderScheduler(context).schedule(
                saved.hour, saved.minute, saved.frequency, saved.targetPefr
            )
        }
    }
}
