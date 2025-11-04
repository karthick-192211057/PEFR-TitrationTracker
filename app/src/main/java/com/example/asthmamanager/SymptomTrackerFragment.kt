package com.example.asthmamanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
// Note the binding class name: FragmentSymptomTrackerBinding
import com.example.asthmamanager.databinding.FragmentSymptomTrackerBinding

class SymptomTrackerFragment : Fragment() {

    private var _binding: FragmentSymptomTrackerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSymptomTrackerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- FIX: Set the Activity's toolbar title ---
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Symptom Tracker"
        // --- END FIX ---

        // --- FIX: This block was removed ---
        /*
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
        */
        // --- END FIX ---


        // Placeholder: Logic to read the rating bars and checkboxes would go here.
        // There is no explicit button to submit on this page, as it was integrated into the Home screen.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}