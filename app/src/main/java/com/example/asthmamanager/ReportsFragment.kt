package com.example.asthmamanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.asthmamanager.databinding.FragmentReportsBinding
import com.example.asthmamanager.network.DoctorPatientLinkRequest
import com.example.asthmamanager.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private val args: ReportsFragmentArgs by navArgs()
    private var patientId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)

        patientId = args.patientId

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (patientId == -1) {
            // Patient viewing their own reports
            (activity as? AppCompatActivity)?.supportActionBar?.title = "My Reports"
            // Set up the toggle listener only for the patient
            setupSharingToggle()
        } else {
            // Doctor viewing a patient's reports
            (activity as? AppCompatActivity)?.supportActionBar?.title = "Patient Report (ID: $patientId)"
            // Disable sharing toggle if it's not your own report
            binding.labelRealTimeSharing.text = "Data sharing is managed by the patient"
            binding.toggleSharing.isEnabled = false
        }

        binding.buttonExportPDF.setOnClickListener {
            Toast.makeText(requireContext(), "Generating PDF for patient $patientId", Toast.LENGTH_SHORT).show()
            // Placeholder logic: You would now call the API with patientId
        }
    }

    // --- [START] NEW FUNCTION TO HANDLE SHARING ---
    private fun setupSharingToggle() {
        // We set setOnCheckedChangeListener, not setOnClickListener
        binding.toggleSharing.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // When toggled on, show confirmation
                showLinkDoctorDialog()
            } else {
                // When toggled off (This would be a "unlink" feature)
                // For now, just show a message and prevent un-checking
                Toast.makeText(requireContext(), "Disabling sharing (not implemented)", Toast.LENGTH_SHORT).show()
                binding.toggleSharing.isChecked = true // Don't let them un-toggle it yet
            }
        }
    }

    private fun showLinkDoctorDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Share Data with Doctor")
            // In a real app, you'd have an EditText field here.
            // We will hardcode the email from your database for this test.
            .setMessage("This will link your account with 'doctor@example.com'. Proceed?")
            .setPositiveButton("Link") { _, _ ->
                // Use the hardcoded email
                linkToDoctor("doctor@example.com")
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                binding.toggleSharing.isChecked = false // Reset toggle
                dialog.dismiss()
            }
            .setOnCancelListener {
                binding.toggleSharing.isChecked = false // Reset toggle
            }
            .show()
    }

    private fun linkToDoctor(doctorEmail: String) {
        val linkRequest = DoctorPatientLinkRequest(doctorEmail)

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.linkDoctor(linkRequest)
                }

                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Successfully linked to doctor!", Toast.LENGTH_LONG).show()
                    binding.toggleSharing.isEnabled = false // Disable after linking
                } else {
                    // Show specific error from backend (e.g., "Doctor not found")
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Toast.makeText(requireContext(), "Linking failed: $errorBody", Toast.LENGTH_LONG).show()
                    binding.toggleSharing.isChecked = false // Reset toggle
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                binding.toggleSharing.isChecked = false // Reset toggle
            }
        }
    }
    // --- [END] NEW FUNCTION TO HANDLE SHARING ---

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
