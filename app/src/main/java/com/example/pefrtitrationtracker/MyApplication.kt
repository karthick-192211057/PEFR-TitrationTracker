package com.example.pefrtitrationtracker

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp   // ✅ ADD

class MyApplication : Application() {

    companion object {
        lateinit var appContext: Context
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // 🔥 REQUIRED: Initialize Firebase
        FirebaseApp.initializeApp(this)
        Log.d("Firebase", "FirebaseApp initialized")
    }
}
