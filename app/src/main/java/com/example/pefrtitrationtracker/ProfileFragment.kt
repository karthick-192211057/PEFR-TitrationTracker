package com.example.pefrtitrationtracker

import android.os.Bundle
import com.google.android.material.textfield.TextInputLayout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
// Emergency contacts removed from patient profile per new requirements
// Medication list moved to TreatmentPlanFragment
import com.example.pefrtitrationtracker.databinding.FragmentProfileBinding
import com.example.pefrtitrationtracker.network.*
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import com.example.pefrtitrationtracker.utils.safeClick

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private var currentUser: User? = null
    // medication UI removed from profile
    private var fetchJob: Job? = null
    private var saveJob: Job? = null

    private fun safeBinding(action: (FragmentProfileBinding) -> Unit) {
        val b = _binding
        if (b != null && isAdded) action(b)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEditingState(false)

        // Ensure gender field is properly disabled initially
        binding.autoCompleteGender.isEnabled = false
        binding.autoCompleteGender.isClickable = false
        binding.autoCompleteGender.isFocusable = false
        binding.autoCompleteGender.isFocusableInTouchMode = false

        binding.buttonEditProfile.safeClick { setEditingState(true) }
        binding.buttonSaveChanges.safeClick { showSaveChangesConfirmationDialog() }
        binding.buttonCancelEdit.safeClick { cancelEditing() }

        // If navigated with forceEdit argument, enable editing immediately
        if (arguments?.getBoolean("forceEdit", false) == true) {
            setEditingState(true)
        }

        // Ensure contact input doesn't accept leading zero and stays max 10 digits
        var editingContact = false
        binding.editTextContact.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (editingContact) return
                editingContact = true
                s?.let {
                    var str = it.toString()
                    // remove any non-digit (shouldn't be necessary due to digits attr)
                    str = str.replace(Regex("\\D"), "")
                    // strip leading zeros
                    while (str.startsWith("0")) str = str.removePrefix("0")
                    // limit to 10 chars
                    if (str.length > 10) str = str.substring(0, 10)
                    if (str != it.toString()) {
                        binding.editTextContact.setText(str)
                        binding.editTextContact.setSelection(str.length)
                    }
                }
                editingContact = false
            }
        })

        // Setup gender dropdown options
        val genderOptions = listOf("Male", "Female", "Other")
        val genderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, genderOptions)
        binding.autoCompleteGender.setAdapter(genderAdapter)

        // Handle gender dropdown clicks - show dropdown when clicked and enabled

        binding.buttonDownloadReport.setOnClickListener {
            // Navigate to Reports (Export) screen from drawer/menu
            findNavController().navigate(R.id.reportsFragment)
        }

        binding.buttonLogout.setOnClickListener {

            val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_action, null)
            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            dialogView.findViewById<android.widget.TextView>(R.id.dialogTitle).text = "Logout"
            dialogView.findViewById<android.widget.TextView>(R.id.dialogMessage).text =
                "Are you sure you want to logout?"

            dialogView.findViewById<View>(R.id.buttonCancel).setOnClickListener {
                dialog.dismiss()
            }

            dialogView.findViewById<View>(R.id.buttonConfirm).setOnClickListener {
                dialog.dismiss()
                SessionManager(requireContext()).clearSession()
                findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
            }

            dialog.show()
        }

        binding.buttonDeleteAccount.setOnClickListener {
            showDeleteAccountConfirmationDialog()
        }


        fetchProfileData()

        // Watch key inputs and enable Save only when all mandatory fields are valid
        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { validateProfileInputs() }
        }
        binding.editTextName.addTextChangedListener(textWatcher)
        binding.editTextContact.addTextChangedListener(textWatcher)
        binding.editTextAddress.addTextChangedListener(textWatcher)
        binding.editTextAge.addTextChangedListener(textWatcher)
        binding.editTextHeight.addTextChangedListener(textWatcher)
        binding.autoCompleteGender.addTextChangedListener(textWatcher)
        binding.editTextBaselinePEFR.addTextChangedListener(textWatcher)
    }

    // ------------------------------------------------
    // FETCH PROFILE + CONTACTS + MEDICATIONS
    // ------------------------------------------------
    private fun fetchProfileData() {
        fetchJob = lifecycleScope.launch {
            try {
                // FETCH PROFILE
                val profileResponse = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.getMyProfile()
                }

                if (profileResponse.isSuccessful) {
                    currentUser = profileResponse.body()

                    currentUser?.let { user ->
                        safeBinding { b ->
                            b.editTextName.setText(user.fullName)
                            b.editTextEmail.setText(user.email)
                            b.editTextContact.setText(user.contactInfo)
                            b.editTextAddress.setText(user.address)
                            b.editTextAge.setText(user.age?.toString())
                            b.editTextHeight.setText(user.height?.toString())
                            // set dropdown selection based on user.gender
                            b.autoCompleteGender.setText(user.gender ?: "", false)
                            b.editTextBaselinePEFR.setText(user.baseline?.baselineValue?.toString())
                        }
                    }
                }

                // Fetch linked doctor (if any) and show in UI
                try {
                    val docResp = withContext(Dispatchers.IO) { RetrofitClient.apiService.getLinkedDoctor() }
                    if (docResp.isSuccessful && docResp.body() != null) {
                        val doctor = docResp.body()!!
                        safeBinding { b ->
                            b.cardDoctorLink.visibility = View.VISIBLE
                            b.textDoctorLinkName.text = "Dr. ${doctor.fullName ?: "-"}"
                            b.textDoctorLinkEmail.text = doctor.email ?: "-"
                            b.textDoctorLinkContact.text = doctor.contactInfo ?: "-"
                            b.buttonUnlinkDoctor.setOnClickListener {
                                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                    .setTitle("Unlink Doctor")
                                    .setMessage("Are you sure you want to unlink Dr. ${doctor.fullName ?: doctor.email ?: "doctor"}?")
                                    .setPositiveButton("Unlink") { _, _ ->
                                        lifecycleScope.launch {
                                            try {
                                                val delResp = withContext(Dispatchers.IO) { RetrofitClient.apiService.unlinkDoctor() }
                                                if (delResp.isSuccessful) {
                                                    safeBinding { bb -> bb.cardDoctorLink.visibility = View.GONE }
                                                    Toast.makeText(requireContext(), "Doctor unlinked successfully", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(requireContext(), "Failed to unlink doctor", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                        }
                    } else {
                        safeBinding { b -> b.cardDoctorLink.visibility = View.GONE }
                    }
                } catch (_: Exception) {
                    safeBinding { b -> b.cardDoctorLink.visibility = View.GONE }
                }

                // Emergency contacts removed from patient profile UI

                // Medications are shown in TreatmentPlanFragment (Drawer menu)

            } catch (e: Exception) {
                safeBinding { _ -> Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    // Medication actions moved to TreatmentPlanFragment

    // ------------------------------------------------
    // UI EDITING STATE
    // ------------------------------------------------
    private fun setEditingState(isEditing: Boolean) {

        // normal fields (unchanged)
        binding.editTextName.isEnabled = isEditing
        binding.editTextEmail.isEnabled = isEditing
        binding.editTextContact.isEnabled = isEditing
        binding.editTextAddress.isEnabled = isEditing
        binding.editTextAge.isEnabled = isEditing
        binding.editTextHeight.isEnabled = isEditing
        binding.editTextBaselinePEFR.isEnabled = isEditing

        // -------- GENDER FIELD (STRICT LOCK) --------
        // -------- GENDER FIELD (CORRECT & SIMPLE) --------
        binding.autoCompleteGender.apply {
            isEnabled = isEditing
            isFocusable = isEditing
            isFocusableInTouchMode = isEditing
            isClickable = isEditing
        }

        val til = binding.autoCompleteGender.parent as? TextInputLayout
        til?.isEnabled = isEditing

        // buttons (unchanged)
        binding.buttonEditProfile.visibility =
            if (isEditing) View.GONE else View.VISIBLE

        binding.layoutEditButtons.visibility =
            if (isEditing) View.VISIBLE else View.GONE

        binding.buttonSaveChanges.isEnabled = false

        if (isEditing) validateProfileInputs()
    }



    private fun cancelEditing() {
        // Restore original profile data
        fetchProfileData()
        // Exit edit mode
        setEditingState(false)
    }

    private fun validateProfileInputs() {
        // All fields mandatory when editing
        val nameVal = binding.editTextName.text.toString().trim()
        val contactVal = binding.editTextContact.text.toString().trim()
        val addressVal = binding.editTextAddress.text.toString().trim()
        val ageVal = binding.editTextAge.text.toString().toIntOrNull()
        val heightVal = binding.editTextHeight.text.toString().toIntOrNull()
        val genderVal = binding.autoCompleteGender.text.toString().trim()
        val baselineVal = binding.editTextBaselinePEFR.text.toString().toIntOrNull()

        val nameOk = nameVal.isNotEmpty() && Regex("^[A-Za-z ]+$").matches(nameVal)
        val contactOk = contactVal.isNotEmpty() && Regex("^[6-9][0-9]{9}$").matches(contactVal)
        val addressOk = addressVal.isNotEmpty() && addressVal.length <= 180
        val ageOk = ageVal != null && ageVal in 6..100
        val heightOk = heightVal != null && heightVal in 70..280
        val genderOk = genderVal.lowercase() in listOf("male","female","other")
        val baselineOk = baselineVal == null || (baselineVal >= 55 && baselineVal <= 999)

        binding.buttonSaveChanges.isEnabled = nameOk && contactOk && addressOk && ageOk && heightOk && genderOk && baselineOk
    }

    // ------------------------------------------------
    // SAVE PROFILE + BASELINE
    // ------------------------------------------------
    private fun showSaveChangesConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Save Changes")
            .setMessage("Are you sure you want to save these changes?")
            .setPositiveButton("Save") { _, _ -> saveProfileChanges() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveProfileChanges() {
        val user = currentUser ?: return

        // ------------------- Validation -------------------
        val nameVal = binding.editTextName.text.toString().trim()
        val contactVal = binding.editTextContact.text.toString().trim()
        val addressVal = binding.editTextAddress.text.toString().trim()
        val ageVal = binding.editTextAge.text.toString().toIntOrNull()
        val heightVal = binding.editTextHeight.text.toString().toIntOrNull()
        // read selected gender from dropdown
        val genderVal = binding.autoCompleteGender.text.toString().trim()
        val baselineVal = binding.editTextBaselinePEFR.text.toString().toIntOrNull()

        // Name: letters and spaces only
        val nameRegex = Regex("^[A-Za-z ]+$")
        if (nameVal.isEmpty() || !nameRegex.matches(nameVal)) {
            Toast.makeText(requireContext(), "Enter a valid name (letters and spaces only)", Toast.LENGTH_LONG).show()
            return
        }

        // Contact: starts with 6-9 and exactly 10 digits
        val contactRegex = Regex("^[6-9][0-9]{9}$")
        if (contactVal.isNotEmpty() && !contactRegex.matches(contactVal)) {
            Toast.makeText(requireContext(), "Enter a valid 10-digit contact starting with 6/7/8/9", Toast.LENGTH_LONG).show()
            return
        }

        // Address: max 180 chars
        if (addressVal.length > 180) {
            Toast.makeText(requireContext(), "Address must be less than 180 characters", Toast.LENGTH_LONG).show()
            return
        }

        // Baseline PEFR: optional, but if provided must be 55 - 999
        if (baselineVal != null && (baselineVal < 55 || baselineVal > 999)) {
            Toast.makeText(requireContext(), "Baseline PEFR must be between 55 and 999", Toast.LENGTH_LONG).show()
            return
        }

        // Age: mandatory and must be 6 - 100
        if (ageVal == null || ageVal < 6 || ageVal > 100) {
            Toast.makeText(requireContext(), "Age is required and must be between 6 and 100", Toast.LENGTH_LONG).show()
            return
        }

        // Height cm: if provided, 70 - 280
        if (heightVal != null && (heightVal < 70 || heightVal > 280)) {
            Toast.makeText(requireContext(), "Enter correct height in cm (70-280)", Toast.LENGTH_LONG).show()
            return
        }

        // Gender: compulsory and must be male/female/other (case-insensitive)
        if (genderVal.isEmpty()) {
            Toast.makeText(requireContext(), "Please select a gender", Toast.LENGTH_LONG).show()
            return
        }
        val genderLower = genderVal.lowercase()
        if (genderLower !in listOf("male", "female", "other")) {
            Toast.makeText(requireContext(), "Gender must be Male, Female or Other", Toast.LENGTH_LONG).show()
            return
        }

        val request = ProfileUpdateRequest(
            email = user.email,
            role = user.role,
            fullName = nameVal,
            age = ageVal,
            height = heightVal,
            gender = genderVal,
            contactInfo = contactVal,
            address = addressVal
        )

        saveJob = lifecycleScope.launch {
            try {
                // UPDATE PROFILE
                RetrofitClient.apiService.updateMyProfile(request)

                // SAVE BASELINE IF PROVIDED
                if (baselineVal != null) {
                    val baselineReq = BaselinePEFRCreate(baseline_value = baselineVal)
                    RetrofitClient.apiService.setBaseline(baselineReq)
                }

                Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
                setEditingState(false)
                fetchProfileData()

                // If navigated from PEFR recording suggestion, return to PEFR page
                if (arguments?.getBoolean("returnToPEFR", false) == true) {
                    findNavController().navigate(R.id.PEFRInputFragment)
                }

            } catch (e: Exception) {
                safeBinding { _ -> Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    // ------------------------------------------------
    // DELETE ACCOUNT
    // ------------------------------------------------
    private fun showDeleteAccountConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_action, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<android.widget.TextView>(R.id.dialogTitle).text = "Delete Account"
        dialogView.findViewById<android.widget.TextView>(R.id.dialogMessage).text =
            "Are you sure you want to delete your account? This action cannot be undone and all your data will be permanently removed."

        dialogView.findViewById<View>(R.id.buttonCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.buttonConfirm).apply {
            (this as com.google.android.material.button.MaterialButton).text = "Delete Account"
            setOnClickListener {
                dialog.dismiss()
                deleteAccount()
            }
        }

        dialog.show()
    }

    private fun deleteAccount() {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.deleteMyAccount()
                }

                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Account deleted successfully", Toast.LENGTH_SHORT).show()
                    SessionManager(requireContext()).clearSession()
                    findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
                } else {
                    Toast.makeText(requireContext(), "Failed to delete account. Please try again.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fetchJob?.cancel()
        fetchJob = null
        saveJob?.cancel()
        saveJob = null
        _binding = null
    }
}
