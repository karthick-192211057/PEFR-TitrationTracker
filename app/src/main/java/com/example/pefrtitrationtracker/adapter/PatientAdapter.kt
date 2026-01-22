package com.example.pefrtitrationtracker.adapter
import com.example.pefrtitrationtracker.network.User
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.example.pefrtitrationtracker.utils.safeClick
import androidx.recyclerview.widget.RecyclerView
import com.example.pefrtitrationtracker.R
import com.example.pefrtitrationtracker.databinding.ItemPatientCardBinding
import com.example.pefrtitrationtracker.network.SessionManager

class PatientAdapter(
    private var patients: List<User>,
    private val onPatientClicked: (User) -> Unit,
    private val onDownloadClicked: (User) -> Unit,
    private val onPrescribeClicked: (User) -> Unit,
    private val onHistoryClicked: (User) -> Unit,
    private val onDeleteClicked: (User) -> Unit
) : RecyclerView.Adapter<PatientAdapter.PatientViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatientViewHolder {
        val binding = ItemPatientCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PatientViewHolder(
            binding,
            onPatientClicked,
            onDownloadClicked,
            onPrescribeClicked,
            onHistoryClicked,
            onDeleteClicked
        )
    }

    override fun onBindViewHolder(holder: PatientViewHolder, position: Int) {
        holder.bind(patients[position])
    }

    override fun getItemCount() = patients.size

    fun updateData(newPatients: List<User>) {
        patients = newPatients
        notifyDataSetChanged()
    }

    fun getItems(): List<User> = patients

    class PatientViewHolder(
        private val binding: ItemPatientCardBinding,
        private val onPatientClicked: (User) -> Unit,
        private val onDownloadClicked: (User) -> Unit,
        private val onPrescribeClicked: (User) -> Unit,
        private val onHistoryClicked: (User) -> Unit,
        private val onDeleteClicked: (User) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(patient: User) {

            // NAME
            val displayName = patient.fullName ?: "Unknown Name"
            binding.textPatientName.text = displayName

            // PATIENT ID TEXT (use userId for display)
            val patientIdDisplay = patient.id?.toString() ?: "-"
            binding.textPatientID.text = "Patient ID: $patientIdDisplay"

            // EMAIL
            try {
                binding.textPatientEmail.text = patient.email ?: ""
            } catch (_: Exception) {}

            // PHONE NUMBER
            try {
                binding.textPatientPhone.text = patient.contactInfo ?: ""
            } catch (_: Exception) {}

            // LATEST PEFR / ZONE
            val latestPefr = patient.latestPefrRecord
            val zone = latestPefr?.zone ?: "Unknown"
            val pefrValue = latestPefr?.pefrValue?.toString() ?: "N/A"

            binding.textPatientZone.text = "Zone: $zone"
            binding.textLatestPEFR.text = "Latest PEFR: $pefrValue"

            // SYMPTOMS
            val severity = patient.latestSymptom?.severity ?: "N/A"
            binding.textSymptomSeverity.text = "Symptom Severity: $severity"

            // SIMPLE STATUS FOR NOW
            binding.textPatientStatus.text = "Status: Not Updated"

            // ZONE COLOR
            val colorRes = when (zone) {
                "Green" -> R.color.greenZone
                "Yellow" -> R.color.yellowZone
                "Red" -> R.color.redZone
                else -> R.color.cardLightBackgroundColor
            }
            binding.textPatientZone.setTextColor(
                ContextCompat.getColor(binding.root.context, colorRes)
            )

            // show recently prescribed marker (UI-only)
            try {
                val sid = patient.id ?: -1
                val session = SessionManager(binding.root.context)
                if (sid != -1 && session.isRecentlyPrescribed(sid)) {
                    binding.textPatientStatus.text = "Status: Prescribed"
                    binding.textPatientStatus.setTextColor(ContextCompat.getColor(binding.root.context, R.color.greenZone))
                }
            } catch (_: Exception) {}

            // CARD CLICK → open graph (debounced)
            itemView.safeClick { onPatientClicked(patient) }

            // BUTTONS (debounced)
            binding.buttonDownloadReport.safeClick { onDownloadClicked(patient) }
            binding.buttonPrescribe.safeClick { onPrescribeClicked(patient) }
            binding.buttonHistory.safeClick { onHistoryClicked(patient) }
            binding.buttonDeletePatient.safeClick { onDeleteClicked(patient) }
        }
    }
}
