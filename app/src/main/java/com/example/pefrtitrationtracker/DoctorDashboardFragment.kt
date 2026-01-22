package com.example.pefrtitrationtracker
import android.widget.TextView
import android.widget.ImageView
import com.google.android.material.button.MaterialButton
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.pefrtitrationtracker.utils.safeClick
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pefrtitrationtracker.adapter.PatientAdapter
import com.example.pefrtitrationtracker.databinding.FragmentDoctorDashboardBinding
import com.example.pefrtitrationtracker.network.RetrofitClient
import com.example.pefrtitrationtracker.network.SessionManager
import kotlinx.coroutines.launch

class DoctorDashboardFragment : Fragment() {

    private var _binding: FragmentDoctorDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var patientAdapter: PatientAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDoctorDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // Remove back button only on Doctor Home
        (requireActivity() as MainActivity).supportActionBar?.setDisplayHomeAsUpEnabled(false)
        (requireActivity() as MainActivity).supportActionBar?.setHomeButtonEnabled(false)

        super.onViewCreated(view, savedInstanceState)

        // Graph mode default = patient graph disabled
        requireActivity().intent.putExtra("isDoctorGraph", false)

        // Profile icon → Doctor profile
        binding.imageProfile.safeClick {
            val action =
                DoctorDashboardFragmentDirections
                    .actionDoctorDashboardFragmentToDoctorProfileFragment()
            findNavController().navigate(action)
        }

        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        fetchPatients()
    }

    private fun setupRecyclerView() {

        patientAdapter = PatientAdapter(
            patients = listOf(),

            // Doctor → Graph
            onPatientClicked = { patient ->
                val id: Int = patient.id ?: return@PatientAdapter
                requireActivity().intent.putExtra("isDoctorGraph", true)

                val action =
                    DoctorDashboardFragmentDirections
                        .actionDoctorDashboardFragmentToGraphFragment(id)

                findNavController().navigate(action)
            },

            // Doctor → PDF Report
            onDownloadClicked = { patient ->
                val id: Int = patient.id ?: return@PatientAdapter

                requireActivity().intent.putExtra("isDoctorGraph", false)
                requireActivity().intent.putExtra("isDoctorReport", true)

                val action =
                    DoctorDashboardFragmentDirections
                        .actionDoctorDashboardFragmentToDoctorReportsFragment(id)

                findNavController().navigate(action)
            },

            // Doctor → Prescribe Medication
            onPrescribeClicked = { patient ->
                val id: Int = patient.id ?: return@PatientAdapter

                requireActivity().intent.putExtra("isDoctorGraph", false)

                val action =
                    DoctorDashboardFragmentDirections
                        .actionDoctorDashboardFragmentToPrescribeMedicationFragment(id)

                findNavController().navigate(action)
            },

            // Doctor → History
            onHistoryClicked = { patient ->
                val id: Int = patient.id ?: return@PatientAdapter

                val action =
                    DoctorDashboardFragmentDirections
                        .actionDoctorDashboardFragmentToDoctorHistoryFragment(id)

                findNavController().navigate(action)
            },

            // Doctor → Delete Patient (show confirmation then delete)
            onDeleteClicked = { patient ->
                val id: Int = patient.id ?: return@PatientAdapter
                val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_action, null)

                // ✅ Set DELETE icon
                dialogView.findViewById<ImageView>(R.id.dialogIcon)
                    .setImageResource(R.drawable.ic_delete)

                // ✅ Set title & message
                dialogView.findViewById<TextView>(R.id.dialogTitle).text = "Delete Patient"
                dialogView.findViewById<TextView>(R.id.dialogMessage).text =
                    "Do you want to delete ${patient.fullName ?: "this patient"}?\n\nThis will unlink the patient from you."

                val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setView(dialogView)
                    .setCancelable(false)
                    .create()

                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

                // Cancel
                dialogView.findViewById<MaterialButton>(R.id.buttonCancel)
                    .setOnClickListener { dialog.dismiss() }

                // ✅ Confirm = DELETE
                dialogView.findViewById<MaterialButton>(R.id.buttonConfirm)
                    .apply {
                        text = "Delete"
                        setOnClickListener {
                            deletePatient(id)
                            dialog.dismiss()
                        }
                    }

                dialog.show()
            }

        )

        binding.recyclerViewPatients.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = patientAdapter
        }
    }

    // DELETE PATIENT FROM DOCTOR LIST
    private fun deletePatient(patientId: Int) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.deleteLinkedPatient(patientId)

                if (response.isSuccessful) {
                    // Remove the patient locally if present for immediate feedback
                    val remaining = patientAdapter.getItems().filter { it.id != patientId }
                    patientAdapter.updateData(remaining)
                    // Persist deletion so it doesn't reappear after relogin
                    SessionManager(requireContext()).addDeletedPatientId(patientId)
                    // Also refresh server list in background to remain consistent
                    fetchPatients()
                } else {
                    Log.e("DoctorDashboard", "Delete failed: ${response.code()}")
                    Toast.makeText(requireContext(), "Failed to delete patient (server error)", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("DoctorDashboard", "Delete error: ${e.message}")
            }
        }
    }

    private fun fetchPatients() {
        binding.progressBar.isVisible = true
        binding.textNoPatients.isVisible = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getDoctorPatients(null, null)

                if (response.isSuccessful && response.body() != null) {
                    val patients = response.body()!!

                    if (patients.isNotEmpty()) {
                        // filter out any patients that were deleted locally by this doctor
                        val deleted = SessionManager(requireContext()).fetchDeletedPatientIds()
                        val filtered = patients.filter { it.id == null || !deleted.contains(it.id.toString()) }
                        patientAdapter.updateData(filtered)
                        // Create notifications for any patients in Red zone
                        val session = SessionManager(requireContext())
                        val existing = session.fetchNotifications()
                        for (p in filtered) {
                            val zone = p.latestPefrRecord?.zone ?: p.latestSymptom?.severity ?: ""
                            if (zone.equals("Red", ignoreCase = true)) {
                                val msg = "Patient ${p.fullName ?: "#${p.id}"} is in Red Zone"
                                // avoid duplicate entries with same message
                                if (!existing.any { it.endsWith("|$msg") }) {
                                    session.addNotification(msg)
                                }
                            }
                        }
                        binding.recyclerViewPatients.isVisible = true
                        binding.textNoPatients.isVisible = false
                    } else {
                        binding.recyclerViewPatients.isVisible = false
                        binding.textNoPatients.text = "No patients linked yet."
                        binding.textNoPatients.isVisible = true
                    }

                } else {
                    binding.recyclerViewPatients.isVisible = false
                    binding.textNoPatients.text = "Failed to load patients."
                    binding.textNoPatients.isVisible = true
                }

            } catch (e: Exception) {
                binding.recyclerViewPatients.isVisible = false
                binding.textNoPatients.text = "Network error. Please retry."
                binding.textNoPatients.isVisible = true
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Reset doctor flag after doctor leaves report screen
        requireActivity().intent.putExtra("isDoctorReport", false)
        _binding = null
    }
}
