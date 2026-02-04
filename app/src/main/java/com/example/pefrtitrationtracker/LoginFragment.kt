package com.example.pefrtitrationtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.pefrtitrationtracker.databinding.FragmentLoginBinding
import com.example.pefrtitrationtracker.network.RetrofitClient
import com.example.pefrtitrationtracker.network.SessionManager
import com.example.pefrtitrationtracker.utils.UserSyncManager
import com.example.pefrtitrationtracker.utils.AppCache
import com.example.pefrtitrationtracker.utils.safeClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.drawerlayout.widget.DrawerLayout

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
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

        setupRoleDropdown()

        binding.buttonLogin.safeClick {
            val email = binding.editTextEmail.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()
            val selectedRole = binding.dropdownRole.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@safeClick
            }

            if (selectedRole == "Select Role" || selectedRole.isEmpty()) {
                Toast.makeText(requireContext(), "Please select a role", Toast.LENGTH_SHORT).show()
                return@safeClick
            }

            // disable button while login in progress
            binding.buttonLogin.isEnabled = false
            lifecycleScope.launch {
                try {
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.apiService.login(email, password)
                    }

                    if (response.isSuccessful && response.body() != null) {
                        val tokenResponse = response.body()!!
                        val session = SessionManager(requireContext())

                        // Ensure selected role matches server-assigned role
                        val serverRole = tokenResponse.userRole ?: ""
                        if (!serverRole.equals(selectedRole, ignoreCase = true)) {
                            Toast.makeText(requireContext(), "Selected role is wrong please select the correct role", Toast.LENGTH_SHORT).show()
                            binding.buttonLogin.isEnabled = true
                            binding.buttonLogin.text = "Login"
                            return@launch
                        }

                        session.saveAuthToken(tokenResponse.accessToken)
                        session.saveUserRole(serverRole)

                        // Run a quick post-login sync so the app UI reflects server state
                        // (medications, PEFRs, symptoms, profile). Best-effort; navigation
                        // proceeds regardless, but cached data will be available for fragments.
                        binding.buttonLogin.text = "Signing in..."
                        try {
                            val syncOk = UserSyncManager.syncAll()
                            if (!syncOk) {
                                // profile fetch failed; still proceed but log
                            }
                        } catch (e: Exception) {
                            // swallow — we already saved token; fragments will fetch on resume
                        } finally {
                            binding.buttonLogin.text = "Login"
                        }

                        // Save user email for per-account preferences. Prefer the synced profile, fall back to entered email.
                        try {
                            val session = SessionManager(requireContext())
                            val profileEmail = com.example.pefrtitrationtracker.utils.AppCache.profile?.email
                            session.saveUserEmail(profileEmail ?: email)
                            // After login, ensure reminders are scheduled for this account if saved
                            try {
                                val saved = com.example.pefrtitrationtracker.reminders.ReminderStore.load(requireContext(), session.fetchUserEmail())
                                if (saved.enabled) {
                                    com.example.pefrtitrationtracker.reminders.ReminderScheduler(requireContext()).schedule(saved.hour, saved.minute, saved.frequency, saved.targetPefr)
                                }
                            } catch (_: Exception) {}
                        } catch (_: Exception) {}

                        if (serverRole.equals("Doctor", ignoreCase = true)) {
                            findNavController().navigate(
                                LoginFragmentDirections.actionLoginFragmentToDoctorDashboardFragment()
                            )
                        } else {
                            findNavController().navigate(
                                LoginFragmentDirections.actionLoginFragmentToHomeDashboardFragment()
                            )
                        }
                        // After navigation, ensure current FCM token (if any) is registered with backend
                        try {
                            val session = SessionManager(requireContext())
                            val auth = session.fetchAuthToken()
                            if (!auth.isNullOrBlank()) {
                                // get current FCM token and register
                                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val t = task.result
                                        if (!t.isNullOrBlank()) {
                                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                                try {
                                                    val resp = RetrofitClient.apiService.registerDeviceToken(t)
                                                    if (resp.isSuccessful) {
                                                        android.util.Log.d("Login", "Device token registered after login")
                                                    } else {
                                                        android.util.Log.d("Login", "Token register failed: ${resp.code()}")
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.d("Login", "Exception registering token: ${e.message}")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // ignore
                        }

                    } else {
                        val errorCode = response.code()
                        if (errorCode == 401 || errorCode == 400) {
                            // Parse error message from response
                            val errorMessage = try {
                                val errorBody = response.errorBody()?.string()
                                if (errorBody != null) {
                                    // Extract detail from JSON error response
                                    val jsonObject = org.json.JSONObject(errorBody)
                                    jsonObject.optString("detail", "Login failed")
                                } else {
                                    "Login failed"
                                }
                            } catch (e: Exception) {
                                "Login failed"
                            }

                            when {
                                errorMessage.contains("Incorrect password", ignoreCase = true) -> {
                                    Toast.makeText(requireContext(), "Incorrect password", Toast.LENGTH_SHORT).show()
                                }
                                errorMessage.contains("User not found", ignoreCase = true) -> {
                                    // Email not found - offer signup option
                                    val notFoundEmail = email  // Capture email for use in dialog
                                    val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                    builder.setTitle("Email Not Found")
                                    builder.setMessage("Mail ID not found.\n\nWould you like to sign up using this email?")
                                    builder.setPositiveButton("Sign Up") { _, _ ->
                                        // Pass email to signup fragment
                                        SignupCache.email = notFoundEmail
                                        findNavController().navigate(
                                            LoginFragmentDirections.actionLoginFragmentToSignupFragment()
                                        )
                                    }
                                    builder.setNegativeButton("Cancel", null)
                                    builder.show()
                                }
                                else -> {
                                    Toast.makeText(requireContext(), "Login failed. Check credentials.", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Toast.makeText(requireContext(), "Login failed. Check credentials.", Toast.LENGTH_LONG).show()
                        }
                        binding.buttonLogin.isEnabled = true
                    }

                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.buttonLogin.isEnabled = true
                }
            }
        }

        binding.textSignupLink.safeClick { findNavController().navigate(LoginFragmentDirections.actionLoginFragmentToSignupFragment()) }

        binding.textForgotPassword.safeClick { findNavController().navigate(LoginFragmentDirections.actionLoginFragmentToForgotPasswordFragment()) }
    }

    override fun onResume() {
        super.onResume()
        // Ensure bottom nav hidden and drawer locked when fragment becomes visible
        try {
            val bottom = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.example.pefrtitrationtracker.R.id.bottom_navigation)
            bottom?.visibility = View.GONE
        } catch (_: Exception) {}
        try {
            val drawer = requireActivity().findViewById<androidx.drawerlayout.widget.DrawerLayout>(com.example.pefrtitrationtracker.R.id.drawer_layout)
            drawer?.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        } catch (_: Exception) {}
    }
    private fun hideKeyboard() {
        val imm = requireActivity().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(requireView().windowToken, 0)
    }


    private fun setupRoleDropdown() {
        val roles = listOf("Patient", "Doctor")
        val adapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, roles)
        binding.dropdownRole.setAdapter(adapter)

        // ❌ Disable keyboard
        binding.dropdownRole.inputType = 0

        // ❌ Hide cursor
        binding.dropdownRole.isCursorVisible = false

        // ❌ Prevent manual typing
        binding.dropdownRole.keyListener = null

        // Ensure single-tap selection: show dropdown on click, touch and focus
        binding.dropdownRole.setOnClickListener {
            hideKeyboard()
            binding.dropdownRole.showDropDown()
        }

        binding.dropdownRole.setOnTouchListener { v, event ->
            // showDropDown on touch so a single tap opens the list immediately
            binding.dropdownRole.showDropDown()
            // allow normal handling (so item selection still works)
            false
        }

        binding.dropdownRole.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.dropdownRole.showDropDown()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        // Restore bottom navigation and drawer
        try {
            val drawer = requireActivity().findViewById<DrawerLayout>(com.example.pefrtitrationtracker.R.id.drawer_layout)
            drawer?.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        } catch (_: Exception) {}
        _binding = null
    }
}
