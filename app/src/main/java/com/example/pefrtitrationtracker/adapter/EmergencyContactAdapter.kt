package com.example.pefrtitrationtracker.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pefrtitrationtracker.databinding.ItemEmergencyContactBinding
import com.example.pefrtitrationtracker.network.EmergencyContact
import com.example.pefrtitrationtracker.utils.safeClick

class EmergencyContactAdapter(
    private val contacts: List<EmergencyContact>,
    private val onEditClicked: (EmergencyContact) -> Unit,
    private val onDeleteClicked: (EmergencyContact) -> Unit
) : RecyclerView.Adapter<EmergencyContactAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEmergencyContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onEditClicked, onDeleteClicked)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(contacts[position])
    }

    override fun getItemCount() = contacts.size

    class ViewHolder(
        private val binding: ItemEmergencyContactBinding,
        private val onEditClicked: (EmergencyContact) -> Unit,
        private val onDeleteClicked: (EmergencyContact) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contact: EmergencyContact) {
            binding.textContactName.text = contact.name
            binding.textContactPhone.text = contact.phoneNumber
            binding.buttonEdit.safeClick { onEditClicked(contact) }
            binding.buttonDelete.safeClick { onDeleteClicked(contact) }
        }
    }
}
