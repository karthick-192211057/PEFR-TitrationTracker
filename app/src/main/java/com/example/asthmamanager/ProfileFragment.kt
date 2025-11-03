package com.example.asthmamanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.asthmamanager.adapter.EmergencyContactAdapter
import com.example.asthmamanager.adapter.MedicationAdapter
import com.example.asthmamanager.databinding.FragmentProfileBinding
import com.example.asthmamanager.network.BaselinePEFRCreate
import com.example.asthmamanager.network.RetrofitClient
import com.example.asthmamanager.network.SessionManager
import com.example.asthmamanager.network.SignupRequest
import com.example.asthmamanager.network.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var currentUser: User? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set initial state to non-editable
        setEditingState(false)

        binding.buttonEditProfile.setOnClickListener {
            setEditingState(true)
        }

        binding.buttonSaveChanges.setOnClickListener {
            showSaveChangesConfirmationDialog()
        }

        binding.buttonDownloadReport.setOnClickListener {
            Toast.makeText(requireContext(), "Downloading Report...", Toast.LENGTH_SHORT).show()
        }

        binding.buttonLogout.setOnClickListener {
            SessionManager(requireContext()).saveAuthToken("")
            findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
        }

        fetchProfileData()
    }

    private fun setEditingState(isEditing: Boolean) {
        binding.editTextName.isEnabled = isEditing
        binding.editTextEmail.isEnabled = isEditing
        binding.editTextContact.isEnabled = isEditing
        binding.editTextAddress.isEnabled = isEditing
        binding.editTextAge.isEnabled = isEditing
        binding.editTextHeight.isEnabled = isEditing
        binding.editTextGender.isEnabled = isEditing
        binding.editTextBaselinePEFR.isEnabled = isEditing

        if (isEditing) {
            binding.buttonEditProfile.visibility = View.GONE
            binding.buttonSaveChanges.visibility = View.VISIBLE
        } else {
            binding.buttonEditProfile.visibility = View.VISIBLE
            binding.buttonSaveChanges.visibility = View.GONE
        }
    }

    private fun fetchProfileData() {
        lifecycleScope.launch {
            try {
                val profileResponse = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.getMyProfile()
                }
                if (profileResponse.isSuccessful) {
                    currentUser = profileResponse.body()
                    binding.editTextName.setText(currentUser?.fullName)
                    binding.editTextEmail.setText(currentUser?.email)
                    binding.editTextContact.setText(currentUser?.contactInfo)
                    binding.editTextAddress.setText(currentUser?.address)
                    binding.editTextAge.setText(currentUser?.age?.toString())
                    binding.editTextHeight.setText(currentUser?.height?.toString())
                    binding.editTextGender.setText(currentUser?.gender)
                    binding.editTextBaselinePEFR.setText(currentUser?.baseline?.baselineValue?.toString())
                } else {
                    Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
                }

                val contactsResponse = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.getEmergencyContacts()
                }
                if (contactsResponse.isSuccessful) {
                    val contacts = contactsResponse.body() ?: emptyList()
                    binding.recyclerViewContacts.layoutManager = LinearLayoutManager(requireContext())
                    binding.recyclerViewContacts.adapter = EmergencyContactAdapter(contacts, { /* TODO: Handle edit */ }, { /* TODO: Handle delete */ })
                }

                val medicationsResponse = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.getMedications()
                }
                if (medicationsResponse.isSuccessful) {
                    val medications = medicationsResponse.body() ?: emptyList()
                    binding.recyclerViewMedications.layoutManager = LinearLayoutManager(requireContext())
                    binding.recyclerViewMedications.adapter = MedicationAdapter(medications, { /* TODO: Handle edit */ }, { /* TODO: Handle delete */ })
                }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSaveChangesConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Save Changes")
            .setMessage("Are you sure you want to save these changes?")
            .setPositiveButton("Save") { _, _ ->
                saveProfileChanges()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveProfileChanges() {
        val userToUpdate = currentUser ?: return

        val name = binding.editTextName.text.toString()
        val contact = binding.editTextContact.text.toString()
        val address = binding.editTextAddress.text.toString()
        val age = binding.editTextAge.text.toString().toIntOrNull()
        val height = binding.editTextHeight.text.toString().toIntOrNull()
        val gender = binding.editTextGender.text.toString()
        val baselinePefr = binding.editTextBaselinePEFR.text.toString().toIntOrNull()

        val profileUpdateRequest = SignupRequest(
            email = userToUpdate.email,
            password = "",
            role = userToUpdate.role,
            fullName = name,
            age = age,
            height = height,
            gender = gender,
            contactInfo = contact,
            address = address
        )

        lifecycleScope.launch {
            try {
                val profileUpdateResponse: Response<User> = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.updateMyProfile(profileUpdateRequest)
                }

                if (!profileUpdateResponse.isSuccessful) {
                    throw Exception("Failed to update profile")
                }

                if (baselinePefr != null) {
                    val baselineRequest = BaselinePEFRCreate(baselinePefr)
                    val baselineResponse = withContext(Dispatchers.IO) {
                        RetrofitClient.apiService.setBaseline(baselineRequest)
                    }
                    if (!baselineResponse.isSuccessful) {
                        throw Exception("Failed to set baseline PEFR")
                    }
                }

                Toast.makeText(requireContext(), "Changes saved successfully", Toast.LENGTH_SHORT).show()
                setEditingState(false)
                fetchProfileData()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error saving changes: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
