package com.example.service

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

enum class SnatchLabel(val displayName: String) {
    UNLABELED("Review"),
    TRUE_SNATCH("Snatch"),
    FALSE_POSITIVE("Mistake")
}

data class LockEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val score: Int,
    val accel: Float,
    val gyro: Float,
    val label: SnatchLabel = SnatchLabel.UNLABELED
)

object SensorStateMonitor {
    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _accelMag = MutableStateFlow(0f)
    val accelMag: StateFlow<Float> = _accelMag.asStateFlow()

    private val _gyroMag = MutableStateFlow(0f)
    val gyroMag: StateFlow<Float> = _gyroMag.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val maxHistorySize = 100
    private val _accelHistory = MutableStateFlow<List<Float>>(emptyList())
    val accelHistory: StateFlow<List<Float>> = _accelHistory.asStateFlow()

    private val _gyroHistory = MutableStateFlow<List<Float>>(emptyList())
    val gyroHistory: StateFlow<List<Float>> = _gyroHistory.asStateFlow()

    private val _lockHistory = MutableStateFlow<List<LockEvent>>(emptyList())
    val lockHistory: StateFlow<List<LockEvent>> = _lockHistory.asStateFlow()

    fun recordLockEvent(score: Int, accel: Float, gyro: Float) {
        val newList = _lockHistory.value.toMutableList()
        newList.add(0, LockEvent(timestamp = System.currentTimeMillis(), score = score, accel = accel, gyro = gyro))
        // Keep last 50 events to avoid memory bloat
        if (newList.size > 50) newList.removeAt(newList.size - 1)
        _lockHistory.value = newList
    }

    fun updateEventLabel(id: String, label: SnatchLabel) {
        _lockHistory.value = _lockHistory.value.map {
            if (it.id == id) it.copy(label = label) else it
        }
    }

    fun clearLockHistory() {
        _lockHistory.value = emptyList()
    }

    fun updateScore(newScore: Int, accel: Float, gyro: Float) {
        _score.value = newScore
        _accelMag.value = accel
        _gyroMag.value = gyro

        val curAccelList = _accelHistory.value.toMutableList()
        curAccelList.add(accel)
        if (curAccelList.size > maxHistorySize) curAccelList.removeAt(0)
        _accelHistory.value = curAccelList

        val curGyroList = _gyroHistory.value.toMutableList()
        curGyroList.add(gyro)
        if (curGyroList.size > maxHistorySize) curGyroList.removeAt(0)
        _gyroHistory.value = curGyroList
    }

    fun setMonitoring(monitoring: Boolean) {
        _isMonitoring.value = monitoring
        if (!monitoring) {
            _score.value = 0
            _accelMag.value = 0f
            _gyroMag.value = 0f
            _accelHistory.value = emptyList()
            _gyroHistory.value = emptyList()
        }
    }
}
