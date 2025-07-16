package com.example.childlocate.ui.parent.home

import android.app.Application
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.Child
import com.example.childlocate.data.model.Location
import com.example.childlocate.repository.LocationRepository
import com.example.childlocate.repository.LocationRepositoryImpl
import com.example.childlocate.repository.SecondRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// LocationState.kt
sealed class LocationState {

    data object Idle : LocationState()
    data object Loading : LocationState()
    data class Success(
        val address: Address,
        val timestamp: Long,
        val accuracy: Float,
        val batteryLevel: Int? = null
    ) : LocationState()
    data class Error(val message: String, val isRetryable: Boolean = true) : LocationState()
    data object GpsDisabled : LocationState()
    data object Timeout : LocationState()
    data class StaleData(val lastUpdateTime: Long) : LocationState()
}


// Data classes for child location
data class ChildLocationData(
    val childId: String,
    val childName: String,
    val latitude: Double,
    val longitude: Double,
    val address: Address? = null,
    val timestamp: Long,
    val accuracy: Float,
    val batteryLevel: Int? = null,
    val avatarUrl: String? = null,
    val isTracking: Boolean = false,
    // Safe location fields
    val currentSafeLocation: Location? = null,
    val isInSafeZone: Boolean = false,
    val safeLocationName: String? = null,
    val safeLocationIcon: Int? = null
)

// Combined state for UI
data class ChildTrackingState(
    val isLoading: Boolean = false,
    val isTracking: Boolean = false,
    val error: String? = null,
    val batteryLevel: Int? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val repository = SecondRepository(application)
    private val locationRepository: LocationRepository = LocationRepositoryImpl(application)

    // Basic data
    private val _parentId = MutableLiveData<String?>()
    val parentId: LiveData<String?> get() = _parentId

    private val _familyId = MutableLiveData<String?>()
    val familyId: LiveData<String?> get() = _familyId

    private val _children = MutableLiveData<List<Child>>()
    val children: LiveData<List<Child>> get() = _children

    private val _focusedChild = MutableLiveData<Child?>()
    val focusedChild: LiveData<Child?> get() = _focusedChild

    // Multiple children location data
    private val _childrenLocations = MutableLiveData<Map<String, ChildLocationData>>()
    val childrenLocations: LiveData<Map<String, ChildLocationData>> get() = _childrenLocations

    // Combined state for focused child (for button UI)
    private val _focusedChildState = MutableStateFlow(ChildTrackingState())
    val focusedChildState: StateFlow<ChildTrackingState> = _focusedChildState.asStateFlow()

    // GPS status
    private val _gpsStatus = MutableStateFlow(false)
    val gpsStatus: StateFlow<Boolean> = _gpsStatus.asStateFlow()

    // Listeners management
    private val locationListeners = mutableMapOf<String, ValueEventListener>()
    private val trackingListeners = mutableMapOf<String, ValueEventListener>()
    private var activeTrackingListener: ValueEventListener? = null
    private var locationUpdateJob: Job? = null

    // Safe locations cache for geofence checking
    private val childSafeLocations = mutableMapOf<String, List<Location>>()
    
    // Previous location cache for optimization
    private val previousSafeLocations = mutableMapOf<String, Location?>()
    private val previousPositions = mutableMapOf<String, Pair<Double, Double>>()
    
    // Debouncing for location updates
    private val locationUpdateDebouncer = mutableMapOf<String, Job>()

    init {
        fetchFamilyId()
        checkGpsStatus()
    }

    // ===== GPS STATUS CHECKING (Business Logic - stays in ViewModel) =====
    
    private fun checkGpsStatus() {
        val locationManager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        _gpsStatus.value = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        viewModelScope.launch {
            while (isActive) {
                _gpsStatus.value = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                delay(5000)
            }
        }
    }

    // ===== DATA FETCHING METHODS (Using Repository) =====

    private fun fetchFamilyId() {
        viewModelScope.launch {
            try {
                val userId = firebaseAuth.currentUser?.uid ?: throw Exception("User ID not found")
                _parentId.value = userId
                
                val familyId = repository.getFamilyId(userId)
                _familyId.value = familyId
                
                if (familyId != null) {
                    fetchChildren(familyId)
                    loadActiveTrackingChildren(familyId)
                    Log.d("familyId", "($familyId)")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error in fetchFamilyId: ${e.message}")
            }
        }
    }

    private fun fetchChildren(familyId: String) {
        viewModelScope.launch {
            try {
                val childrenList = repository.getChildren(familyId)
                _children.value = childrenList
                
                if (childrenList.isNotEmpty() && _focusedChild.value == null) {
                    selectChild(childrenList[0])
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error fetching children: ${e.message}")
            }
        }
    }

    private fun loadActiveTrackingChildren(familyId: String) {
        // Remove existing listener
        activeTrackingListener?.let { 
            repository.removeActiveTrackingListener(familyId, it)
        }
        
        // Setup new listener
        activeTrackingListener = repository.setupActiveTrackingListener(familyId) { activeTrackingMap ->
            for ((childId, isActive) in activeTrackingMap) {
                if (isActive) {
                    loadSafeLocationsForChild(childId)
                    startLocationListener(childId)
                    setupTrackingListener(familyId, childId)
                } else {
                    stopLocationListener(childId)
                    removeTrackingListener(familyId, childId)
                }
            }
        }
    }

    // ===== SAFE LOCATIONS & GEOFENCE METHODS (Business Logic - stays in ViewModel) =====
    
    private fun loadSafeLocationsForChild(childId: String) {
        viewModelScope.launch {
            try {
                val locations = locationRepository.getLocalLocationsForChild(childId)
                childSafeLocations[childId] = locations
                Log.d("HomeViewModel", "Loaded ${locations.size} safe locations for child: $childId")
                
                // nếu không có dữ liệu, load từ firebase
                if (locations.isEmpty()) {
                    locationRepository.syncLocationsFromFirebase(childId)
                    delay(1000) // Wait for sync
                    val syncedLocations = locationRepository.getLocalLocationsForChild(childId)
                    childSafeLocations[childId] = syncedLocations
                    Log.d("HomeViewModel", "Synced ${syncedLocations.size} locations from Firebase")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading safe locations: ${e.message}")
                childSafeLocations[childId] = emptyList()
            }
        }
    }

    // ===== LOCATION UPDATE METHODS (Using Debounce) =====
    private fun updateChildLocationWithDebounce(childId: String, locationData: ChildLocationData) {
        // Cancel previous job
        locationUpdateDebouncer[childId]?.cancel()
        
        // Start new debounced job
        locationUpdateDebouncer[childId] = viewModelScope.launch {
            delay(1000) // đợi 1 s
            
            // Check safe location
            val safeLocation = checkSafeLocationOptimized(
                childId, locationData.latitude, locationData.longitude
            )
            
            val updatedData = locationData.copy(
                currentSafeLocation = safeLocation,
                isInSafeZone = safeLocation != null,
                safeLocationName = safeLocation?.name,
                safeLocationIcon = safeLocation?.type?.getIconResource()
            )
            
            updateChildLocation(childId, updatedData)
        }
    }

    private fun checkSafeLocationOptimized(childId: String, lat: Double, lng: Double): Location? {
        val previousPos = previousPositions[childId]
        val previousSafe = previousSafeLocations[childId]

        // nếu vị trí trước đó không thay đổi nhiều, trả về vị trí đã được cached trong previousSafe
        if (previousPos != null) {
            val distanceMoved = calculateDistance(
                previousPos.first, previousPos.second, lat, lng
            )

            // nếu nhỏ hơn 10m, sử dụng cached safe location
            if (distanceMoved < 10.0) {
                return previousSafe
            }
        }

        // Nếu không tính lại safe location
        val newSafeLocation = checkSafeLocation(childId, lat, lng)

        // cập nhật lại các cache
        previousPositions[childId] = Pair(lat, lng)
        previousSafeLocations[childId] = newSafeLocation

        return newSafeLocation
    }

    private fun checkSafeLocation(childId: String, lat: Double, lng: Double): Location? {
        val locations = childSafeLocations[childId] ?: return null

        // Step 1: sử dụng bounding box (very fast)
        val candidateLocations = locations.filter { location ->
            isInBoundingBox(lat, lng, location.latitude, location.longitude, location.radius)
        }

        // Step 2: Tính khoảng cách chính xác giữa hai vị trí
        return candidateLocations.find { location ->
            val distance = calculateDistance(lat, lng, location.latitude, location.longitude)
            distance <= location.radius
        }
    }

    private fun isInBoundingBox(
        currentLat: Double, currentLng: Double,
        locationLat: Double, locationLng: Double,
        radiusMeters: Int
    ): Boolean {
        // tính bounding box theo 4 hướng kinh độ vĩ độ
        val latDiff = abs(currentLat - locationLat)
        val lngDiff = abs(currentLng - locationLng)

        // 1 degree khoảng 111km, chuyển từ radius sang độ
        val radiusDegrees = radiusMeters / 111000.0

        return latDiff <= radiusDegrees && lngDiff <= radiusDegrees
    }
    //sử dụng công thức Haversine
    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat/2) * sin(dLat/2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng/2) * sin(dLng/2)
        val c = 2 * atan2(sqrt(a), sqrt(1-a))
        return earthRadius * c
    }

    // ===== LOCATION LISTENER METHODS =====

    private fun startLocationListener(childId: String) {
        // Remove existing listener if any
        stopLocationListener(childId)
        
        val listener = repository.setupLocationListener(childId) { firebaseLocationData ->
            if (firebaseLocationData == null) return@setupLocationListener
            
            // Get child info
            val child = _children.value?.find { it.childId == childId }
            val childName = child?.childName ?: "Unknown"

            // lấy avatar url của trẻ và chuyển sang address
            viewModelScope.launch {
                val familyId = _familyId.value ?: return@launch
                
                val avatarUrl = repository.getChildAvatar(familyId, childId)
                val address = getLocationInfo(firebaseLocationData.latitude, firebaseLocationData.longitude)

                val locationData = ChildLocationData(
                    childId = firebaseLocationData.childId,
                    childName = childName,
                    latitude = firebaseLocationData.latitude,
                    longitude = firebaseLocationData.longitude,
                    address = address,
                    timestamp = firebaseLocationData.timestamp,
                    accuracy = firebaseLocationData.accuracy,
                    batteryLevel = firebaseLocationData.batteryLevel,
                    avatarUrl = avatarUrl,
                    isTracking = true
                )

                // Use debounced để cập nhật trẻ đang ở nơi nào
                updateChildLocationWithDebounce(childId, locationData)

                // Update focused child state if this is the focused child
                if (_focusedChild.value?.childId == childId) {
                    _focusedChildState.value = ChildTrackingState(
                        isLoading = false,
                        isTracking = true,
                        batteryLevel = firebaseLocationData.batteryLevel
                    )
                }
            }
        }
        
        locationListeners[childId] = listener
    }

    private fun stopLocationListener(childId: String) {
        locationListeners[childId]?.let { listener ->
            repository.removeLocationListener(childId, listener)
            locationListeners.remove(childId)
        }
        
        // Remove from locations map
        val currentLocations = _childrenLocations.value?.toMutableMap() ?: mutableMapOf()
        currentLocations.remove(childId)
        _childrenLocations.value = currentLocations
        
        // Clean up geofence cache
        childSafeLocations.remove(childId)
        previousSafeLocations.remove(childId)
        previousPositions.remove(childId)
        locationUpdateDebouncer[childId]?.cancel()
        locationUpdateDebouncer.remove(childId)
    }

    private fun setupTrackingListener(familyId: String, childId: String) {
        removeTrackingListener(familyId, childId)
        
        val listener = repository.setupTrackingListener(familyId, childId) { status ->
            if (_focusedChild.value?.childId == childId) {
                _focusedChildState.value = _focusedChildState.value.copy(
                    isTracking = status.isActive,
                    isLoading = false
                )
            }
        }
        
        trackingListeners[childId] = listener
    }

    private fun removeTrackingListener(familyId: String, childId: String) {
        trackingListeners[childId]?.let { listener ->
            repository.removeTrackingListener(familyId, childId, listener)
            trackingListeners.remove(childId)
        }
    }

    private fun updateChildLocation(childId: String, locationData: ChildLocationData) {
        val currentLocations = _childrenLocations.value?.toMutableMap() ?: mutableMapOf()
        currentLocations[childId] = locationData
        _childrenLocations.postValue(currentLocations)
    }

    // ===== PUBLIC METHODS =====

    fun selectChild(child: Child) {
        _focusedChild.value = child
        
        // Update focused child state based on current tracking status
        val familyId = _familyId.value ?: return
        viewModelScope.launch {
            try {
                val status = repository.getTrackingRequestStatus(familyId, child.childId)
                val currentLocation = _childrenLocations.value?.get(child.childId)
                
                _focusedChildState.value = ChildTrackingState(
                    isLoading = false,
                    isTracking = status?.isActive ?: false,
                    batteryLevel = currentLocation?.batteryLevel
                )
            } catch (e: Exception) {
                _focusedChildState.value = ChildTrackingState(
                    isLoading = false,
                    isTracking = false,
                    error = "Error checking tracking status"
                )
            }
        }
    }

    fun sendLocationRequest(childId: String) {
        locationUpdateJob?.cancel()
        locationUpdateJob = viewModelScope.launch {
            try {
                if (!_gpsStatus.value) {
                    _focusedChildState.value = _focusedChildState.value.copy(
                        isLoading = false,
                        error = "GPS is disabled"
                    )
                    return@launch
                }

                val familyId = _familyId.value ?: return@launch
                val parentId = _parentId.value ?: return@launch

                _focusedChildState.value = _focusedChildState.value.copy(
                    isLoading = true,
                    error = null
                )

                // Update tracking state in Firebase
                val trackingSuccess = repository.setTrackingRequest(familyId, childId, parentId, true)
                if (!trackingSuccess) {
                    _focusedChildState.value = _focusedChildState.value.copy(
                        isLoading = false,
                        error = "Failed to set tracking request"
                    )
                    return@launch
                }

                val fcmSuccess = repository.sendLocationRequest(childId)

                if (fcmSuccess) {
                    // Load safe locations first
                    loadSafeLocationsForChild(childId)
                    
                    // Start listening to this child's location
                    startLocationListener(childId)
                    setupTrackingListener(familyId, childId)
                    
                    _focusedChildState.value = _focusedChildState.value.copy(
                        isLoading = false,
                        isTracking = true
                    )
                } else {
                    // Rollback tracking state if FCM fails
                    repository.setTrackingRequest(familyId, childId, parentId, false)
                    
                    _focusedChildState.value = _focusedChildState.value.copy(
                        isLoading = false,
                        isTracking = false,
                        error = "Failed to send location request"
                    )
                }
            } catch (e: TimeoutCancellationException) {
                _focusedChildState.value = _focusedChildState.value.copy(
                    isLoading = false,
                    error = "Request timeout"
                )
            } catch (e: Exception) {
                _focusedChildState.value = _focusedChildState.value.copy(
                    isLoading = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun sendStopLocationRequest(childId: String) {
        viewModelScope.launch {
            try {
                val familyId = _familyId.value ?: return@launch
                val parentId = _parentId.value ?: return@launch
                
                _focusedChildState.value = _focusedChildState.value.copy(
                    isLoading = true,
                    error = null
                )
                
                // Update tracking state to inactive
                repository.setTrackingRequest(familyId, childId, parentId, false)
                repository.sendStopLocationRequest(childId)
                
                // Stop listening regardless of FCM result
                stopLocationListener(childId)
                removeTrackingListener(familyId, childId)
                
                _focusedChildState.value = _focusedChildState.value.copy(
                    isLoading = false,
                    isTracking = false
                )
                
            } catch (e: Exception) {
                _focusedChildState.value = _focusedChildState.value.copy(
                    isLoading = false,
                    error = "Error stopping tracking: ${e.message}"
                )
            }
        }
    }

    // ===== GEOCODING METHODS (Utility - could be moved to repository later if needed) =====

    private suspend fun getLocationInfo(latitude: Double, longitude: Double): Address? {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(getApplication(), Locale.getDefault())

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Sử dụng suspendCancellableCoroutine cho API mới
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(
                            latitude,
                            longitude,
                            1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    continuation.resume(addresses.firstOrNull())
                                }

                                override fun onError(errorMessage: String?) {
                                    Log.e("HomeViewModel", "Geocoding error: $errorMessage")
                                    continuation.resume(null)
                                }
                            }
                        )
                    }
                } else {
                    // API cũ - có thể gọi trực tiếp trong IO context
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    addresses?.firstOrNull()
                }
            } catch (e: IOException) {
                Log.e("HomeViewModel", "Geocoding error: ${e.message}")
                null
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Unexpected geocoding error: ${e.message}")
                null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val familyId = _familyId.value ?: return
        
        // Clean up all listeners using repository
        locationListeners.forEach { (childId, listener) ->
            repository.removeLocationListener(childId, listener)
        }
        
        trackingListeners.forEach { (childId, listener) ->
            repository.removeTrackingListener(familyId, childId, listener)
        }
        
        activeTrackingListener?.let { 
            repository.removeActiveTrackingListener(familyId, it)
        }
        
        locationListeners.clear()
        trackingListeners.clear()
        
        // Clean up geofence cache
        childSafeLocations.clear()
        previousSafeLocations.clear()
        previousPositions.clear()
        locationUpdateDebouncer.values.forEach { it.cancel() }
        locationUpdateDebouncer.clear()
    }
}
