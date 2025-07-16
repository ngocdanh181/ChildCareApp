package com.example.childlocate.repository


import android.content.Context
import android.util.Log
import com.example.childlocate.data.api.RetrofitInstance
import com.example.childlocate.data.model.Child
import com.example.childlocate.data.model.Data
import com.example.childlocate.data.model.FcmRequest
import com.example.childlocate.data.model.Message
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

// Data class for location updates from Firebase
data class FirebaseLocationData(
    val childId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val accuracy: Float,
    val batteryLevel: Int?
)

// Data class for tracking request status
data class TrackingRequestStatus(
    val childId: String,
    val isActive: Boolean,
    val requestedBy: String?,
    val requestedAt: Long?
)

class SecondRepository(private val context: Context) {

    private val database = FirebaseDatabase.getInstance().reference

    // ===== FAMILY & CHILDREN METHODS =====
    
    suspend fun getFamilyId(userId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = database.child("users").child(userId).get().await()
                snapshot.child("familyId").getValue(String::class.java)
            } catch (e: Exception) {
                Log.e("SecondRepository", "Error fetching family ID: ${e.message}")
                null
            }
        }
    }

    suspend fun getChildren(familyId: String): List<Child> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = database.child("families").child(familyId).child("members").get().await()
                val childrenList = mutableListOf<Child>()
                
                for (childSnapshot in snapshot.children) {
                    val role = childSnapshot.child("role").getValue(String::class.java)
                    if (role == "child") {
                        val childId = childSnapshot.key
                        val childName = childSnapshot.child("name").getValue(String::class.java) ?: ""
                        childId?.let { 
                            childrenList.add(Child(childId = it, childName = childName))
                        }
                    }
                }
                childrenList
            } catch (e: Exception) {
                Log.e("SecondRepository", "Error fetching children: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun getChildAvatar(familyId: String, childId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = database.child("families").child(familyId).child("members")
                    .child(childId).child("avatarUrl").get().await()
                snapshot.getValue(String::class.java)
            } catch (e: Exception) {
                Log.e("SecondRepository", "Error fetching child avatar: ${e.message}")
                null
            }
        }
    }

    // ===== TRACKING REQUEST METHODS =====
    
    suspend fun setTrackingRequest(familyId: String, childId: String, parentId: String, isActive: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val trackingData = mapOf(
                    "isActive" to isActive,
                    "requestedBy" to parentId,
                    "requestedAt" to System.currentTimeMillis()
                )
                
                database.child("trackingRequests").child(familyId).child(childId)
                    .setValue(trackingData).await()
                true
            } catch (e: Exception) {
                Log.e("SecondRepository", "Error setting tracking request: ${e.message}")
                false
            }
        }
    }

    suspend fun getTrackingRequestStatus(familyId: String, childId: String): TrackingRequestStatus? {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = database.child("trackingRequests").child(familyId).child(childId).get().await()
                if (snapshot.exists()) {
                    TrackingRequestStatus(
                        childId = childId,
                        isActive = snapshot.child("isActive").getValue(Boolean::class.java) ?: false,
                        requestedBy = snapshot.child("requestedBy").getValue(String::class.java),
                        requestedAt = snapshot.child("requestedAt").getValue(Long::class.java)
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("SecondRepository", "Error getting tracking status: ${e.message}")
                null
            }
        }
    }

    // ===== LISTENER SETUP METHODS =====
    
    fun setupLocationListener(childId: String, onLocationUpdate: (FirebaseLocationData?) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    onLocationUpdate(null)
                    return
                }
                
                val latitude = snapshot.child("latitude").value?.toString()?.toDoubleOrNull()
                val longitude = snapshot.child("longitude").value?.toString()?.toDoubleOrNull()
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                val accuracy = snapshot.child("accuracy").getValue(Float::class.java) ?: 0f
                val batteryLevel = snapshot.child("batteryLevel").getValue(Int::class.java)
                
                if (latitude != null && longitude != null) {
                    val locationData = FirebaseLocationData(
                        childId = childId,
                        latitude = latitude,
                        longitude = longitude,
                        timestamp = timestamp,
                        accuracy = accuracy,
                        batteryLevel = batteryLevel
                    )
                    onLocationUpdate(locationData)
                } else {
                    onLocationUpdate(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecondRepository", "Location listener cancelled for $childId: ${error.message}")
                onLocationUpdate(null)
            }
        }
        
        database.child("locationRealTime").child(childId).addValueEventListener(listener)
        return listener
    }

    fun setupTrackingListener(familyId: String, childId: String, onTrackingUpdate: (TrackingRequestStatus) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = TrackingRequestStatus(
                    childId = childId,
                    isActive = snapshot.child("isActive").getValue(Boolean::class.java) ?: false,
                    requestedBy = snapshot.child("requestedBy").getValue(String::class.java),
                    requestedAt = snapshot.child("requestedAt").getValue(Long::class.java)
                )
                onTrackingUpdate(status)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecondRepository", "Tracking listener cancelled for $childId: ${error.message}")
            }
        }
        
        database.child("trackingRequests").child(familyId).child(childId).addValueEventListener(listener)
        return listener
    }

    fun setupActiveTrackingListener(familyId: String, onActiveTrackingUpdate: (Map<String, Boolean>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val activeTracking = mutableMapOf<String, Boolean>()
                for (childSnapshot in snapshot.children) {
                    val childId = childSnapshot.key ?: continue
                    val isActive = childSnapshot.child("isActive").getValue(Boolean::class.java) ?: false
                    activeTracking[childId] = isActive
                }
                onActiveTrackingUpdate(activeTracking)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecondRepository", "Active tracking listener cancelled: ${error.message}")
            }
        }
        
        database.child("trackingRequests").child(familyId).addValueEventListener(listener)
        return listener
    }

    // ===== LISTENER REMOVAL METHODS =====
    
    fun removeLocationListener(childId: String, listener: ValueEventListener) {
        database.child("locationRealTime").child(childId).removeEventListener(listener)
    }

    fun removeTrackingListener(familyId: String, childId: String, listener: ValueEventListener) {
        database.child("trackingRequests").child(familyId).child(childId).removeEventListener(listener)
    }

    fun removeActiveTrackingListener(familyId: String, listener: ValueEventListener) {
        database.child("trackingRequests").child(familyId).removeEventListener(listener)
    }

    // ===== FCM METHODS (existing) =====

    suspend fun sendLocationRequest(childId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val token = getToken(childId) ?: return@withContext false
                Log.d("SecondRepository", token)
                val serviceAccount: InputStream = context.assets.open("childlocatedemo.json")
                val credentials = GoogleCredentials.fromStream(serviceAccount)
                    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

                val accessToken = credentials.refreshAccessToken().tokenValue
                val authHeader = "Bearer $accessToken"

                val request = FcmRequest(
                    message = Message(
                        token = token,
                        data = Data(request_type = "location_request")
                    )
                )

                val response = RetrofitInstance.api.sendLocationRequest(authHeader, request)
                return@withContext response.isSuccessful
            } catch (e: IOException) {
                Log.d("FCM", "Error: ${e.message}")
                return@withContext false
            }
        }
    }

    suspend fun sendStopLocationRequest(childId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val token = getToken(childId) ?: return@withContext false

                val serviceAccount: InputStream = context.assets.open("childlocatedemo.json")
                val credentials = GoogleCredentials.fromStream(serviceAccount)
                    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

                val accessToken = credentials.refreshAccessToken().tokenValue
                val authHeader = "Bearer $accessToken"

                val request = FcmRequest(
                    message = Message(
                        token = token,
                        data = Data(request_type = "stop_location_request")
                    )
                )

                val response = RetrofitInstance.api.sendLocationRequest(authHeader, request)
                return@withContext response.isSuccessful
            } catch (e: IOException) {
                Log.d("FCM", "Error: ${e.message}")
                return@withContext false
            }
        }
    }

    private suspend fun getToken(childId:String): String? {
        return withContext(Dispatchers.IO) {
            try {
                database.child("users").child(childId).child("primaryDeviceToken").get().await().getValue(String::class.java)

            } catch (e: Exception) {
                Log.e("FCM", "Failed to get FCM token: ${e.message}")
                null
            }
        }
    }
}
