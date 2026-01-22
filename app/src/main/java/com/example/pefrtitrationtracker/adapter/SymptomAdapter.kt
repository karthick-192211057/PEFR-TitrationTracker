package com.example.pefrtitrationtracker.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.pefrtitrationtracker.R
import com.example.pefrtitrationtracker.databinding.ItemSymptomBinding

class SymptomAdapter(private val symptoms: List<String>)
    : RecyclerView.Adapter<SymptomAdapter.SymptomViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SymptomViewHolder {
        val binding = ItemSymptomBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SymptomViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SymptomViewHolder, position: Int) {
        holder.bind(symptoms[position])

        // Fade + Slide animation
        val anim = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.item_fade_in_up)
        holder.itemView.startAnimation(anim)
    }

    override fun getItemCount() = symptoms.size

    class SymptomViewHolder(private val binding: ItemSymptomBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(symptom: String) {

            binding.textSymptomName.text = symptom

            // Determine severity value from the string
            val maxSeverity = Regex("\\d+")
                .findAll(symptom)
                .map { it.value.toInt() }
                .maxOrNull() ?: 0

            val ctx = binding.root.context

            // Decide color
            val color = when {
                maxSeverity >= 3 -> R.color.zone_red
                maxSeverity == 2 -> R.color.zone_yellow
                else -> R.color.zone_green
            }

            binding.viewIndicator.setBackgroundColor(
                ContextCompat.getColor(ctx, color)
            )
        }
    }
}
