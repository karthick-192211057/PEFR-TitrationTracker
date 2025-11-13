package com.example.asthmamanager

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.asthmamanager.adapter.PatientAdapter
import com.example.asthmamanager.databinding.FragmentDoctorDashboardBinding
import com.example.asthmamanager.network.RetrofitClient
import kotlinx.coroutines.launch

class DoctorDashboardFragment : Fragment() {

    private var _binding: FragmentDoctorDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var patientAdapter: PatientAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDoctorDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Navigation to Doctor Profile
        binding.imageProfile.setOnClickListener {
            findNavController().navigate(DoctorDashboardFragmentDirections.actionDoctorDashboardFragmentToDoctorProfileFragment())
        }

        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        fetchPatients()
    }

    private fun setupRecyclerView() {
        // Initialize adapter with empty list and 3 actions
        patientAdapter = PatientAdapter(
            listOf(),
            onPatientClicked = { patient ->
                val action = DoctorDashboardFragmentDirections.actionDoctorDashboardFragmentToGraphFragment(patient.id)
                findNavController().navigate(action)
            },
            onDownloadClicked = { patient ->
                val action = DoctorDashboardFragmentDirections.actionDoctorDashboardFragmentToReportsFragment(patient.id)
                findNavController().navigate(action)
            },
            onPrescribeClicked = { patient ->
                val action = DoctorDashboardFragmentDirections.actionDoctorDashboardFragmentToPrescribeMedicationFragment(patient.id)
                findNavController().navigate(action)
            }
        )

        binding.recyclerViewPatients.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = patientAdapter
        }
    }

    private fun fetchPatients() {
        binding.progressBar.isVisible = true
        binding.textNoPatients.isVisible = false

        lifecycleScope.launch {
            try {
                // Fetch patients from API
                val response = RetrofitClient.apiService.getDoctorPatients(null, null)

                if (response.isSuccessful && response.body() != null) {
                    val patients = response.body()!!

                    if (patients.isNotEmpty()) {
                        patientAdapter.updateData(patients)
                        binding.recyclerViewPatients.isVisible = true
                        binding.textNoPatients.isVisible = false
                    } else {
                        binding.recyclerViewPatients.isVisible = false
                        binding.textNoPatients.text = "No patients linked yet."
                        binding.textNoPatients.isVisible = true
                    }
                } else {
                    Log.e("DoctorDashboard", "Error: ${response.code()}")
                    binding.textNoPatients.text = "Failed to load patients."
                    binding.textNoPatients.isVisible = true
                }
            } catch (e: Exception) {
                Log.e("DoctorDashboard", "Exception: ${e.message}")
                binding.textNoPatients.text = "Network error. Please retry."
                binding.textNoPatients.isVisible = true
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}