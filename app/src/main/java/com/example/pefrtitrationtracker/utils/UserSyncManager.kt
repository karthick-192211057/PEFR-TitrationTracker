package com.example.pefrtitrationtracker.utils

import android.util.Log
import com.example.pefrtitrationtracker.network.RetrofitClient

object UserSyncManager {
    private const val TAG = "UserSyncManager"

    /**
     * Fetches profile, medications, PEFRs and symptoms and stores them in AppCache.
     * Returns true if at least the profile fetch succeeded (best-effort for others).
     */
    suspend fun syncAll(): Boolean {
        var profileOk = false

        try {
            val profileResp = RetrofitClient.apiService.getMyProfile()
            if (profileResp.isSuccessful) {
                AppCache.profile = profileResp.body()
                profileOk = true
            } else {
                Log.w(TAG, "getMyProfile failed: ${profileResp.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "getMyProfile exception: ${e.message}", e)
        }

        try {
            val medsResp = RetrofitClient.apiService.getMedications()
            if (medsResp.isSuccessful) {
                AppCache.medications = medsResp.body() ?: emptyList()
            } else {
                Log.w(TAG, "getMedications failed: ${medsResp.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "getMedications exception: ${e.message}", e)
        }

        try {
            val pefrResp = RetrofitClient.apiService.getMyPefrRecords()
            if (pefrResp.isSuccessful) {
                AppCache.pefrRecords = pefrResp.body() ?: emptyList()
            } else {
                Log.w(TAG, "getMyPefrRecords failed: ${pefrResp.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "getMyPefrRecords exception: ${e.message}", e)
        }

        try {
            val symResp = RetrofitClient.apiService.getMySymptomRecords()
            if (symResp.isSuccessful) {
                AppCache.symptoms = symResp.body() ?: emptyList()
            } else {
                Log.w(TAG, "getMySymptomRecords failed: ${symResp.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "getMySymptomRecords exception: ${e.message}", e)
        }

        return profileOk
    }
}
