package com.example.pefrtitrationtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.pefrtitrationtracker.utils.safeClick
import com.example.pefrtitrationtracker.databinding.FragmentPatientDetailsBinding
import com.example.pefrtitrationtracker.network.BaselinePEFRCreate
import com.example.pefrtitrationtracker.network.RetrofitClient
import com.example.pefrtitrationtracker.network.ProfileUpdateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PatientDetailsFragment : Fragment() {

    private var _binding: FragmentPatientDetailsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPatientDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonNext.safeClick {
            val name = binding.editTextName.text.toString().trim()
            val age = binding.editTextAge.text.toString().toIntOrNull()
            val height = binding.editTextHeight.text.toString().toIntOrNull()
            val gender = binding.editTextGender.text.toString().trim()
            val contact = binding.editTextContact.text.toString().trim()
            val address = binding.editTextAddress.text.toString().trim()
            val baselinePefr = binding.editTextBaselinePefr.text.toString().toIntOrNull()

            if (name.isEmpty() || age == null || height == null || gender.isEmpty() || contact.isEmpty() || address.isEmpty() || baselinePefr == null) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@safeClick
            }

            // Baseline PEFR must be 55 - 999
                if (baselinePefr != null && (baselinePefr < 55 || baselinePefr > 999)) {
                Toast.makeText(requireContext(), "Enter correct PEFR value", Toast.LENGTH_SHORT).show()
                return@safeClick
            }

            val profileUpdateRequest = ProfileUpdateRequest(
                role = "Patient",
                fullName = name,
                age = age,
                height = height,
                gender = gender,
                contactInfo = contact,
                address = address
            )

            lifecycleScope.launch {
                try {
                    // This fragment is used post-login to complete patient details.
                    // Call updateMyProfile instead of signup so we don't recreate users here.
                        val response = withContext(Dispatchers.IO) {
                        RetrofitClient.apiService.updateMyProfile(profileUpdateRequest)
                    }

                    if (response.isSuccessful) {
                        val baselineRequest = BaselinePEFRCreate(baselinePefr)
                        val baselineResponse = withContext(Dispatchers.IO) {
                            RetrofitClient.apiService.setBaseline(baselineRequest)
                        }

                        if (baselineResponse.isSuccessful) {
                            findNavController().navigate(PatientDetailsFragmentDirections.actionPatientDetailsFragmentToHomeDashboardFragment())
                        } else {
                            Toast.makeText(requireContext(), "Failed to set baseline. Please try again.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "Profile update failed. Please try again.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.imageBack.safeClick { findNavController().popBackStack() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
