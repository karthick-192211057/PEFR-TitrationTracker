package com.example.asthmamanager.network

import com.google.gson.annotations.SerializedName
import java.util.Date

// --- Auth ---

data class SignupRequest(
    val email: String,
    val password: String,
    val role: String,
    @SerializedName("name") val fullName: String? = null, // "full_name" changed to "name"
    val age: Int? = null,
    val height: Int? = null,
    val gender: String? = null,
    @SerializedName("contact_number") val contactInfo: String? = null, // "contact_info" changed to "contact_number"
    val address: String? = null
    // @SerializedName("baseline_pefr") val baselinePefr: Int? = null // <-- This line is now removed
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user_role") val userRole: String
)

data class User(
    val id: Int,
    val email: String,
    val role: String,
    @SerializedName("name") val fullName: String?, // "full_name" changed to "name"
    val age: Int?,
    val height: Int?,
    val gender: String?,
    @SerializedName("contact_number") val contactInfo: String?, // "contact_info" changed to "contact_number"
    val address: String?,
    val baseline: BaselinePEFR? // This is correct
)

// --- PEFR & Baseline ---

data class BaselinePEFRCreate(
    @SerializedName("baseline_value")
    val baselineValue: Int
)

data class BaselinePEFR(
    val id: Int,
    @SerializedName("baseline_value")
    val baselineValue: Int,
    @SerializedName("owner_id")
    val ownerId: Int
)

data class PEFRRecordCreate(
    @SerializedName("pefr_value")
    val pefrValue: Int,
    val source: String = "manual"
)

data class PEFRRecord(
    val id: Int,
    @SerializedName("pefr_value")
    val pefrValue: Int,
    val zone: String,
    @SerializedName("recorded_at")
    val recordedAt: Date,
    @SerializedName("owner_id")
    val ownerId: Int,
    val percentage: Double?,
    val trend: String?,
    val source: String?
)

data class PEFRRecordResponse(
    val zone: String,
    val guidance: String,
    val record: PEFRRecord,
    val percentage: Double?,
    val trend: String?
)

// --- Symptoms ---

data class SymptomCreate(
    @SerializedName("wheeze_rating")
    val wheezeRating: Int?,
    @SerializedName("cough_rating")
    val coughRating: Int?,
    @SerializedName("dust_exposure")
    val dustExposure: Boolean?,
    @SerializedName("smoke_exposure")
    val smokeExposure: Boolean?,
    @SerializedName("dyspnea_rating")
    val dyspneaRating: Int?,
    @SerializedName("night_symptoms_rating")
    val nightSymptomsRating: Int?,
    val severity: String?,
    @SerializedName("onset_at")
    val onsetAt: Date?,
    val duration: Int?,
    @SerializedName("suspected_trigger")
    val suspectedTrigger: String?
)

data class Symptom(
    val id: Int,
    @SerializedName("recorded_at")
    val recordedAt: Date,
    @SerializedName("owner_id")
    val ownerId: Int,
    @SerializedName("wheeze_rating")
    val wheezeRating: Int?,
    @SerializedName("cough_rating")
    val coughRating: Int?,
    @SerializedName("dust_exposure")
    val dustExposure: Boolean?,
    @SerializedName("smoke_exposure")
    val smokeExposure: Boolean?,
    @SerializedName("dyspnea_rating")
    val dyspneaRating: Int?,
    @SerializedName("night_symptoms_rating")
    val nightSymptomsRating: Int?,
    val severity: String?,
    @SerializedName("onset_at")
    val onsetAt: Date?,
    val duration: Int?,
    @SerializedName("suspected_trigger")
    val suspectedTrigger: String?
)

// --- Profile: Medications, Contacts, Reminders ---

data class Medication(
    val id: Int,
    val name: String,
    val dose: String?,
    val schedule: String?,
    @SerializedName("owner_id")
    val ownerId: Int
)

data class MedicationCreate(
    val name: String,
    val dose: String?,
    val schedule: String?
)

data class EmergencyContact(
    val id: Int,
    val name: String,
    @SerializedName("phone_number")
    val phoneNumber: String,
    @SerializedName("contact_relationship")
    val contactRelationship: String?,
    @SerializedName("owner_id")
    val ownerId: Int
)

data class EmergencyContactCreate(
    val name: String,
    @SerializedName("phone_number")
    val phoneNumber: String,
    @SerializedName("contact_relationship")
    val contactRelationship: String?
)

data class Reminder(
    val id: Int,
    @SerializedName("reminder_type")
    val reminderType: String,
    val time: String,
    val frequency: String,
    @SerializedName("compliance_count")
    val complianceCount: Int,
    @SerializedName("missed_count")
    val missedCount: Int,
    @SerializedName("owner_id")
    val ownerId: Int
)

data class ReminderCreate(
    @SerializedName("reminder_type")
    val reminderType: String,
    val time: String,
    val frequency: String
)


// --- [START] NEW DOCTOR-PATIENT LINK MODELS ---

data class DoctorPatientLinkRequest(
    @SerializedName("doctor_email")
    val doctorEmail: String
)

data class DoctorPatientLink(
    val id: Int,
    @SerializedName("doctor_id")
    val doctorId: Int,
    @SerializedName("patient_id")
    val patientId: Int
)

// --- [END] NEW DOCTOR-PATIENT LINK MODELS ---
