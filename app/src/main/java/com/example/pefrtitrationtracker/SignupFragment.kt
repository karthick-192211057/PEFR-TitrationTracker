package com.example.pefrtitrationtracker

import android.util.Patterns
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.pefrtitrationtracker.databinding.FragmentSignupBinding
import com.example.pefrtitrationtracker.network.SignupRequest
import com.example.pefrtitrationtracker.SignupCache
import com.example.pefrtitrationtracker.network.RetrofitClient
import kotlinx.coroutines.launch
import com.example.pefrtitrationtracker.utils.safeClick
import androidx.drawerlayout.widget.DrawerLayout

class SignupFragment : Fragment() {

    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
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

        binding.buttonSignup.safeClick {

            val name = binding.editTextName.text.toString().trim()
            val email = binding.editTextEmail.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()
            val confirmPassword = binding.editTextConfirmPassword.text.toString().trim()
            val role = binding.spinnerRole.selectedItem.toString()

            // ✅ Empty field check
            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@safeClick
            }

            // ✅ Email validation
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.editTextEmail.error = "Enter a valid email address"
                binding.editTextEmail.requestFocus()
                return@safeClick
            }

            // ✅ Strong password validation
            val passwordPattern = Regex(
                "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@\$!%*#?&])[A-Za-z\\d@\$!%*#?&]{8,}$"
            )

            if (!passwordPattern.matches(password)) {
                binding.editTextPassword.error =
                    "Password must be at least 8 characters and include a letter, number, and special character"
                binding.editTextPassword.requestFocus()
                return@safeClick
            }

            // ✅ Password match check
            if (password != confirmPassword) {
                Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@safeClick
            }

            // ✅ Role check
            if (role == "Select Role") {
                Toast.makeText(requireContext(), "Please select a role", Toast.LENGTH_SHORT).show()
                return@safeClick
            }

            val signupRequest = SignupRequest(
                email = email,
                password = password,
                role = role,
                fullName = name
            )

            // Cache the signup payload so VerifyOtpFragment can resend OTP if needed.
            SignupCache.lastSignupRequest = signupRequest

            // Disable button and show sending state
            binding.buttonSignup.isEnabled = false
            val originalText = binding.buttonSignup.text
            binding.buttonSignup.text = "Sending OTP..."

            lifecycleScope.launch {
                try {
                    val result = OtpSender.sendOtpWithRetry(requireContext(), signupRequest, 3)
                    if (result.success) {
                        Toast.makeText(requireContext(), "OTP sent to your email", Toast.LENGTH_LONG).show()
                        findNavController().navigate(
                            SignupFragmentDirections.actionSignupFragmentToVerifyOtpFragment(email)
                        )
                    } else {
                        if (result.statusCode == 409) {
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("Email already registered")
                                .setMessage("An account with this email already exists. Please login or use a different email.")
                                .setPositiveButton("Login") { _, _ ->
                                    findNavController()
                                        .navigate(SignupFragmentDirections.actionSignupFragmentToLoginFragment())
                                }
                                .setNeutralButton("Enter OTP") { _, _ ->
                                    findNavController().navigate(
                                        SignupFragmentDirections.actionSignupFragmentToVerifyOtpFragment(email)
                                    )
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        } else {
                            val errMsg = result.message ?: "We couldn't send the OTP. Please check your network connection and try again."
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("Failed to send OTP")
                                .setMessage(
                                    errMsg +
                                            "\n\nIf you received the OTP via another channel (or for testing), you can enter it directly."
                                )
                                .setPositiveButton("Retry") { _, _ ->
                                    binding.buttonSignup.performClick()
                                }
                                .setNeutralButton("Enter OTP") { _, _ ->
                                    findNavController().navigate(
                                        SignupFragmentDirections.actionSignupFragmentToVerifyOtpFragment(email)
                                    )
                                }
                                .setNegativeButton("Check Network") { _, _ ->
                                    try {
                                        startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                                    } catch (_: Exception) {
                                    }
                                }
                                .show()
                        }
                    }
                } finally {
                    binding.buttonSignup.isEnabled = true
                    binding.buttonSignup.text = originalText
                }
            }
        }

        binding.textLoginLink.safeClick {
            findNavController().navigate(SignupFragmentDirections.actionSignupFragmentToLoginFragment())
        }

        binding.imageBack.safeClick { findNavController().popBackStack() }
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
