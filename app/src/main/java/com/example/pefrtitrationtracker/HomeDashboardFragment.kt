package com.example.pefrtitrationtracker
import com.example.pefrtitrationtracker.network.User
import com.example.pefrtitrationtracker.network.PEFRRecord
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import androidx.navigation.fragment.findNavController
import android.net.Uri
import com.example.pefrtitrationtracker.utils.safeClick
import com.bumptech.glide.Glide
import com.example.pefrtitrationtracker.network.SessionManager
import com.example.pefrtitrationtracker.databinding.FragmentHomeDashboardBinding
import com.example.pefrtitrationtracker.network.RetrofitClient
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeDashboardFragment : Fragment() {

    private var _binding: FragmentHomeDashboardBinding? = null
    private val binding get() = _binding!!

    // Guards to avoid concurrent network/UI updates causing flicker or race conditions
    private var isDashboardLoading = false
    private var isChartLoading = false

    // Keep references to running jobs so we can cancel them when view is destroyed
    private var dashboardJob: Job? = null
    private var chartJob: Job? = null
    
    // simple cache of the last fetched profile so we can show it immediately when returning
    private var cachedUser: User? = null

    // Navigation throttling to prevent rapid clicks
    private var lastNavigationTime = 0L
    private val NAVIGATION_THROTTLE_MS = 1000L

    private fun safeBinding(action: (FragmentHomeDashboardBinding) -> Unit) {
        if (_binding != null && isAdded) action(binding)
    }
    
    private fun canNavigate(): Boolean {
        val now = System.currentTimeMillis()
        return if (now - lastNavigationTime >= NAVIGATION_THROTTLE_MS) {
            lastNavigationTime = now
            true
        } else {
            false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // if we already have cached data show it right away so user doesn't
        // briefly see the loading placeholder again
        cachedUser?.let { updateUI(it) }
        fetchDashboardData()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ensure placeholder is visible immediately
        try { Glide.with(this).load(R.drawable.ic_person).centerCrop().into(binding.imageProfile) } catch (_: Exception) {}

        // Load persisted profile image URI if available
        try {
            val uriString = SessionManager(requireContext()).fetchProfileImageUri()
            if (!uriString.isNullOrEmpty()) {
                val uri = Uri.parse(uriString)
                Glide.with(this).load(uri).centerCrop().placeholder(R.drawable.ic_person).into(binding.imageProfile)
            } else {
                // show placeholder so UI always has an avatar visible
                Glide.with(this).load(R.drawable.ic_person).centerCrop().into(binding.imageProfile)
            }
        } catch (e: Exception) {
            // fallback to placeholder
            try { Glide.with(this).load(R.drawable.ic_person).centerCrop().into(binding.imageProfile) } catch (_: Exception) {}
        }

        binding.imageProfile.safeClick { 
            if (canNavigate()) findNavController().navigate(HomeDashboardFragmentDirections.actionHomeDashboardFragmentToProfileFragment()) 
        }
        // Use safeClick to prevent multiple rapid navigations/crashes
        binding.buttonRecordPEFR.safeClick { 
            if (canNavigate()) findNavController().navigate(HomeDashboardFragmentDirections.actionHomeDashboardFragmentToPEFRInputFragment()) 
        }
        binding.buttonSetReminder.safeClick { 
            if (canNavigate()) findNavController().navigate(HomeDashboardFragmentDirections.actionHomeDashboardFragmentToNotificationFragment()) 
        }
        binding.cardGraph.safeClick { 
            if (canNavigate()) findNavController().navigate(HomeDashboardFragmentDirections.actionHomeDashboardFragmentToGraphFragment()) 
        }
        binding.cardTodayZone.safeClick { 
            if (canNavigate()) findNavController().navigate(HomeDashboardFragmentDirections.actionHomeDashboardFragmentToTreatmentPlanFragment()) 
        }
        binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && !isChartLoading) fetchChartData(isWeekly = checkedId == R.id.buttonWeekly)
        }
    }

    private fun fetchDashboardData() {
        if (isDashboardLoading) return
        isDashboardLoading = true

        // Cancel previous job if still running
        dashboardJob?.cancel()

        // only show the loading placeholder on the very first load;
        // if we already have cachedUser then keep the UI visible and
        // simply refresh in background
        if (cachedUser == null) {
            safeBinding {
                it.progressBar.isVisible = true
                it.textViewError.isVisible = false
                it.contentScrollView.isVisible = false
            }
        } else {
            safeBinding { it.progressBar.isVisible = true } // small spinner on top
        }

        dashboardJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Check if fragment is still valid
                if (!isAdded || _binding == null) {
                    isDashboardLoading = false
                    return@launch
                }

                val response = try {
                    RetrofitClient.apiService.getMyProfile()
                } catch (e: Exception) {
                    Log.e("Dashboard", "Network error: ${e.message}")
                    safeBinding {
                        it.progressBar.isVisible = false
                        it.textViewError.setText(R.string.error_network)
                        it.textViewError.isVisible = true
                    }
                    isDashboardLoading = false
                    return@launch
                }

                // Check again after network call
                if (!isAdded || _binding == null) {
                    isDashboardLoading = false
                    return@launch
                }

                safeBinding { it.progressBar.isVisible = false }

                if (response.isSuccessful) {
                    response.body()?.let { user ->
                        cachedUser = user
                        if (isAdded && _binding != null) {
                            safeBinding {
                                it.contentScrollView.isVisible = true
                                updateUI(user)
                            }
                        }
                    } ?: run {
                        safeBinding {
                            it.textViewError.setText(R.string.error_profile_not_retrieved)
                            it.textViewError.isVisible = true
                        }
                    }
                } else {
                    safeBinding {
                        it.textViewError.text = "Error: ${response.message()}"
                        it.textViewError.isVisible = true
                    }
                }
            } catch (e: Exception) {
                if (!isAdded || _binding == null) {
                    isDashboardLoading = false
                    return@launch
                }
                Log.e("Dashboard", "Error: ${e.message}", e)
                safeBinding {
                    it.progressBar.isVisible = false
                    it.textViewError.setText(R.string.error_network)
                    it.textViewError.isVisible = true
                }
            } finally {
                isDashboardLoading = false
            }
        }
    }

    private fun updateUI(user: User) {
        // remember for next time so we can show immediately
        cachedUser = user

        safeBinding {
            it.textViewHeader.text = getString(R.string.welcome_message, user.fullName ?: "User")
        }

        val baseline = user.baseline?.baselineValue
        if (baseline != null) {
            safeBinding { it.textBaselinePEFRValue.text = baseline.toString() }
            setupChartLimits(baseline)
        } else {
            safeBinding {
                it.textBaselinePEFRValue.text = "N/A"
                it.lineChart.clear()
                it.textBaselinePEFRValue.setOnClickListener {
                    Toast.makeText(context, "Please set your baseline PEFR.", Toast.LENGTH_SHORT).show()
                }
                // New user: disable some actions until baseline/profile set
                it.buttonSetReminder.isEnabled = false
                it.buttonSetReminder.alpha = 0.5f
                it.cardTodayZone.isEnabled = false
                it.cardTodayZone.alpha = 0.5f
            }
        }

        val latestPefr = user.latestPefrRecord
        if (latestPefr != null) {
            safeBinding {
                it.textPEFRValue.text = latestPefr.pefrValue.toString()
                it.textPEFRPercentage.text = "(${latestPefr.percentage?.toInt() ?: 0}%)"
                it.textTrendIndicator.text = latestPefr.trend ?: "Stable"
                it.textLastRecorded.text =
                    getString(R.string.last_recorded_format, formatDateString(latestPefr.recordedAt))
            }
            updateZoneUI(latestPefr.zone)
        } else {
            safeBinding {
                it.textPEFRValue.text = "---"
                it.textLastRecorded.setText(R.string.no_pefr_record)
            }
            updateZoneUI("Unknown")
        }

        safeBinding { it.toggleGroup.check(R.id.buttonWeekly) }
        fetchChartData(true)
    }

    private fun setupChartLimits(baselinePefr: Int) {
        safeBinding { binding ->

            val axis = binding.lineChart.axisLeft
            axis.removeAllLimitLines()

            axis.addLimitLine(LimitLine(baselinePefr * 0.5f, "Red Zone"))
            axis.addLimitLine(LimitLine(baselinePefr * 0.8f, "Yellow Zone"))

            axis.axisMinimum = 0f
            binding.lineChart.axisRight.isEnabled = false
            binding.lineChart.xAxis.setDrawLabels(false)
        }
    }

    private fun fetchChartData(isWeekly: Boolean) {
        if (isChartLoading) return
        isChartLoading = true

        // Cancel previous chart job if still running
        chartJob?.cancel()

        chartJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Check if fragment is still valid
                if (!isAdded || _binding == null) {
                    isChartLoading = false
                    return@launch
                }

                val response = try {
                    RetrofitClient.apiService.getMyPefrRecords()
                } catch (e: Exception) {
                    Log.e("Chart", "Network error: ${e.message}")
                    isChartLoading = false
                    return@launch
                }

                // Check again after network call
                if (!isAdded || _binding == null) {
                    isChartLoading = false
                    return@launch
                }

                if (response.isSuccessful) {
                    val records = response.body() ?: emptyList()
                    
                    // Filter records by date range
                    val calendar = Calendar.getInstance()
                    if (isWeekly) {
                        calendar.add(Calendar.DAY_OF_MONTH, -7)
                    } else {
                        calendar.add(Calendar.DAY_OF_MONTH, -30)
                    }
                    val cutoffDate = calendar.time
                    
                    val filteredRecords = records.filter { record ->
                        val recordDate = parseDate(record.recordedAt)
                        recordDate.after(cutoffDate)
                    }
                    
                    val entries = filteredRecords
                        .sortedBy { parseDate(it.recordedAt) }
                        .mapIndexed { i, r -> Entry(i.toFloat(), r.pefrValue.toFloat()) }

                    // Final check before updating UI
                    if (!isAdded || _binding == null) {
                        isChartLoading = false
                        return@launch
                    }

                    safeBinding {
                        if (entries.isNotEmpty()) {
                            val dataSet = LineDataSet(
                                entries,
                                if (isWeekly) getString(R.string.weekly_pefr)
                                else getString(R.string.monthly_pefr)
                            )
                            styleDataSet(dataSet)
                            it.lineChart.data = LineData(dataSet)
                            it.lineChart.isVisible = true
                            it.noDataText.isVisible = false
                        } else {
                            it.lineChart.clear()
                            it.lineChart.isVisible = false
                            val noDataMsg = if (isWeekly) 
                                getString(R.string.no_pefr_records_last_7_days)
                            else 
                                getString(R.string.no_pefr_records_last_30_days)
                            it.noDataText.text = noDataMsg
                            it.noDataText.isVisible = true
                        }
                        it.lineChart.invalidate()
                    }
                }
            } catch (e: Exception) {
                if (!isAdded || _binding == null) {
                    isChartLoading = false
                    return@launch
                }
                Log.e("Chart", "Error: ${e.message}", e)
            }
            finally {
                isChartLoading = false
            }
        }
    }

    private fun styleDataSet(dataSet: LineDataSet) {
        dataSet.color = Color.WHITE
        dataSet.setCircleColor(Color.WHITE)
        dataSet.setDrawValues(false)
    }

    private fun updateZoneUI(zone: String) {
        safeBinding { binding ->
            val context = requireContext()

            val green = ContextCompat.getColor(context, R.color.greenZone)
            val yellow = ContextCompat.getColor(context, R.color.yellowZone)
            val red = ContextCompat.getColor(context, R.color.redZone)
            val grey = ContextCompat.getColor(context, R.color.cardLightBackgroundColor)

            when (zone) {
                "Green" -> {
                    binding.cardTodayZone.setCardBackgroundColor(green)
                    binding.textZoneGuidance.setText(R.string.green_zone_guidance)
                }
                "Yellow" -> {
                    binding.cardTodayZone.setCardBackgroundColor(yellow)
                    binding.textZoneGuidance.setText(R.string.yellow_zone_guidance)
                }
                "Red" -> {
                    binding.cardTodayZone.setCardBackgroundColor(red)
                    binding.textZoneGuidance.setText(R.string.red_zone_guidance)
                }
                else -> {
                    binding.cardTodayZone.setCardBackgroundColor(grey)
                    binding.textZoneGuidance.setText(R.string.unknown_zone_guidance)
                }
            }
        }
    }

    private fun formatDateString(dateString: String?): String {
        if (dateString == null) return getString(R.string.just_now)
        return try {
            // Parse server timestamp as UTC then display in device timezone
            val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            input.timeZone = TimeZone.getTimeZone("UTC")
            val date = input.parse(dateString)

            // Respect device 24-hour format preference
            val use24 = android.text.format.DateFormat.is24HourFormat(requireContext())
            val outputPattern = if (use24) "dd MMM, HH:mm" else "dd MMM, hh:mm a"
            SimpleDateFormat(outputPattern, Locale.getDefault()).format(date!!)
        } catch (e: Exception) {
            getString(R.string.just_now)
        }
    }

    private fun parseDate(dateString: String?): Date {
        if (dateString == null) return Date()
        return try {
            // Parse server timestamp as UTC
            val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            input.timeZone = TimeZone.getTimeZone("UTC")
            input.parse(dateString) ?: Date()
        } catch (e: Exception) {
            Date()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel any in-flight network/UI jobs to avoid resuming into a destroyed view
        dashboardJob?.cancel()
        chartJob?.cancel()
        _binding = null
    }
}
