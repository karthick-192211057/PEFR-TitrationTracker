package com.example.asthmamanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.asthmamanager.databinding.FragmentEditDoctorProfileBinding

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

        // Pre-fill the input fields with the doctor's current information
        binding.editTextName.setText("Dr. Smith")
        binding.editTextEmail.setText("dr.smith@example.com")

        // Handle save button click
        binding.buttonSave.setOnClickListener {
            // Save the updated information
            val name = binding.editTextName.text.toString()
            val email = binding.editTextEmail.text.toString()

            // You can add your logic here to save the updated information
            // For example, you can make a network call to update the doctor's profile on the server

            Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()

            // Navigate back to the doctor's profile screen
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
