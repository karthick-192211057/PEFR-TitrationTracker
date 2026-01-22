package com.example.pefrtitrationtracker.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.pefrtitrationtracker.R
import com.example.pefrtitrationtracker.databinding.ItemPrescriptionBinding
import com.example.pefrtitrationtracker.network.MedicationWithHistory

class PrescriptionHistoryAdapter(
    private var list: List<MedicationWithHistory>,
    private val onDeleteClicked: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<PrescriptionHistoryAdapter.Holder>() {

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount() = list.size

    fun update(newList: List<MedicationWithHistory>) {
        list = newList
        notifyDataSetChanged()
    }

    class Holder(private val binding: ItemPrescriptionBinding, private val onDelete: ((Int) -> Unit)?) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(m: MedicationWithHistory) {

            // MEDICINE NAME
            binding.textMedicineName.text = m.name

            // DOSAGE
            binding.textDosage.text = "Dosage: ${m.dose ?: "-"}"

            // FREQUENCY / SCHEDULE
            binding.textFrequency.text = "Frequency: ${m.schedule ?: "-"}"

            // DURATION is not in your backend model → hide or replace
            binding.textDuration.text = "Duration: Not Provided"

            // NOTES → Not available in backend model
            binding.textNotes.text = "Notes: Not Provided"

            // STATUS (taken / not taken)
            val status = m.takenStatus ?: "Not Updated"
            binding.textStatus.text = "Status: $status"

            val colorRes = if (status.equals("taken", ignoreCase = true))
                R.color.greenZone
            else
                R.color.redZone

            binding.textStatus.setTextColor(
                ContextCompat.getColor(binding.root.context, colorRes)
            )

            // Show delete button only if a delete handler was provided
            if (onDelete != null) {
                binding.buttonDeletePrescription.visibility = android.view.View.VISIBLE
                binding.buttonDeletePrescription.setOnClickListener {
                    onDelete.invoke(m.id)
                }
            } else {
                binding.buttonDeletePrescription.visibility = android.view.View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemPrescriptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return Holder(binding, onDeleteClicked)
    }
}
