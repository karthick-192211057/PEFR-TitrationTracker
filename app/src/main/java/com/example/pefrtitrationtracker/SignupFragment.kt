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

        // Pre-fill email if it was passed from login (new email not found)
        if (!SignupCache.email.isNullOrEmpty()) {
            binding.editTextEmail.setText(SignupCache.email)
            SignupCache.email = null  // Clear cache after use
        }

        // Setup role dropdown
        setupRoleDropdown()

        binding.buttonSignup.safeClick {

            val name = binding.editTextName.text.toString().trim()
            val email = binding.editTextEmail.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()
            val confirmPassword = binding.editTextConfirmPassword.text.toString().trim()
            val role = binding.autoCompleteRole.text.toString().trim()
            
            val agreedToTerms = binding.checkboxAgreeTerms.isChecked
            val agreedToPrivacy = binding.checkboxAgreePrivacy.isChecked

            // ✅ Empty field check
            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@safeClick
            }

            // ✅ Email validation
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.editTextEmail.error = "Invalid email id"
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
            if (role.isEmpty()) {
                Toast.makeText(requireContext(), "Please select a role", Toast.LENGTH_SHORT).show()
                return@safeClick
            }

            // ✅ Terms and Privacy Policy check
            if (!agreedToTerms) {
                Toast.makeText(requireContext(), "Please agree to Terms & Conditions", Toast.LENGTH_SHORT).show()
                binding.checkboxAgreeTerms.requestFocus()
                return@safeClick
            }

            if (!agreedToPrivacy) {
                Toast.makeText(requireContext(), "Please agree to Privacy Policy", Toast.LENGTH_SHORT).show()
                binding.checkboxAgreePrivacy.requestFocus()
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
                                .setNegativeButton("Cancel", null)
                                .show()
                        } else {
                            val errMsg = result.message ?: "Invalid email id or network error. Please check and try again."
                            Toast.makeText(requireContext(), errMsg, Toast.LENGTH_LONG).show()
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

        // Terms & Conditions Link
        binding.textTermsLink.setOnClickListener {
            showTermsAndConditions()
        }

        // Privacy Policy Link
        binding.textPrivacyLink.setOnClickListener {
            showPrivacyPolicy()
        }

        // Update button state when checkboxes change
        binding.checkboxAgreeTerms.setOnCheckedChangeListener { _, _ -> updateSignupButtonState() }
        binding.checkboxAgreePrivacy.setOnCheckedChangeListener { _, _ -> updateSignupButtonState() }
    }

    private fun setupRoleDropdown() {
        val roles = arrayOf("Patient", "Doctor")
        val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, roles)
        binding.autoCompleteRole.setAdapter(adapter)
    }

    private fun updateSignupButtonState() {
        val agreedBoth = binding.checkboxAgreeTerms.isChecked && binding.checkboxAgreePrivacy.isChecked
        binding.buttonSignup.isEnabled = agreedBoth
        binding.buttonSignup.alpha = if (agreedBoth) 1.0f else 0.5f
    }

    private fun showTermsAndConditions() {
        val termsText = """
            TERMS AND CONDITIONS
            
            Last Updated: February 2, 2026
            
            1. ACCEPTANCE OF TERMS
            By registering for and using the Asthma Manager App, you agree to comply with and be bound by these Terms and Conditions. If you do not agree to these terms, please do not use our app.
            
            2. USE LICENSE
            Permission is granted to temporarily download one copy of materials (information or software) from the Asthma Manager App for personal, non-commercial transitory viewing only. This is the grant of a license, not a transfer of title, and under this license you may not:
            - Modify or copy the materials
            - Use the materials for any commercial purpose or for any public display
            - Attempt to decompile or reverse engineer any software contained on the app
            - Remove any copyright or other proprietary notations from the materials
            - Transfer the materials to another person or "mirror" the materials on any other server
            
            3. DISCLAIMER
            The materials on the Asthma Manager App are provided on an 'as is' basis. We make no warranties, expressed or implied, and hereby disclaim and negate all other warranties including, without limitation, implied warranties or conditions of merchantability, fitness for a particular purpose, or non-infringement of intellectual property or other violation of rights.
            
            4. LIMITATIONS
            In no event shall the Asthma Manager App or its suppliers be liable for any damages (including, without limitation, damages for loss of data or profit, or due to business interruption) arising out of the use or inability to use the materials on the Asthma Manager App.
            
            5. ACCURACY OF MATERIALS
            The materials appearing on the Asthma Manager App could include technical, typographical, or photographic errors. The Asthma Manager App does not warrant that any of the materials on its app are accurate, complete, or current.
            
            6. MODIFICATIONS
            The Asthma Manager App may revise these terms and conditions for its app at any time without notice. By using this app, you are agreeing to be bound by the then current version of these terms and conditions.
            
            7. GOVERNING LAW
            These terms and conditions are governed by and construed in accordance with the laws of the jurisdiction in which the Asthma Manager App operates, and you irrevocably submit to the exclusive jurisdiction of the courts in that location.
            
            8. CONTACT US
            If you have any questions about these Terms and Conditions, please contact us at support@asthmamanagerapp.com.
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Terms & Conditions")
            .setMessage(termsText)
            .setPositiveButton("I Agree") { _, _ ->
                binding.checkboxAgreeTerms.isChecked = true
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPrivacyPolicy() {
        val privacyText = """
            PRIVACY POLICY
            
            Last Updated: February 2, 2026
            
            1. INTRODUCTION
            The Asthma Manager App ("we", "our", or "us") is committed to protecting your privacy. This Privacy Policy explains how we collect, use, disclose, and safeguard your information.
            
            2. INFORMATION WE COLLECT
            We may collect information about you in a variety of ways. The information we may collect on the app includes:
            - Personal Data: Name, email address, phone number, date of birth
            - Health Data: PEFR readings, symptom information, medication information
            - Device Information: Device ID, device type, operating system version
            - Usage Data: App usage patterns, features accessed, crash reports
            
            3. USE OF YOUR INFORMATION
            Having accurate, up-to-date information about you permits us to provide you with a smooth, efficient, and customized experience. Specifically, we may use information collected about you via the app to:
            - Create and manage your account
            - Send you important notifications and updates
            - Improve our app and services
            - Respond to your inquiries and support requests
            - Generate analytics and insights about app usage
            - Comply with legal obligations
            
            4. DISCLOSURE OF YOUR INFORMATION
            We may share your information in the following situations:
            - With your healthcare providers (if you authorize)
            - With third-party service providers who assist us in operating our app
            - When required by law or to protect our legal rights
            - In connection with a merger, acquisition, or sale of assets
            
            5. SECURITY OF YOUR INFORMATION
            We use administrative, technical, and physical security measures to protect your personal information. However, no method of transmission over the Internet or electronic storage is completely secure.
            
            6. RETENTION OF YOUR INFORMATION
            We will retain your personal information for as long as your account is active or as needed to provide you services, unless you request deletion.
            
            7. YOUR RIGHTS
            You have the right to:
            - Access your personal information
            - Correct inaccurate information
            - Request deletion of your information
            - Opt-out of certain communications
            - Data portability
            
            8. THIRD-PARTY LINKS
            The app may contain links to third-party websites. We are not responsible for the privacy practices of these websites. We encourage you to review their privacy policies.
            
            9. CONTACT US
            If you have questions about this Privacy Policy, please contact us at privacy@asthmamanagerapp.com.
            
            10. CHANGES TO THIS PRIVACY POLICY
            We may update this Privacy Policy from time to time. We will notify you of any changes by posting the new Privacy Policy on the app.
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Privacy Policy")
            .setMessage(privacyText)
            .setPositiveButton("I Agree") { _, _ ->
                binding.checkboxAgreePrivacy.isChecked = true
            }
            .setNegativeButton("Cancel", null)
            .show()
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
