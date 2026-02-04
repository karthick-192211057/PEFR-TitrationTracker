package com.example.pefrtitrationtracker

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.pefrtitrationtracker.network.User
import com.example.pefrtitrationtracker.network.PEFRRecord
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import android.widget.ImageView
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pefrtitrationtracker.network.Medication
import com.example.pefrtitrationtracker.network.MedicationStatusUpdate
import com.example.pefrtitrationtracker.network.SessionManager
import com.example.pefrtitrationtracker.adapter.MedicationAdapter
import com.example.pefrtitrationtracker.databinding.FragmentTreatmentPlanBinding
import com.example.pefrtitrationtracker.network.RetrofitClient
import com.example.pefrtitrationtracker.utils.safeClick
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.example.pefrtitrationtracker.worker.MedSyncWorker

class TreatmentPlanFragment : Fragment() {

    private var _binding: FragmentTreatmentPlanBinding? = null
    private val binding get() = _binding!!

    // Adapters for the medication lists (doctor-prescribed and AI-recommended)
    private lateinit var medicationDoctorAdapter: MedicationAdapter
    private lateinit var medicationAiAdapter: MedicationAdapter
    private var fetchJob: Job? = null

    private fun safeBinding(action: (FragmentTreatmentPlanBinding) -> Unit) {
        val b = _binding
        if (b != null && isAdded) action(b)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTreatmentPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.title = "Your Treatment Plan"

        // Setup the RecyclerView
        setupRecyclerView()

        // Schedule a daily background sync to keep treatment plan updated
        try {
            val workRequest = PeriodicWorkRequestBuilder<MedSyncWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                "med_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            // Also poll for notifications periodically (every 15 minutes)
            try {
                val notifReq = androidx.work.PeriodicWorkRequestBuilder<com.example.pefrtitrationtracker.worker.NotificationPollWorker>(15, TimeUnit.MINUTES).build()
                WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                    "notif_poll",
                    ExistingPeriodicWorkPolicy.KEEP,
                    notifReq
                )
            } catch (_: Exception) {
                // ignore notification worker scheduling errors
            }
        } catch (_: Exception) {
            // ignore WorkManager scheduling errors to keep UI stable
        }
        // Detailed prescription button removed — descriptions shown in medication cards.
    }

    override fun onResume() {
        super.onResume()
        // Fetch data every time the fragment is viewed
        fetchTreatmentData()
    }

    private fun setupRecyclerView() {

        // Initialize adapters
        medicationDoctorAdapter = MedicationAdapter(
            emptyList(),
            onEditClicked = { medication ->
                try {
                    // If medication is doctor-prescribed, show a simplified three-button dialog
                    // with only "Taken", "Not taken", and "Cancel". For AI meds keep full editor.
                    if (medication.source == "doctor") {
                    val dialogView = layoutInflater.inflate(R.layout.dialog_update_status, null)

// Icon + text
                    dialogView.findViewById<TextView>(R.id.dialogTitle)
                        .text = "Update status for ${medication.name}"

                    dialogView.findViewById<TextView>(R.id.dialogMessage)
                        .text = "Choose the current status for this doctor-prescribed medication."

                    val dialog = MaterialAlertDialogBuilder(requireContext())
                        .setView(dialogView)
                        .setCancelable(false)
                        .create()

                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

// Cancel
                    dialogView.findViewById<MaterialButton>(R.id.buttonCancel)
                        .setOnClickListener { dialog.dismiss() }

// Taken
                    dialogView.findViewById<MaterialButton>(R.id.buttonTaken)
                        .setOnClickListener {
                            dialog.dismiss()
                            lifecycleScope.launch {
                                try {
                                    val resp = RetrofitClient.apiService.updateMedicationStatus(
                                        medId = medication.id,
                                        request = MedicationStatusUpdate(status = "Taken", notes = null)
                                    )
                                    if (resp.isSuccessful) {
                                        Toast.makeText(context, "Marked Taken", Toast.LENGTH_SHORT).show()
                                        try {
                                            val session = SessionManager(requireContext())
                                            session.saveMedicationStatusTime(medication.id, System.currentTimeMillis())
                                            session.addNotification("Medication ${medication.name} marked 'Taken'")
                                        } catch (_: Exception) {}
                                        fetchTreatmentData()
                                    } else {
                                        Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }

// Not taken
                    dialogView.findViewById<MaterialButton>(R.id.buttonNotTaken)
                        .setOnClickListener {
                            dialog.dismiss()
                            lifecycleScope.launch {
                                try {
                                    val resp = RetrofitClient.apiService.updateMedicationStatus(
                                        medId = medication.id,
                                        request = MedicationStatusUpdate(status = "Not Taken", notes = null)
                                    )
                                    if (resp.isSuccessful) {
                                            Toast.makeText(context, "Marked Not Taken", Toast.LENGTH_SHORT).show()
                                            try {
                                                val session = SessionManager(requireContext())
                                                session.saveMedicationStatusTime(medication.id, System.currentTimeMillis())
                                                session.addNotification("Medication ${medication.name} marked 'Not Taken'")
                                            } catch (_: Exception) {}
                                            fetchTreatmentData()
                                    } else {
                                        Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }

                    dialog.show()

                    } else {
                    // AI-prescribed or other sources: show the full editor dialog
                    val dlgView = layoutInflater.inflate(com.example.pefrtitrationtracker.R.layout.dialog_edit_medication, null)
                    val inputDose = dlgView.findViewById<android.widget.EditText>(com.example.pefrtitrationtracker.R.id.inputDose)
                    val inputDays = dlgView.findViewById<android.widget.EditText>(com.example.pefrtitrationtracker.R.id.inputDays)
                    val inputCure = dlgView.findViewById<android.widget.EditText>(com.example.pefrtitrationtracker.R.id.inputCureProb)
                    val inputStart = dlgView.findViewById<android.widget.EditText>(com.example.pefrtitrationtracker.R.id.inputStartDate)
                    val btnMarkTaken = dlgView.findViewById<android.widget.Button>(com.example.pefrtitrationtracker.R.id.buttonMarkTaken)
                    val btnSave = dlgView.findViewById<android.widget.Button>(com.example.pefrtitrationtracker.R.id.buttonSaveMed)

                    // Prefill
                    inputDose.setText(medication.dose ?: "")
                    inputDays.setText(medication.days?.toString() ?: "")
                    inputCure.setText(medication.cureProbability?.toString() ?: "")
                    inputStart.setText(medication.startDate ?: "")

                    val dialog = MaterialAlertDialogBuilder(requireContext())
                        .setView(dlgView)
                        .setNegativeButton("Close", null)
                        .create()

                    btnSave.setOnClickListener {
                        // Collect values and call updateMedication
                        val doseVal = inputDose.text.toString().ifBlank { null }
                        val daysVal = inputDays.text.toString().toIntOrNull()
                        val cureVal = inputCure.text.toString().toDoubleOrNull()
                        val startVal = inputStart.text.toString().ifBlank { null }

                        lifecycleScope.launch {
                            try {
                                val req = com.example.pefrtitrationtracker.network.MedicationUpdate(
                                    name = null,
                                    dose = doseVal,
                                    schedule = null,
                                    startDate = startVal,
                                    days = daysVal,
                                    cureProbability = cureVal,
                                    dosesRemaining = null
                                )
                                val resp = RetrofitClient.apiService.updateMedication(medication.id, req)
                                if (resp.isSuccessful) {
                                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                    fetchTreatmentData()
                                } else {
                                    Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    btnMarkTaken.setOnClickListener {
                        // Simple mark-taken action: default 1 dose
                        lifecycleScope.launch {
                            try {
                                val takeReq = com.example.pefrtitrationtracker.network.MedicationTake(doses = 1, notes = "Taken via app")
                                val resp = RetrofitClient.apiService.takeMedication(medication.id, takeReq)
                                if (resp.isSuccessful) {
                                    Toast.makeText(context, "Marked taken", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                    fetchTreatmentData()
                                } else {
                                    Toast.makeText(context, "Mark taken failed", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    dialog.show()
                    }
                } catch (e: Exception) {
                    Log.e("TreatmentPlan", "Error opening medication editor", e)
                    Toast.makeText(context, "Could not open editor: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            onDeleteClicked = { medication ->

                val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_action, null)

                // 🗑️ Delete icon
                dialogView.findViewById<ImageView>(R.id.dialogIcon)
                    .setImageResource(R.drawable.ic_delete)

                // Title & message
                dialogView.findViewById<TextView>(R.id.dialogTitle)
                    .text = "Delete Medication"

                dialogView.findViewById<TextView>(R.id.dialogMessage)
                    .text = "Are you sure you want to delete ${medication.name}?"

                val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setView(dialogView)
                    .setCancelable(false)
                    .create()

                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

                // Cancel
                dialogView.findViewById<MaterialButton>(R.id.buttonCancel)
                    .setOnClickListener { dialog.dismiss() }

                // Confirm = DELETE (same logic as before)
                dialogView.findViewById<MaterialButton>(R.id.buttonConfirm)
                    .apply {
                        text = "Delete"
                        setOnClickListener {
                            dialog.dismiss()
                            lifecycleScope.launch {
                                try {
                                    val delResp =
                                        RetrofitClient.apiService.deleteMedication(medication.id)

                                    if (delResp.isSuccessful) {
                                        Toast.makeText(context, "Medication deleted", Toast.LENGTH_SHORT).show()
                                        fetchTreatmentData()
                                    } else {
                                        val err = try { delResp.errorBody()?.string() ?: "" } catch (_: Exception) { "" }
                                        Log.e("TreatmentPlan", "Delete failed: ${delResp.code()} $err")

                                        if (delResp.code() == 404 || err.contains("Not Found", ignoreCase = true)) {
                                            Toast.makeText(
                                                context,
                                                "Medication removed (was not found on server)",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            fetchTreatmentData()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Delete failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Error: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }

                dialog.show()
            },
            isPatientView = true
        )
        medicationAiAdapter = MedicationAdapter(
            emptyList(),
            onEditClicked = { medication ->
                try {
                    // If medication is doctor-prescribed, show a simplified three-button dialog
                    // with only "Taken", "Not taken", and "Cancel". For AI meds keep full editor.
                    if (medication.source == "doctor") {
                    val dialogView = layoutInflater.inflate(R.layout.dialog_update_status, null)

// Icon + text
                    dialogView.findViewById<TextView>(R.id.dialogTitle)
                        .text = "Update status for ${medication.name}"

                    dialogView.findViewById<TextView>(R.id.dialogMessage)
                        .text = "Choose the current status for this doctor-prescribed medication."

                    val dialog = MaterialAlertDialogBuilder(requireContext())
                        .setView(dialogView)
                        .setCancelable(false)
                        .create()

                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

// Cancel
                    dialogView.findViewById<MaterialButton>(R.id.buttonCancel)
                        .setOnClickListener { dialog.dismiss() }

// Taken
                    dialogView.findViewById<MaterialButton>(R.id.buttonTaken)
                        .setOnClickListener {
                            dialog.dismiss()
                            lifecycleScope.launch {
                                try {
                                    val resp = RetrofitClient.apiService.updateMedicationStatus(
                                        medId = medication.id,
                                        request = MedicationStatusUpdate(status = "Taken", notes = null)
                                    )
                                    if (resp.isSuccessful) {
                                        Toast.makeText(context, "Marked Taken", Toast.LENGTH_SHORT).show()
                                        try {
                                            val session = SessionManager(requireContext())
                                            session.saveMedicationStatusTime(medication.id, System.currentTimeMillis())
                                            session.addNotification("Medication ${medication.name} marked 'Taken'")
                                        } catch (_: Exception) {}
                                        fetchTreatmentData()
                                    } else {
                                        Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }

// Not taken
                    dialogView.findViewById<MaterialButton>(R.id.buttonNotTaken)
                        .setOnClickListener {
                            dialog.dismiss()
                            lifecycleScope.launch {
                                try {
                                    val resp = RetrofitClient.apiService.updateMedicationStatus(
                                        medId = medication.id,
                                        request = MedicationStatusUpdate(status = "Not Taken", notes = null)
                                    )
                                    if (resp.isSuccessful) {
                                            Toast.makeText(context, "Marked Not Taken", Toast.LENGTH_SHORT).show()
                                            try {
                                                val session = SessionManager(requireContext())
                                                session.saveMedicationStatusTime(medication.id, System.currentTimeMillis())
                                                session.addNotification("Medication ${medication.name} marked 'Not Taken'")
                                            } catch (_: Exception) {}
                                            fetchTreatmentData()
                                    } else {
                                        Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }

                    dialog.show()

                    } else {
                    // AI-prescribed or other sources: show the full editor dialog
                    val dlgView = layoutInflater.inflate(com.example.pefrtitrationtracker.R.layout.dialog_edit_medication, null)
                    val inputDose = dlgView.findViewById<android.widget.EditText>(com.example.pefrtitrationtracker.R.id.inputDose)
                    val inputDays = dlgView.findViewById<android.widget.EditText>(com.example.pefrtitrationtracker.R.id.inputDays)
                    val inputCure = dlgView.findViewById<android.widget.EditText>(com.example.pefrtitrationtracker.R.id.inputCureProb)
                    val inputStart = dlgView.findViewById<android.widget.EditText>(com.example.pefrtitrationtracker.R.id.inputStartDate)
                    val btnMarkTaken = dlgView.findViewById<android.widget.Button>(com.example.pefrtitrationtracker.R.id.buttonMarkTaken)
                    val btnSave = dlgView.findViewById<android.widget.Button>(com.example.pefrtitrationtracker.R.id.buttonSaveMed)

                    // Prefill
                    inputDose.setText(medication.dose ?: "")
                    inputDays.setText(medication.days?.toString() ?: "")
                    inputCure.setText(medication.cureProbability?.toString() ?: "")
                    inputStart.setText(medication.startDate ?: "")

                    val dialog = MaterialAlertDialogBuilder(requireContext())
                        .setView(dlgView)
                        .setNegativeButton("Close", null)
                        .create()

                    btnSave.setOnClickListener {
                        // Collect values and call updateMedication
                        val doseVal = inputDose.text.toString().ifBlank { null }
                        val daysVal = inputDays.text.toString().toIntOrNull()
                        val cureVal = inputCure.text.toString().toDoubleOrNull()
                        val startVal = inputStart.text.toString().ifBlank { null }

                        lifecycleScope.launch {
                            try {
                                val req = com.example.pefrtitrationtracker.network.MedicationUpdate(
                                    name = null,
                                    dose = doseVal,
                                    schedule = null,
                                    startDate = startVal,
                                    days = daysVal,
                                    cureProbability = cureVal,
                                    dosesRemaining = null
                                )
                                val resp = RetrofitClient.apiService.updateMedication(medication.id, req)
                                if (resp.isSuccessful) {
                                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                    fetchTreatmentData()
                                } else {
                                    Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    btnMarkTaken.setOnClickListener {
                        // Simple mark-taken action: default 1 dose
                        lifecycleScope.launch {
                            try {
                                val takeReq = com.example.pefrtitrationtracker.network.MedicationTake(doses = 1, notes = "Taken via app")
                                val resp = RetrofitClient.apiService.takeMedication(medication.id, takeReq)
                                if (resp.isSuccessful) {
                                    Toast.makeText(context, "Marked taken", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                    fetchTreatmentData()
                                } else {
                                    Toast.makeText(context, "Mark taken failed", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    dialog.show()
                    }
                } catch (e: Exception) {
                    Log.e("TreatmentPlan", "Error opening medication editor", e)
                    Toast.makeText(context, "Could not open editor: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            onDeleteClicked = { medication ->

                val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_action, null)

                // 🗑️ Delete icon
                dialogView.findViewById<ImageView>(R.id.dialogIcon)
                    .setImageResource(R.drawable.ic_delete)

                // Title & message
                dialogView.findViewById<TextView>(R.id.dialogTitle)
                    .text = "Delete Medication"

                dialogView.findViewById<TextView>(R.id.dialogMessage)
                    .text = "Are you sure you want to delete ${medication.name}?"

                val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setView(dialogView)
                    .setCancelable(false)
                    .create()

                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

                // Cancel
                dialogView.findViewById<MaterialButton>(R.id.buttonCancel)
                    .setOnClickListener { dialog.dismiss() }

                // Confirm = DELETE (same logic as before)
                dialogView.findViewById<MaterialButton>(R.id.buttonConfirm)
                    .apply {
                        text = "Delete"
                        setOnClickListener {
                            dialog.dismiss()
                            lifecycleScope.launch {
                                try {
                                    val delResp =
                                        RetrofitClient.apiService.deleteMedication(medication.id)

                                    if (delResp.isSuccessful) {
                                        Toast.makeText(context, "Medication deleted", Toast.LENGTH_SHORT).show()
                                        fetchTreatmentData()
                                    } else {
                                        val err = try { delResp.errorBody()?.string() ?: "" } catch (_: Exception) { "" }
                                        Log.e("TreatmentPlan", "Delete failed: ${delResp.code()} $err")

                                        if (delResp.code() == 404 || err.contains("Not Found", ignoreCase = true)) {
                                            Toast.makeText(
                                                context,
                                                "Medication removed (was not found on server)",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            fetchTreatmentData()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Delete failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Error: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }

                dialog.show()
            },
            isPatientView = true
        )

        binding.recyclerViewDoctorMedications.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = medicationDoctorAdapter
        }

        binding.recyclerViewAiMedications.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = medicationAiAdapter
        }

        // no-op: detailed prescription button removed
    }

    private fun fetchTreatmentData() {
        safeBinding { b ->
            b.progressBar.isVisible = true
            b.recyclerViewDoctorMedications.isVisible = false
            b.recyclerViewAiMedications.isVisible = false
            b.textNoMedications.isVisible = false
        }

        fetchJob = lifecycleScope.launch {
            try {
                // --- 1. Fetch Medications ---
                val medsResponse = RetrofitClient.apiService.getMedications()
                if (medsResponse.isSuccessful) {
                    val medications = medsResponse.body()
                    
                    // Cache created_at timestamps locally for all doctor-prescribed medications
                    // so they persist across logout/login cycles
                    if (!medications.isNullOrEmpty()) {
                        try {
                            val session = SessionManager(requireContext())
                            medications.forEach { med ->
                                if (!med.createdAt.isNullOrBlank() && med.source == "doctor") {
                                    try {
                                        val instant = java.time.OffsetDateTime.parse(med.createdAt).toInstant()
                                        session.saveMedicationCreatedTime(med.id, instant.toEpochMilli())
                                    } catch (_: Exception) {
                                        // skip if parsing fails
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    
                    if (medications.isNullOrEmpty()) {
                        safeBinding { b ->
                            b.textNoMedications.isVisible = true
                            b.recyclerViewDoctorMedications.isVisible = false
                            b.recyclerViewAiMedications.isVisible = false
                            b.textDoctorTitle.isVisible = false
                            b.textAiTitle.isVisible = false
                        }
                    } else {
                        safeBinding { b ->
                            // Split into doctor-prescribed and AI-recommended
                            val doctorMeds = medications.filter { it.source == "doctor" }
                            val aiMeds = medications.filter { it.source != "doctor" }

                            medicationDoctorAdapter.updateData(doctorMeds)
                            medicationAiAdapter.updateData(aiMeds)

                            b.textNoMedications.isVisible = false

                            // Doctor section
                            if (doctorMeds.isNotEmpty()) {
                                b.textDoctorTitle.isVisible = true
                                b.recyclerViewDoctorMedications.isVisible = true
                            } else {
                                b.textDoctorTitle.isVisible = false
                                b.recyclerViewDoctorMedications.isVisible = false
                            }

                            // AI section
                            if (aiMeds.isNotEmpty()) {
                                b.textAiTitle.isVisible = true
                                b.recyclerViewAiMedications.isVisible = true
                            } else {
                                b.textAiTitle.isVisible = false
                                b.recyclerViewAiMedications.isVisible = false
                            }
                        }
                    }
                } else {
                    Log.e("TreatmentPlan", "Error fetching medications")
                    safeBinding { b ->
                        b.textNoMedications.text = "Could not load medications"
                        b.textNoMedications.isVisible = true
                    }
                }

                // --- 2. Fetch User Profile (for Zone) ---
                val profileResponse = RetrofitClient.apiService.getMyProfile()
                if (profileResponse.isSuccessful && profileResponse.body() != null) {
                    val user = profileResponse.body()!!
                    safeBinding { b -> updateZoneCard(b, user) }
                } else {
                    Log.e("TreatmentPlan", "Error fetching profile")
                    safeBinding { b ->
                        b.textZoneTitle.text = "Error"
                        b.textPlanMessage.text = "Could not load your zone status."
                    }
                }

            } catch (e: Exception) {
                Log.e("TreatmentPlan", "Network Exception: ${e.message}", e)
                safeBinding { b -> Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show() }
            } finally {
                safeBinding { b -> b.progressBar.isVisible = false }
            }
        }
    }

    private fun updateZoneCard(b: FragmentTreatmentPlanBinding, user: User) {
        // This is a placeholder until we update the /profile/me endpoint
        // to return the patient's *latest* zone.
        val baseline = user.baseline?.baselineValue
        if (baseline != null) {
            b.textZoneTitle.text = "Your Baseline"
            b.textPlanMessage.text = "Your personal baseline PEFR is $baseline. " +
                    "Your zones are calculated based on this value."
            b.textZoneTitle.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    requireContext(),
                    R.color.primaryDarkColor
                )
            )

        } else {
            b.textZoneTitle.text = "No Baseline Set"
            b.textPlanMessage.text = "Please go to your profile to set your baseline PEFR."
            b.textZoneTitle.setTextColor(Color.YELLOW)
        }

        // TODO: The ideal solution is to have the /profile/me endpoint
        // also return the user's *latest_pefr_record* to get the zone.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fetchJob?.cancel()
        fetchJob = null
        _binding = null
    }
}
