package com.example.pefrtitrationtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import com.example.pefrtitrationtracker.utils.safeClick
import com.example.pefrtitrationtracker.databinding.FragmentForgotPasswordBinding
import androidx.drawerlayout.widget.DrawerLayout

class ForgotPasswordFragment : Fragment() {

    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
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

        // Navigate back to Login
        binding.imageBack.safeClick { findNavController().popBackStack() }

        // Navigate back to Login
        binding.textLoginLink.safeClick { findNavController().navigate(ForgotPasswordFragmentDirections.actionForgotPasswordFragmentToLoginFragment()) }

        // Send OTP and navigate to Reset Password on success
        binding.buttonSubmit.safeClick {
            val email = binding.editTextEmail.text.toString().trim()
            if (email.isEmpty()) {
                binding.editTextEmail.error = "Enter your registered email"
                return@safeClick
            }

            // disable button while sending
            binding.buttonSubmit.isEnabled = false
            val orig = binding.buttonSubmit.text
            binding.buttonSubmit.text = "Sending OTP..."

            // call API to send forgot-password OTP
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val resp = com.example.pefrtitrationtracker.network.RetrofitClient.apiService.forgotPassword(email)
                    if (resp.isSuccessful) {
                        android.widget.Toast.makeText(requireContext(), "OTP sent to $email", android.widget.Toast.LENGTH_LONG).show()
                        // navigate to reset password fragment with email arg
                        findNavController().navigate(ForgotPasswordFragmentDirections.actionForgotPasswordFragmentToResetPasswordFragment(email))
                    } else {
                        val err = try { resp.errorBody()?.string() } catch (_: Exception) { null }
                        androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("Failed to send OTP")
                            .setMessage("Failed to send OTP: ${err ?: resp.code()}. If you received the code (check logs), you can enter it below.")
                            .setPositiveButton("Retry") { _, _ -> binding.buttonSubmit.performClick() }
                            .setNeutralButton("Enter OTP") { _, _ ->
                                findNavController().navigate(ForgotPasswordFragmentDirections.actionForgotPasswordFragmentToResetPasswordFragment(email))
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                } catch (e: Exception) {
                    // Network/timeout — allow user to proceed to enter OTP manually (helpful when server prints OTP to logs)
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Network error")
                        .setMessage("Network error: ${e.message}. If you received the 6-digit OTP (e.g., from server logs), you can enter it to reset your password.")
                        .setPositiveButton("Retry") { _, _ -> binding.buttonSubmit.performClick() }
                        .setNeutralButton("Enter OTP") { _, _ ->
                            findNavController().navigate(ForgotPasswordFragmentDirections.actionForgotPasswordFragmentToResetPasswordFragment(email))
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } finally {
                    binding.buttonSubmit.isEnabled = true
                    binding.buttonSubmit.text = orig
                }
            }
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
