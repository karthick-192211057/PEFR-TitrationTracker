package com.example.asthmamanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.asthmamanager.adapter.PatientAdapter
import com.example.asthmamanager.databinding.FragmentDoctorDashboardBinding
import com.example.asthmamanager.network.BaselinePEFR
import com.example.asthmamanager.network.User

class DoctorDashboardFragment : Fragment() {

    private var _binding: FragmentDoctorDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDoctorDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Navigate to Doctor's own profile
        binding.imageProfile.setOnClickListener {
            findNavController().navigate(DoctorDashboardFragmentDirections.actionDoctorDashboardFragmentToDoctorProfileFragment())
        }

        // Dummy patient data
        val dummyPatients = listOf(
            User(1, "john.doe@email.com", "patient", "John Doe", 35, 175, "Male", "111-222-3333", "456 Pine St", BaselinePEFR(1, 450, 1)),
            User(2, "jane.smith@email.com", "patient", "Jane Smith", 28, 165, "Female", "444-555-6666", "789 Maple Ave", BaselinePEFR(2, 200, 2)),
            User(3, "peter.jones@email.com", "patient", "Peter Jones", 52, 180, "Male", "777-888-9999", "123 Oak St", BaselinePEFR(3, 300, 3))
        )

        binding.textNoPatients.isVisible = dummyPatients.isEmpty()
        val adapter = PatientAdapter(dummyPatients, { patient ->
            // On patient clicked, navigate to graph with patientId
            val action = DoctorDashboardFragmentDirections.actionDoctorDashboardFragmentToGraphFragment(patient.id)
            findNavController().navigate(action)
        }, { patient ->
            // On download clicked, navigate to reports with patientId
            val action = DoctorDashboardFragmentDirections.actionDoctorDashboardFragmentToReportsFragment(patient.id)
            findNavController().navigate(action)
        }, { patient ->
            // On prescribe clicked, navigate to prescribe medication with patientId
            val action = DoctorDashboardFragmentDirections.actionDoctorDashboardFragmentToPrescribeMedicationFragment(patient.id)
            findNavController().navigate(action)
        })
        binding.recyclerViewPatients.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewPatients.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}