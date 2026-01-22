package com.example.pefrtitrationtracker

import android.content.Context
import android.content.SharedPreferences

object TokenManager {

    private const val PREFS_NAME = "AuthPrefs"
    private const val KEY_TOKEN = "authToken"
    private const val KEY_USER_ROLE = "userRole"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(context: Context, token: String, role: String) {
        val editor = getPrefs(context).edit()
        editor.putString(KEY_TOKEN, token)
        editor.putString(KEY_USER_ROLE, role)
        editor.apply()
    }

    fun getToken(context: Context): String? {
        return getPrefs(context).getString(KEY_TOKEN, null)
    }

    fun getUserRole(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_ROLE, null)
    }

    fun clearToken(context: Context) {
        val editor = getPrefs(context).edit()
        editor.clear()
        editor.apply()
    }
}
