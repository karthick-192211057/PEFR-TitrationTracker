package com.example.pefrtitrationtracker

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.pefrtitrationtracker.databinding.FragmentEditDoctorProfileBinding
import com.example.pefrtitrationtracker.utils.safeClick
import com.example.pefrtitrationtracker.network.RetrofitClient
import com.example.pefrtitrationtracker.network.ProfileUpdateRequest
import kotlinx.coroutines.launch

class EditDoctorProfileFragment : Fragment() {

    private var _binding: FragmentEditDoctorProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditDoctorProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load real doctor profile from backend
        loadDoctorData()

        // Save button (use safeClick to avoid double taps)
        binding.buttonSave.safeClick {
            saveProfile()
        }

        // Ensure phone input doesn't accept leading zero and stays max 10 digits
        var editingPhone = false
        binding.inputPhone.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (editingPhone) return
                editingPhone = true
                s?.let {
                    var str = it.toString()
                    str = str.replace(Regex("\\D"), "")
                    while (str.startsWith("0")) str = str.removePrefix("0")
                    if (str.length > 10) str = str.substring(0, 10)
                    if (str != it.toString()) {
                        binding.inputPhone.setText(str)
                        binding.inputPhone.setSelection(str.length)
                    }
                }
                editingPhone = false
            }
        })
    }

    private fun loadDoctorData() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getMyProfile()

                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!

                    // Pre-fill fields
                    binding.inputName.setText(user.fullName ?: "")
                    binding.inputEmail.setText(user.email ?: "")
                    binding.inputPhone.setText(user.contactInfo ?: "")
                    binding.inputAddress.setText(user.address ?: "")
                }

            } catch (e: Exception) {
                Log.e("EditProfile", "Error: ${e.message}")
            }
        }
    }

    private fun saveProfile() {
        val updatedName = binding.inputName.text.toString().trim()
        val updatedEmail = binding.inputEmail.text.toString().trim()
        var updatedPhone = binding.inputPhone.text.toString().trim()
        val updatedAddress = binding.inputAddress.text.toString().trim()

        // sanitize phone: digits only, drop leading zeros, limit to 10
        updatedPhone = updatedPhone.replace(Regex("\\D"), "")
        while (updatedPhone.startsWith("0")) updatedPhone = updatedPhone.removePrefix("0")
        if (updatedPhone.length > 10) updatedPhone = updatedPhone.substring(0, 10)

        lifecycleScope.launch {
            // prevent double taps and show saving state
            binding.buttonSave.isEnabled = false
            val originalText = binding.buttonSave.text
            try { binding.buttonSave.text = "Saving..." } catch (_: Exception) {}
            try {
                // Load existing doctor profile
                val currentResponse = RetrofitClient.apiService.getMyProfile()
                if (!currentResponse.isSuccessful || currentResponse.body() == null) {
                    Toast.makeText(requireContext(), "Could not fetch profile!", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val user = currentResponse.body()!!

                // Require that all editable fields are filled before saving
                if (updatedName.isBlank() || updatedPhone.isBlank() || updatedAddress.isBlank()) {
                    Toast.makeText(requireContext(), "Please fill all details before saving", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Validate name and phone
                val nameRegex = Regex("^[A-Za-z .'-]+$")
                if (updatedName.isBlank() || !nameRegex.matches(updatedName)) {
                    Toast.makeText(requireContext(), "Enter a valid name", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val contactRegex = Regex("^[6-9][0-9]{9}$")
                if (updatedPhone.isNotBlank() && !contactRegex.matches(updatedPhone)) {
                    Toast.makeText(requireContext(), "Enter a valid 10-digit contact starting with 6/7/8/9", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Prepare proper update request. Include an explicit empty password
                // key so backend validations that expect the key do not fail.
                if (updatedAddress.length > 180) {
                    Toast.makeText(requireContext(), "Address too long", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val updateRequest = ProfileUpdateRequest(
                    password = "",
                    // use server-side role to avoid mismatches (case/syntax)
                    email = user.email,
                    role = user.role,
                    fullName = updatedName,
                    age = user.age,
                    height = user.height,
                    gender = user.gender,
                    contactInfo = updatedPhone,
                    address = if (updatedAddress.isNotBlank()) updatedAddress else user.address
                )

                // Send update
                val updateResponse = RetrofitClient.apiService.updateMyProfile(updateRequest)

                if (updateResponse.isSuccessful) {
                    Toast.makeText(requireContext(), "Changes Saved!", Toast.LENGTH_SHORT).show()
                    // go back to profile (previous fragment) so existing instance refreshes onResume
                    findNavController().popBackStack()
                } else {
                    val err = try { updateResponse.errorBody()?.string() } catch (_: Exception) { null }
                    Toast.makeText(requireContext(), "Update failed: ${updateResponse.code()} ${err ?: ""}", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                // always restore the button state
                binding.buttonSave.isEnabled = true
                try { binding.buttonSave.text = originalText } catch (_: Exception) {}
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
