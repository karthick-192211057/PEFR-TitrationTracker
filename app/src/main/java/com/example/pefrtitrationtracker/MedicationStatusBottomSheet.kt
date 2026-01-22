package com.example.pefrtitrationtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.pefrtitrationtracker.databinding.BottomsheetMedicationStatusBinding
import com.example.pefrtitrationtracker.network.ApiService
import com.example.pefrtitrationtracker.network.Medication
import com.example.pefrtitrationtracker.network.MedicationStatusUpdate
import com.example.pefrtitrationtracker.network.RetrofitClient
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MedicationStatusBottomSheet(
    private val medication: Medication,
    private val onSaved: (String) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomsheetMedicationStatusBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetMedicationStatusBinding.inflate(inflater, container, false)

        // Pre-select existing status
        when (medication.takenStatus) {
            "Taken" -> binding.radioTaken.isChecked = true
            "Not Taken" -> binding.radioNotTaken.isChecked = true
            else -> binding.radioNotUpdated.isChecked = true
        }

        binding.buttonSave.setOnClickListener {
            val newStatus = when {
                binding.radioTaken.isChecked -> "Taken"
                binding.radioNotTaken.isChecked -> "Not Taken"
                else -> "Not Updated"
            }

            updateMedicationStatusBackend(newStatus)
        }

        return binding.root
    }

    private fun updateMedicationStatusBackend(newStatus: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val response = RetrofitClient.apiService.updateMedicationStatus(
                medId = medication.id,
                request = MedicationStatusUpdate(
                    status = newStatus,
                    notes = null
                )
            )

            if (response.isSuccessful) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Status Updated", Toast.LENGTH_SHORT).show()
                    onSaved(newStatus)
                    try {
                        // Persist a simple notification entry so linked doctors (or local UI) can show it
                        val session = com.example.pefrtitrationtracker.network.SessionManager(requireContext())
                        val msg = "Medication ${medication.name} marked '$newStatus'"
                        session.addNotification(msg)
                    } catch (_: Exception) {}
                    dismiss()
                }
            } else {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to update", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
