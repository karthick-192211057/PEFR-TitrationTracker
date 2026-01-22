package com.example.pefrtitrationtracker

import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.pefrtitrationtracker.databinding.FragmentVerifyOtpBinding
import com.example.pefrtitrationtracker.network.SignupRequest
import com.example.pefrtitrationtracker.network.RetrofitClient
import kotlinx.coroutines.launch
import com.example.pefrtitrationtracker.utils.safeClick

class VerifyOtpFragment : Fragment() {

    private var _binding: FragmentVerifyOtpBinding? = null
    private val binding get() = _binding!!

    private val args: VerifyOtpFragmentArgs by navArgs()

    private var countdown: CountDownTimer? = null
    private val OTP_TIMEOUT_MS: Long = 2 * 60 * 1000 // 2 minutes

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVerifyOtpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide bottom navigation and lock drawer on the OTP verification screen
        try {
            val bottom = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.example.pefrtitrationtracker.R.id.bottom_navigation)
            bottom?.visibility = View.GONE
        } catch (_: Exception) {
            // ignore if not found
        }
        try {
            val drawer = requireActivity().findViewById<androidx.drawerlayout.widget.DrawerLayout>(com.example.pefrtitrationtracker.R.id.drawer_layout)
            drawer?.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        } catch (_: Exception) {}

        // Also enforce this when coming back from other screens
        try {
            val bottom = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.example.pefrtitrationtracker.R.id.bottom_navigation)
            bottom?.visibility = View.GONE
        } catch (_: Exception) {}

        val email = args.email
        binding.textEmail.text = email

        // Prompt user and focus OTP input
        android.widget.Toast.makeText(requireContext(), "OTP sent to $email. Enter the 6-digit code.", android.widget.Toast.LENGTH_LONG).show()
        binding.editTextOtp.requestFocus()

        // OTP input constraints
        binding.editTextOtp.filters = arrayOf(InputFilter.LengthFilter(6))
        binding.editTextOtp.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        binding.buttonVerify.isEnabled = false
        // Allow resend even while timer is running (some emails may be delayed)
        binding.buttonResend.isEnabled = true

        binding.editTextOtp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.buttonVerify.isEnabled = (s?.length == 6)
                if (binding.textError.visibility == View.VISIBLE) {
                    binding.textError.visibility = View.GONE
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        startTimer()

        binding.buttonResend.safeClick {
            // Resend OTP: use cached signup payload if available
            val cached: SignupRequest? = SignupCache.lastSignupRequest
            if (cached == null || cached.email != email) {
                Toast.makeText(requireContext(), "Cannot resend OTP — please go back and try again", Toast.LENGTH_LONG).show()
                return@safeClick
            }

            // disable resend while sending
            binding.buttonResend.isEnabled = false
            val origText = binding.buttonResend.text
            binding.buttonResend.text = "Resending..."
            lifecycleScope.launch {
                try {
                    val result = OtpSender.sendOtpWithRetry(requireContext(), cached, 3)
                    if (result.success) {
                        Toast.makeText(requireContext(), "OTP resent", Toast.LENGTH_SHORT).show()
                        // restart the validation timer
                        startTimer()
                    } else {
                        if (result.statusCode == 409) {
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("Email already registered")
                                .setMessage("An account with this email already exists. Please login or use a different email.")
                                .setPositiveButton("Login") { _, _ ->
                                    findNavController()
                                        .navigate(VerifyOtpFragmentDirections.actionVerifyOtpFragmentToLoginFragment())
                                }
                                .setNeutralButton("Enter OTP") { _, _ -> /* stay on screen to enter OTP manually */ }
                                .setNegativeButton("Cancel", null)
                                .show()
                        } else {
                            val errMsg = result.message ?: "Could not resend OTP after multiple attempts. Check your network and try again."
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("Failed to resend OTP")
                                .setMessage(errMsg)
                                .setPositiveButton("Retry") { _, _ -> binding.buttonResend.performClick() }
                                .setNeutralButton("Network") { _, _ -> try { startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) } catch (_: Exception) {} }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                } finally {
                    binding.buttonResend.text = origText
                    binding.buttonResend.isEnabled = true
                }
            }
        }

        binding.buttonVerify.safeClick {
            val otp = binding.editTextOtp.text.toString().trim()
            if (otp.length != 6) return@safeClick
            // show verifying state
            binding.buttonVerify.isEnabled = false
            val orig = binding.buttonVerify.text
            binding.buttonVerify.text = "Verifying..."

            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.apiService.verifySignupOtp(email, otp)
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "Signup verified. Please login.", Toast.LENGTH_LONG).show()
                        // Clear cached signup data
                        SignupCache.lastSignupRequest = null
                        findNavController().navigate(VerifyOtpFragmentDirections.actionVerifyOtpFragmentToLoginFragment())
                    } else {
                        val body = response.errorBody()?.string() ?: ""
                        if (body.contains("expired", true)) {
                            binding.textError.visibility = View.VISIBLE
                            binding.textError.text = "OTP expired. Please resend."
                            binding.buttonVerify.isEnabled = false
                            binding.buttonResend.isEnabled = true
                            countdown?.cancel()
                        } else {
                            binding.textError.visibility = View.VISIBLE
                            binding.textError.text = "Invalid OTP. Try again."
                            binding.editTextOtp.text?.clear()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    binding.buttonVerify.text = orig
                    // only re-enable if OTP not complete or timer still running
                    binding.buttonVerify.isEnabled = (binding.editTextOtp.text?.length == 6 && binding.textTimer.text != "00:00")
                }
            }
        }

        binding.buttonBack.safeClick { findNavController().popBackStack() }
    }

    private fun startTimer() {
        binding.textError.visibility = View.GONE
        binding.buttonVerify.isEnabled = false
        // keep resend enabled while timer runs so user can trigger resend if needed

        countdown?.cancel()
        countdown = object : CountDownTimer(OTP_TIMEOUT_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                val mm = seconds / 60
                val ss = seconds % 60
                binding.textTimer.text = String.format("%02d:%02d", mm, ss)
                // enable verify only when 6 digits entered
                binding.buttonVerify.isEnabled = (binding.editTextOtp.text?.length == 6)
            }

            override fun onFinish() {
                binding.textTimer.text = "00:00"
                binding.textError.visibility = View.VISIBLE
                binding.textError.text = "OTP expired. Please resend."
                binding.buttonVerify.isEnabled = false
                binding.buttonResend.isEnabled = true
            }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countdown?.cancel()
        // Restore bottom navigation visibility and unlock drawer when leaving this screen
        try {
            val bottom = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.example.pefrtitrationtracker.R.id.bottom_navigation)
            bottom?.visibility = View.VISIBLE
        } catch (_: Exception) {}
        try {
            val drawer = requireActivity().findViewById<androidx.drawerlayout.widget.DrawerLayout>(com.example.pefrtitrationtracker.R.id.drawer_layout)
            drawer?.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)
        } catch (_: Exception) {}
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        try {
            val bottom = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.example.pefrtitrationtracker.R.id.bottom_navigation)
            bottom?.visibility = View.GONE
        } catch (_: Exception) {}
        try {
            val drawer = requireActivity().findViewById<androidx.drawerlayout.widget.DrawerLayout>(com.example.pefrtitrationtracker.R.id.drawer_layout)
            drawer?.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        } catch (_: Exception) {}
    }
}
