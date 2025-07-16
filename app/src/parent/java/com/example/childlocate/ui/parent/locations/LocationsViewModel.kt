package com.example.childlocate.ui.parent.locations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.Location
import com.example.childlocate.repository.LocationRepository
import com.example.childlocate.repository.LocationRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LocationsViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val repository : LocationRepository = LocationRepositoryImpl(application)
    
    // Lấy childId từ navigation arguments
    private val childId: String = savedStateHandle.get<String>("childId") ?: ""
    private val parentId: String = savedStateHandle.get<String>("parentId") ?: ""

    private val _locations = MutableStateFlow<List<Location>>(emptyList())
    val locations: StateFlow<List<Location>> = _locations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        if (childId.isNotEmpty()) {
            loadLocations()
        } else {
            _errorMessage.value = "Không tìm thấy thông tin trẻ"
        }
    }

    fun loadLocations() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            repository.getLocationsForChild(childId).collectLatest { locationsList ->
                _locations.value = locationsList
                _isLoading.value = false
            }
        }
    }

    fun deleteLocation(locationId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            repository.deleteLocation(childId, locationId)
                .onSuccess {
                    // Không cần gọi loadLocations() vì Flow sẽ tự động cập nhật
                }
                .onFailure { error ->
                    _errorMessage.value = "Không thể xóa địa điểm: ${error.message}"
                    _isLoading.value = false
                }
        }
    }

    fun toggleNotifications(locationId: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleNotifications(childId, locationId, enabled)
                .onFailure { error ->
                    _errorMessage.value = "Không thể cập nhật thông báo: ${error.message}"
                }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
    
    fun getChildId(): String = childId
    
    fun getParentId(): String = parentId
} 