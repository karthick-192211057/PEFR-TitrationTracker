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

    // Job management
    private var prescribeJob: kotlinx.coroutines.Job? = null
    private var isPrescribing = false

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

        // Add real-time validation for dosage field (1-2000 mg)
        binding.textInputDosage.editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().isNotEmpty()) {
                    try {
                        val dosageValue = s.toString().toInt()
                        when {
                            dosageValue < 1 -> binding.textInputDosage.error = "Dosage must be at least 1 mg"
                            dosageValue > 2000 -> {
                                binding.textInputDosage.error = "Dosage cannot exceed 2000 mg"
                                // Trim to 2000
                                s?.delete(s.length - 1, s.length)
                            }
                            else -> binding.textInputDosage.error = null
                        }
                    } catch (e: NumberFormatException) {
                        binding.textInputDosage.error = "Please enter a valid number"
                    }
                }
            }
        })

        // Add real-time character filtering for description (letters and spaces only)
        binding.inputDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                // Filter to keep only letters, spaces, and common punctuation for descriptions
                val filtered = text.filter { it.isLetter() || it.isWhitespace() || it in ",.!?-'" }
                
                if (text != filtered) {
                    s?.clear()
                    s?.append(filtered)
                }
                
                // Count words and show counter
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

        // Validate dosage is numeric and in range 1-2000
        try {
            val dosageValue = dosage.toInt()
            if (dosageValue < 1 || dosageValue > 2000) {
                Toast.makeText(context, "Dosage must be between 1 and 2000 mg", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: NumberFormatException) {
            Toast.makeText(context, "Please enter a valid dosage number", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate description contains only letters, spaces, and common punctuation
        if (!description.isNullOrEmpty()) {
            val invalidChars = description.filter { !it.isLetter() && !it.isWhitespace() && it !in ",.!?-'" }
            if (invalidChars.isNotEmpty()) {
                Toast.makeText(context, "Description can only contain letters, spaces, and basic punctuation", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // Create the request object
        // We'll put the dosage in the 'dose' field and leave 'schedule' null for now
        val medicationRequest = MedicationCreate(
            name = medName,
            dose = dosage,
            schedule = null,
            description = description
        )

        if (isPrescribing) {
            Toast.makeText(context, "Prescribing already in progress", Toast.LENGTH_SHORT).show()
            return
        }

        isPrescribing = true
        prescribeJob?.cancel()

        try {
            binding.buttonPrescribe.isEnabled = false
            binding.buttonPrescribe.text = "Prescribing..."
        } catch (e: Exception) {
            Log.e("PrescribeMed", "UI setup error: ${e.message}")
        }

        prescribeJob = lifecycleScope.launch {
            try {
                if (!isAdded || _binding == null) {
                    isPrescribing = false
                    return@launch
                }

                // Call the new API endpoint
                val response = RetrofitClient.apiService.prescribeMedication(patientId, medicationRequest)

                if (!isAdded || _binding == null) {
                    isPrescribing = false
                    return@launch
                }

                if (response.isSuccessful) {
                    try {
                        Toast.makeText(context, "Medication prescribed", Toast.LENGTH_SHORT).show()
                        // Mark patient locally so dashboard shows updated status immediately
                        val session = SessionManager(requireContext())
                        session.addRecentlyPrescribed(patientId)
                        // Add notification for this prescribe action
                        val patientLabel = binding.textPatientName.text.toString().ifBlank { "Patient #$patientId" }
                        session.addNotification("Prescribed $medName to $patientLabel")

                        // Save created time locally so the patient card shows the prescription timestamp.
                        // If backend returned a created_at value, use that; otherwise use now.
                        try {
                            val med = response.body()
                            if (med != null) {
                                val medId = med.id
                                val created = med.createdAt
                                if (!created.isNullOrBlank()) {
                                    try {
                                        val inst = java.time.OffsetDateTime.parse(created).toInstant()
                                        session.saveMedicationCreatedTime(medId, inst.toEpochMilli())
                                    } catch (e: Exception) {
                                        try {
                                            val inst2 = java.time.Instant.parse(created)
                                            session.saveMedicationCreatedTime(medId, inst2.toEpochMilli())
                                        } catch (_: Exception) {
                                            session.saveMedicationCreatedTime(medId, System.currentTimeMillis())
                                        }
                                    }
                                } else {
                                    session.saveMedicationCreatedTime(medId, System.currentTimeMillis())
                                }
                            }
                        } catch (_: Exception) {}

                        if (isAdded && _binding != null) {
                            // Go back to the doctor dashboard
                            findNavController().popBackStack()
                        }
                    } catch (e: Exception) {
                        Log.e("PrescribeMed", "UI error after success: ${e.message}")
                    }
                } else {
                    if (isAdded && _binding != null) {
                        val errorMsg = response.errorBody()?.string() ?: "Prescription failed"
                        Log.e("PrescribeMedication", "API Error: $errorMsg")
                        Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("PrescribeMedication", "Network Exception: ${e.message}", e)
                if (isAdded && _binding != null) {
                    Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isPrescribing = false
                if (isAdded && _binding != null) {
                    try {
                        binding.buttonPrescribe.isEnabled = true
                        binding.buttonPrescribe.text = "Prescribe"
                    } catch (e: Exception) {
                        Log.e("PrescribeMed", "Error resetting button: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        prescribeJob?.cancel()
        _binding = null
    }
}
