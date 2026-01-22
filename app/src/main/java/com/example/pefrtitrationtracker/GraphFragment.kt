package com.example.pefrtitrationtracker
import com.example.pefrtitrationtracker.network.Symptom
import com.example.pefrtitrationtracker.network.PEFRRecord
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pefrtitrationtracker.adapter.SymptomAdapter
import com.example.pefrtitrationtracker.databinding.FragmentGraphBinding
import com.example.pefrtitrationtracker.network.RetrofitClient
import com.example.pefrtitrationtracker.utils.safeClick
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.text.format.DateFormat

class GraphFragment : Fragment() {

    private var _binding: FragmentGraphBinding? = null
    private val binding get() = _binding!!

    private fun safeBinding(action: (FragmentGraphBinding) -> Unit) {
        val b = _binding
        if (b != null && isAdded) action(b)
    }
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


        binding.recyclerViewSymptoms.layoutManager =
            LinearLayoutManager(requireContext())
        binding.recyclerViewSymptoms.adapter = SymptomAdapter(emptyList())

        // Guard rapid taps on View History
        binding.buttonViewHistory.safeClick {
            val action = GraphFragmentDirections.actionGraphFragmentToHistoryListFragment(patientId)
            findNavController().navigate(action)
        }
    }

    override fun onResume() {
        super.onResume()
        fetchData()
    }

    private fun parseDate(dateString: String?): Date {
        if (dateString == null) return Date()
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                // server timestamps are UTC — parse as UTC and display using device timezone
                timeZone = TimeZone.getTimeZone("UTC")
            }
            inputFormat.parse(dateString) ?: Date()
        } catch (_: Exception) {
            Date()
        }
    }

    private fun fetchData() {
        // initial UI state while load starts
        safeBinding {
            it.progressBar.isVisible = true
            it.lineChart.isVisible = false
            it.recyclerViewSymptoms.isVisible = false
            it.noDataText.isVisible = false
            it.emptyChartPlaceholder.isVisible = false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Load PEFR
                val pefrResponse =
                    if (patientId == -1)
                        RetrofitClient.apiService.getMyPefrRecords()
                    else
                        RetrofitClient.apiService.getPatientPefrRecords(patientId)

                if (pefrResponse.isSuccessful && pefrResponse.body() != null) {
                    val pefrData = pefrResponse.body()!!
                    if (pefrData.isNotEmpty()) {
                        // update UI only if binding still present
                        safeBinding { b ->
                            setupChart(b, pefrData)
                            b.lineChart.isVisible = true
                            b.emptyChartPlaceholder.isVisible = false
                        }
                    } else {
                        safeBinding { b ->
                            b.noDataText.text = "No PEFR data available. Record your first PEFR to populate the chart."
                            b.noDataText.isVisible = true
                            b.emptyChartPlaceholder.isVisible = true
                        }
                    }
                }

                // Load Symptoms
                val symResponse =
                    if (patientId == -1)
                        RetrofitClient.apiService.getMySymptomRecords()
                    else
                        RetrofitClient.apiService.getPatientSymptomRecords(patientId)

                if (symResponse.isSuccessful && symResponse.body() != null) {
                    val symptomList = symResponse.body()!!
                    // Show the most recent 5 symptoms (newest first)
                    val displayList = symptomList
                        .sortedByDescending { parseDate(it.recordedAt) }
                        .mapNotNull { formatSymptom(it) }
                        .take(5)

                    if (displayList.isNotEmpty()) {
                        safeBinding { b ->
                            setupSymptomList(b, displayList)
                            b.recyclerViewSymptoms.isVisible = true
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("GraphFragment", "Network Error: ${e.message}")
                safeBinding { b ->
                    b.noDataText.isVisible = true
                    b.noDataText.text = "Network error"
                }
            } finally {
                safeBinding { b -> b.progressBar.isVisible = false }
            }
        }
    }

    private fun formatSymptom(s: Symptom): String? {
        val parts = mutableListOf<String>()
        if ((s.wheezeRating ?: 0) > 0) parts.add("Wheeze: ${s.wheezeRating}")
        if ((s.coughRating ?: 0) > 0) parts.add("Cough: ${s.coughRating}")
        if ((s.dyspneaRating ?: 0) > 0) parts.add("Dyspnea: ${s.dyspneaRating}")
        if ((s.nightSymptomsRating ?: 0) > 0) parts.add("Night: ${s.nightSymptomsRating}")
        if (parts.isEmpty()) return null

        val date = parseDate(s.recordedAt)
        val is24 = DateFormat.is24HourFormat(requireContext())
        val pattern = if (is24) "dd MMM, HH:mm" else "dd MMM, hh:mm a"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        return "${sdf.format(date)}: ${parts.joinToString(", ")}" 
    }

    private fun setupChart(b: FragmentGraphBinding, pefrRecords: List<PEFRRecord>) {
        if (pefrRecords.isEmpty()) return

        val entries = mutableListOf<Entry>()
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())

        val sorted = pefrRecords.sortedBy { parseDate(it.recordedAt) }
        sorted.forEachIndexed { i, r ->
            entries.add(Entry(i.toFloat(), r.pefrValue.toFloat()))
        }

        val dataSet = LineDataSet(entries, "PEFR")
        // line styling
        dataSet.color = android.graphics.Color.parseColor("#0077CC")
        dataSet.setCircleColor(android.graphics.Color.parseColor("#0077CC"))
        dataSet.circleRadius = 4f
        dataSet.lineWidth = 2f
        dataSet.valueTextSize = 10f
        dataSet.valueTextColor = android.graphics.Color.DKGRAY
        dataSet.setDrawValues(false) // hide numbers

        // enable filled gradient below line
        dataSet.setDrawFilled(true)
        val drawable = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                android.graphics.Color.parseColor("#80AEE9"), // semi transparent top
                android.graphics.Color.parseColor("#00FFFFFF") // transparent bottom
            )
        )
        dataSet.fillDrawable = drawable

        // create data and set
        val lineData = LineData(dataSet)

        // chart styling
        b.lineChart.data = lineData

        b.lineChart.description.isEnabled = false
        b.lineChart.setDrawGridBackground(false)
        b.lineChart.axisRight.isEnabled = false
        b.lineChart.xAxis.setDrawGridLines(false)
        b.lineChart.axisLeft.setDrawGridLines(false)
        b.lineChart.legend.isEnabled = false
        b.lineChart.setTouchEnabled(true)
        b.lineChart.isDragEnabled = true
        b.lineChart.setPinchZoom(true)

        // animate the chart
        b.lineChart.animateX(900)

        // invalidate to refresh
        b.lineChart.invalidate()
    }



    private fun setupSymptomList(b: FragmentGraphBinding, list: List<String>) {
        b.recyclerViewSymptoms.adapter = SymptomAdapter(list)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
