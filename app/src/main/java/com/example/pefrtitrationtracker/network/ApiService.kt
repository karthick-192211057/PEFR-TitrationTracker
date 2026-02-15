package com.example.pefrtitrationtracker.network

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
    suspend fun signup(@Body signupRequest: SignupRequest): Response<User>

    // ---------------- OTP BASED AUTH ----------------

    // Signup - Send OTP
    @POST("/auth/signup-send-otp")
    suspend fun signupSendOtp(
        @Body signupRequest: SignupRequest
    ): Response<Map<String, String>>

    // Verify Signup OTP
    @FormUrlEncoded
    @POST("/auth/verify-signup-otp")
    suspend fun verifySignupOtp(
        @Field("email") email: String,
        @Field("otp") otp: String
    ): Response<Map<String, String>>

    // Forgot Password - Send OTP
    @FormUrlEncoded
    @POST("/auth/forgot-password")
    suspend fun forgotPassword(
        @Field("email") email: String
    ): Response<Map<String, String>>

    // Reset Password - Verify OTP & Reset
    @FormUrlEncoded
    @POST("/auth/reset-password")
    suspend fun resetPassword(
        @Field("email") email: String,
        @Field("otp") otp: String,
        @Field("new_password") newPassword: String
    ): Response<Map<String, String>>

    // Verify OTP for forgot-password (pre-verification step)
    @FormUrlEncoded
    @POST("/auth/verify-forgot-otp")
    suspend fun verifyForgotOtp(
        @Field("email") email: String,
        @Field("otp") otp: String
    ): Response<Map<String, String>>


    // --- Profile ---
    @GET("/profile/me")
    suspend fun getMyProfile(): Response<User>

    @PUT("/profile/me")
    suspend fun updateMyProfile(@Body profileUpdateRequest: ProfileUpdateRequest): Response<User>

    @FormUrlEncoded
    @POST("/profile/device-token")
    suspend fun registerDeviceToken(@Field("token") token: String): Response<Map<String, String>>

    @DELETE("/profile/me")
    suspend fun deleteMyAccount(): Response<Map<String, String>>


    // --- Baseline ---
    @POST("/patient/baseline")
    suspend fun setBaseline(@Body baselineRequest: BaselinePEFRCreate): Response<BaselinePEFR>


    // --- PEFR ---
    @POST("/pefr/record")
    suspend fun recordPEFR(@Body pefrRequest: PEFRRecordCreate): Response<PEFRRecordResponse>

    @GET("/pefr/records")
    suspend fun getMyPefrRecords(): Response<List<PEFRRecord>>


    // --- Symptoms ---
    @POST("/symptom/record")
    suspend fun recordSymptom(@Body symptomRequest: SymptomCreate): Response<Symptom>

    @GET("/symptom/records")
    suspend fun getMySymptomRecords(): Response<List<Symptom>>

    // ---------------- ML ----------------
    @POST("/ml/predict")
    suspend fun mlPredict(@Body input: MLInput): Response<MLPrediction>


    // ---------------------------------------------------
    //                  MEDICATIONS
    // ---------------------------------------------------

    @GET("/medications")
    suspend fun getMedications(): Response<List<Medication>>

    @POST("/medications")
    suspend fun createMedication(@Body medicationRequest: MedicationCreate): Response<Medication>

    @PATCH("/medications/{med_id}/status")
    suspend fun updateMedicationStatus(
        @Path("med_id") medId: Int,
        @Body request: MedicationStatusUpdate
    ): Response<Any>

    @PATCH("/medications/{med_id}")
    suspend fun updateMedication(
        @Path("med_id") medId: Int,
        @Body request: com.example.pefrtitrationtracker.network.MedicationUpdate
    ): Response<com.example.pefrtitrationtracker.network.Medication>

    @POST("/medications/{med_id}/take")
    suspend fun takeMedication(
        @Path("med_id") medId: Int,
        @Body request: com.example.pefrtitrationtracker.network.MedicationTake
    ): Response<com.example.pefrtitrationtracker.network.Medication>

    @DELETE("/medications/{id}")
    suspend fun deleteMedication(
        @Path("id") medicationId: Int
    ): Response<Any>


    // --- Notifications ---
    @GET("/notifications")
    suspend fun getNotifications(): Response<List<Notification>>

    @PATCH("/notifications/{notif_id}/read")
    suspend fun markNotificationRead(@Path("notif_id") notifId: Int): Response<Notification>


    // --- Doctor: Medication History ---
    @GET("/doctor/patient/{patient_id}/medications/history")
    suspend fun getMedicationHistory(
        @Path("patient_id") patientId: Int
    ): Response<List<MedicationWithHistory>>


    // ---------------------------------------------------
    //       DOCTOR DASHBOARD + DOCTOR–PATIENT
    // ---------------------------------------------------

    @GET("/doctor/patients")
    suspend fun getDoctorPatients(
        @Query("search") search: String?,
        @Query("zone") zone: String?
    ): Response<List<User>>

    @POST("patient/link-doctor")
    suspend fun linkDoctor(@Body linkRequest: DoctorPatientLinkRequest): Response<DoctorPatientLink>

    @GET("/patient/doctor")
    suspend fun getLinkedDoctor(): Response<User>

    @DELETE("/patient/doctor")
    suspend fun unlinkDoctor(): Response<Any>


    @GET("/patient/{patient_id}/pefr")
    suspend fun getPatientPefrRecords(@Path("patient_id") patientId: Int): Response<List<PEFRRecord>>

    @GET("/patient/{patient_id}/symptoms")
    suspend fun getPatientSymptomRecords(@Path("patient_id") patientId: Int): Response<List<Symptom>>


    @POST("/doctor/patient/{patient_id}/medication")
    suspend fun prescribeMedication(
        @Path("patient_id") patientId: Int,
        @Body medicationRequest: MedicationCreate
    ): Response<Medication>

    @DELETE("/doctor/patient/{patient_id}")
    suspend fun deleteLinkedPatient(
        @Path("patient_id") patientId: Int
    ): Response<Any>


    // ---------------------------------------------------
    //             EMERGENCY CONTACTS  (CORRECT!)
    // ---------------------------------------------------
    @GET("/contacts/emergency")
    suspend fun getEmergencyContacts(): Response<List<EmergencyContact>>
    
}
