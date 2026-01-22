package com.example.pefrtitrationtracker

data class Patient(
    val name: String,
    val zone: String,
    val latestPEFR: Int,
    val symptomSeverity: String
)
