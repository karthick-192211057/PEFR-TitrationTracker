package com.example.asthmamanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
// Note the binding class name: FragmentAlertsBinding
import com.example.asthmamanager.databinding.FragmentAlertsBinding

class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- FIX: This block was removed ---
        /*
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
        */
        // --- END FIX ---

        // Logic for "Add" button (Floating Action Button)
        binding.fabAddAlert.setOnClickListener {
            // Placeholder: This would launch a dialog to set the time/message
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}