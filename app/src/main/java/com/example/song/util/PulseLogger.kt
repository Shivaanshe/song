package com.example.song.util

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
        
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, logEntry) // Add to top
        if (currentLogs.size > 50) {
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
    }
}
