package com.example.childlocate.data.model

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class LocationHistoryRepository(private val context: Context) {

    private val database = FirebaseDatabase.getInstance().reference

    suspend fun getLocationHistory(childId: String): List<LocationHistory> {
        return try {
            val locationHistoryRef = database.child("location_history/$childId")
            val snapshot = locationHistoryRef.get().await()

            val locationList = mutableListOf<LocationHistory>()
            
            for (dataSnapshot in snapshot.children) {
                val timestamp = dataSnapshot.key ?: continue
                val latitude = dataSnapshot.child("latitude").getValue(Double::class.java) ?: continue
                val longitude = dataSnapshot.child("longitude").getValue(Double::class.java) ?: continue
                val firebaseTimestamp = dataSnapshot.child("timestamp").getValue(Long::class.java)
                
                // Read new classification fields
                val locationType = dataSnapshot.child("locationType").getValue(String::class.java) ?: "OTHER"
                val locationName = dataSnapshot.child("locationName").getValue(String::class.java)
                val isInSafeZone = dataSnapshot.child("isInSafeZone").getValue(Boolean::class.java) ?: false
                val distanceFromCenter = dataSnapshot.child("distanceFromCenter").getValue(Int::class.java)
                val accuracy = dataSnapshot.child("accuracy").getValue(Float::class.java) ?: 0f
                
                // Get address - use locationName if in safe zone, otherwise geocode
                val address = if (isInSafeZone && !locationName.isNullOrEmpty()) {
                    locationName
                } else {
                    getAddress(latitude, longitude)
                }
                
                locationList.add(
                    LocationHistory(
                        timestamp = timestamp,
                        latitude = latitude,
                        longitude = longitude,
                        address = address,
                        timestampLong = firebaseTimestamp ?: parseTimestampFromKey(timestamp),
                        locationType = locationType,
                        locationName = locationName,
                        isInSafeZone = isInSafeZone,
                        distanceFromCenter = distanceFromCenter,
                        accuracy = accuracy
                    )
                )
            }
            
            // Sort by timestamp descending (newest first)
            locationList.sortedByDescending { it.timestampLong }
        } catch (e: Exception) {
            Log.e("LocationHistoryRepo", "Error loading location history: ${e.message}")
            emptyList()
        }
    }

    suspend fun getLocationHistoryInRange(
        childId: String, 
        startDate: String, 
        endDate: String
    ): List<LocationHistory> {
        return try {
            val allHistory = getLocationHistory(childId)
            allHistory.filter { history ->
                val historyDate = history.timestamp.substring(0, 10) // Extract dd-MM-yyyy
                historyDate >= startDate && historyDate <= endDate
            }
        } catch (e: Exception) {
            Log.e("LocationHistoryRepo", "Error loading location history in range: ${e.message}")
            emptyList()
        }
    }

    // Listen for real-time updates
    /*suspend fun observeLocationHistory(
        childId: String,
        onUpdate: (List<LocationHistory>) -> Unit,
        onError: (String) -> Unit
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        val locationHistoryRef = database.child("location_history/$childId")
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val locationList = mutableListOf<LocationHistory>()
                    
                    for (dataSnapshot in snapshot.children) {
                        val timestamp = dataSnapshot.key ?: continue
                        val latitude = dataSnapshot.child("latitude").getValue(Double::class.java) ?: continue
                        val longitude = dataSnapshot.child("longitude").getValue(Double::class.java) ?: continue
                        val firebaseTimestamp = dataSnapshot.child("timestamp").getValue(Long::class.java)
                        
                        // Read new classification fields
                        val locationType = dataSnapshot.child("locationType").getValue(String::class.java) ?: "OTHER"
                        val locationName = dataSnapshot.child("locationName").getValue(String::class.java)
                        val isInSafeZone = dataSnapshot.child("isInSafeZone").getValue(Boolean::class.java) ?: false
                        val distanceFromCenter = dataSnapshot.child("distanceFromCenter").getValue(Int::class.java)
                        val accuracy = dataSnapshot.child("accuracy").getValue(Float::class.java) ?: 0f
                        
                        // Get address - use locationName if in safe zone, otherwise geocode
                        val address = if (isInSafeZone && !locationName.isNullOrEmpty()) {
                            locationName
                        } else {
                            getAddress(latitude, longitude)
                        }
                        
                        locationList.add(
                            LocationHistory(
                                timestamp = timestamp,
                                latitude = latitude,
                                longitude = longitude,
                                address = address,
                                timestampLong = firebaseTimestamp ?: parseTimestampFromKey(timestamp),
                                locationType = locationType,
                                locationName = locationName,
                                isInSafeZone = isInSafeZone,
                                distanceFromCenter = distanceFromCenter,
                                accuracy = accuracy
                            )
                        )
                    }
                    
                    val sortedList = locationList.sortedByDescending { it.timestampLong }
                    onUpdate(sortedList)
                } catch (e: Exception) {
                    onError("Error processing location data: ${e.message}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onError("Database error: ${error.message}")
            }
        }
        
        locationHistoryRef.addValueEventListener(listener)
        
        continuation.invokeOnCancellation {
            locationHistoryRef.removeEventListener(listener)
        }
        
        continuation.resume(Unit)
    }*/

    private fun parseTimestampFromKey(timestampKey: String): Long {
        return try {
            val format = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
            format.parse(timestampKey)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private suspend fun getAddress(latitude: Double, longitude: Double): String {
        return withContext(Dispatchers.IO){
         try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            addresses?.firstOrNull()?.getAddressLine(0) ?: "Vị trí không xác định"
        } catch (e: IOException) {
             Log.e("LocationRepo", "Không thể lấy địa chỉ: ${e.message}")
             "Không thể lấy địa chỉ (lỗi mạng/IO)"
         } catch (e: Exception) {
             Log.e("LocationRepo", "Lỗi khi lấy địa chỉ: ${e.message}")
             "Không thể lấy địa chỉ (lỗi không xác định)"
         }
        }
    }

    // Get summary statistics from the data
    fun getLocationSummary(locationHistory: List<LocationHistory>): LocationSummary {
        val totalPoints = locationHistory.size
        val knownLocationCount = locationHistory.count { it.isInSafeZone }
        val unknownLocationCount = totalPoints - knownLocationCount
        val knownLocationRate = if (totalPoints > 0) {
            (knownLocationCount.toDouble() / totalPoints) * 100
        } else 0.0
        
        val unknownLocations = locationHistory.filter { !it.isInSafeZone }
        
        return LocationSummary(
            totalPoints = totalPoints,
            knownLocationCount = knownLocationCount,
            unknownLocationCount = unknownLocationCount,
            knownLocationRate = knownLocationRate,
            unknownLocations = unknownLocations
        )
    }
}

// Data class for location summary
data class LocationSummary(
    val totalPoints: Int,
    val knownLocationCount: Int,
    val unknownLocationCount: Int,
    val knownLocationRate: Double,
    val unknownLocations: List<LocationHistory>
)
