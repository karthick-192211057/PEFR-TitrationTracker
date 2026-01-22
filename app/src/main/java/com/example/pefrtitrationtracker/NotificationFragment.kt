package com.example.pefrtitrationtracker

import android.app.TimePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.pefrtitrationtracker.databinding.FragmentNotificationBinding
import com.example.pefrtitrationtracker.reminders.*
import com.example.pefrtitrationtracker.network.SessionManager
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import android.text.format.DateFormat
import com.example.pefrtitrationtracker.utils.safeClick

class NotificationFragment : Fragment() {

    private var _binding: FragmentNotificationBinding? = null
    private val binding get() = _binding!!

    private var selectedHour = 8
    private var selectedMinute = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationBinding.inflate(inflater, container, false)
        return binding.root
    }


    // ----------------------------------------------
    // VIEW CREATED
    // ----------------------------------------------
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val deviceCal = getDeviceCalendar()
        selectedHour = deviceCal.get(Calendar.HOUR_OF_DAY)
        selectedMinute = deviceCal.get(Calendar.MINUTE)

        binding.textTime.text = formatTime(selectedHour, selectedMinute)

        // Load saved reminder (per-user when available)
        val session = SessionManager(requireContext())
        val currentEmail = session.fetchUserEmail()
        val saved = ReminderStore.load(requireContext(), currentEmail)
        // Ensure the switch reflects persisted state explicitly (fixes default-on UI issue)
        binding.switchReminder.isChecked = saved.enabled
        selectedHour = saved.hour
        selectedMinute = saved.minute
        binding.textTime.text = formatTime(saved.hour, saved.minute)

        when (saved.frequency) {
            "DAILY" -> binding.radioDaily.isChecked = true
            "WEEKLY" -> binding.radioWeekly.isChecked = true
            "MONTHLY" -> binding.radioMonthly.isChecked = true
        }

        if (saved.targetPefr > 0) {
            binding.editTextTargetPEFR.setText(saved.targetPefr.toString())
        }

        updateUI(saved.enabled)

        // Check exact alarm permissions and show warning if needed
        checkExactAlarmPermissions()

        // Time picker (respect device 24h setting)
        binding.textTime.safeClick {
            if (!binding.switchReminder.isChecked) return@safeClick
            val is24Hour = DateFormat.is24HourFormat(requireContext())
            TimePickerDialog(requireContext(), { _, hour, minute ->
                selectedHour = hour
                selectedMinute = minute
                binding.textTime.text = formatTime(hour, minute)
            }, selectedHour, selectedMinute, is24Hour).show()
        }

            // Switch listener
            binding.switchReminder.safeClick {
                val isChecked = binding.switchReminder.isChecked
                updateUI(isChecked)

                // Persist quick-enable/disable and cancel if turning off immediately
                val loaded = ReminderStore.load(requireContext(), currentEmail)
                if (!isChecked) {
                    // disable immediately (per-user)
                    ReminderStore.save(requireContext(), loaded.copy(enabled = false), currentEmail)
                    ReminderScheduler(requireContext()).cancel()
                } else {
                    // mark enabled in store but scheduling happens on Save
                    ReminderStore.save(requireContext(), loaded.copy(enabled = true), currentEmail)
                }
            }

        // Save button
        binding.buttonSave.safeClick {
            if (!binding.switchReminder.isChecked) {
                Toast.makeText(requireContext(), "Enable the reminder first", Toast.LENGTH_SHORT).show()
                return@safeClick
            }

            val freq = when (binding.radioGroupFrequency.checkedRadioButtonId) {
                R.id.radioDaily -> "DAILY"
                R.id.radioWeekly -> "WEEKLY"
                R.id.radioMonthly -> "MONTHLY"
                else -> "DAILY"
            }

            val pefr = binding.editTextTargetPEFR.text.toString().toIntOrNull() ?: -1

            if (pefr < 55 || pefr > 999) {
                Toast.makeText(requireContext(), "Enter correct PEFR value", Toast.LENGTH_SHORT).show()
                return@safeClick
            }

            val savedReminder = SavedReminder(
                enabled = true,
                hour = selectedHour,
                minute = selectedMinute,
                frequency = freq,
                targetPefr = pefr
            )

            ReminderStore.save(requireContext(), savedReminder, currentEmail)

            // schedule using device timezone and exact alarms
            ReminderScheduler(requireContext())
                .schedule(selectedHour, selectedMinute, freq, pefr)

            // Persist a notification entry so UI shows the scheduling action immediately
            try {
                val session = SessionManager(requireContext())
                val message = "Reminder scheduled: ${binding.textTime.text} ($freq)"
                session.addNotification(message)
            } catch (_: Exception) {}

            Toast.makeText(
                requireContext(),
                "Reminder scheduled (${binding.textTime.text})",
                Toast.LENGTH_LONG
            ).show()

            // refresh the list so the new notification appears right away
            renderNotifications()

            Handler(Looper.getMainLooper()).postDelayed({
                findNavController().navigate(R.id.homeDashboardFragment)
            }, 800)
        }

        // Load and render persisted notifications
        renderNotifications()
    }

    private fun renderNotifications() {
        val container = binding.notificationContainer
        container.removeAllViews()
        val session = SessionManager(requireContext())
        val items = session.fetchNotifications()

        // Sort by timestamp desc and show message part
        val sorted = items.mapNotNull {
            val parts = it.split('|', limit = 2)
            if (parts.size == 2) Pair(parts[0].toLongOrNull() ?: 0L, parts[1]) else null
        }.sortedByDescending { it.first }

        if (sorted.isEmpty()) {
            val tv = TextView(requireContext())
            tv.text = "No notifications"
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            tv.textSize = 14f
            tv.gravity = Gravity.CENTER
            container.addView(tv)
            return
        }

        for ((_, msg) in sorted) {
            val card = com.google.android.material.card.MaterialCardView(requireContext())
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 8, 0, 8)
            card.layoutParams = lp
            card.radius = 14f
            card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.fieldBackgroundColor))
            card.cardElevation = 4f

            val inner = LinearLayout(requireContext())
            inner.orientation = LinearLayout.VERTICAL
            inner.setPadding(18, 18, 18, 18)

            val tv = TextView(requireContext())
            tv.text = msg
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.primaryDarkColor))
            tv.textSize = 14f

            inner.addView(tv)
            card.addView(inner)
            container.addView(card)
        }
    }


    // ----------------------------------------------
    // UI UPDATE (Enable/Disable fields)
    // ----------------------------------------------
    private fun updateUI(isOn: Boolean) {
        val alpha = if (isOn) 1f else 0.35f

        // Dim all input sections
        binding.labelTime.alpha = alpha
        binding.textTime.alpha = alpha
        binding.labelFrequency.alpha = alpha
        binding.radioGroupFrequency.alpha = alpha
        binding.textInputTargetPEFR.alpha = alpha

        // Enable/Disable interaction
        binding.textTime.isEnabled = isOn
        binding.radioGroupFrequency.isEnabled = isOn
        binding.editTextTargetPEFR.isEnabled = isOn

        // Save button state
        binding.buttonSave.isEnabled = isOn
        binding.buttonSave.alpha = if (isOn) 1f else 0.5f

        // Status text
        if (isOn) {
            binding.textReminderStatus.text = "Reminder Enabled"
            binding.textReminderStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.greenZone)
            )
        } else {
            binding.textReminderStatus.text = "Reminder Disabled"
            binding.textReminderStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.gray)
            )
        }
    }


    // ----------------------------------------------
    // HELPERS
    // ----------------------------------------------
    private fun checkExactAlarmPermissions() {
        val scheduler = ReminderScheduler(requireContext())
        if (!scheduler.canScheduleExactAlarms()) {
            // Show warning about exact alarm permissions
            binding.textPermissionWarning.visibility = View.VISIBLE
            binding.textPermissionWarning.text = "⚠️ Exact alarm permission required for on-time reminders. " +
                    "Go to Settings > Apps > ${context?.getString(R.string.app_name)} > Alarms & reminders to enable."
        } else {
            binding.textPermissionWarning.visibility = View.GONE
        }
    }

    private fun getDeviceCalendar(): Calendar {
        return Calendar.getInstance()
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val cal = getDeviceCalendar().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }

        val is24Hour = DateFormat.is24HourFormat(requireContext())
        val pattern = if (is24Hour) "HH:mm" else "hh:mm a"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(cal.time)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
