package com.example.asthmamanager.network

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // --- Auth ---
    @FormUrlEncoded
    @POST("/auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<TokenResponse>

    @POST("/auth/signup")
    suspend fun signup(
        @Body signupRequest: SignupRequest
    ): Response<User>

    // --- Profile & User Data (Requires Auth) ---
    @GET("/profile/me")
    suspend fun getMyProfile(): Response<User>

    @PUT("/profile/me")
    suspend fun updateMyProfile(@Body profileUpdateRequest: SignupRequest): Response<User>

    @POST("/patient/baseline")
    suspend fun setBaseline(
        @Body baselineRequest: BaselinePEFRCreate
    ): Response<BaselinePEFR>

    // --- PEFR & Symptoms (Requires Auth) ---
    @POST("/pefr/record")
    suspend fun recordPEFR(
        @Body pefrRequest: PEFRRecordCreate
    ): Response<PEFRRecordResponse>

    @POST("/symptom/record")
    suspend fun recordSymptom(
        @Body symptomRequest: SymptomCreate
    ): Response<Symptom>

    // --- Medications (Requires Auth) ---
    @GET("/medications")
    suspend fun getMedications(): Response<List<Medication>>

    @POST("/medications")
    suspend fun createMedication(
        @Body medicationRequest: MedicationCreate
    ): Response<Medication>

    // --- Contacts (Requires Auth) ---
    @GET("/contacts")
    suspend fun getEmergencyContacts(): Response<List<EmergencyContact>>

    @POST("/contacts")
    suspend fun createEmergencyContact(
        @Body contactRequest: EmergencyContactCreate
    ): Response<EmergencyContact>

    // --- Reminders (Requires Auth) ---
    @GET("/reminders")
    suspend fun getReminders(): Response<List<Reminder>>

    @POST("/reminders")
    suspend fun createReminder(
        @Body reminderRequest: ReminderCreate
    ): Response<Reminder>

    // --- Doctor (Requires Auth) ---
    @GET("/doctor/patients")
    suspend fun getDoctorPatients(
        @Query("search") search: String?,
        @Query("zone") zone: String?
    ): Response<List<User>>

    // --- [START] NEW FUNCTION TO LINK PATIENT TO DOCTOR ---
    @POST("/patient/link-doctor")
    suspend fun linkDoctor(
        @Body linkRequest: DoctorPatientLinkRequest
    ): Response<DoctorPatientLink>
    // --- [END] NEW FUNCTION TO LINK PATIENT TO DOCTOR ---


    // --- [START] NEW DOCTOR FUNCTIONS ---
    @GET("/patient/{patient_id}/pefr")
    suspend fun getPatientPefrRecords(
        @Path("patient_id") patientId: Int
    ): Response<List<PEFRRecord>>

    @GET("/patient/{patient_id}/symptoms")
    suspend fun getPatientSymptomRecords(
        @Path("patient_id") patientId: Int
    ): Response<List<Symptom>>
    // --- [END] NEW DOCTOR FUNCTIONS ---
}
