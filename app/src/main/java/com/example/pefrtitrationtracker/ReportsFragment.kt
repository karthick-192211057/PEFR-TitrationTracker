package com.example.pefrtitrationtracker

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.example.pefrtitrationtracker.databinding.FragmentReportsBinding
import com.example.pefrtitrationtracker.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.example.pefrtitrationtracker.utils.safeClick

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private val args: ReportsFragmentArgs by navArgs()
    private var patientId: Int = -1

    // Models
    private var user: User? = null
    private var pefrList: List<PEFRRecord> = emptyList()
    private var symptomsList: List<Symptom> = emptyList()

    private var pdfData: ByteArray? = null
    private var csvData: ByteArray? = null

    private val savePdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && pdfData != null) {
            requireContext().contentResolver.openOutputStream(uri)?.use {
                it.write(pdfData)
            }
            Toast.makeText(requireContext(), "PDF Saved!", Toast.LENGTH_LONG).show()
        }
    }

    private val saveCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null && csvData != null) {
            requireContext().contentResolver.openOutputStream(uri)?.use {
                it.write(csvData)
            }
            Toast.makeText(requireContext(), "CSV Saved!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        patientId = args.patientId
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.title =
            if (patientId == -1) "My Reports" else "Patient Report"

        // CHECK IF DOCTOR OPENED THIS REPORT
        val isDoctorReport = requireActivity().intent.getBooleanExtra("isDoctorReport", false)

        if (isDoctorReport) {
            // 🔥 DOCTOR MODE → HIDE SHARING OPTIONS COMPLETELY
            binding.toggleSharing.visibility = View.GONE
            binding.labelRealTimeSharing.visibility = View.GONE
            binding.toggleSharing.isEnabled = false
        } else {
            // PATIENT MODE → Show toggle normally
            if (patientId == -1) {
                // initialize toggle from session (per-user if available)
                val session = com.example.pefrtitrationtracker.network.SessionManager(requireContext())
                val email = session.fetchUserEmail()
                binding.toggleSharing.isChecked = if (email != null) session.fetchSharingEnabledFor(email) else session.fetchSharingEnabled()
                setupSharingToggle()
            } else {
                binding.toggleSharing.isEnabled = false
                binding.labelRealTimeSharing.text = "Sharing managed by patient"
            }
        }

        binding.buttonExportPDF.safeClick {
            lifecycleScope.launch {
                fetchAllData()
                exportPdf()
            }
        }

        binding.buttonExportCSV.safeClick {
            lifecycleScope.launch {
                fetchAllData()
                exportCsv()
            }
        }

        lifecycleScope.launch {
            fetchAllData()
        }
    }

    // ----------------------------------------------------------------------
    // PATIENT-ONLY SHARING TOGGLE
    // ----------------------------------------------------------------------
    private fun setupSharingToggle() {
        val session = com.example.pefrtitrationtracker.network.SessionManager(requireContext())
        val email = session.fetchUserEmail()
        binding.toggleSharing.setOnCheckedChangeListener { _, checked ->
            if (email != null) session.saveSharingEnabledFor(email, checked) else session.saveSharingEnabled(checked)
            if (checked) showDoctorDialog() else Toast.makeText(requireContext(), "Sharing Disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDoctorDialog() {
        val input = EditText(requireContext())
        input.hint = "doctor@email.com"
        input.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        input.setSingleLine(true)
        input.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                val email = input.text.toString().trim()
                if (email.isNotEmpty()) {
                    linkToDoctor(email)
                } else {
                    binding.toggleSharing.isChecked = false
                }
                true
            } else false
        }

        val box = FrameLayout(requireContext())
        box.addView(input)

        AlertDialog.Builder(requireContext())
            .setTitle("Link Doctor")
            .setMessage("Enter your doctor's email:")
            .setView(box)
            .setPositiveButton("Link") { _, _ ->
                val email = input.text.toString().trim()
                if (email.isNotEmpty()) linkToDoctor(email)
                else binding.toggleSharing.isChecked = false
            }
            .setNegativeButton("Cancel") { d, _ ->
                binding.toggleSharing.isChecked = false
                d.dismiss()
            }
            .show()
    }

    private fun linkToDoctor(email: String) {
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.linkDoctor(DoctorPatientLinkRequest(email))
            }

            if (response.isSuccessful)
                Toast.makeText(requireContext(), "Linked!", Toast.LENGTH_LONG).show()
            else
                Toast.makeText(requireContext(), "Failed to link", Toast.LENGTH_LONG).show()
        }
    }

    // ----------------------------------------------------------------------
    // DATA FETCH
    // ----------------------------------------------------------------------
    private suspend fun fetchAllData() {
        withContext(Dispatchers.IO) {
            try {
                if (patientId == -1) {
                    // Export for current user
                    user = RetrofitClient.apiService.getMyProfile().body()
                    pefrList = RetrofitClient.apiService.getMyPefrRecords().body() ?: emptyList()
                    symptomsList = RetrofitClient.apiService.getMySymptomRecords().body() ?: emptyList()
                } else {
                    // Doctor mode: fetch patient's profile via doctor patients list and specific endpoints
                    val patientsResp = RetrofitClient.apiService.getDoctorPatients(null, null)
                    if (patientsResp.isSuccessful) {
                        val found = patientsResp.body()?.firstOrNull { it.id == patientId }
                        user = found
                    }

                    // fetch pefr & symptoms for that patient specifically
                    pefrList = RetrofitClient.apiService.getPatientPefrRecords(patientId).body() ?: emptyList()
                    symptomsList = RetrofitClient.apiService.getPatientSymptomRecords(patientId).body() ?: emptyList()
                }
            } catch (_: Exception) {
            }
        }
    }

    // ----------------------------------------------------------------------
    // CSV EXPORT
    // ----------------------------------------------------------------------
    private fun parseDate(dateString: String?): Date {
        if (dateString == null) return Date()
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            inputFormat.parse(dateString) ?: Date()
        } catch (_: Exception) {
            Date()
        }
    }

    private fun exportCsv() {
        try {
            val builder = StringBuilder()

            builder.append("### USER PROFILE ###\n")
            builder.append("Name,${user?.fullName}\n")
            builder.append("Email,${user?.email}\n")
            builder.append("Age,${user?.age}\n")
            builder.append("Height,${user?.height}\n")
            builder.append("Gender,${user?.gender}\n")
            builder.append("Address,${user?.address}\n")
            builder.append("Role,${user?.role}\n")
            builder.append("Baseline PEFR,${user?.baseline?.baselineValue}\n\n")

            builder.append("### PEFR RECORDS (LAST WEEK) ###\n")
            builder.append("Value,Date,Zone,Trend\n")
            val oneWeekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -7) }.time
            for (r in pefrList.filter { parseDate(it.recordedAt)?.after(oneWeekAgo) ?: false }) {
                builder.append("${r.pefrValue},${r.recordedAt},${r.zone},${r.trend}\n")
            }
            builder.append("\n")

            builder.append("### SYMPTOMS (LAST WEEK) ###\n")
            builder.append("Date,Wheeze,Cough,Dyspnea,Night,Severity\n")
            for (s in symptomsList.filter { parseDate(it.recordedAt)?.after(oneWeekAgo) ?: false }) {
                builder.append("${s.recordedAt},${s.wheezeRating},${s.coughRating},${s.dyspneaRating},${s.nightSymptomsRating},${s.severity}\n")
            }

            csvData = builder.toString().toByteArray()
            saveCsvLauncher.launch("asthma_report.csv")

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "CSV Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ----------------------------------------------------------------------
    // PDF EXPORT
    // ----------------------------------------------------------------------
    private fun exportPdf() {
        try {
            if (user == null) {
                Toast.makeText(requireContext(), "No data to export.", Toast.LENGTH_LONG).show()
                return
            }

            val pdf = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val paint = Paint()

            var y = 40f
            val leftMargin = 25f
            val rightMargin = pageInfo.pageWidth - 25f

            fun write(
                text: String,
                bold: Boolean = false,
                size: Float = 12f,
                lineSpacing: Float = 20f
            ) {
                paint.isFakeBoldText = bold
                paint.textSize = size
                canvas.drawText(text, leftMargin, y, paint)
                y += lineSpacing
            }

            fun divider() {
                val oldStroke = paint.strokeWidth
                paint.strokeWidth = 1.2f
                canvas.drawLine(leftMargin, y, rightMargin, y, paint)
                paint.strokeWidth = oldStroke
                y += 14f
            }

            fun space(height: Float = 10f) {
                y += height
            }

            // Header
            val dateString =
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

            write("ASTHMA MANAGER", bold = true, size = 18f)
            write(if (patientId == -1) "MY REPORT" else "PATIENT REPORT", bold = true, size = 16f)
            write("Generated: $dateString", size = 11f)
            space(6f)
            divider()

            // Profile
            write("PATIENT DETAILS", bold = true, size = 15f)
            divider()

            user?.let {
                write("Name: ${it.fullName ?: "-"}")
                write("Email: ${it.email ?: "-"}")
                write("Age: ${it.age ?: "-"}")
                write("Height: ${it.height ?: "-"}")
                write("Gender: ${it.gender ?: "-"}")
                write("Address: ${it.address ?: "-"}")
                write("Role: ${it.role ?: "-"}")
                write("Baseline PEFR: ${it.baseline?.baselineValue ?: "-"}")
            }

            space(6f)
            divider()

            // PEFR chart (last week)
            write("PEFR (last 7 days)", bold = true, size = 14f)
            space(6f)

            // draw a simple sparkline for last week
            val oneWeekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -7) }.time
            val pefrWeek = pefrList.filter { parseDate(it.recordedAt)?.after(oneWeekAgo) ?: false }
            if (pefrWeek.isNotEmpty()) {
                val chartLeft = leftMargin
                val chartRight = rightMargin
                val chartTop = y + 6f
                val chartBottom = chartTop + 80f

                // draw border
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRect(chartLeft, chartTop, chartRight, chartBottom, paint)

                // convert values to points
                val values = pefrWeek.map { it.pefrValue.toFloat() }
                val minV = values.minOrNull() ?: 0f
                val maxV = values.maxOrNull() ?: (minV + 1f)
                val span = maxV - minV
                val stepX = (chartRight - chartLeft) / (values.size.coerceAtLeast(1))

                paint.style = Paint.Style.FILL
                paint.strokeWidth = 2f
                var cx = chartLeft + stepX / 2f
                var prevX = cx
                var prevY = chartBottom - ((values[0] - minV) / (if (span == 0f) 1f else span)) * (chartBottom - chartTop)
                for (i in values.indices) {
                    val v = values[i]
                    val px = chartLeft + i * stepX + stepX / 2f
                    val py = chartBottom - ((v - minV) / (if (span == 0f) 1f else span)) * (chartBottom - chartTop)
                    // line
                    paint.color = android.graphics.Color.parseColor("#2E7D32")
                    canvas.drawLine(prevX, prevY, px, py, paint)
                    prevX = px; prevY = py
                }
                y = chartBottom + 12f
            } else {
                write("No PEFR data for last week.")
            }

            space(6f)
            divider()

            // Symptoms list (last week)
            write("SYMPTOMS (last 7 days)", bold = true, size = 14f)
            space(6f)
            val symptomsWeek = symptomsList.filter { parseDate(it.recordedAt)?.after(oneWeekAgo) ?: false }
            if (symptomsWeek.isNotEmpty()) {
                for (s in symptomsWeek) {
                    write("Date: ${s.recordedAt} | Severity: ${s.severity ?: "-"}")
                    write("Wheeze: ${s.wheezeRating ?: "-"}, Cough: ${s.coughRating ?: "-"}, Dyspnea: ${s.dyspneaRating ?: "-"}")
                    space(4f)
                }
            } else {
                write("No symptom records for last week.")
            }

            pdf.finishPage(page)

            val stream = ByteArrayOutputStream()
            pdf.writeTo(stream)
            pdfData = stream.toByteArray()
            pdf.close()

            savePdfLauncher.launch("asthma_report.pdf")

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "PDF Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Reset doctor flag after leaving
        requireActivity().intent.putExtra("isDoctorReport", false)

        _binding = null
    }
}
