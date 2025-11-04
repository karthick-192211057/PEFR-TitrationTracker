package com.example.asthmamanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.asthmamanager.databinding.FragmentDoctorProfileBinding

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

        // Set doctor's name
        binding.textDoctorName.text = "Dr. Smith"

        // Handle edit profile button click
        binding.buttonEditProfile.setOnClickListener {
            findNavController().navigate(DoctorProfileFragmentDirections.actionDoctorProfileFragmentToEditDoctorProfileFragment())
        }

        // Handle logout button click
        binding.buttonLogout.setOnClickListener {
            // Logout user and navigate to login screen
            TokenManager.clearToken(requireContext())
            findNavController().navigate(R.id.action_doctorProfileFragment_to_loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
