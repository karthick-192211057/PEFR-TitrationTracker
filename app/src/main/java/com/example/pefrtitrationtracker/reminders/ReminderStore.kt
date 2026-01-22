package com.example.pefrtitrationtracker.reminders

import android.content.Context

data class SavedReminder(
    val enabled: Boolean = false,
    val hour: Int = 8,
    val minute: Int = 0,
    val frequency: String = "DAILY", // DAILY | WEEKLY | MONTHLY | ONCE
    val targetPefr: Int = -1
)

object ReminderStore {

    private const val PREFS = "reminder_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MIN = "minute"
    private const val KEY_FREQ = "frequency"
    private const val KEY_PEFR = "pefr"

    fun save(context: Context, r: SavedReminder, ownerEmail: String? = null) {
        val prefsName = if (!ownerEmail.isNullOrBlank()) "${PREFS}_${ownerEmail.replace("@", "_at_").replace(".", "_")}" else PREFS
        val p = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        p.edit()
            .putBoolean(KEY_ENABLED, r.enabled)
            .putInt(KEY_HOUR, r.hour)
            .putInt(KEY_MIN, r.minute)
            .putString(KEY_FREQ, r.frequency)
            .putInt(KEY_PEFR, r.targetPefr)
            .apply()
    }

    fun load(context: Context, ownerEmail: String? = null): SavedReminder {
        val prefsName = if (!ownerEmail.isNullOrBlank()) "${PREFS}_${ownerEmail.replace("@", "_at_").replace(".", "_")}" else PREFS
        val p = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        return SavedReminder(
            enabled = p.getBoolean(KEY_ENABLED, false),
            hour = p.getInt(KEY_HOUR, 8),
            minute = p.getInt(KEY_MIN, 0),
            frequency = p.getString(KEY_FREQ, "DAILY") ?: "DAILY",
            targetPefr = p.getInt(KEY_PEFR, -1)
        )
    }

    fun clear(context: Context, ownerEmail: String? = null) {
        val prefsName = if (!ownerEmail.isNullOrBlank()) "${PREFS}_${ownerEmail.replace("@", "_at_").replace(".", "_")}" else PREFS
        val p = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        p.edit().clear().apply()
    }
}
