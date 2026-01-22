package com.example.pefrtitrationtracker.network

import com.google.gson.annotations.SerializedName

data class MedicationStatusHistory(
    val id: Int,

    @SerializedName("medication_id")
    val medicationId: Int,

    val status: String,

    val notes: String? = null,

    @SerializedName("changed_at")
    val changedAt: String,

    @SerializedName("changed_by_user_id")
    val changedByUserId: Int?
)

data class MedicationWithHistory(
    val id: Int,

    @SerializedName("owner_id")
    val ownerId: Int,

    val name: String,

    val dose: String?,

    val schedule: String?,

    @SerializedName("taken_status")
    val takenStatus: String?,

    @SerializedName("status_history")
    val statusHistory: List<MedicationStatusHistory>
)
