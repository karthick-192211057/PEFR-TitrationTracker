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
import com.example.pefrtitrationtracker.databinding.FragmentSymptomTrackerBinding
import com.example.pefrtitrationtracker.network.RetrofitClient
import com.example.pefrtitrationtracker.network.SymptomCreate
import kotlinx.coroutines.launch
// --- ADD THESE IMPORTS ---
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.text.InputFilter
// --- END IMPORTS ---
import com.example.pefrtitrationtracker.utils.safeClick

class SymptomTrackerFragment : Fragment() {

    private var _binding: FragmentSymptomTrackerBinding? = null
    private val binding get() = _binding!!
    
    private var lastSubmitTime = 0L
    private val SUBMIT_THROTTLE_MS = 1000L // Prevent double-submit within 1 second

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSymptomTrackerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.title = "Symptom Tracker"

        // Filter to accept only letters (a-z, A-Z) and spaces, max 35 characters
        binding.etSuspectedTrigger.filters = arrayOf(
            InputFilter.LengthFilter(35),
            InputFilter { source, start, end, dest, dstart, dend ->
                // Allow only letters and spaces
                for (i in start until end) {
                    val c = source[i]
                    if (!c.isLetter() && c != ' ') {
                        // Return empty string to block this character
                        return@InputFilter source.subSequence(0, 0)
                    }
                }
                null // Accept the input
            }
        )
        binding.etSuspectedTrigger.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                // hide keyboard
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else false
        }

        // Set click listener for the submit button
        binding.btnSubmitSymptoms.safeClick {
            // hide keyboard when submitting
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            submitSymptoms()
        }
    }

    private fun submitSymptoms() {
        // Guard against rapid submission
        val now = System.currentTimeMillis()
        if (now - lastSubmitTime < SUBMIT_THROTTLE_MS) {
            Toast.makeText(context, "Please wait before submitting again", Toast.LENGTH_SHORT).show()
            return
        }
        lastSubmitTime = now
        val wheezeRating = binding.ratingWheeze.rating.toInt()
        val coughRating = binding.ratingCough.rating.toInt()
        val dyspneaRating = binding.ratingDyspnea.rating.toInt()
        val nightSymptomsRating = binding.ratingNightSymptoms.rating.toInt()
        val dustExposure = binding.checkDust.isChecked
        val smokeExposure = binding.checkSmoke.isChecked
        val trigger = binding.etSuspectedTrigger.text.toString()

        // Combine all ratings to determine a general severity
        val totalScore = wheezeRating + coughRating + dyspneaRating + nightSymptomsRating
        val severity = when {
            totalScore == 0 -> "None"
            totalScore <= 4 -> "Mild"
            totalScore <= 10 -> "Moderate"
            else -> "Severe"
        }

        // --- THIS IS THE FIX ---
        // Create an ISO 8601 formatter that Python/FastAPI understands
        // "yyyy-MM-dd'T'HH:mm:ss'Z'"
        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        isoFormatter.timeZone = TimeZone.getTimeZone("UTC")
        val nowAsISO = isoFormatter.format(Date())
        // --- END FIX ---

        // Create the request object (build before triage so we can submit on confirm)
        val symptomRequest = SymptomCreate(
            wheezeRating = wheezeRating,
            coughRating = coughRating,
            dyspneaRating = dyspneaRating,
            nightSymptomsRating = nightSymptomsRating,
            dustExposure = dustExposure,
            smokeExposure = smokeExposure,
            severity = severity, // Set a calculated severity
            onsetAt = nowAsISO,
            duration = null, // Not captured in this form
            suspectedTrigger = trigger.ifBlank { null }
        )

        // Run the local triage assessment
        val triage = SymptomTriage.assess(
            wheeze = wheezeRating,
            cough = coughRating,
            dyspnea = dyspneaRating,
            nightSymptoms = nightSymptomsRating,
            dustExposure = dustExposure,
            smokeExposure = smokeExposure
        )

        // Show triage result to user before submitting
        val triageMsg = StringBuilder()
        triageMsg.append("Urgency: ${triage.urgency}\n\n")
        triageMsg.append(triage.message)
        if (triage.actions.isNotEmpty()) {
            triageMsg.append("\n\nRecommended actions:\n")
            triage.actions.forEach { a -> triageMsg.append("- $a\n") }
        }

        // Use a custom dialog layout for a clearer triage UI
        val dlgView = layoutInflater.inflate(R.layout.dialog_symptom_triage, null)
        val tTitle = dlgView.findViewById<android.widget.TextView>(R.id.textTriageTitle)
        val tUrgency = dlgView.findViewById<android.widget.TextView>(R.id.textUrgency)
        val tMessage = dlgView.findViewById<android.widget.TextView>(R.id.textMessage)
        val tActions = dlgView.findViewById<android.widget.TextView>(R.id.textActions)
        val btnEdit = dlgView.findViewById<android.widget.Button>(R.id.buttonEdit)
        val btnProceed = dlgView.findViewById<android.widget.Button>(R.id.buttonProceed)

        tTitle.text = "Symptom Triage"
        tUrgency.text = "Urgency: ${triage.urgency}"
        tMessage.text = triage.message
        tActions.text = if (triage.actions.isNotEmpty()) triage.actions.joinToString("\n") { "- $it" } else ""

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dlgView)
            .setCancelable(true)
            .create()

        btnEdit.setOnClickListener {
            dialog.dismiss()
            // allow user to edit the form
        }

        btnProceed.setOnClickListener {
            dialog.dismiss()
            performSubmit(symptomRequest)
        }

        dialog.show()

        // return early; performSubmit will handle API call when user confirms
        return

        // performSubmit will be called after triage confirmation
    }

    private fun performSubmit(symptomRequest: com.example.pefrtitrationtracker.network.SymptomCreate) {
        binding.btnSubmitSymptoms.isEnabled = false
        binding.btnSubmitSymptoms.text = "Saving..."

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.recordSymptom(symptomRequest)

                if (response.isSuccessful) {
                    Toast.makeText(context, "Symptoms saved successfully", Toast.LENGTH_SHORT).show()
                    // After saving symptoms, call ML prediction endpoint to get recommendation
                    try {
                        // Fetch profile to get age and latest PEFR
                        val profileResp = RetrofitClient.apiService.getMyProfile()
                        if (profileResp.isSuccessful) {
                            val user = profileResp.body()
                            val age = user?.age
                            val latestPefr = user?.latestPefrRecord?.pefrValue ?: 0

                            val mlInput = com.example.pefrtitrationtracker.network.MLInput(
                                age = age,
                                pefrValue = latestPefr,
                                wheezeRating = symptomRequest.wheezeRating ?: 0,
                                coughRating = symptomRequest.coughRating ?: 0,
                                dustExposure = symptomRequest.dustExposure ?: false,
                                smokeExposure = symptomRequest.smokeExposure ?: false
                            )

                            val mlResp = RetrofitClient.apiService.mlPredict(mlInput)
                            if (mlResp.isSuccessful) {
                                val pred = mlResp.body()
                                val dlg = layoutInflater.inflate(R.layout.dialog_ml_recommendation, null)
                                val tMed = dlg.findViewById<android.widget.TextView>(R.id.textMed)
                                val tDays = dlg.findViewById<android.widget.TextView>(R.id.textDays)
                                val tProb = dlg.findViewById<android.widget.TextView>(R.id.textProb)
                                val btnSave = dlg.findViewById<android.widget.Button>(R.id.buttonSave)
                                val btnRemind = dlg.findViewById<android.widget.Button>(R.id.buttonRemind)
                                val btnClose = dlg.findViewById<android.widget.Button>(R.id.buttonClose)

                                tMed.text = "Recommendation: ${pred?.recommendedMedicine ?: "-"}"
                                tDays.text = "Days: ${pred?.recommendedDays ?: "-"}"
                                tProb.text = "Cure chance: ${String.format("%.1f", (pred?.predictedCureProbability ?: 0.0) * 100)}%"

                                val dialog = AlertDialog.Builder(requireContext()).setView(dlg).create()
                                btnClose.setOnClickListener { dialog.dismiss(); findNavController().popBackStack(R.id.homeDashboardFragment, false) }
                                    btnRemind.setOnClickListener {
                                        dialog.dismiss()
                                        // If user already has a saved reminder schedule, schedule now; otherwise ask to enable
                                            val session = com.example.pefrtitrationtracker.network.SessionManager(requireContext())
                                        val ownerEmail = session.fetchUserEmail()
                                        val saved = com.example.pefrtitrationtracker.reminders.ReminderStore.load(requireContext(), ownerEmail)
                                        if (saved.enabled) {
                                            // schedule reminder using existing saved reminder
                                            val scheduler = com.example.pefrtitrationtracker.reminders.ReminderScheduler(requireContext())
                                            scheduler.schedule(saved.hour, saved.minute, saved.frequency, session.fetchUserEmail()?.hashCode() ?: -1)
                                            Toast.makeText(requireContext(), "Reminder scheduled", Toast.LENGTH_SHORT).show()
                                        } else {
                                            // prompt user to enable notifications or set a default
                                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                                .setTitle("Enable Reminders")
                                                .setMessage("You don't have reminders enabled. Would you like to set a default daily reminder at 08:00?")
                                                .setPositiveButton("Set Default") { _, _ ->
                                                    val default = com.example.pefrtitrationtracker.reminders.SavedReminder(enabled = true, hour = 8, minute = 0, frequency = "DAILY", targetPefr = -1)
                                                    com.example.pefrtitrationtracker.reminders.ReminderStore.save(requireContext(), default, ownerEmail)
                                                    val scheduler = com.example.pefrtitrationtracker.reminders.ReminderScheduler(requireContext())
                                                    scheduler.schedule(default.hour, default.minute, default.frequency, session.fetchUserEmail()?.hashCode() ?: -1)
                                                    Toast.makeText(requireContext(), "Default reminder set at 08:00 daily", Toast.LENGTH_SHORT).show()
                                                }
                                                .setNegativeButton("Open Notification Settings") { _, _ ->
                                                    findNavController().navigate(R.id.notificationFragment)
                                                }
                                                .show()
                                        }
                                    }
                                btnSave.setOnClickListener {
                                    dialog.dismiss()
                                        // Call backend to create medication and save local metadata
                                        lifecycleScope.launch {
                                            try {
                                                val medReq = com.example.pefrtitrationtracker.network.MedicationCreate(
                                                    name = pred?.recommendedMedicine ?: "Medication",
                                                    dose = null,
                                                    schedule = null,
                                                    description = "From ML: cure=${pred?.predictedCureProbability ?: 0.0}"
                                                )
                                                val createResp = RetrofitClient.apiService.createMedication(medReq)
                                                if (createResp.isSuccessful && createResp.body() != null) {
                                                    val created = createResp.body()!!
                                                    // Save metadata locally so TreatmentPlan can show days left + cure %
                                                    val start = System.currentTimeMillis()
                                                    com.example.pefrtitrationtracker.network.SessionManager(requireContext()).saveMedicationMeta(
                                                        created.id,
                                                        start,
                                                        pred?.recommendedDays ?: 0,
                                                        pred?.predictedCureProbability ?: 0.0
                                                    )
                                                    Toast.makeText(requireContext(), "Saved to treatment plan", Toast.LENGTH_SHORT).show()
                                                    findNavController().navigate(R.id.treatmentPlanFragment)
                                                } else {
                                                    Toast.makeText(requireContext(), "Could not save medication", Toast.LENGTH_LONG).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                }
                                dialog.show()
                            } else {
                                // if ml call fails, fallback to normal navigation
                                findNavController().popBackStack(R.id.homeDashboardFragment, false)
                            }
                        } else {
                            findNavController().popBackStack(R.id.homeDashboardFragment, false)
                        }
                    } catch (e: Exception) {
                        Log.e("SymptomTracker", "ML call failed: ${e.message}")
                        findNavController().popBackStack(R.id.homeDashboardFragment, false)
                    }
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                    Log.e("SymptomTracker", "API Error: $errorMsg")
                    Toast.makeText(context, "Error saving: $errorMsg", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("SymptomTracker", "Network Exception: ${e.message}", e)
                Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSubmitSymptoms.isEnabled = true
                binding.btnSubmitSymptoms.text = "Submit Symptoms"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
