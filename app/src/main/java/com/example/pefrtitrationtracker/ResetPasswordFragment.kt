package com.example.pefrtitrationtracker

import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.pefrtitrationtracker.databinding.FragmentResetPasswordBinding
import com.example.pefrtitrationtracker.network.RetrofitClient
import kotlinx.coroutines.launch
import androidx.drawerlayout.widget.DrawerLayout

class ResetPasswordFragment : Fragment() {

    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!
    private val args: ResetPasswordFragmentArgs by navArgs()

    // OTP timer fields lifted to class scope so we can cancel from lifecycle callbacks
    private var countdown: CountDownTimer? = null
    private val OTP_TIMEOUT_MS: Long = 2 * 60 * 1000 // 2 minutes

    private fun cancelTimer() {
        countdown?.cancel()
        countdown = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResetPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide bottom navigation and lock drawer while on auth screens
        try {
            val bottom = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.example.pefrtitrationtracker.R.id.bottom_navigation)
            bottom?.visibility = View.GONE
        } catch (_: Exception) {}
        try {
            val drawer = requireActivity().findViewById<DrawerLayout>(com.example.pefrtitrationtracker.R.id.drawer_layout)
            drawer?.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        } catch (_: Exception) {}

        val email = args.email
        binding.textEmail.text = email

        // OTP timer (uses class-scope countdown/timeout)

        binding.editTextOtp.filters = arrayOf(InputFilter.LengthFilter(6))
        binding.editTextOtp.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        // Allow resend even while timer is running (some emails may be delayed)
        binding.buttonResend.isEnabled = true

        // Initially hide password inputs until OTP is verified
        binding.textInputPassword.visibility = View.GONE
        binding.textInputConfirmPassword.visibility = View.GONE
        var otpVerified = false

        fun checkEnableSubmit() {
            val timerNotExpired = binding.textTimer.text != "00:00"
            if (!otpVerified) {
                val otpOk = binding.editTextOtp.text?.length == 6
                binding.buttonSubmit.isEnabled = otpOk && timerNotExpired
                if (binding.buttonSubmit.isEnabled) binding.buttonSubmit.text = "Verify OTP"
            } else {
                val newPass = binding.editTextPassword.text.toString()
                val confirm = binding.editTextConfirmPassword.text.toString()
                val ok = newPass.length >= 8 && newPass == confirm
                binding.buttonSubmit.isEnabled = ok
                binding.buttonSubmit.text = "Reset Password"
            }
        }

        binding.editTextOtp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkEnableSubmit()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // enable/disable based on password fields after OTP verified
        binding.editTextPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { checkEnableSubmit() }
            override fun afterTextChanged(s: Editable?) {}
        })
        binding.editTextConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { checkEnableSubmit() }
            override fun afterTextChanged(s: Editable?) {}
        })

        fun startTimer() {
            // keep resend enabled while timer is running so user can request resend immediately
            countdown?.cancel()
            countdown = object : CountDownTimer(OTP_TIMEOUT_MS, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val seconds = millisUntilFinished / 1000
                    val mm = seconds / 60
                    val ss = seconds % 60
                    binding.textTimer.text = String.format("%02d:%02d", mm, ss)
                    checkEnableSubmit()
                }

                override fun onFinish() {
                    binding.textTimer.text = "00:00"
                    binding.buttonResend.isEnabled = true
                    checkEnableSubmit()
                }
            }
            countdown?.start()
        }

        startTimer()

        binding.buttonResend.setOnClickListener {
            // hide keyboard on user action
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)

            binding.buttonResend.isEnabled = false
            val orig = binding.buttonResend.text
            binding.buttonResend.text = "Resending..."
            lifecycleScope.launch {
                try {
                    val resp = RetrofitClient.apiService.forgotPassword(email)
                    if (resp.isSuccessful) {
                        android.widget.Toast.makeText(requireContext(), "OTP resent", android.widget.Toast.LENGTH_SHORT).show()
                        startTimer()
                    } else {
                        android.widget.Toast.makeText(requireContext(), "Failed to resend OTP", android.widget.Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(requireContext(), "Network error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    binding.buttonResend.text = orig
                    binding.buttonResend.isEnabled = true
                }
            }
        }
        binding.buttonSubmit.setOnClickListener {
            // hide keyboard on user action
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)

            if (!otpVerified) {
                val otp = binding.editTextOtp.text.toString().trim()
                if (otp.length != 6) {
                    android.widget.Toast.makeText(requireContext(), "Enter the 6-digit OTP", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // disable while verifying
                binding.buttonSubmit.isEnabled = false
                val orig = binding.buttonSubmit.text
                binding.buttonSubmit.text = "Verifying..."

                lifecycleScope.launch {
                    try {
                        val resp = RetrofitClient.apiService.verifyForgotOtp(email, otp)
                        if (resp.isSuccessful) {
                            otpVerified = true
                            // only update UI if fragment still has a view
                            if (isAdded && _binding != null) {
                                binding.textInputPassword.visibility = View.VISIBLE
                                binding.textInputConfirmPassword.visibility = View.VISIBLE
                                android.widget.Toast.makeText(requireContext(), "OTP verified. Enter a new password.", android.widget.Toast.LENGTH_SHORT).show()
                                // focus password input and show keyboard to help the user
                                binding.editTextPassword.requestFocus()
                                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
                            }
                        } else {
                            val body = try { resp.errorBody()?.string() } catch (_: Exception) { null }
                            if (body?.contains("expired", true) == true) {
                                if (isAdded) android.widget.Toast.makeText(requireContext(), "OTP expired. Please resend.", android.widget.Toast.LENGTH_LONG).show()
                                if (isAdded && _binding != null) binding.buttonResend.isEnabled = true
                            } else {
                                if (isAdded) android.widget.Toast.makeText(requireContext(), "Invalid OTP.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        if (isAdded) android.widget.Toast.makeText(requireContext(), "Network error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    } finally {
                        if (isAdded && _binding != null) {
                            binding.buttonSubmit.isEnabled = true
                            binding.buttonSubmit.text = orig
                            checkEnableSubmit()
                        }
                    }
                }
                return@setOnClickListener
            }

            // OTP is verified -> perform reset
            val otp = binding.editTextOtp.text.toString().trim()
            val newPass = binding.editTextPassword.text.toString()
            val confirm = binding.editTextConfirmPassword.text.toString()
            if (newPass.length < 8) {
                binding.textInputPassword.error = "Password must be at least 8 characters"
                return@setOnClickListener
            }
            if (newPass != confirm) {
                android.widget.Toast.makeText(requireContext(), "Passwords do not match", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // disable while submitting
            binding.buttonSubmit.isEnabled = false
            val orig = binding.buttonSubmit.text
            binding.buttonSubmit.text = "Resetting..."

            lifecycleScope.launch {
                try {
                    val resp = RetrofitClient.apiService.resetPassword(email, otp, newPass)
                    if (resp.isSuccessful) {
                        cancelTimer()
                        if (isAdded) android.widget.Toast.makeText(requireContext(), "Password updated. Please login.", android.widget.Toast.LENGTH_LONG).show()
                        // navigate only if still attached
                        if (isAdded && _binding != null) {
                            findNavController().navigate(ResetPasswordFragmentDirections.actionResetPasswordFragmentToLoginFragment())
                        }
                    } else {
                        val body = try { resp.errorBody()?.string() } catch (_: Exception) { null }
                        if (body?.contains("expired", true) == true) {
                            if (isAdded) android.widget.Toast.makeText(requireContext(), "OTP expired. Please resend.", android.widget.Toast.LENGTH_LONG).show()
                            if (isAdded && _binding != null) binding.buttonResend.isEnabled = true
                        } else {
                            if (isAdded) android.widget.Toast.makeText(requireContext(), "Reset failed.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    if (isAdded) android.widget.Toast.makeText(requireContext(), "Network error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    if (isAdded && _binding != null) {
                        binding.buttonSubmit.isEnabled = true
                        binding.buttonSubmit.text = orig
                    }
                }
            }
        }

        binding.buttonSubmit.isEnabled = false
        checkEnableSubmit()

        binding.buttonBack.setOnClickListener {
            cancelTimer()
            findNavController().popBackStack()
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        // ensure timer can't fire after view destroyed
        cancelTimer()
        // Restore bottom navigation and drawer
        try {
            val bottom = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.example.pefrtitrationtracker.R.id.bottom_navigation)
            bottom?.visibility = View.VISIBLE
        } catch (_: Exception) {}
        try {
            val drawer = requireActivity().findViewById<DrawerLayout>(com.example.pefrtitrationtracker.R.id.drawer_layout)
            drawer?.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        } catch (_: Exception) {}
        _binding = null
    }
}
