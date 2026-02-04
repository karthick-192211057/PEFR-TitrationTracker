package com.example.pefrtitrationtracker.network

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("AsthmaAppPrefs", Context.MODE_PRIVATE)

    companion object {
        const val AUTH_TOKEN = "auth_token"
        const val USER_ROLE = "user_role"
        const val PROFILE_IMAGE_URI = "profile_image_uri"
    }

    // Real-time sharing preference key (legacy global key)
    private val KEY_SHARING_ENABLED = "sharing_enabled"

    // Per-user email key
    private val KEY_USER_EMAIL = "user_email"

    // ---------------- DELETED PATIENTS (persisted per-doctor)
    private val KEY_DELETED_PATIENTS = "deleted_patients"

    // Recently prescribed (transient UI flag stored locally)
    private val KEY_RECENT_PRESCRIBED = "recently_prescribed"
    // Notifications (persisted simple list)
    private val KEY_NOTIFICATIONS = "notifications"
    private val KEY_OTP_ERRORS = "otp_errors"

    fun addDeletedPatientId(id: Int) {
        val current = fetchDeletedPatientIds().toMutableSet()
        current.add(id.toString())
        prefs.edit().putStringSet(KEY_DELETED_PATIENTS, current).apply()
    }

    fun fetchDeletedPatientIds(): Set<String> {
        return prefs.getStringSet(KEY_DELETED_PATIENTS, emptySet()) ?: emptySet()
    }

    fun isPatientDeleted(id: Int): Boolean {
        return fetchDeletedPatientIds().contains(id.toString())
    }

    fun clearDeletedPatients() {
        prefs.edit().remove(KEY_DELETED_PATIENTS).apply()
    }

    // ---------------- RECENTLY PRESCRIBED (UI-only marker)
    fun addRecentlyPrescribed(id: Int) {
        val current = prefs.getStringSet(KEY_RECENT_PRESCRIBED, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(id.toString())
        prefs.edit().putStringSet(KEY_RECENT_PRESCRIBED, current).apply()
    }

    fun fetchRecentlyPrescribed(): Set<String> {
        return prefs.getStringSet(KEY_RECENT_PRESCRIBED, emptySet()) ?: emptySet()
    }

    fun isRecentlyPrescribed(id: Int): Boolean {
        return fetchRecentlyPrescribed().contains(id.toString())
    }

    fun clearRecentlyPrescribed() {
        prefs.edit().remove(KEY_RECENT_PRESCRIBED).apply()
    }

    // ---------------- MEDICATION METADATA (local cache)
    private fun medKey(medId: Int) = "med_meta_$medId"

    fun saveMedicationMeta(medId: Int, startMillis: Long, days: Int, cureProb: Double) {
        val v = "$startMillis|$days|$cureProb"
        prefs.edit().putString(medKey(medId), v).apply()
    }

    fun fetchMedicationMeta(medId: Int): Triple<Long, Int, Double>? {
        val s = prefs.getString(medKey(medId), null) ?: return null
        val parts = s.split("|")
        if (parts.size != 3) return null
        return try {
            val start = parts[0].toLong()
            val days = parts[1].toInt()
            val prob = parts[2].toDouble()
            Triple(start, days, prob)
        } catch (e: Exception) {
            null
        }
    }

    fun clearMedicationMeta(medId: Int) {
        prefs.edit().remove(medKey(medId)).apply()
    }

    // ---------------- MEDICATION CREATED TIME (local cache)
    private fun medCreatedKey(medId: Int) = "med_created_time_$medId"

    fun saveMedicationCreatedTime(medId: Int, timeMillis: Long) {
        prefs.edit().putLong(medCreatedKey(medId), timeMillis).apply()
    }

    fun fetchMedicationCreatedTime(medId: Int): Long? {
        val v = prefs.getLong(medCreatedKey(medId), -1L)
        return if (v <= 0L) null else v
    }

    fun clearMedicationCreatedTime(medId: Int) {
        prefs.edit().remove(medCreatedKey(medId)).apply()
    }

    // ---------------- MEDICATION STATUS TIME (local cache)
    private fun medStatusKey(medId: Int) = "med_status_time_$medId"

    fun saveMedicationStatusTime(medId: Int, timeMillis: Long) {
        prefs.edit().putLong(medStatusKey(medId), timeMillis).apply()
    }

    fun fetchMedicationStatusTime(medId: Int): Long? {
        val v = prefs.getLong(medStatusKey(medId), -1L)
        return if (v <= 0L) null else v
    }

    fun clearMedicationStatusTime(medId: Int) {
        prefs.edit().remove(medStatusKey(medId)).apply()
    }

    // ---------------- NOTIFICATIONS HELPERS ----------------
    fun addNotification(message: String) {
        val current = prefs.getStringSet(KEY_NOTIFICATIONS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val entry = "${System.currentTimeMillis()}|$message"
        current.add(entry)
        prefs.edit().putStringSet(KEY_NOTIFICATIONS, current).apply()
    }

    fun fetchNotifications(): Set<String> {
        return prefs.getStringSet(KEY_NOTIFICATIONS, emptySet()) ?: emptySet()
    }

    fun clearNotifications() {
        prefs.edit().remove(KEY_NOTIFICATIONS).apply()
    }

    // Log OTP send failures locally for debugging/telemetry
    fun logOtpFailure(message: String) {
        val current = prefs.getStringSet(KEY_OTP_ERRORS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val entry = "${System.currentTimeMillis()}|$message"
        current.add(entry)
        prefs.edit().putStringSet(KEY_OTP_ERRORS, current).apply()
    }

    fun fetchOtpFailures(): Set<String> {
        return prefs.getStringSet(KEY_OTP_ERRORS, emptySet()) ?: emptySet()
    }

    fun clearOtpFailures() {
        prefs.edit().remove(KEY_OTP_ERRORS).apply()
    }

    // ---------------- TOKEN ----------------
    fun saveAuthToken(token: String) {
        prefs.edit().putString(AUTH_TOKEN, token).apply()
    }

    fun fetchAuthToken(): String? {
        return prefs.getString(AUTH_TOKEN, null)
    }

    fun clearAuthToken() {
        prefs.edit().remove(AUTH_TOKEN).apply()
    }

    // ---------------- USER ROLE ----------------
    fun saveUserRole(role: String) {
        prefs.edit().putString(USER_ROLE, role).apply()
    }

    fun fetchUserRole(): String? {
        return prefs.getString(USER_ROLE, null)
    }

    fun clearUserRole() {
        prefs.edit().remove(USER_ROLE).apply()
    }

    // ---------------- PROFILE IMAGE ----------------
    fun saveProfileImageUri(uri: String?) {
        if (uri == null) prefs.edit().remove(PROFILE_IMAGE_URI).apply()
        else prefs.edit().putString(PROFILE_IMAGE_URI, uri).apply()
    }

    fun fetchProfileImageUri(): String? {
        return prefs.getString(PROFILE_IMAGE_URI, null)
    }

    // ---------------- CLEAR ALL ----------------
    fun clearSession() {
        // Preserve the deleted patient list across logouts so doctor-hidden links remain hidden
        val deleted = fetchDeletedPatientIds()
        prefs.edit().clear().apply()
        if (deleted.isNotEmpty()) prefs.edit().putStringSet(KEY_DELETED_PATIENTS, deleted).apply()
    }

    // ---------------- REAL-TIME SHARING PREF ----------------
    fun saveSharingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHARING_ENABLED, enabled).apply()
    }

    fun fetchSharingEnabled(): Boolean {
        return prefs.getBoolean(KEY_SHARING_ENABLED, false)
    }

    // Per-user sharing helpers (store under key with email suffix)
    fun saveSharingEnabledFor(email: String, enabled: Boolean) {
        val key = "${KEY_SHARING_ENABLED}_$email"
        prefs.edit().putBoolean(key, enabled).apply()
    }

    fun fetchSharingEnabledFor(email: String): Boolean {
        val key = "${KEY_SHARING_ENABLED}_$email"
        return prefs.getBoolean(key, prefs.getBoolean(KEY_SHARING_ENABLED, false))
    }

    // Save current signed-in user's email (used for per-user prefs)
    fun saveUserEmail(email: String) {
        prefs.edit().putString(KEY_USER_EMAIL, email).apply()
    }

    fun fetchUserEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }

    fun clearUserEmail() {
        prefs.edit().remove(KEY_USER_EMAIL).apply()
    }
}
