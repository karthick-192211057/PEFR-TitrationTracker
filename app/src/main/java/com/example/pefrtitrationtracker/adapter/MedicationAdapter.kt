package com.example.pefrtitrationtracker.adapter

import com.example.pefrtitrationtracker.R
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.example.pefrtitrationtracker.utils.safeClick
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pefrtitrationtracker.databinding.ItemMedicationBinding
import com.example.pefrtitrationtracker.network.SessionManager
import java.util.concurrent.TimeUnit
import com.example.pefrtitrationtracker.network.Medication


class MedicationAdapter(
    private var medications: List<Medication>,
    private val onEditClicked: (Medication) -> Unit,
    private val onDeleteClicked: (Medication) -> Unit
) : RecyclerView.Adapter<MedicationAdapter.ViewHolder>() {

    fun updateData(newMedications: List<Medication>) {
        medications = newMedications
        notifyDataSetChanged()
    }

    fun getItems(): List<Medication> = medications

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMedicationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onEditClicked, onDeleteClicked)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(medications[position])
    }

    override fun getItemCount() = medications.size

    class ViewHolder(
        private val binding: ItemMedicationBinding,
        private val onEditClicked: (Medication) -> Unit,
        private val onDeleteClicked: (Medication) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(medication: Medication) {

            binding.textMedicationName.text = medication.name
            binding.textMedicationDose.text = medication.dose ?: "No dose"

            // Show description if available and append medication metadata if present
            val session = SessionManager(binding.root.context)
            // Prefer server-persisted metadata; fallback to local session cache
            var startMs: Long? = null
            var days: Int? = medication.days
            var prob: Double? = medication.cureProbability
            var dosesRemaining: Int? = medication.dosesRemaining

            if (!medication.startDate.isNullOrBlank()) {
                try {
                    val inst = java.time.OffsetDateTime.parse(medication.startDate).toInstant()
                    startMs = inst.toEpochMilli()
                } catch (e: Exception) {
                    try {
                        val inst2 = java.time.Instant.parse(medication.startDate)
                        startMs = inst2.toEpochMilli()
                    } catch (_: Exception) {
                        startMs = null
                    }
                }
            }

            if (startMs == null) {
                val meta = session.fetchMedicationMeta(medication.id)
                if (meta != null) {
                    startMs = meta.first
                    if (days == null) days = meta.second
                    if (prob == null) prob = meta.third
                }
            }

            if (prob != null || days != null) {
                val base = if (!medication.description.isNullOrBlank()) medication.description else ""
                var metaText = ""
                if (prob != null) metaText += "Cure: ${String.format("%.1f", prob * 100)}%"
                if (startMs != null && days != null) {
                    val elapsed = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - startMs).toInt()
                    val daysLeft = (days - elapsed).coerceAtLeast(0)
                    if (metaText.isNotBlank()) metaText += " • "
                    metaText += "Days left: $daysLeft"
                    // progress bar animation
                    try {
                        val progress = ((days - daysLeft).toFloat() / days.toFloat() * 100).toInt().coerceIn(0,100)
                        binding.progressMedication.progress = progress
                    } catch (_: Exception) {
                        binding.progressMedication.progress = 0
                    }
                }

                binding.textMedicationDescription.visibility = android.view.View.VISIBLE
                binding.textMedicationDescription.text = if (base.isNotBlank()) "$base\n$metaText" else metaText
                binding.textMedicationMeta.text = metaText
                binding.textMedicationMeta.visibility = android.view.View.VISIBLE
            } else {
                if (!medication.description.isNullOrBlank()) {
                    binding.textMedicationDescription.visibility = android.view.View.VISIBLE
                    binding.textMedicationDescription.text = medication.description
                } else {
                    binding.textMedicationDescription.visibility = android.view.View.GONE
                }
                binding.textMedicationMeta.visibility = android.view.View.GONE
                binding.progressMedication.progress = 0
            }

            // -----------------------------
            // SHOW STATUS IN UI
            // -----------------------------
            val status = medication.takenStatus ?: "Not Updated"
            binding.textMedicationStatus.text = "Status: $status"

            // -----------------------------
            // STATUS COLOR (use theme resources)
            // -----------------------------
            val ctx = binding.root.context
            when (status.lowercase()) {
                "taken" -> binding.textMedicationStatus.setTextColor(ContextCompat.getColor(ctx, R.color.greenZone))
                "not taken" -> binding.textMedicationStatus.setTextColor(ContextCompat.getColor(ctx, R.color.redZone))
                else -> binding.textMedicationStatus.setTextColor(ContextCompat.getColor(ctx, R.color.yellowZone))
            }

            // -----------------------------
            // BUTTON ACTIONS
            // -----------------------------
            binding.buttonEdit.safeClick {
                onEditClicked(medication)
            }
            binding.buttonDelete.safeClick {
                val status = medication.takenStatus ?: "Not Updated"
                if (status.equals("Not Updated", ignoreCase = true) || status.isBlank()) {
                    // require patient to update status before deleting
                    android.widget.Toast.makeText(binding.root.context, "Please update medication status before deleting", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    onDeleteClicked(medication)
                }
            }
        }
    }
}
