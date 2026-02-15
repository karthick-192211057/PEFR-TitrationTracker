package com.example.pefrtitrationtracker

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.util.Log
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
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.example.pefrtitrationtracker.utils.safeClick

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    // helper to safely access binding only when view exists
    private fun <T> safeBinding(action: (FragmentReportsBinding) -> T): T? {
        return if (_binding != null && isAdded) {
            action(binding)
        } else null
    }

    private val args: ReportsFragmentArgs by navArgs()
    private var patientId: Int = -1

    // Models
    private var user: User? = null
    private var pefrList: List<PEFRRecord> = emptyList()
    private var symptomsList: List<Symptom> = emptyList()

    private var pdfData: ByteArray? = null
    private var csvData: ByteArray? = null

    // Job management for async operations
    private var fetchJob: kotlinx.coroutines.Job? = null
    private var exportJob: kotlinx.coroutines.Job? = null
    private var linkDoctorJob: kotlinx.coroutines.Job? = null
    private var isFetching = false
    private var isExporting = false

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
                fetchDoctorInfo()   // load any already-linked doctor for display
            } else {
                binding.toggleSharing.isEnabled = false
                binding.labelRealTimeSharing.text = "Sharing managed by patient"
            }
        }

        binding.buttonExportPDF.safeClick {
            if (isExporting) {
                Toast.makeText(requireContext(), "Export already in progress", Toast.LENGTH_SHORT).show()
                return@safeClick
            }
            isExporting = true
            exportJob?.cancel()
            exportJob = lifecycleScope.launch {
                try {
                    if (!isAdded || _binding == null) {
                        isExporting = false
                        return@launch
                    }
                    fetchAllData()
                    if (!isAdded || _binding == null) {
                        isExporting = false
                        return@launch
                    }
                    exportPdf()
                } catch (e: Exception) {
                    Log.e("Reports", "PDF export error: ${e.message}")
                    if (isAdded && _binding != null) {
                        Toast.makeText(requireContext(), "Export failed", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isExporting = false
                }
            }
        }

        binding.buttonExportCSV.safeClick {
            if (isExporting) {
                Toast.makeText(requireContext(), "Export already in progress", Toast.LENGTH_SHORT).show()
                return@safeClick
            }
            isExporting = true
            exportJob?.cancel()
            exportJob = lifecycleScope.launch {
                try {
                    if (!isAdded || _binding == null) {
                        isExporting = false
                        return@launch
                    }
                    fetchAllData()
                    if (!isAdded || _binding == null) {
                        isExporting = false
                        return@launch
                    }
                    exportCsv()
                } catch (e: Exception) {
                    Log.e("Reports", "CSV export error: ${e.message}")
                    if (isAdded && _binding != null) {
                        Toast.makeText(requireContext(), "Export failed", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isExporting = false
                }
            }
        }

        if (isFetching) return
        isFetching = true
        fetchJob?.cancel()
        fetchJob = lifecycleScope.launch {
            try {
                if (!isAdded || _binding == null) {
                    isFetching = false
                    return@launch
                }
                fetchAllData()
            } catch (e: Exception) {
                Log.e("Reports", "Initial data fetch error: ${e.message}")
            } finally {
                isFetching = false
            }
        }
    }

    // ----------------------------------------------------------------------
    // PATIENT-ONLY SHARING TOGGLE
    // ----------------------------------------------------------------------
    private fun setupSharingToggle() {
        val session = com.example.pefrtitrationtracker.network.SessionManager(requireContext())
        val email = session.fetchUserEmail()
        binding.toggleSharing.setOnCheckedChangeListener { _, checked ->
            // persist toggle state per-user if possible
            if (email != null) session.saveSharingEnabledFor(email, checked) else session.saveSharingEnabled(checked)
            if (checked) {
                // when enabling, first check if a doctor is already linked
                checkExistingDoctorBeforeLinking()
            } else {
                Toast.makeText(requireContext(), "Sharing Disabled", Toast.LENGTH_SHORT).show()
                // hiding doctor info makes sense when disabled?
                updateDoctorInfo(null)
            }
        }
    }

    private fun updateDoctorInfo(doctor: User?) {
        // build a formatted string with additional contact if available
        val infoText = if (doctor != null) {
            val namePart = "Dr. ${doctor.fullName ?: doctor.email}"
            val contactPart = doctor.contactInfo?.takeIf { it.isNotBlank() }?.let { "\nContact: $it" } ?: ""
            "Linked doctor: $namePart$contactPart"
        } else null

        safeBinding { b ->
            if (infoText != null) {
                b.textDoctorInfo.text = infoText
                b.textDoctorInfo.visibility = View.VISIBLE
            } else {
                b.textDoctorInfo.visibility = View.GONE
            }
        }
    }

    private fun fetchDoctorInfo() {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.apiService.getLinkedDoctor() }
                if (resp.isSuccessful) {
                    updateDoctorInfo(resp.body())
                } else {
                    updateDoctorInfo(null)
                }
            } catch (_: Exception) {
                updateDoctorInfo(null)
            }
        }
    }

    /**
     * If the patient already has a linked doctor, inform them instead of prompting
     * for an email. Otherwise show the dialog to enter the doctor's address.
     */
    private fun checkExistingDoctorBeforeLinking() {
        linkDoctorJob?.cancel()
        linkDoctorJob = lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.apiService.getLinkedDoctor() }
                if (resp.isSuccessful && resp.body() != null) {
                    // Already linked – notify user and leave toggle checked
                    val doc = resp.body()!!
                    updateDoctorInfo(doc)
                    AlertDialog.Builder(requireContext())
                        .setTitle("Doctor Already Linked")
                        .setMessage("You are already linked with Dr. ${doc.fullName ?: doc.email}.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    // no existing link, proceed to ask (prefill previous email if any)
                    val prev = SessionManager(requireContext()).fetchLastDoctorEmail()
                    showDoctorDialog(prev)
                }
            } catch (e: Exception) {
                // network error; still prompt so user can try linking
                val prev = SessionManager(requireContext()).fetchLastDoctorEmail()
                showDoctorDialog(prev)
            }
        }
    }

    /**
     * Show a dialog prompting the user for their doctor's email.  If an
     * email was previously entered we pre‑fill the field so the user only
     * has to correct it (useful when they mistyped earlier).
     */
    private fun showDoctorDialog(initialEmail: String? = null) {
        val input = EditText(requireContext())
        input.hint = "doctor@email.com"
        input.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        input.setSingleLine(true)
        if (!initialEmail.isNullOrBlank()) {
            input.setText(initialEmail)
            input.setSelection(initialEmail.length)
        }
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
                if (email.isNotEmpty()) {
                    if (android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        linkToDoctor(email)
                    } else {
                        Toast.makeText(requireContext(), "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                        binding.toggleSharing.isChecked = false
                    }
                } else {
                    Toast.makeText(requireContext(), "Please enter doctor's email", Toast.LENGTH_SHORT).show()
                    binding.toggleSharing.isChecked = false
                }
            }
            .setNegativeButton("Cancel") { d, _ ->
                binding.toggleSharing.isChecked = false
                d.dismiss()
            }
            .show()
    }

    private fun linkToDoctor(email: String) {
        if (!isAdded || _binding == null) return
        linkDoctorJob?.cancel()
        linkDoctorJob = lifecycleScope.launch {
            try {
                if (!isAdded || _binding == null) return@launch
                
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.linkDoctor(DoctorPatientLinkRequest(email))
                }

                if (!isAdded || _binding == null) return@launch
                
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Linked!", Toast.LENGTH_LONG).show()
                    // remember for prefill and update UI
                    val session = SessionManager(requireContext())
                    session.saveLastDoctorEmail(email)
                    fetchDoctorInfo()
                } else {
                    // Show detailed error message from backend
                    val errorMessage = try {
                        val errorBody = response.errorBody()?.string()
                        if (errorBody != null) {
                            val jsonError = JSONObject(errorBody)
                            jsonError.optString("detail", "Failed to link")
                        } else {
                            "Failed to link: ${response.code()}"
                        }
                    } catch (e: Exception) {
                        "Failed to link: ${response.message()}"
                    }
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                    Log.e("Reports", "Link doctor error: $errorMessage")
                    // since the backend didn't create a link, reset toggle state
                    binding.toggleSharing.isChecked = false
                }
            } catch (e: Exception) {
                Log.e("Reports", "Link doctor error: ${e.message}")
                if (isAdded && _binding != null) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
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

        // Cancel all async operations
        fetchJob?.cancel()
        exportJob?.cancel()
        linkDoctorJob?.cancel()

        // Reset doctor flag after leaving
        requireActivity().intent.putExtra("isDoctorReport", false)

        _binding = null
    }
}
