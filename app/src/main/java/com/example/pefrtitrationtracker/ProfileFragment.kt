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
        val genderOptions = listOf("Male", "Female", "Prefer Not to say")
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

        // Validation rules
        val nameOk = nameVal.isNotEmpty() && nameVal.length <= 30 && Regex("^[A-Za-z ]+$").matches(nameVal)
        val contactOk = contactVal.isNotEmpty() && Regex("^[6-9][0-9]{9}$").matches(contactVal)
        val addressOk = addressVal.isNotEmpty() && addressVal.length <= 180
        val ageOk = ageVal != null && ageVal in 6..125
        val heightOk = heightVal != null && heightVal in 100..300
        val genderOk = genderVal.lowercase() in listOf("male", "female", "prefer not to say")
        val baselineOk = baselineVal == null || (baselineVal >= 55 && baselineVal <= 999)

        // Show error messages if fields are invalid
        if (binding.buttonSaveChanges.visibility == View.VISIBLE) {
            if (!nameOk && nameVal.isNotEmpty()) {
                if (nameVal.length > 30) {
                    binding.editTextName.error = "Maximum 30 characters"
                } else {
                    binding.editTextName.error = "Only letters and spaces allowed"
                }
            } else {
                binding.editTextName.error = null
            }

            if (!contactOk && contactVal.isNotEmpty()) {
                binding.editTextContact.error = "Enter valid mobile number"
            } else {
                binding.editTextContact.error = null
            }

            if (!addressOk && addressVal.isNotEmpty()) {
                binding.editTextAddress.error = if (addressVal.length > 180) "Maximum 180 characters" else "Address required"
            } else {
                binding.editTextAddress.error = null
            }

            val tilGender = binding.autoCompleteGender.parent as? TextInputLayout
            if (!genderOk && genderVal.isNotEmpty()) {
                tilGender?.error = "Select a valid gender"
            } else {
                tilGender?.error = null
            }

            if (!ageOk && !binding.editTextAge.text.isNullOrEmpty()) {
                binding.editTextAge.error = "Enter valid age between 6-125"
            } else {
                binding.editTextAge.error = null
            }

            if (!heightOk && !binding.editTextHeight.text.isNullOrEmpty()) {
                binding.editTextHeight.error = "Enter valid height 100-300 cm"
            } else {
                binding.editTextHeight.error = null
            }
        }

        binding.buttonSaveChanges.isEnabled = nameOk && contactOk && addressOk && ageOk && heightOk && genderOk && baselineOk
    }

    // ------------------------------------------------
    // SAVE PROFILE + BASELINE
    // ------------------------------------------------
    private fun showSaveChangesConfirmationDialog() {
        // Validate all fields before showing confirmation dialog
        val nameVal = binding.editTextName.text.toString().trim()
        val contactVal = binding.editTextContact.text.toString().trim()
        val addressVal = binding.editTextAddress.text.toString().trim()
        val ageVal = binding.editTextAge.text.toString().toIntOrNull()
        val heightVal = binding.editTextHeight.text.toString().toIntOrNull()
        val genderVal = binding.autoCompleteGender.text.toString().trim()
        
        // Check for empty required fields
        if (nameVal.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill Full Name", Toast.LENGTH_SHORT).show()
            return
        }
        if (contactVal.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill Contact Number", Toast.LENGTH_SHORT).show()
            return
        }
        if (addressVal.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill Address", Toast.LENGTH_SHORT).show()
            return
        }
        if (ageVal == null) {
            Toast.makeText(requireContext(), "Please fill Age", Toast.LENGTH_SHORT).show()
            return
        }
        if (heightVal == null) {
            Toast.makeText(requireContext(), "Please fill Height", Toast.LENGTH_SHORT).show()
            return
        }
        if (genderVal.isEmpty()) {
            Toast.makeText(requireContext(), "Please select Gender", Toast.LENGTH_SHORT).show()
            return
        }
        
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

        // Name: letters and spaces only, max 30 characters
        val nameRegex = Regex("^[A-Za-z ]+$")
        if (nameVal.isEmpty() || !nameRegex.matches(nameVal) || nameVal.length > 30) {
            if (nameVal.isEmpty()) {
                binding.editTextName.error = "Name is required"
            } else if (nameVal.length > 30) {
                binding.editTextName.error = "Name must be maximum 30 characters"
            } else {
                binding.editTextName.error = "Only letters and spaces allowed"
            }
            binding.editTextName.requestFocus()
            return
        }

        // Contact: starts with 6-9 and exactly 10 digits
        val contactRegex = Regex("^[6-9][0-9]{9}$")
        if (contactVal.isEmpty() || !contactRegex.matches(contactVal)) {
            binding.editTextContact.error = "Enter valid mobile number"
            binding.editTextContact.requestFocus()
            return
        } else {
            binding.editTextContact.error = null
        }

        // Address: max 180 chars
        if (addressVal.isEmpty() || addressVal.length > 180) {
            binding.editTextAddress.error = if (addressVal.isEmpty()) "Address is required" else "Address must be maximum 180 characters"
            binding.editTextAddress.requestFocus()
            return
        } else {
            binding.editTextAddress.error = null
        }

        // Age: mandatory and must be 6-125
        if (ageVal == null || ageVal < 6 || ageVal > 125) {
            binding.editTextAge.error = "Enter valid age between 6-125"
            binding.editTextAge.requestFocus()
            return
        } else {
            binding.editTextAge.error = null
        }

        // Height cm: mandatory and must be 100-300
        if (heightVal == null || heightVal < 100 || heightVal > 300) {
            binding.editTextHeight.error = "Enter valid height between 100-300 cm"
            binding.editTextHeight.requestFocus()
            return
        } else {
            binding.editTextHeight.error = null
        }

        // Gender: compulsory and must be male/female/prefer not to say (case-insensitive)
        if (genderVal.isEmpty()) {
            val tilGender = binding.autoCompleteGender.parent as? TextInputLayout
            tilGender?.error = "Please select gender"
            binding.autoCompleteGender.requestFocus()
            return
        }
        val genderLower = genderVal.lowercase()
        if (genderLower !in listOf("male", "female", "prefer not to say")) {
            val tilGender = binding.autoCompleteGender.parent as? TextInputLayout
            tilGender?.error = "Gender must be Male, Female or Prefer Not to say"
            binding.autoCompleteGender.requestFocus()
            return
        } else {
            val tilGender = binding.autoCompleteGender.parent as? TextInputLayout
            tilGender?.error = null
        }

        // Baseline PEFR: optional, but if provided must be 55 - 999
        if (baselineVal != null && (baselineVal < 55 || baselineVal > 999)) {
            binding.editTextBaselinePEFR.error = "Baseline PEFR must be between 55 and 999"
            binding.editTextBaselinePEFR.requestFocus()
            return
        } else {
            binding.editTextBaselinePEFR.error = null
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_account_confirmation, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<android.widget.TextView>(R.id.dialogTitle).text = "Delete Account"
        dialogView.findViewById<android.widget.TextView>(R.id.dialogMessage).text =
            "Your account and all associated data will be permanently deleted from our servers. This action cannot be undone."

        val checkboxAcknowledge = dialogView.findViewById<android.widget.CheckBox>(R.id.checkboxAcknowledge)
        val confirmButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonConfirm)
        
        // Initially disable confirm button
        confirmButton.isEnabled = false
        confirmButton.alpha = 0.5f
        
        // Enable confirm button only when checkbox is ticked
        checkboxAcknowledge.setOnCheckedChangeListener { _, isChecked ->
            confirmButton.isEnabled = isChecked
            confirmButton.alpha = if (isChecked) 1.0f else 0.5f
        }

        dialogView.findViewById<View>(R.id.buttonCancel).setOnClickListener {
            dialog.dismiss()
        }

        confirmButton.apply {
            text = "Delete Account"
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
