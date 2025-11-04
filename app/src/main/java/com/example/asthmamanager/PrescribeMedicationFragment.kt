package com.example.asthmamanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.asthmamanager.databinding.FragmentPrescribeMedicationBinding

class PrescribeMedicationFragment : Fragment() {

    private var _binding: FragmentPrescribeMedicationBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPrescribeMedicationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set patient's name
        binding.textPatientName.text = "Patient Name"

        // Handle prescribe button click
        binding.buttonPrescribe.setOnClickListener {
            // Prescribe medication
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
