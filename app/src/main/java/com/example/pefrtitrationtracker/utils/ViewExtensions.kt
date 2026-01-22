package com.example.pefrtitrationtracker.utils

import android.view.View
import android.content.Context
import android.view.inputmethod.InputMethodManager

/**
 * Prevents multiple rapid clicks by disabling the view for [intervalMs].
 * This implementation uses the view handler (no additional coroutine scopes),
 * and ensures the enabled flag is reset if the view is detached.
 * Usage: view.safeClick { /* action */ }
 */
fun View.safeClick(intervalMs: Long = 600L, action: (View) -> Unit) {
    var enabled = true

    val listener = View.OnClickListener { v ->
        if (!enabled) return@OnClickListener
        enabled = false
        v.isEnabled = false

        // hide keyboard when a clickable view is tapped (best-effort)
        try {
            val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        } catch (_: Exception) {}

        try {
            action(v)
        } catch (_: Exception) {}

        v.postDelayed({
            enabled = true
            try { v.isEnabled = true } catch (_: Exception) {}
        }, intervalMs)

        v.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {}
            override fun onViewDetachedFromWindow(view: View) {
                v.removeOnAttachStateChangeListener(this)
                enabled = true
            }
        })
    }

    setOnClickListener(listener)
}
