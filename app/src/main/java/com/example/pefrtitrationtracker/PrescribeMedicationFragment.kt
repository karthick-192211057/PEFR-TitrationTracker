package com.example.pefrtitrationtracker

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.pefrtitrationtracker.databinding.FragmentPrescribeMedicationBinding
import com.example.pefrtitrationtracker.network.MedicationCreate
import com.example.pefrtitrationtracker.network.RetrofitClient
import com.example.pefrtitrationtracker.network.SessionManager
import kotlinx.coroutines.launch
import android.text.Editable
import android.text.TextWatcher

class PrescribeMedicationFragment : Fragment() {

    private var _binding: FragmentPrescribeMedicationBinding? = null
    private val binding get() = _binding!!

    // Get the patientId argument from the navigation action
    private val args: PrescribeMedicationFragmentArgs by navArgs()
    private var patientId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPrescribeMedicationBinding.inflate(inflater, container, false)
        patientId = args.patientId // Store the patientId
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set the title
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Prescribe Medication"

        // Set patient's name
        binding.textPatientName.text = "Prescribing for Patient (ID: $patientId)"

        // Handle description word counter and prescribe click
        binding.inputDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val words = s?.toString()?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: listOf()
                val wordCount = words.size
                if (wordCount > 150) {
                    // Trim to first 150 words
                    val allowed = words.take(150).joinToString(" ")
                    binding.inputDescription.setText(allowed)
                    binding.inputDescription.setSelection(allowed.length)
                    binding.textDescriptionCounter.text = "150/150 words"
                } else {
                    binding.textDescriptionCounter.text = "$wordCount/150 words"
                }
            }
        })

        binding.buttonPrescribe.setOnClickListener {
            prescribeMedication()
        }
    }

    private fun prescribeMedication() {
        // Get text from the EditText fields
        val medName = binding.textInputMedication.editText?.text.toString()
        val dosage = binding.textInputDosage.editText?.text.toString()
        val description = binding.inputDescription.text.toString().trim().ifEmpty { null }

        // Validate input
        if (medName.isBlank()) {
            Toast.makeText(context, "Please enter a medication name", Toast.LENGTH_SHORT).show()
            return
        }
        if (dosage.isBlank()) {
            Toast.makeText(context, "Please enter a dosage", Toast.LENGTH_SHORT).show()
            return
        }

        // Create the request object
        // We'll put the dosage in the 'dose' field and leave 'schedule' null for now
        val medicationRequest = MedicationCreate(
            name = medName,
            dose = dosage,
            schedule = null,
            description = description
        )

        binding.buttonPrescribe.isEnabled = false
        binding.buttonPrescribe.text = "Prescribing..."

        lifecycleScope.launch {
            try {
                // Call the new API endpoint
                val response = RetrofitClient.apiService.prescribeMedication(patientId, medicationRequest)

                if (response.isSuccessful) {
                    Toast.makeText(context, "Medication prescribed", Toast.LENGTH_SHORT).show()
                    // Mark patient locally so dashboard shows updated status immediately
                    val session = SessionManager(requireContext())
                    session.addRecentlyPrescribed(patientId)
                    // Add notification for this prescribe action
                    val patientLabel = binding.textPatientName.text.toString().ifBlank { "Patient #$patientId" }
                    session.addNotification("Prescribed $medName to $patientLabel")
                    // Go back to the doctor dashboard
                    findNavController().popBackStack()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Prescription failed"
                    Log.e("PrescribeMedication", "API Error: $errorMsg")
                    Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("PrescribeMedication", "Network Exception: ${e.message}", e)
                Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.buttonPrescribe.isEnabled = true
                binding.buttonPrescribe.text = "Prescribe"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
