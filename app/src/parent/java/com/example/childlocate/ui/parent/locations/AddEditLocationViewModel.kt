package com.example.childlocate.ui.parent.locations

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.childlocate.BuildConfig
import com.example.childlocate.data.model.Location
import com.example.childlocate.data.model.LocationType
import com.example.childlocate.repository.LocationRepository
import com.example.childlocate.repository.LocationRepositoryImpl
import com.example.childlocate.repository.OpenCageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddEditLocationViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val openCageApiKey:String = BuildConfig.OPEN_CAGE_API_KEY
        ?: throw IllegalArgumentException("OpenCage API key is not set in BuildConfig")
    private val repository: LocationRepository = LocationRepositoryImpl(application)
    private val openCageResository = OpenCageRepository(openCageApiKey)
    private val TAG = "AddEditLocationViewModel"

    //tạo token cho phiên bản tìm kiếm
    //private val autocompleteSessionToken = AutocompleteSessionToken.newInstance()

    // Lấy thông tin từ navigation arguments
    private val locationId: String? = savedStateHandle.get<String>("locationId")
    private val childId: String = savedStateHandle.get<String>("childId") ?: ""
    private val parentId: String = savedStateHandle.get<String>("parentId") ?: ""

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    // Form fields
    val name = MutableStateFlow("")
    val address = MutableStateFlow("")
    val locationType = MutableStateFlow(LocationType.HOME)
    val radius = MutableStateFlow(100)
    val latitude = MutableStateFlow(0.0)
    val longitude = MutableStateFlow(0.0)
    val notificationsEnabled = MutableStateFlow(true)
    val placeId = MutableStateFlow("")

    // Tìm kiếm địa điểm
    private val _searchResults = MutableStateFlow<List<PlaceSearchResult>>(emptyList())
    val searchResults: StateFlow<List<PlaceSearchResult>> = _searchResults.asStateFlow()
    //Luu nguon goc cua dia diem
    private val _sourceLocation = MutableStateFlow<Location?>(null)
    val sourceLocation: StateFlow<Location?> = _sourceLocation.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    init {
        Log.d(TAG, "childId: $childId, locationId: $locationId")
        if (locationId != null && childId.isNotEmpty()) {
            loadLocation(locationId)
        }
    }

    private fun loadLocation(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            Log.d(TAG, "Loading ID: $id")

            repository.getLocation(childId, id)
                .onSuccess { location ->
                    Log.d(TAG, "Loading Successfully: ${location.name}")
                    _location.value = location

                    // Populate form fields
                    name.value = location.name
                    address.value = location.address
                    locationType.value = location.type
                    radius.value = location.radius
                    latitude.value = location.latitude
                    longitude.value = location.longitude
                    notificationsEnabled.value = location.notificationsEnabled
                    placeId.value = location.placeId

                    // No schedule handling needed anymore
                }
                .onFailure { error ->
                    Log.e(TAG, "Error: ${error.message}")
                    _errorMessage.value = "Error: ${error.message}"
                }

            _isLoading.value = false
        }
    }

    fun saveLocation() {
        if (!validateInputs()) {
            return
        }

        viewModelScope.launch {
            _isLoading.value = true

            val location = createLocationFromInputs()
            Log.d(TAG, "Loading Save: name=${location.name}, isEditMode=${isEditMode()}")

            val result = if (locationId != "" && locationId !=null) {
                // Khi chỉnh sửa, sử dụng ID hiện tại
                Log.d(TAG, "Updating ID: $locationId")
                repository.updateLocation(childId, location)
                Result.success(locationId)
            } else {
                // Khi thêm mới, không cần gán ID vì repository sẽ tạo ID mới
                Log.d(TAG, "Adding Location")
                repository.addLocation(childId, location)
            }

            result.onSuccess { id ->
                Log.d(TAG, "Adding Successfully with ID: $id")
                _saveSuccess.value = true
            }.onFailure { error ->
                Log.e(TAG, "Adding Fail: ${error.message}")
                _errorMessage.value = "Cannot adding: ${error.message}"
            }

            _isLoading.value = false
        }
    }

    private fun validateInputs(): Boolean {
        when {
            name.value.isBlank() -> {
                _errorMessage.value = "Vui lòng nhập tên địa điểm"
                return false
            }
            address.value.isBlank() -> {
                _errorMessage.value = "Vui lòng nhập địa chỉ"
                return false
            }
            latitude.value == 0.0 && longitude.value == 0.0 -> {
                _errorMessage.value = "Vui lòng chọn vị trí trên bản đồ"
                return false
            }
        }
        return true
    }

    private fun createLocationFromInputs(): Location {
        return Location(
            // Chỉ sử dụng ID hiện tại khi đang chỉnh sửa
            id = locationId ?: "",
            name = name.value,
            address = address.value,
            latitude = latitude.value,
            longitude = longitude.value,
            radius = radius.value,
            type = locationType.value,
            notificationsEnabled = notificationsEnabled.value,
            childId = childId,
            parentId = parentId,
            schedule = null, // No schedule needed
            placeId = placeId.value
        )
    }

    fun updateLocationOnMap(lat: Double, lng: Double) {
        latitude.value = lat
        longitude.value = lng
    }

    fun searchPlace(query:String){
        if(query.length<3){
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            Log.d(TAG, "Keyword finding is: $query")
            val result = openCageResository.searchPlaces(query)
            if(result.isSuccess){
                val results = result.getOrNull()
                _searchResults.value = results ?: emptyList()
            }else{
                Log.e(TAG, "Error: ${result.exceptionOrNull()?.message}")
                _errorMessage.value = "Error: ${result.exceptionOrNull()?.message}"
            }
            _isSearching.value = false
        }
    }
    fun selectPlace (place: PlaceSearchResult){
        viewModelScope.launch {
            try{
                _isLoading.value = true
                Log.d(TAG, "Get detail Location: ${place.name}")
                name.value = place.name
                address.value = place.address
                latitude.value = place.latitude
                longitude.value = place.longitude
                placeId.value = place.placeId
                _searchResults.value = emptyList()
                _isLoading.value = false
            }catch(e:Exception){
                Log.e(TAG, "Error")
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun isEditMode(): Boolean = locationId != null
}

// Kết quả tìm kiếm địa điểm
data class PlaceSearchResult(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)
