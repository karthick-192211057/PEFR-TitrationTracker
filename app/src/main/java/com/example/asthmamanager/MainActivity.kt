package com.example.asthmamanager

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.asthmamanager.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val drawerLayout = binding.drawerLayout

        appBarConfiguration = AppBarConfiguration(
            setOf(
                // These are the ONLY fragments that will show the Bottom Nav
                R.id.homeDashboardFragment, R.id.profileFragment, R.id.reportsFragment, R.id.alertsFragment
            ), drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNavigation.setupWithNavController(navController)
        binding.navigationView.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                // 1. Auth screens (ALL UI HIDDEN)
                R.id.splashFragment,
                R.id.loginFragment,
                R.id.signupFragment,
                R.id.forgotPasswordFragment,
                R.id.resetPasswordFragment,
                R.id.patientDetailsFragment -> {
                    binding.toolbar.visibility = View.GONE
                    binding.bottomNavigation.visibility = View.GONE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }

                // 2. Doctor screens & "Detail" screens (Toolbar VISIBLE, BottomNav HIDDEN)
                R.id.doctorDashboardFragment,
                R.id.doctorProfileFragment,
                R.id.editDoctorProfileFragment,
                R.id.prescribeMedicationFragment, // <--- ADDED
                R.id.graphFragment,              // <--- ADDED
                R.id.historyListFragment,        // <--- ADDED
                R.id.PEFRInputFragment,          // <--- ADDED
                R.id.symptomTrackerFragment,     // <--- ADDED
                R.id.notificationFragment,       // <--- ADDED
                R.id.treatmentPlanFragment       // <--- ADDED
                    -> {
                    binding.toolbar.visibility = View.VISIBLE
                    binding.bottomNavigation.visibility = View.GONE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }

                // 3. Main Patient screens (ALL UI VISIBLE)
                else -> {
                    binding.toolbar.visibility = View.VISIBLE
                    binding.bottomNavigation.visibility = View.VISIBLE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}