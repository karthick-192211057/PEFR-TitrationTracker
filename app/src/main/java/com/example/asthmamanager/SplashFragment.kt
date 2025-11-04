package com.example.asthmamanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.asthmamanager.databinding.FragmentSplashBinding
import com.example.asthmamanager.network.RetrofitClient
import com.example.asthmamanager.network.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            delay(3000)
            if (isAdded) {
                val token = withContext(Dispatchers.IO) {
                    SessionManager(requireContext()).fetchAuthToken()
                }

                if (token == null) {
                    findNavController().navigate(SplashFragmentDirections.actionSplashFragmentToLoginFragment())
                } else {
                    // If a token exists, try to fetch the user profile to determine the role
                    try {
                        val response = withContext(Dispatchers.IO) {
                            RetrofitClient.apiService.getMyProfile()
                        }
                        if (response.isSuccessful) {
                            val user = response.body()
                            // --- THIS IS THE FIX ---
                            if (user?.role.equals("Doctor", ignoreCase = true)) {
                                findNavController().navigate(SplashFragmentDirections.actionSplashFragmentToDoctorDashboardFragment())
                            } else {
                                findNavController().navigate(SplashFragmentDirections.actionSplashFragmentToHomeDashboardFragment())
                            }
                            // --- END FIX ---
                        } else {
                            // If the token is invalid, go to login
                            findNavController().navigate(SplashFragmentDirections.actionSplashFragmentToLoginFragment())
                        }
                    } catch (e: Exception) {
                        // If there's a network error, go to login
                        findNavController().navigate(SplashFragmentDirections.actionSplashFragmentToLoginFragment())
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}