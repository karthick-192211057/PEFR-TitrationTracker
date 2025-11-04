package com.example.asthmamanager.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.asthmamanager.databinding.ItemPatientCardBinding
import com.example.asthmamanager.network.User

class PatientAdapter(
    private val patients: List<User>,
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

    class PatientViewHolder(
        private val binding: ItemPatientCardBinding,
        private val onPatientClicked: (User) -> Unit,
        private val onDownloadClicked: (User) -> Unit,
        private val onPrescribeClicked: (User) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(patient: User) {
            binding.textPatientName.text = patient.fullName ?: "N/A"
            binding.textLatestPEFR.text = patient.baseline?.baselineValue?.toString() ?: "N/A"

            // Set patient zone and symptom severity with sample data
            val (zone, color, severity) = getPatientZoneAndSeverity(patient.baseline?.baselineValue)
            binding.textPatientZone.text = zone
            binding.textPatientZone.setTextColor(color)
            binding.textSymptomSeverity.text = severity

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

        private fun getPatientZoneAndSeverity(pefr: Int?): Triple<String, Int, String> {
            if (pefr == null) {
                return Triple("Unknown", Color.BLACK, "N/A")
            }

            return when {
                pefr >= 400 -> Triple("Green Zone", Color.parseColor("#4CAF50"), "Low (2/10)")
                pefr >= 250 -> Triple("Yellow Zone", Color.parseColor("#FFEB3B"), "Moderate (5/10)")
                else -> Triple("Red Zone", Color.parseColor("#EF5350"), "High (8/10)")
            }
        }
    }
}
