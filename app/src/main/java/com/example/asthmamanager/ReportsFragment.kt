package com.example.asthmamanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
            setupSharingToggle()
        } else {
            // Doctor viewing a patient's reports
            (activity as? AppCompatActivity)?.supportActionBar?.title = "Patient Report (ID: $patientId)"
            binding.labelRealTimeSharing.text = "Data sharing is managed by the patient"
            binding.toggleSharing.isEnabled = false
        }

        binding.buttonExportPDF.setOnClickListener {
            Toast.makeText(requireContext(), "Generating PDF for patient $patientId", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSharingToggle() {
        // Note: If the toggle is ON by default in XML, this listener only fires when you CHANGE it.
        // You may need to turn it OFF and then ON again to see the popup.
        binding.toggleSharing.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // When toggled ON, show the dialog to enter email
                showLinkDoctorDialog()
            } else {
                // When toggled OFF
                Toast.makeText(requireContext(), "Sharing disabled.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- THIS IS THE FUNCTION THAT CREATES THE POPUP ---
    private fun showLinkDoctorDialog() {
        // 1. Create an EditText for user input
        val input = EditText(requireContext())
        input.hint = "doctor@email.com"

        // 2. Add some padding/margin so it looks nice
        val container = FrameLayout(requireContext())
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(50, 20, 50, 20) // Left, Top, Right, Bottom margins
        input.layoutParams = params
        container.addView(input)

        // 3. Build and Show the Alert Dialog
        AlertDialog.Builder(requireContext())
            .setTitle("Link to Doctor")
            .setMessage("Enter your doctor's email address to share your live data.")
            .setView(container) // Add the input field to the dialog
            .setPositiveButton("Link") { _, _ ->
                val doctorEmail = input.text.toString().trim()
                if (doctorEmail.isNotEmpty()) {
                    linkToDoctor(doctorEmail) // Call backend with the typed email
                } else {
                    Toast.makeText(requireContext(), "Email cannot be empty", Toast.LENGTH_SHORT).show()
                    binding.toggleSharing.isChecked = false // Turn toggle back off
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                binding.toggleSharing.isChecked = false // Turn toggle back off
                dialog.dismiss()
            }
            .setOnCancelListener {
                binding.toggleSharing.isChecked = false // Turn toggle back off
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
                    Toast.makeText(requireContext(), "Successfully linked to $doctorEmail!", Toast.LENGTH_LONG).show()
                    // You might want to disable the toggle so they can't accidentally unlink
                    // binding.toggleSharing.isEnabled = false
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Toast.makeText(requireContext(), "Link failed: $errorBody", Toast.LENGTH_LONG).show()
                    binding.toggleSharing.isChecked = false // Reset toggle
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                binding.toggleSharing.isChecked = false // Reset toggle
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}