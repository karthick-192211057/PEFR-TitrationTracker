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
import kotlinx.coroutines.Job

class HistoryListFragment : Fragment() {

    private var _binding: FragmentHistoryListBinding? = null
    private val binding get() = _binding!!

    // Get the patientId argument (will be -1 for patients)
    private val args: HistoryListFragmentArgs by navArgs()
    private var patientId: Int = -1

    private lateinit var historyAdapter: HistoryAdapter
    
    // Job management to prevent crashes when user navigates away during loading
    private var fetchJob: Job? = null
    private var isFetching = false

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

        // Enable back button navigation - safe to click anytime
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as? AppCompatActivity)?.supportActionBar?.setHomeButtonEnabled(true)

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
        // Prevent concurrent fetch operations
        if (isFetching) return
        isFetching = true
        
        // Cancel previous fetch job if still running
        fetchJob?.cancel()

        try {
            binding.progressBar.isVisible = true
            binding.recyclerViewHistory.isVisible = false
            binding.textNoHistory.isVisible = false
        } catch (e: Exception) {
            Log.e("HistoryList", "UI setup error: ${e.message}")
            isFetching = false
            return
        }

        fetchJob = lifecycleScope.launch {
            try {
                // Check if fragment is still valid
                if (!isAdded || _binding == null) {
                    isFetching = false
                    return@launch
                }

                val allHistoryItems = mutableListOf<HistoryItem>()
                var fetchError = false

                try {
                    // --- 1. Fetch PEFR Data ---
                    val pefrResponse = try {
                        if (patientId == -1) {
                            RetrofitClient.apiService.getMyPefrRecords()
                        } else {
                            RetrofitClient.apiService.getPatientPefrRecords(patientId)
                        }
                    } catch (e: Exception) {
                        Log.e("HistoryList", "PEFR fetch error: ${e.message}")
                        fetchError = true
                        null
                    }

                    // Check if fragment is still valid after network call
                    if (!isAdded || _binding == null) {
                        isFetching = false
                        return@launch
                    }

                    if (pefrResponse?.isSuccessful == true && pefrResponse.body() != null) {
                        // Add all PEFR records to the list
                        allHistoryItems.addAll(pefrResponse.body()!!.map { HistoryItem.Pefr(it) })
                    } else if (pefrResponse != null) {
                        fetchError = true
                    }

                    // --- 2. Fetch Symptom Data ---
                    val symptomResponse = try {
                        if (patientId == -1) {
                            RetrofitClient.apiService.getMySymptomRecords()
                        } else {
                            RetrofitClient.apiService.getPatientSymptomRecords(patientId)
                        }
                    } catch (e: Exception) {
                        Log.e("HistoryList", "Symptom fetch error: ${e.message}")
                        fetchError = true
                        null
                    }

                    // Check if fragment is still valid
                    if (!isAdded || _binding == null) {
                        isFetching = false
                        return@launch
                    }

                    if (symptomResponse?.isSuccessful == true && symptomResponse.body() != null) {
                        // Add all Symptom records to the list
                        allHistoryItems.addAll(symptomResponse.body()!!.map { HistoryItem.Sym(it) })
                    } else if (symptomResponse != null) {
                        fetchError = true
                    }

                    // Final check before UI update
                    if (!isAdded || _binding == null) {
                        isFetching = false
                        return@launch
                    }

                    // --- 3. Update UI ---
                    try {
                        if (fetchError && allHistoryItems.isNotEmpty()) {
                            Toast.makeText(context, "Partial data loaded", Toast.LENGTH_SHORT).show()
                        } else if (fetchError) {
                            Toast.makeText(context, "Error fetching history data", Toast.LENGTH_SHORT).show()
                        }

                        if (allHistoryItems.isEmpty()) {
                            binding.emptyHistoryPlaceholder.isVisible = true
                            binding.recyclerViewHistory.isVisible = false
                            binding.textNoHistory.isVisible = false
                        } else {
                            historyAdapter.updateData(allHistoryItems)
                            binding.recyclerViewHistory.isVisible = true
                            binding.emptyHistoryPlaceholder.isVisible = false
                            binding.textNoHistory.isVisible = false
                        }
                    } catch (e: Exception) {
                        Log.e("HistoryList", "UI update error: ${e.message}")
                    }

                } catch (e: Exception) {
                    if (!isAdded || _binding == null) {
                        isFetching = false
                        return@launch
                    }
                    Log.e("HistoryList", "Fetch Exception: ${e.message}", e)
                    try {
                        Toast.makeText(context, "Error loading history", Toast.LENGTH_LONG).show()
                        binding.textNoHistory.text = "Error loading data"
                        binding.textNoHistory.isVisible = true
                        binding.recyclerViewHistory.isVisible = false
                        binding.emptyHistoryPlaceholder.isVisible = false
                    } catch (uiError: Exception) {
                        Log.e("HistoryList", "Error update failed: ${uiError.message}")
                    }
                } finally {
                    try {
                        binding.progressBar.isVisible = false
                    } catch (e: Exception) {
                        Log.e("HistoryList", "Progress bar error: ${e.message}")
                    }
                }
            } finally {
                isFetching = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel any in-flight data loading operations when user navigates away
        fetchJob?.cancel()
        _binding = null
        _binding = null
    }
}
