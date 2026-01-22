package com.example.pefrtitrationtracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pefrtitrationtracker.network.RetrofitClient
import com.example.pefrtitrationtracker.network.SessionManager
import com.google.gson.Gson

class MedSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        try {
            val resp = RetrofitClient.apiService.getMedications()
            if (resp.isSuccessful) {
                val body = resp.body()
                val gson = Gson()
                val prefs = SessionManager(applicationContext)
                // ensure prefs initialized; only save if we have a non-null email
                val cachedEmail = prefs.fetchUserEmail()
                if (!cachedEmail.isNullOrEmpty()) {
                    prefs.saveUserEmail(cachedEmail)
                }
                // store cached JSON for quick UI reads
                applicationContext.getSharedPreferences("AsthmaAppPrefs", Context.MODE_PRIVATE)
                    .edit().putString("cached_medications", gson.toJson(body)).apply()
                return Result.success()
            }
            return Result.retry()
        } catch (e: Exception) {
            return Result.retry()
        }
    }
}
