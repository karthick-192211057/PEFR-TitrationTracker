package com.example.pefrtitrationtracker

import android.content.Context
import android.util.Log
import com.example.pefrtitrationtracker.network.RetrofitClient
import com.example.pefrtitrationtracker.network.SessionManager
import kotlinx.coroutines.delay

object OtpSender {
    data class Result(val success: Boolean, val statusCode: Int? = null, val message: String? = null)

    // Attempts to send OTP with exponential backoff. Returns Result with details.
    suspend fun sendOtpWithRetry(context: Context, signupRequest: com.example.pefrtitrationtracker.network.SignupRequest, maxAttempts: Int = 3): Result {
        var attempt = 0
        var delayMs = 1000L
        while (attempt < maxAttempts) {
            try {
                attempt++
                val resp = RetrofitClient.apiService.signupSendOtp(signupRequest)
                if (resp.isSuccessful) {
                    Log.i("OtpSender", "OTP sent on attempt $attempt")
                    return Result(true, resp.code(), "OK")
                } else {
                    val code = resp.code()
                    val err = try { resp.errorBody()?.string() } catch (_: Exception) { null }
                    val msg = "OTP send failed (code=$code) ${err ?: ""}".trim()
                    Log.w("OtpSender", msg)
                    SessionManager(context).logOtpFailure(msg)

                    // If email already exists (409), stop retrying and return immediately with that code
                    if (code == 409) return Result(false, 409, "Email already exists")
                }
            } catch (e: Exception) {
                val msg = "Network exception on OTP send: ${e.message}"
                Log.w("OtpSender", msg, e)
                SessionManager(context).logOtpFailure(msg)
            }

            // backoff before retrying
            if (attempt < maxAttempts) {
                delay(delayMs)
                delayMs *= 2
            }
        }

        return Result(false, null, "Failed after retries")
    }
}
