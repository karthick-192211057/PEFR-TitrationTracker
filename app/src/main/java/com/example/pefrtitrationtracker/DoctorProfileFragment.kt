package com.example.pefrtitrationtracker

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.pefrtitrationtracker.databinding.FragmentDoctorProfileBinding
import com.example.pefrtitrationtracker.network.RetrofitClient
import com.example.pefrtitrationtracker.network.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DoctorProfileFragment : Fragment() {

    private var _binding: FragmentDoctorProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDoctorProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.title = "My Profile"

        // Set app logo instead of profile photo
        binding.imageDoctorProfile.setImageResource(R.drawable.ic_app_logo)

        // Load dynamic doctor data
        loadDoctorProfile()

        // Edit Profile
        binding.buttonEditDoctor.setOnClickListener {
            findNavController().navigate(
                DoctorProfileFragmentDirections.actionDoctorProfileFragmentToEditDoctorProfileFragment()
            )
        }

        // Logout with confirmation
        binding.buttonLogoutDoctor.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_action, null)

            // Logout icon
            dialogView.findViewById<ImageView>(R.id.dialogIcon)
                .setImageResource(R.drawable.ic_baseline_logout_24)

            dialogView.findViewById<TextView>(R.id.dialogTitle).text = "Logout"
            dialogView.findViewById<TextView>(R.id.dialogMessage).text =
                "Are you sure you want to logout?"

            val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            dialogView.findViewById<MaterialButton>(R.id.buttonCancel)
                .setOnClickListener { dialog.dismiss() }

            dialogView.findViewById<MaterialButton>(R.id.buttonConfirm)
                .apply {
                    text = "Logout"
                    setOnClickListener {
                        SessionManager(requireContext()).clearAuthToken()
                        findNavController().navigate(
                            DoctorProfileFragmentDirections.actionDoctorProfileFragmentToLoginFragment()
                        )
                        dialog.dismiss()
                    }
                }

            dialog.show()
        }

        // Delete account with confirmation
        binding.buttonDeleteAccountDoctor.setOnClickListener {
            showDeleteAccountConfirmationDialog()
        }
    }

    private fun loadDoctorProfile() {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.getMyProfile()
                }

                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!

                    // Update UI dynamically
                    binding.textDoctorName.text = "Dr. ${user.fullName ?: "-"}"
                    binding.textEmail.text = "Email: ${user.email ?: "-"}"
                    binding.textContact.text = "Contact: ${user.contactInfo ?: "-"}"
                    binding.textAddress.text = "Address: ${user.address ?: "-"}"
                    binding.textSpecialty.text = "Specialty: ${user.role ?: "-"}"

                } else {
                    Toast.makeText(context, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("DoctorProfile", "Error fetching profile", e)
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteAccountConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_action, null)

        // Delete icon
        dialogView.findViewById<ImageView>(R.id.dialogIcon)
            .setImageResource(R.drawable.ic_delete)

        dialogView.findViewById<TextView>(R.id.dialogTitle).text = "Delete Account"
        dialogView.findViewById<TextView>(R.id.dialogMessage).text =
            "Are you sure you want to delete your account? This action cannot be undone and all your data will be permanently removed."

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<MaterialButton>(R.id.buttonCancel)
            .setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<MaterialButton>(R.id.buttonConfirm)
            .apply {
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
                    Toast.makeText(context, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                    SessionManager(requireContext()).clearAuthToken()
                    findNavController().navigate(
                        DoctorProfileFragmentDirections.actionDoctorProfileFragmentToLoginFragment()
                    )
                } else {
                    Toast.makeText(context, "Failed to delete account. Please try again.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadDoctorProfile() // Refresh every time page opens
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
