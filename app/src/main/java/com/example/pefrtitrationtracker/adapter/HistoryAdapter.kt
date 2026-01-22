package com.example.pefrtitrationtracker.adapter
import com.example.pefrtitrationtracker.network.Symptom
import com.example.pefrtitrationtracker.network.PEFRRecord
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.pefrtitrationtracker.R
import com.example.pefrtitrationtracker.databinding.ItemHistoryRecordBinding
import java.text.SimpleDateFormat
import java.util.*

sealed class HistoryItem {
    data class Pefr(val record: PEFRRecord) : HistoryItem()
    data class Sym(val record: Symptom) : HistoryItem()

    fun getDateString(): String {
        return when (this) {
            is Pefr -> this.record.recordedAt
            is Sym -> this.record.recordedAt
        }
    }
}

class HistoryAdapter(
    private var historyItems: List<HistoryItem>
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private fun parseDate(dateString: String): Date {
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            input.parse(dateString) ?: Date()
        } catch (_: Exception) {
            Date()
        }
    }

    fun updateData(newItems: List<HistoryItem>) {
        historyItems = newItems.sortedByDescending { parseDate(it.getDateString()) }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(historyItems[position])
    }

    override fun getItemCount(): Int = historyItems.size

    inner class HistoryViewHolder(private val binding: ItemHistoryRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val parseFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private fun formatDate(dateString: String): String {
            return try {
                val date = parseFormat.parse(dateString) ?: Date()
                val is24 = android.text.format.DateFormat.is24HourFormat(binding.root.context)
                val pattern = if (is24) "dd MMM yyyy, HH:mm" else "dd MMM yyyy, hh:mm a"
                val displayFormat = SimpleDateFormat(pattern, Locale.getDefault()).apply {
                    timeZone = TimeZone.getDefault()
                }
                displayFormat.format(date)
            } catch (_: Exception) {
                dateString
            }
        }

        fun bind(item: HistoryItem) {
            when (item) {

                // ----------------------------------------------------------------
                // PEFR RECORD
                // ----------------------------------------------------------------
                is HistoryItem.Pefr -> {
                    val r = item.record

                    binding.iconType.setImageResource(R.drawable.ic_graph)
                    binding.textRecordType.text = "PEFR Record"
                    binding.textRecordDate.text = formatDate(r.recordedAt)
                    binding.textRecordDetails.text =
                        "Value: ${r.pefrValue} (${r.zone} Zone)"

                    // Zone color background
                    setZoneColor(r.zone)
                }

                // ----------------------------------------------------------------
                // SYMPTOM RECORD
                // ----------------------------------------------------------------
                is HistoryItem.Sym -> {
                    val s = item.record

                    binding.iconType.setImageResource(R.drawable.ic_history)
                    binding.textRecordType.text = "Symptom Record"
                    binding.textRecordDate.text = formatDate(s.recordedAt)
                    binding.textRecordDetails.text = buildSymptomDetails(s)

                    // Symptom card uses fixed light background
                    setCardColor(R.color.cardMediumBackgroundColor)
                }
            }
        }

        private fun buildSymptomDetails(s: Symptom): String {
            val list = mutableListOf<String>()

            s.wheezeRating?.let { if (it > 0) list.add("Wheeze: $it") }
            s.coughRating?.let { if (it > 0) list.add("Cough: $it") }
            s.dyspneaRating?.let { if (it > 0) list.add("Dyspnea: $it") }
            s.nightSymptomsRating?.let { if (it > 0) list.add("Night: $it") }

            return if (list.isEmpty()) "No significant symptoms"
            else list.joinToString(", ")
        }

        private fun setZoneColor(zone: String?) {
            val c = binding.root.context
            val colorRes = when (zone?.lowercase()) {
                "green" -> R.color.zone_green
                "yellow" -> R.color.zone_yellow
                "red" -> R.color.zone_red
                else -> R.color.cardLightBackgroundColor
            }
            setCardColor(colorRes)
        }

        private fun setCardColor(colorRes: Int) {
            binding.rootLayout.setCardBackgroundColor(
                ContextCompat.getColor(binding.root.context, colorRes)
            )
        }
    }
}
