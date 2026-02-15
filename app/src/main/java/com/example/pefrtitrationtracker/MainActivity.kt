package com.example.pefrtitrationtracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.util.Log
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.pefrtitrationtracker.databinding.ActivityMainBinding
import com.example.pefrtitrationtracker.reminders.ReminderReceiver
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.FirebaseApp

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.bottomNavigation.visibility = View.GONE


        // =====================================================
        // 🔥 FORCE FIREBASE INITIALIZATION (REQUIRED)
        // =====================================================
        FirebaseApp.initializeApp(this)

        // =====================================================
        // 🔥 FORCE FCM TOKEN PRINT (DEBUG – TEMPORARY)
        // =====================================================
        try {
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.e("FCM", "Fetching FCM token failed", task.exception)
                        return@addOnCompleteListener
                    }
                    Log.d("FCM", "FCM token (forced): ${task.result}")
                }
        } catch (e: Exception) {
            Log.e("FCM", "FCM init error", e)
        }
        // =====================================================

        requestNotificationPermission()
        // Request exact alarm permission if required (improves on-time reminders)
        requestExactAlarmPermission()
        createNotificationChannel()

        setSupportActionBar(binding.toolbar)

        // Nav host + controller
        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        val drawerLayout = binding.drawerLayout

        // Patient top-level screens
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeDashboardFragment,
                R.id.profileFragment,
                R.id.reportsFragment,
                R.id.graphFragment,
                R.id.notificationFragment
            ),
            drawerLayout
        )

        // Toolbar + nav controller
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Drawer menu handling
        binding.navigationView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawer(GravityCompat.START)
            NavigationUI.onNavDestinationSelected(item, navController)
            true
        }

        // Bottom navigation
        binding.bottomNavigation.setupWithNavController(navController)
        binding.bottomNavigation.visibility = View.GONE


        // 🔥 Control toolbar & icons based on current screen
        navController.addOnDestinationChangedListener { _, destination, _ ->

            when (destination.id) {

                // AUTH SCREENS
                R.id.splashFragment, R.id.loginFragment,
                R.id.signupFragment, R.id.forgotPasswordFragment,
                R.id.resetPasswordFragment, R.id.patientDetailsFragment, R.id.verifyOtpFragment -> {

                    binding.toolbar.visibility = View.GONE
                    binding.bottomNavigation.visibility = View.GONE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }

                // DOCTOR SCREENS → Back arrow only
                // DOCTOR SCREENS → Back arrow only
                // DOCTOR SCREENS → Back arrow only
                R.id.doctorDashboardFragment,
                R.id.doctorProfileFragment,
                R.id.editDoctorProfileFragment,
                R.id.prescribeMedicationFragment,
                R.id.prescriptionHistoryFragment,
                R.id.reportsFragment,
                R.id.historyListFragment,
                R.id.aboutFragment-> {

                    binding.toolbar.visibility = View.VISIBLE
                    binding.bottomNavigation.visibility = View.GONE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

                    supportActionBar?.setDisplayHomeAsUpEnabled(true)
                    binding.toolbar.navigationIcon =
                        getDrawable(R.drawable.ic_baseline_arrow_back_24)

                    binding.toolbar.setNavigationOnClickListener {
                        onBackPressedDispatcher.onBackPressed()
                    }
                }


                // GRAPH SCREEN
                R.id.graphFragment -> {

                    val isDoctorGraph =
                        intent.getBooleanExtra("isDoctorGraph", false)

                    if (isDoctorGraph) {
                        binding.toolbar.visibility = View.VISIBLE
                        binding.bottomNavigation.visibility = View.GONE
                        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

                        supportActionBar?.setDisplayHomeAsUpEnabled(true)
                        binding.toolbar.navigationIcon =
                            getDrawable(R.drawable.ic_baseline_arrow_back_24)

                        binding.toolbar.setNavigationOnClickListener {
                            intent.putExtra("isDoctorGraph", false)
                            onBackPressedDispatcher.onBackPressed()
                        }

                    } else {
                        binding.toolbar.visibility = View.VISIBLE
                        binding.bottomNavigation.visibility = View.VISIBLE
                        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)

                        supportActionBar?.setDisplayHomeAsUpEnabled(true)
                        binding.toolbar.navigationIcon = getDrawable(R.drawable.ic_menu)

                        binding.toolbar.setNavigationOnClickListener {
                            drawerLayout.openDrawer(GravityCompat.START)
                        }
                    }
                }

                // PATIENT SCREENS (default)
                else -> {
                    binding.toolbar.visibility = View.VISIBLE
                    binding.bottomNavigation.visibility = View.VISIBLE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)

                    supportActionBar?.setDisplayHomeAsUpEnabled(true)
                    binding.toolbar.navigationIcon = getDrawable(R.drawable.ic_menu)

                    binding.toolbar.setNavigationOnClickListener {
                        drawerLayout.openDrawer(GravityCompat.START)
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ReminderReceiver.CHANNEL_ID,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    200
                )
            }
        }
    }

    private fun requestExactAlarmPermission() {
        // On Android 12+ the OS may require the user to grant exact alarm permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
                if (!alarmManager.canScheduleExactAlarms()) {
                    // Launch system settings to request exact alarm scheduling permission
                    try {
                        // Use the action string to avoid compile-time dependency on newer SDK constants
                        val intent = Intent("android.app.action.REQUEST_SCHEDULE_EXACT_ALARM")
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.w("MainActivity", "Could not open exact alarm settings: ${e.message}")
                    }
                } else {
                    Log.d("MainActivity", "Exact alarms already permitted")
                }
            } catch (t: Throwable) {
                Log.w("MainActivity", "Error checking exact alarm permission: ${t.message}")
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                    as NavHostFragment).navController
        return NavigationUI.navigateUp(navController, appBarConfiguration)
    }
}
