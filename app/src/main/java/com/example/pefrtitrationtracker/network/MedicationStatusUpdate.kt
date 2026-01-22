package com.example.pefrtitrationtracker.network

data class MedicationStatusUpdate(
    val status: String,
    val notes: String? = null
)
