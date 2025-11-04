package com.example.asthmamanager

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.asthmamanager.adapter.SymptomAdapter
import com.example.asthmamanager.databinding.FragmentGraphBinding
import com.example.asthmamanager.network.PEFRRecord
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.util.Date

class GraphFragment : Fragment() {

    private var _binding: FragmentGraphBinding? = null
    private val binding get() = _binding!!

    private val args: GraphFragmentArgs by navArgs()
    private var patientId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGraphBinding.inflate(inflater, container, false)

        patientId = args.patientId

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Logic for "View History" button would go here
        binding.buttonViewHistory.setOnClickListener {
            // Placeholder: maybe navigate to a list view
        }

        if (patientId == -1) {
            // No patientId passed, so this is a Patient viewing their own graph
            (activity as? AppCompatActivity)?.supportActionBar?.title = "My PEFR Graph"
            setupChart(createDummyData())
            setupSymptomList(createDummySymptoms())
        } else {
            // A patientId was passed, so this is a Doctor viewing a patient's graph
            (activity as? AppCompatActivity)?.supportActionBar?.title = "Patient Graph (ID: $patientId)"
            setupChart(createDummyData())
            setupSymptomList(createDummySymptoms())
        }
    }

    private fun createDummyData(): List<PEFRRecord> {
        val dummyRecords = mutableListOf<PEFRRecord>()
        dummyRecords.add(PEFRRecord(1, 450, "Green", Date(), 1, 95.0, "Up", "Manual"))
        dummyRecords.add(PEFRRecord(2, 400, "Green", Date(), 1, 90.0, "Stable", "Manual"))
        dummyRecords.add(PEFRRecord(3, 350, "Yellow", Date(), 1, 80.0, "Down", "Manual"))
        dummyRecords.add(PEFRRecord(4, 300, "Red", Date(), 1, 70.0, "Down", "Manual"))
        dummyRecords.add(PEFRRecord(5, 420, "Green", Date(), 1, 92.0, "Up", "Manual"))
        return dummyRecords
    }

    private fun createDummySymptoms(): List<String> {
        return listOf("Wheezing", "Coughing", "Shortness of breath")
    }

    private fun setupChart(pefrRecords: List<PEFRRecord>) {
        val entries = mutableListOf<Entry>()

        pefrRecords.sortedBy { it.recordedAt }.forEachIndexed { index, record ->
            entries.add(Entry(index.toFloat(), record.pefrValue.toFloat()))
        }

        val dataSet = LineDataSet(entries, "Patient PEFR")
        styleDataSet(dataSet)
        binding.lineChart.data = LineData(dataSet)

        binding.lineChart.apply {
            description.isEnabled = false
            legend.textColor = Color.BLACK
            axisLeft.textColor = Color.BLACK
            xAxis.textColor = Color.BLACK
            xAxis.setDrawLabels(true)
            xAxis.setDrawGridLines(false)
            axisLeft.setDrawGridLines(true)
            axisRight.isEnabled = false
        }

        binding.lineChart.invalidate() // Refresh the chart
    }

    private fun setupSymptomList(symptoms: List<String>) {
        val adapter = SymptomAdapter(symptoms)
        binding.recyclerViewSymptoms.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewSymptoms.adapter = adapter
    }

    private fun styleDataSet(dataSet: LineDataSet) {
        dataSet.color = Color.BLUE
        dataSet.valueTextColor = Color.BLACK
        dataSet.setCircleColor(Color.BLUE)
        dataSet.setDrawCircleHole(false)
        dataSet.setDrawValues(true)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}