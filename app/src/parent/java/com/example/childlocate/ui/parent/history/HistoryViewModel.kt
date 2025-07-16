package com.example.childlocate.ui.parent.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.LocationHistory
import com.example.childlocate.data.model.LocationHistoryRepository
import com.example.childlocate.data.model.LocationSummary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LocationHistoryRepository(application)

    private val _locationHistory = MutableLiveData<List<LocationHistory>>()
    val locationHistory: LiveData<List<LocationHistory>> get() = _locationHistory

    private val _filteredHistory = MutableLiveData<List<LocationHistory>>()
    val filteredHistory: LiveData<List<LocationHistory>> get() = _filteredHistory

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    private val _selectedDate = MutableLiveData<String>()
    val selectedDate: LiveData<String> get() = _selectedDate

    private val _locationSummary = MutableLiveData<LocationSummary>()
    val locationSummary: LiveData<LocationSummary> get() = _locationSummary

    private var currentChildId: String? = null
    private var allHistory: List<LocationHistory> = emptyList()

    init {
        // Set default date to today
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
        _selectedDate.value = today
    }

    fun loadLocationHistory(childId: String) {
        if (currentChildId == childId && allHistory.isNotEmpty()) {
            // Data already loaded for this child
            return
        }
        
        currentChildId = childId
        _isLoading.value = true
        _errorMessage.value = null
        
        viewModelScope.launch {
            try {
                val history = repository.getLocationHistory(childId)
                allHistory = history
                _locationHistory.value = history
                
                // Apply current date filter
                _selectedDate.value?.let { date ->
                    filterByDate(date)
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Không thể tải lịch sử vị trí: ${e.message}"
                _locationHistory.value = emptyList()
                _filteredHistory.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun filterByDate(date: String) {
        _selectedDate.value = date
        val filtered = allHistory.filter { it.timestamp.startsWith(date) }
        _filteredHistory.value = filtered
        
        // Calculate summary for filtered data
        updateLocationSummary(filtered)
    }

    private fun filterByDateRange(startDate: String, endDate: String) {
        currentChildId?.let { childId ->
            _isLoading.value = true
            viewModelScope.launch {
                try {
                    val history = repository.getLocationHistoryInRange(childId, startDate, endDate)
                    _filteredHistory.value = history
                    updateLocationSummary(history)
                } catch (e: Exception) {
                    _errorMessage.value = "Không thể lọc lịch sử: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    private fun updateLocationSummary(locationHistory: List<LocationHistory>) {
        val summary = repository.getLocationSummary(locationHistory)
        _locationSummary.value = summary
    }

    fun loadTodayHistory() {
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
        filterByDate(today)
    }

    fun loadYesterdayHistory() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(calendar.time)
        filterByDate(yesterday)
    }

    fun loadThisWeekHistory() {
        val calendar = Calendar.getInstance()
        val endDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(calendar.time)
        
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val startDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(calendar.time)
        
        filterByDateRange(startDate, endDate)
    }

    fun refreshData() {
        currentChildId?.let { childId ->
            // Force reload
            allHistory = emptyList()
            loadLocationHistory(childId)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun getLocationCountForDate(date: String): Int {
        return allHistory.count { it.timestamp.startsWith(date) }
    }

    // Get unique dates that have location data
    private fun getAvailableDates(): List<String> {
        return allHistory.map { it.getFormattedDate() }.distinct().sorted()
    }

    // Get summary statistics
    fun getLocationSummary(): Map<String, Int> {
        val summary = mutableMapOf<String, Int>()
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
        
        summary["today"] = getLocationCountForDate(today)
        
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(calendar.time)
        summary["yesterday"] = getLocationCountForDate(yesterday)
        
        summary["total"] = allHistory.size
        summary["days_with_data"] = getAvailableDates().size
        
        return summary
    }

}
