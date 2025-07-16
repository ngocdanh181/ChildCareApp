package com.example.childlocate.ui.child.main

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.childlocate.MyFirebaseManager
import com.example.childlocate.R
import com.example.childlocate.unuse.CHANNEL_ID
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Data class for location classification result
data class LocationClassification(
    val locationType: String,
    val locationName: String?,
    val distanceFromCenter: Int?,
    val isInSafeZone: Boolean
)

class LocationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationHistoryRef: DatabaseReference
    private lateinit var locationsRef: DatabaseReference
    private val sharedPreferences = applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
    private val childId = sharedPreferences.getString("childId", null).toString()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        MyFirebaseManager.initFirebase(applicationContext)
        val firebaseApp = MyFirebaseManager.getFirebaseApp()
        if (firebaseApp == null) {
            return@withContext Result.failure()
        } else {
            val database: FirebaseDatabase = FirebaseDatabase.getInstance()
            locationHistoryRef = database.getReference("location_history/$childId")
            locationsRef = database.getReference("locations/$childId")
            
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            getLastLocation()
            Log.d("LocationWorker", "Location sharing completed for 15 minutes cycle")
            return@withContext Result.success()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    Log.d("LocationWorker", "Location obtained: ${location.latitude}, ${location.longitude}")
                    classifyAndShareLocation(location.latitude, location.longitude, location.accuracy)
                }
            }
            .addOnFailureListener { exception ->
                Log.e("LocationWorker", "Failed to get location: ${exception.message}")
            }
    }

    private fun classifyAndShareLocation(latitude: Double, longitude: Double, accuracy: Float) {
        locationsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val classification = classifyLocation(snapshot, latitude, longitude)
                
                // Share to history with classification
                shareLocationToHistory(latitude, longitude, accuracy, classification)
                
                // Update current status
                updateLocationStatus(classification)
                
                // Show notification
                val locationText = if (classification.isInSafeZone) {
                    "Tại ${classification.locationName}"
                } else {
                    "Địa điểm lạ"
                }
                showNotification("Vị trí đã chia sẻ", locationText)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("LocationWorker", "Error reading locations: ${error.message}")
                // Still share location even if classification fails
                val fallbackClassification = LocationClassification(
                    locationType = "OTHER",
                    locationName = null,
                    distanceFromCenter = null,
                    isInSafeZone = false
                )
                shareLocationToHistory(latitude, longitude, accuracy, fallbackClassification)
            }
        })
    }

    private fun classifyLocation(snapshot: DataSnapshot, latitude: Double, longitude: Double): LocationClassification {
        var nearestLocation: String? = null
        var nearestDistance = Double.MAX_VALUE
        var nearestLocationType = "OTHER"

        for (locationSnapshot in snapshot.children) {
            val name = locationSnapshot.child("name").getValue(String::class.java) ?: continue
            val locationLat = locationSnapshot.child("latitude").getValue(Double::class.java) ?: continue
            val locationLng = locationSnapshot.child("longitude").getValue(Double::class.java) ?: continue
            val radius = locationSnapshot.child("radius").getValue(Long::class.java)?.toInt() ?: 100
            val type = locationSnapshot.child("type").getValue(String::class.java) ?: "OTHER"

            val distance = calculateDistance(latitude, longitude, locationLat, locationLng)

            // Check if within radius of this location
            if (distance <= radius) {
                Log.d("LocationWorker", "Child is at location: $name (${distance.toInt()}m from center)")
                return LocationClassification(
                    locationType = type,
                    locationName = name,
                    distanceFromCenter = distance.toInt(),
                    isInSafeZone = true
                )
            }

            // Track nearest location for logging
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestLocation = name
                nearestLocationType = type
            }
        }

        // Not in any defined location
        if (nearestLocation != null) {
            Log.d("LocationWorker", "Child is not at any defined location. Nearest: $nearestLocation (${nearestDistance.toInt()}m away)")
        } else {
            Log.d("LocationWorker", "No locations defined for this child")
        }

        return LocationClassification(
            locationType = "OTHER",
            locationName = null,
            distanceFromCenter = nearestDistance.toInt(),
            isInSafeZone = false
        )
    }

    private fun shareLocationToHistory(latitude: Double, longitude: Double, accuracy: Float, classification: LocationClassification) {
        val timeStamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        val formattedTime = dateFormat.format(Date(timeStamp))

        val locationData = HashMap<String, Any>().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("timestamp", timeStamp)
            put("accuracy", accuracy)
            put("locationType", classification.locationType)
            put("isInSafeZone", classification.isInSafeZone)
            
            classification.locationName?.let { put("locationName", it) }
            classification.distanceFromCenter?.let { put("distanceFromCenter", it) }
        }

        locationHistoryRef.child(formattedTime).setValue(locationData)
            .addOnSuccessListener {
                Log.d("LocationWorker", "Location saved to history successfully with classification: ${classification.locationType}")
            }
            .addOnFailureListener { exception ->
                Log.e("LocationWorker", "Failed to save location: ${exception.message}")
            }
    }

    private fun updateLocationStatus(classification: LocationClassification) {
        val statusData = HashMap<String, Any>().apply {
            put("timestamp", System.currentTimeMillis())
            put("locationType", classification.locationType)
            put("isInSafeZone", classification.isInSafeZone)
            
            if (classification.isInSafeZone && classification.locationName != null) {
                put("currentLocation", classification.locationName)
                put("distanceFromCenter", classification.distanceFromCenter ?: 0)
                put("status", "at_location")
            } else {
                put("currentLocation", "unknown")
                put("status", "not_at_any_location")
            }
        }

        // Update current status (for real-time tracking)
        val database = FirebaseDatabase.getInstance()
        database.getReference("current_status/$childId").setValue(statusData)
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters

        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)

        val a = sin(latDistance / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(title: String, content: String) {
        createNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(notificationId, notificationBuilder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Location Service"
            val channelDescription = "Location sharing notifications"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, channelName, importance).apply {
                description = channelDescription
            }

            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val TAG = "LocationWorker"
        const val notificationId = 109
    }
}
