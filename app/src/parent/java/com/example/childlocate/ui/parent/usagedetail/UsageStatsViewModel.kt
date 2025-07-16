package com.example.childlocate.ui.parent.usagedetail

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.DayUsageStats
import com.example.childlocate.data.model.UsageStatsState
import com.example.childlocate.data.model.UsageUiState
import com.example.childlocate.repository.UsageStatsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UsageStatsViewModel(application: Application): AndroidViewModel(application) {

    private val repository = UsageStatsRepository(application)
    
    private val _uiState = MutableStateFlow(UsageUiState())
    val uiState: StateFlow<UsageUiState> = _uiState.asStateFlow()

    private val _selectedDay = MutableStateFlow<DayUsageStats?>(null)
    val selectedDay: StateFlow<DayUsageStats?> = _selectedDay.asStateFlow()

    private var currentWeekOffset = 0
    private var firebaseListenerJob: Job? = null
    private var appLimitsListenerJob: Job? = null

    fun loadUsageStats(childId: String) {
        viewModelScope.launch {
            // Step 1: Start observing app limits
            startAppLimitsListener(childId)

            // Step 2: Request update from child device
            _uiState.value = _uiState.value.copy(
                isRequestingUpdate = true,
                error = null
            )

            val requestSuccess = repository.requestUsageUpdate(childId)
            
            if (requestSuccess) {
                // Step 3: Start listening for Firebase changes
                _uiState.value = _uiState.value.copy(
                    isRequestingUpdate = false,
                    isLoadingData = true
                )
                
                startFirebaseListener(childId)
                
                // Step 4: Stop listening after 60 seconds
                delay(60000)
                stopFirebaseListener()
                
            } else {
                // Request failed, just load existing data
                _uiState.value = _uiState.value.copy(
                    isRequestingUpdate = false,
                    isLoadingData = true
                )
                
                loadWeekData(childId)
            }
        }
    }

    private fun startAppLimitsListener(childId: String) {
        appLimitsListenerJob?.cancel()
        appLimitsListenerJob = viewModelScope.launch {
            repository.getAppLimitsFlow(childId)
                .collect { appLimits ->
                _uiState.value = _uiState.value.copy(appLimits = appLimits)
                    Log.d("Usage", "Updated app limits: $appLimits")
            }
        }
    }

    private fun stopAppLimitsListener() {
        appLimitsListenerJob?.cancel()
        appLimitsListenerJob = null
    }

    private fun startFirebaseListener(childId: String) {
        firebaseListenerJob?.cancel()
        firebaseListenerJob = viewModelScope.launch {
            val startDate = getWeekStartDate(currentWeekOffset)
            repository.getWeeklyUsageStatsFlow(childId, startDate.toString())
                .collect { state ->
                    when (state) {
                        is UsageStatsState.Success -> {
                            _uiState.value = _uiState.value.copy(
                                isLoadingData = false,
                                weeklyData = state.data,
                                currentWeek = formatWeekTitle(startDate),
                                error = null
                            )
                        
                            // Auto-select most recent day with data
                            val mostRecentDay = state.data.dailyStats.values
                                .filter { it.totalTime > 0 }
                                .maxByOrNull { it.date }
                                ?: state.data.dailyStats.values.maxByOrNull { it.date }

                            mostRecentDay?.let { selectDay(it) }
                        }
                        is UsageStatsState.Error -> {
                            _uiState.value = _uiState.value.copy(
                                isLoadingData = false,
                                error = state.message
                            )
                        }
                        else -> {}
                    }
                }
            }
    }

    private fun stopFirebaseListener() {
        firebaseListenerJob?.cancel()
        firebaseListenerJob = null
    }

    private suspend fun loadWeekData(childId: String) {
        val startDate = getWeekStartDate(currentWeekOffset)
        when (val result = repository.getWeeklyUsageStats(childId, startDate.toString())) {
            is UsageStatsState.Success -> {
                _uiState.value = _uiState.value.copy(
                    isLoadingData = false,
                    weeklyData = result.data,
                    currentWeek = formatWeekTitle(startDate),
                    error = null
                )
                
                // Auto-select most recent day
                val mostRecentDay = result.data.dailyStats.values.maxByOrNull { it.date }
                mostRecentDay?.let { selectDay(it) }
            }
            is UsageStatsState.Error -> {
                _uiState.value = _uiState.value.copy(
                    isLoadingData = false,
                    error = result.message
                )
            }
            else -> {}
        }
    }

    fun loadPreviousWeek(childId: String) {
        currentWeekOffset -= 1
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingData = true)
            loadWeekData(childId)
        }
    }

    fun loadNextWeek(childId: String) {
        currentWeekOffset += 1
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingData = true)
            loadWeekData(childId)
        }
    }

    fun selectDay(dayStats: DayUsageStats) {
        _selectedDay.value = dayStats
    }

    fun refreshData(childId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingData = true)
            loadWeekData(childId)
        }
    }

    fun setAppPin(childId: String, pin: String) {
        viewModelScope.launch {
            try {
                repository.saveAppPin(childId, pin)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    private fun getWeekStartDate(weekOffset: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.WEEK_OF_YEAR, weekOffset)
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun formatWeekTitle(startDate: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = startDate
        
        val startDateStr = SimpleDateFormat("dd/MM", Locale.getDefault()).format(calendar.time)
        
        calendar.add(Calendar.DAY_OF_YEAR, 6)
        val endDateStr = SimpleDateFormat("dd/MM", Locale.getDefault()).format(calendar.time)
        
        return "$startDateStr - $endDateStr"
    }

    override fun onCleared() {
        super.onCleared()
        stopFirebaseListener()
        stopAppLimitsListener()
    }
}