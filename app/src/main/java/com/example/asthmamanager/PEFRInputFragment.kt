package com.example.asthmamanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.asthmamanager.databinding.FragmentPefrInputBinding
import com.example.asthmamanager.network.PEFRRecordCreate
import com.example.asthmamanager.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PEFRInputFragment : Fragment() {

    private var _binding: FragmentPefrInputBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPefrInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- FIX: Set the Activity's toolbar title ---
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Record PEFR"
        // --- END FIX ---

        binding.buttonSubmit.setOnClickListener {
            val pefrValue = binding.editTextPEFR.text.toString().toIntOrNull()

            if (pefrValue == null) {
                Toast.makeText(requireContext(), "Please enter a valid PEFR value", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val pefrRequest = PEFRRecordCreate(pefrValue)

            lifecycleScope.launch {
                try {
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.apiService.recordPEFR(pefrRequest)
                    }

                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "PEFR recorded successfully", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    } else {
                        Toast.makeText(requireContext(), "Failed to record PEFR. Please try again.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}