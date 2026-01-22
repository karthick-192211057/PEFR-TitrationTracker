package com.example.pefrtitrationtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pefrtitrationtracker.adapter.NotificationAdapter
import com.example.pefrtitrationtracker.databinding.FragmentNotificationsBinding
import com.example.pefrtitrationtracker.network.SessionManager
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {
    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = NotificationAdapter(emptyList()) { notif ->
            // For local notifications, just mark as read or remove
            // Since they are local, perhaps remove on click
            lifecycleScope.launch {
                // Remove the notification
                val session = SessionManager(requireContext())
                val current = session.fetchNotifications().toMutableSet()
                current.remove(notif)
                session.clearNotifications()
                for (n in current) {
                    // Re-add others, but since it's set, need to re-save
                }
                // Actually, since it's SharedPreferences, better to clear and re-add filtered
                val filtered = current.filter { it != notif }
                session.clearNotifications()
                for (n in filtered) {
                    val parts = n.split("|", limit = 2)
                    if (parts.size == 2) {
                        session.addNotification(parts[1])
                    }
                }
                fetchNotifications()
                Toast.makeText(context, "Notification dismissed", Toast.LENGTH_SHORT).show()
            }
        }

        binding.recyclerNotifications.layoutManager = LinearLayoutManager(context)
        binding.recyclerNotifications.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { fetchNotifications() }

        fetchNotifications()
    }

    private fun fetchNotifications() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val session = SessionManager(requireContext())
                val notifications = session.fetchNotifications()
                // Convert to list of strings for adapter
                val list = notifications.toList()
                adapter.updateData(list)
                binding.textNoNotifications.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                binding.textNoNotifications.visibility = View.VISIBLE
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
