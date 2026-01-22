package com.example.pefrtitrationtracker.utils

import com.example.pefrtitrationtracker.network.Medication
import com.example.pefrtitrationtracker.network.PEFRRecord
import com.example.pefrtitrationtracker.network.Symptom
import com.example.pefrtitrationtracker.network.User

/**
 * Simple in-memory cache to hold recently synced user data.
 * This is intentionally minimal: fragments still fetch from server as needed,
 * but this allows a quick population after login so UI reflects server state.
 */
object AppCache {
    @Volatile
    var profile: User? = null

    @Volatile
    var medications: List<Medication> = emptyList()

    @Volatile
    var pefrRecords: List<PEFRRecord> = emptyList()

    @Volatile
    var symptoms: List<Symptom> = emptyList()
}
