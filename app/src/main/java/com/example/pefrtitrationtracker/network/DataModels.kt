package com.example.pefrtitrationtracker.network

import com.google.gson.annotations.SerializedName
import java.util.Date // Keep this import, it's okay if unused

// --- Auth ---

data class SignupRequest(
    val email: String,
    val password: String,
    val role: String,
    @SerializedName("name") val fullName: String? = null,
    val age: Int? = null,
    val height: Int? = null,
    val gender: String? = null,
    @SerializedName("contact_number") val contactInfo: String? = null,
    val address: String? = null
)

// Use this for profile updates so callers don't need to provide password/email.
data class ProfileUpdateRequest(
    // Some backends require the `password` key present even for profile updates.
    // Provide an empty default so callers don't need to supply it but the JSON
    // will include the key, preventing 422 "missing" validation errors.
    val password: String? = "",
    val email: String? = null,
    val role: String? = null,
    @SerializedName("name") val fullName: String? = null,
    val age: Int? = null,
    val height: Int? = null,
    val gender: String? = null,
    @SerializedName("contact_number") val contactInfo: String? = null,
    val address: String? = null
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
    @SerializedName("name") val fullName: String?,
    val age: Int?,
    val height: Int?,
    val gender: String?,
    @SerializedName("contact_number") val contactInfo: String?,
    val address: String?,
    val baseline: BaselinePEFR?,
    @SerializedName("latest_pefr_record") val latestPefrRecord: PEFRRecord?,
    @SerializedName("latest_symptom") val latestSymptom: Symptom?
)

// --- PEFR & Baseline ---

data class BaselinePEFRCreate(
    @SerializedName("baseline_value")
    val baseline_value: Int
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

    // *** CHANGED TO STRING ***
    @SerializedName("recorded_at")
    val recordedAt: String,

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

    // *** THIS IS THE FIX ***
    @SerializedName("onset_at")
    val onsetAt: String?, // <-- WAS: Date?

    val duration: Int?,
    @SerializedName("suspected_trigger")
    val suspectedTrigger: String?
)

data class Symptom(
    val id: Int,

    // *** This should already be String from our last fix ***
    @SerializedName("recorded_at")
    val recordedAt: String,

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

    // *** THIS IS THE FIX ***
    @SerializedName("onset_at")
    val onsetAt: String?, // <-- WAS: Date?

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
    val description: String?,
    @SerializedName("start_date") val startDate: String? = null,
    val days: Int?,
    @SerializedName("cure_probability") val cureProbability: Double?,
    @SerializedName("doses_remaining") var dosesRemaining: Int?,
    val source: String?,
    @SerializedName("prescribed_by") val prescribedBy: Int?,
    @SerializedName("created_at") val createdAt: String? = null,

    @SerializedName("owner_id")
    val ownerId: Int,

    // NEW FIELD (Required for Taken/Not Taken)
    @SerializedName("taken_status")
    var takenStatus: String? = null
)


data class MedicationCreate(
    val name: String,
    val dose: String?,
    val schedule: String?,
    val description: String?
    ,
    val source: String? = null,
    @SerializedName("prescribed_by") val prescribedBy: Int? = null
)


data class MedicationUpdate(
    val name: String? = null,
    val dose: String? = null,
    val schedule: String? = null,
    @SerializedName("start_date") val startDate: String? = null,
    val days: Int? = null,
    @SerializedName("cure_probability") val cureProbability: Double? = null,
    @SerializedName("doses_remaining") val dosesRemaining: Int? = null
)


data class MedicationTake(
    val doses: Int = 1,
    val notes: String? = null
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


// ---------------- ML Models ----------------
data class MLInput(
    val age: Int?,
    @SerializedName("pefr_value") val pefrValue: Int,
    @SerializedName("wheeze_rating") val wheezeRating: Int?,
    @SerializedName("cough_rating") val coughRating: Int?,
    @SerializedName("dust_exposure") val dustExposure: Boolean?,
    @SerializedName("smoke_exposure") val smokeExposure: Boolean?
)

data class MLPrediction(
    @SerializedName("recommended_medicine") val recommendedMedicine: String,
    @SerializedName("recommended_days") val recommendedDays: Int,
    @SerializedName("predicted_cure_probability") val predictedCureProbability: Double
)


// --- Notifications ---
data class Notification(
    val id: Int,
    @SerializedName("owner_id") val ownerId: Int,
    val message: String,
    val link: String?,
    @SerializedName("created_at") val createdAt: String,
    val read: Boolean
)
