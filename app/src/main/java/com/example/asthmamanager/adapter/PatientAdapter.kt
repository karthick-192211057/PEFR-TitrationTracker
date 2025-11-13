package com.example.asthmamanager.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.asthmamanager.R
import com.example.asthmamanager.databinding.ItemPatientCardBinding
import com.example.asthmamanager.network.User

class PatientAdapter(
    private var patients: List<User>,
    private val onPatientClicked: (User) -> Unit,
    private val onDownloadClicked: (User) -> Unit,
    private val onPrescribeClicked: (User) -> Unit
) : RecyclerView.Adapter<PatientAdapter.PatientViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatientViewHolder {
        val binding = ItemPatientCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PatientViewHolder(binding, onPatientClicked, onDownloadClicked, onPrescribeClicked)
    }

    override fun onBindViewHolder(holder: PatientViewHolder, position: Int) {
        holder.bind(patients[position])
    }

    override fun getItemCount() = patients.size

    // Helper to update data efficiently
    fun updateData(newPatients: List<User>) {
        patients = newPatients
        notifyDataSetChanged()
    }

    class PatientViewHolder(
        private val binding: ItemPatientCardBinding,
        private val onPatientClicked: (User) -> Unit,
        private val onDownloadClicked: (User) -> Unit,
        private val onPrescribeClicked: (User) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(patient: User) {
            // 1. FIX: Handle null names gracefully
            val displayName = patient.fullName ?: "Unknown Name"
            binding.textPatientName.text = displayName

            // 2. FIX: Get Real Zone Data
            // We use the 'latestPefrRecord' attached by the backend fix
            val latestPefr = patient.latestPefrRecord
            val zone = latestPefr?.zone ?: "Unknown"
            val pefrValue = latestPefr?.pefrValue?.toString() ?: "N/A"

            // 3. Display Zone and PEFR
            binding.textPatientZone.text = "Zone: $zone"
            binding.textLatestPEFR.text = "PEFR: $pefrValue"

            // 4. Display Symptom Severity
            val severity = patient.latestSymptom?.severity ?: "N/A"
            binding.textSymptomSeverity.text = "Symptoms: $severity"

            // 5. FIX: Add Color Coding for Zone
            val colorRes = when (zone) {
                "Green" -> R.color.greenZone
                "Yellow" -> R.color.yellowZone
                "Red" -> R.color.redZone
                else -> R.color.cardLightBackgroundColor // Default grey/black
            }
            // Assuming you want to color the Zone text
            binding.textPatientZone.setTextColor(
                ContextCompat.getColor(binding.root.context, colorRes)
            )

            // 6. Click Listeners
            itemView.setOnClickListener {
                onPatientClicked(patient)
            }

            binding.buttonDownloadReport.setOnClickListener {
                onDownloadClicked(patient)
            }

            binding.buttonPrescribe.setOnClickListener {
                onPrescribeClicked(patient)
            }
        }
    }
}