package com.example.pefrtitrationtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.pefrtitrationtracker.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        // set app name and version in UI
        binding.titleAboutApp.text = getString(R.string.app_name)
        val version = try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            pInfo.versionName ?: ""
        } catch (e: Exception) { "" }
        binding.textVersion.text = if (version.isNotEmpty()) "Version $version" else ""
        
        // Set up click listeners for Terms & Conditions and Privacy Policy
        binding.textTermsAndConditions.setOnClickListener {
            showTermsAndConditionsDialog()
        }
        
        binding.textPrivacyPolicy.setOnClickListener {
            showPrivacyPolicyDialog()
        }
        
        return binding.root
    }
    
    private fun showTermsAndConditionsDialog() {
        val termsContent = """
            TERMS & CONDITIONS
            
            Last Updated: January 2025
            
            1. ACCEPTANCE OF TERMS
            By using the Asthma Manager App, you agree to comply with these Terms & Conditions. If you do not agree, please do not use this app.
            
            2. USE LICENSE
            We grant you a limited, non-exclusive, non-transferable license to use this app for personal, non-commercial purposes.
            
            3. DISCLAIMER OF WARRANTIES
            This app is provided "as is" without warranties of any kind. We do not warrant that the app will be error-free or continuously available.
            
            4. LIMITATION OF LIABILITY
            In no event shall we be liable for any indirect, incidental, special, consequential, or punitive damages arising from your use of this app.
            
            5. USER RESPONSIBILITIES
            You agree to use this app responsibly and not for any illegal or unauthorized purposes. You are responsible for maintaining the confidentiality of your account.
            
            6. MODIFICATIONS
            We reserve the right to modify these terms at any time. Your continued use of the app after modifications constitutes acceptance of the new terms.
            
            7. MEDICAL DISCLAIMER
            This app is designed to assist in tracking PEFR and symptoms. It is not a substitute for professional medical advice. Always consult with a healthcare provider for medical decisions.
            
            8. CONTACT US
            For questions about these terms, please contact our support team through the app.
        """.trimIndent()
        
        showDialog("Terms & Conditions", termsContent)
    }
    
    private fun showPrivacyPolicyDialog() {
        val privacyContent = """
            PRIVACY POLICY
            
            Last Updated: January 2025
            
            1. INFORMATION WE COLLECT
            • Personal information: Name, email, contact number, age, height
            • Health information: PEFR readings, symptoms, medication usage
            • Device information: Device type, OS version, app version
            • Usage data: Features used, interaction patterns
            
            2. HOW WE USE YOUR INFORMATION
            • To provide and improve the app services
            • To track your health metrics securely
            • To share reports with your linked doctor (with your consent)
            • To send health reminders and notifications
            • To analyze app performance and user experience
            
            3. DATA SECURITY
            We implement industry-standard security measures including encryption and secure servers to protect your personal and health information.
            
            4. DATA SHARING
            Your health data is only shared with doctors you explicitly link to your account. We do not sell or share your data with third parties without your consent.
            
            5. DATA RETENTION
            Your data is retained as long as your account is active. You can request deletion of your account and all associated data at any time.
            
            6. THIRD-PARTY SERVICES
            We use Firebase for cloud authentication and messaging. Firebase's privacy policy applies to data processed through their services.
            
            7. YOUR RIGHTS
            You have the right to:
            • Access your personal and health data
            • Correct inaccurate information
            • Request deletion of your account and data
            • Withdraw consent at any time
            
            8. CHANGES TO THIS POLICY
            We may update this privacy policy periodically. We will notify you of material changes through the app.
            
            9. CONTACT US
            For privacy concerns or data requests, please contact our support team through the app.
        """.trimIndent()
        
        showDialog("Privacy Policy", privacyContent)
    }
    
    private fun showDialog(title: String, content: String) {
        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        val textView = TextView(requireContext()).apply {
            text = content
            textSize = 14f
            setPadding(24, 24, 24, 24)
            setTextColor(resources.getColor(R.color.black, null))
            setLineSpacing(8f, 1.2f)
        }
        
        scrollView.addView(textView)
        
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
