package com.example.pefrtitrationtracker

import android.os.Bundle
import android.util.Log
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

    // Job management
    private var fetchJob: kotlinx.coroutines.Job? = null
    private var deleteJob: kotlinx.coroutines.Job? = null
    private var isFetching = false

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
                        deleteJob?.cancel()
                        deleteJob = lifecycleScope.launch {
                            try {
                                if (!isAdded || _binding == null) return@launch
                                
                                val del = RetrofitClient.apiService.deleteMedication(medId)
                                
                                if (!isAdded || _binding == null) return@launch
                                
                                if (del.isSuccessful) {
                                    fetchHistory()
                                } else {
                                    if (isAdded && _binding != null) {
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
                                }
                            } catch (e: Exception) {
                                Log.e("PrescriptionHistory", "Delete error: ${e.message}")
                                if (isAdded && _binding != null) {
                                    Toast.makeText(
                                        requireContext(),
                                        "Error: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
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
        if (isFetching) return
        isFetching = true
        fetchJob?.cancel()
        
        try {
            binding.progressBarHistory.isVisible = true
            binding.recyclerViewPrescriptionHistory.isVisible = false
            binding.textNoHistory.isVisible = false
        } catch (e: Exception) {
            Log.e("PrescriptionHistory", "UI setup error: ${e.message}")
            isFetching = false
            return
        }

        fetchJob = lifecycleScope.launch {
            try {
                if (!isAdded || _binding == null) {
                    isFetching = false
                    return@launch
                }
                
                val response = RetrofitClient.apiService.getMedicationHistory(patientId)

                if (!isAdded || _binding == null) {
                    isFetching = false
                    return@launch
                }
                
                if (response.isSuccessful && response.body() != null) {
                    try {
                        val list = response.body()!!

                        if (list.isNotEmpty()) {
                            adapter.update(list)
                            binding.recyclerViewPrescriptionHistory.isVisible = true
                        } else {
                            binding.textNoHistory.isVisible = true
                        }
                    } catch (e: Exception) {
                        Log.e("PrescriptionHistory", "UI update error: ${e.message}")
                        if (isAdded && _binding != null) {
                            binding.textNoHistory.isVisible = true
                        }
                    }

                } else {
                    if (isAdded && _binding != null) {
                        binding.textNoHistory.isVisible = true
                    }
                }

            } catch (e: Exception) {
                Log.e("PrescriptionHistory", "Fetch error: ${e.message}")
                if (isAdded && _binding != null) {
                    binding.textNoHistory.isVisible = true
                }
            } finally {
                isFetching = false
                if (isAdded && _binding != null) {
                    try {
                        binding.progressBarHistory.isVisible = false
                    } catch (e: Exception) {
                        Log.e("PrescriptionHistory", "Error hiding progress: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fetchJob?.cancel()
        deleteJob?.cancel()
        _binding = null
    }
}
