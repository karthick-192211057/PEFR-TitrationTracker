package com.example.asthmamanager.network

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("AsthmaAppPrefs", Context.MODE_PRIVATE)

    companion object {
        const val AUTH_TOKEN = "auth_token"
    }

    fun saveAuthToken(token: String) {
        val editor = prefs.edit()
        editor.putString(AUTH_TOKEN, token)
        editor.apply()
    }

    fun fetchAuthToken(): String? {
        return prefs.getString(AUTH_TOKEN, null)
    }

    // --- ADD THIS FUNCTION ---
    fun clearAuthToken() {
        val editor = prefs.edit()
        editor.remove(AUTH_TOKEN)
        editor.apply()
    }
}