package com.example.pefrtitrationtracker.reminders

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.pefrtitrationtracker.MainActivity
import com.example.pefrtitrationtracker.R
import com.example.pefrtitrationtracker.databinding.ActivityReminderPopupBinding

class ReminderPopupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReminderPopupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReminderPopupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pefr = intent.getIntExtra("target_pefr", -1)
        if (pefr > 0) binding.textPopupMessage.text = "It's time to record your PEFR (target: $pefr)"

        binding.buttonDismiss.setOnClickListener {
            finish()
        }

        binding.buttonOpenApp.setOnClickListener {
            // Open the main activity
            val i = Intent(this, MainActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(i)
            finish()
        }
    }
}
