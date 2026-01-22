package com.example.pefrtitrationtracker

import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pefrtitrationtracker.adapter.PrescriptionHistoryAdapter
import com.example.pefrtitrationtracker.databinding.FragmentPrescriptionHistoryBinding
import com.example.pefrtitrationtracker.network.RetrofitClient
import kotlinx.coroutines.launch

class PrescriptionHistoryFragment : Fragment() {

    private var _binding: FragmentPrescriptionHistoryBinding? = null
    private val binding get() = _binding!!

    private val args: PrescriptionHistoryFragmentArgs by navArgs()
    private var patientId: Int = -1

    private lateinit var adapter: PrescriptionHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPrescriptionHistoryBinding.inflate(inflater, container, false)
        patientId = args.patientId
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PrescriptionHistoryAdapter(listOf(), onDeleteClicked = { medId ->
            // confirm and delete via API
            val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_action, null)

// 🗑️ Delete icon
            dialogView.findViewById<ImageView>(R.id.dialogIcon)
                .setImageResource(R.drawable.ic_delete)

// Title & message
            dialogView.findViewById<TextView>(R.id.dialogTitle)
                .text = "Delete Prescription"

            dialogView.findViewById<TextView>(R.id.dialogMessage)
                .text = "Are you sure you want to delete this prescription?"

            val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

// Cancel
            dialogView.findViewById<MaterialButton>(R.id.buttonCancel)
                .setOnClickListener { dialog.dismiss() }

// Confirm = DELETE
            dialogView.findViewById<MaterialButton>(R.id.buttonConfirm)
                .apply {
                    text = "Delete"
                    setOnClickListener {
                        dialog.dismiss()
                        lifecycleScope.launch {
                            try {
                                val del = RetrofitClient.apiService.deleteMedication(medId)
                                if (del.isSuccessful) {
                                    fetchHistory()
                                } else {
                                    val err = try { del.errorBody()?.string() ?: "" } catch (_: Exception) { "" }
                                    if (del.code() == 400 && err.contains("Please update", ignoreCase = true)) {
                                        Toast.makeText(
                                            requireContext(),
                                            "Please update medication status before deleting",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            requireContext(),
                                            "Delete failed: ${del.code()}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    requireContext(),
                                    "Error: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }

            dialog.show()
        })

        binding.recyclerViewPrescriptionHistory.layoutManager =
            LinearLayoutManager(requireContext())

        binding.recyclerViewPrescriptionHistory.adapter = adapter

        fetchHistory()
    }

    private fun fetchHistory() {
        binding.progressBarHistory.isVisible = true
        binding.recyclerViewPrescriptionHistory.isVisible = false
        binding.textNoHistory.isVisible = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getMedicationHistory(patientId)

                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!

                    if (list.isNotEmpty()) {
                        adapter.update(list)
                        binding.recyclerViewPrescriptionHistory.isVisible = true
                    } else {
                        binding.textNoHistory.isVisible = true
                    }

                } else {
                    binding.textNoHistory.isVisible = true
                }

            } catch (e: Exception) {
                binding.textNoHistory.isVisible = true
            } finally {
                binding.progressBarHistory.isVisible = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
