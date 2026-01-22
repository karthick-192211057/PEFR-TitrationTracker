package com.example.pefrtitrationtracker

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pefrtitrationtracker.adapter.HistoryAdapter
import com.example.pefrtitrationtracker.adapter.HistoryItem
import com.example.pefrtitrationtracker.databinding.FragmentHistoryListBinding
import com.example.pefrtitrationtracker.network.RetrofitClient
import kotlinx.coroutines.launch

class HistoryListFragment : Fragment() {

    private var _binding: FragmentHistoryListBinding? = null
    private val binding get() = _binding!!

    // Get the patientId argument (will be -1 for patients)
    private val args: HistoryListFragmentArgs by navArgs()
    private var patientId: Int = -1

    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryListBinding.inflate(inflater, container, false)
        patientId = args.patientId
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (patientId == -1) {
            (activity as? AppCompatActivity)?.supportActionBar?.title = "My History"
        } else {
            (activity as? AppCompatActivity)?.supportActionBar?.title = "Patient History (ID: $patientId)"
        }

        setupRecyclerView()
        fetchHistoryData()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(emptyList())
        binding.recyclerViewHistory.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = historyAdapter
        }
    }

    private fun fetchHistoryData() {
        binding.progressBar.isVisible = true
        binding.recyclerViewHistory.isVisible = false
        binding.textNoHistory.isVisible = false

        lifecycleScope.launch {
            val allHistoryItems = mutableListOf<HistoryItem>()
            var fetchError = false

            try {
                // --- 1. Fetch PEFR Data ---
                val pefrResponse = if (patientId == -1) {
                    RetrofitClient.apiService.getMyPefrRecords()
                } else {
                    RetrofitClient.apiService.getPatientPefrRecords(patientId)
                }

                if (pefrResponse.isSuccessful && pefrResponse.body() != null) {
                    // Add all PEFR records to the list
                    allHistoryItems.addAll(pefrResponse.body()!!.map { HistoryItem.Pefr(it) })
                } else {
                    fetchError = true
                }

                // --- 2. Fetch Symptom Data ---
                val symptomResponse = if (patientId == -1) {
                    RetrofitClient.apiService.getMySymptomRecords()
                } else {
                    RetrofitClient.apiService.getPatientSymptomRecords(patientId)
                }

                if (symptomResponse.isSuccessful && symptomResponse.body() != null) {
                    // Add all Symptom records to the list
                    allHistoryItems.addAll(symptomResponse.body()!!.map { HistoryItem.Sym(it) })
                } else {
                    fetchError = true
                }

                // --- 3. Update UI ---
                if (fetchError) {
                    Toast.makeText(context, "Error fetching some history data", Toast.LENGTH_SHORT).show()
                }

                if (allHistoryItems.isEmpty()) {
                    binding.emptyHistoryPlaceholder.isVisible = true
                    binding.recyclerViewHistory.isVisible = false
                } else {
                    historyAdapter.updateData(allHistoryItems)
                    binding.recyclerViewHistory.isVisible = true
                    binding.emptyHistoryPlaceholder.isVisible = false
                }

            } catch (e: Exception) {
                Log.e("HistoryList", "Network Exception: ${e.message}", e)
                Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                binding.textNoHistory.text = "Network error"
                binding.textNoHistory.isVisible = true
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
