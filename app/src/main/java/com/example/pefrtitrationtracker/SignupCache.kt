package com.example.pefrtitrationtracker

import com.example.pefrtitrationtracker.network.SignupRequest

object SignupCache {
    // Temporary in-memory cache for signup data between fragments
    var lastSignupRequest: SignupRequest? = null
}
