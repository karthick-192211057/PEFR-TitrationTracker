package com.example.asthmamanager

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.asthmamanager.databinding.FragmentDoctorProfileBinding
import com.example.asthmamanager.network.RetrofitClient
import com.example.asthmamanager.network.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DoctorProfileFragment : Fragment() {

    private var _binding: FragmentDoctorProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDoctorProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.title = "My Profile"

        // 1. Fetch the real profile data immediately
        fetchDoctorProfile()

        binding.buttonEditProfile.setOnClickListener {
            findNavController().navigate(DoctorProfileFragmentDirections.actionDoctorProfileFragmentToEditDoctorProfileFragment())
        }

        binding.buttonLogout.setOnClickListener {
            // Clear session and go to Login
            SessionManager(requireContext()).clearAuthToken()
            findNavController().navigate(DoctorProfileFragmentDirections.actionDoctorProfileFragmentToLoginFragment())
        }
    }

    private fun fetchDoctorProfile() {
        lifecycleScope.launch {
            try {
                // Call the API on IO thread
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.getMyProfile()
                }

                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    // 2. Update the UI with the real name
                    binding.textDoctorName.text = "Dr. ${user.fullName ?: "Unknown"}"
                } else {
                    Toast.makeText(context, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("DoctorProfile", "Error fetching profile", e)
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}