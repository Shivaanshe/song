package com.example.song.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

object PulseLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _currentTask = MutableStateFlow<String?>(null)
    val currentTask: StateFlow<String?> = _currentTask.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(message: String, isError: Boolean = false) {
        val timestamp = dateFormat.format(Date())
        val prefix = if (isError) "[ERROR]" else "[INFO]"
        val logEntry = "$timestamp $prefix $message"
        
        // Also log to system Logcat for developer visibility
        if (isError) Log.e("PulseDebug", message)
        else Log.d("PulseDebug", message)

        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, logEntry) // Add to top for real-time feed
        
        // Keep last 150 entries for better debugging history
        if (currentLogs.size > 150) {
            currentLogs.removeAt(currentLogs.size - 1)
        }
        _logs.value = currentLogs
    }

    fun clear() {
        _logs.value = emptyList()
        _currentTask.value = null
    }

    fun updateTask(task: String?) {
        _currentTask.value = task
        if (task != null) log("Task: $task")
    }
}
