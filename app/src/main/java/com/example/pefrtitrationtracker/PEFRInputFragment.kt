package com.example.pefrtitrationtracker

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.pefrtitrationtracker.databinding.FragmentPefrInputBinding
import com.example.pefrtitrationtracker.utils.safeClick
import com.example.pefrtitrationtracker.network.PEFRRecordCreate
import com.example.pefrtitrationtracker.network.RetrofitClient
// single Dispatchers import
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers

class PEFRInputFragment : Fragment() {

    private var _binding: FragmentPefrInputBinding? = null
    private val binding get() = _binding!!

    private fun recordPEFR(profileUser: com.example.pefrtitrationtracker.network.User?) {
        val pefrValue = binding.editTextPEFR.text.toString().toIntOrNull()
        if (pefrValue == null || pefrValue < 55 || pefrValue > 999) {
            Toast.makeText(requireContext(), "Enter correct PEFR value", Toast.LENGTH_SHORT).show()
            return
        }

        val baselineVal = profileUser?.baseline?.baselineValue
        val previousPefr = profileUser?.latestPefrRecord?.pefrValue ?: baselineVal

        val pefrRequest = PEFRRecordCreate(pefrValue)

        // --- Disable button to prevent double-click ---
        binding.buttonSubmit.isEnabled = false
        binding.buttonSubmit.text = "Saving..."

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { RetrofitClient.apiService.recordPEFR(pefrRequest) }

                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "PEFR recorded successfully", Toast.LENGTH_SHORT).show()

                    // compute trend
                    val delta = pefrValue - (previousPefr ?: pefrValue)
                    val trend = when {
                        delta > 0 -> "Improving"
                        delta < 0 -> "Worsening"
                        else -> "No change"
                    }
                    val baselinePct = if (baselineVal != null && baselineVal > 0) (pefrValue.toDouble() / baselineVal.toDouble()) * 100.0 else 0.0

                    // show a custom dialog with trend + actions
                    val dlgView = layoutInflater.inflate(R.layout.dialog_pefr_result, null)
                    val tValue = dlgView.findViewById<android.widget.TextView>(R.id.textPefrValue)
                    val tTrend = dlgView.findViewById<android.widget.TextView>(R.id.textTrend)
                    val tBaseline = dlgView.findViewById<android.widget.TextView>(R.id.textBaselinePercent)
                    val btnOk = dlgView.findViewById<android.widget.Button>(R.id.buttonOk)
                    val btnTrack = dlgView.findViewById<android.widget.Button>(R.id.buttonTrack)

                    tValue.text = "PEFR: $pefrValue"
                    tTrend.text = "Trend: $trend (${if (delta!=0) String.format("%+d", delta) else "0"})"
                    tBaseline.text = "Baseline %: ${String.format("%.1f", baselinePct)}%"

                    val dialog = AlertDialog.Builder(requireContext()).setView(dlgView).create()
                    btnOk.setOnClickListener { dialog.dismiss(); findNavController().popBackStack(R.id.homeDashboardFragment, false) }
                    btnTrack.setOnClickListener { dialog.dismiss(); findNavController().navigate(R.id.symptomTrackerFragment) }
                    dialog.show()

                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                    Log.e("PEFRInputFragment", "API Error: ${response.code()} - $errorMsg")
                    Toast.makeText(requireContext(), "Failed to record PEFR. Please try again.", Toast.LENGTH_LONG).show()
                    binding.buttonSubmit.isEnabled = true
                    binding.buttonSubmit.text = "Submit"
                }
            } catch (e: Exception) {
                Log.e("PEFRInputFragment", "Network/Error: ${e.message}", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                binding.buttonSubmit.isEnabled = true
                binding.buttonSubmit.text = "Submit"
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPefrInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.title = "Record PEFR"

        binding.buttonSubmit.safeClick {
            // First, check if the user has a baseline and age set. If not, prompt them to set it in profile.
            lifecycleScope.launch {
                var profileUser: com.example.pefrtitrationtracker.network.User? = null
                try {
                    val profileResp = withContext(Dispatchers.IO) { RetrofitClient.apiService.getMyProfile() }
                    if (profileResp.isSuccessful) profileUser = profileResp.body()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Could not fetch profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }

                val baselineVal = profileUser?.baseline?.baselineValue
                val ageVal = profileUser?.age

                if (baselineVal == null || ageVal == null) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Profile Update Recommended")
                        .setMessage("Your profile is incomplete (age and baseline PEFR). Recording PEFR works best with a complete profile. Would you like to edit your profile now?")
                        .setPositiveButton("Edit Profile") { _, _ ->
                            // pass an argument to force edit mode and return to PEFR after saving
                            val bundle = android.os.Bundle()
                            bundle.putBoolean("forceEdit", true)
                            bundle.putBoolean("returnToPEFR", true)
                            findNavController().navigate(R.id.profileFragment, bundle)
                        }
                        .setNegativeButton("Continue Anyway") { _, _ ->
                            // Proceed with recording PEFR even without profile
                            recordPEFR(profileUser)
                        }
                        .show()
                    return@launch
                }

                // If profile is complete, proceed directly
                recordPEFR(profileUser)
            }
        }

        // Submit when the keyboard action (tick / done) is pressed
        binding.editTextPEFR.setOnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                // Trigger the existing click handler directly
                binding.buttonSubmit.performClick()
                true
            } else {
                false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
