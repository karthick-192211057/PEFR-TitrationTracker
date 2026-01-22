package com.example.pefrtitrationtracker.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pefrtitrationtracker.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter(
    private var items: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    fun updateData(newItems: List<String>) {
        items = newItems.sortedByDescending { it.split("|", limit = 2).firstOrNull()?.toLongOrNull() ?: 0L }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(private val binding: ItemNotificationBinding, private val onClick: (String) -> Unit) : RecyclerView.ViewHolder(binding.root) {
        fun bind(notificationString: String) {
            val parts = notificationString.split("|", limit = 2)
            val message = if (parts.size >= 2) parts[1] else notificationString
            val timestamp = parts.firstOrNull()?.toLongOrNull() ?: System.currentTimeMillis()

            binding.textNotificationMessage.text = message

            val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
            sdf.timeZone = TimeZone.getDefault()
            binding.textNotificationTime.text = sdf.format(Date(timestamp))

            binding.root.setOnClickListener { onClick(notificationString) }
            binding.root.alpha = 1.0f // All are "unread" locally
        }
    }
}
