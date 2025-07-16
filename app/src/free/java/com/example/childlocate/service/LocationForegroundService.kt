package com.example.childlocate.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelProvider
import com.example.childlocate.ChildLocationViewModel
import com.example.childlocate.R
import com.example.childlocate.ui.child.main.MainChildActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority


class LocationForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val viewModel: ChildLocationViewModel by lazy {
        ViewModelProvider.AndroidViewModelFactory(application).create(ChildLocationViewModel::class.java)
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "LocationForegroundServiceChannel"
        const val LOCATION_UPDATE_INTERVAL = 10000L // 10 seconds
        const val LOCATION_FASTEST_INTERVAL = 5000L // 5 seconds
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationForegroundService", "Service onCreate")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startForegroundService()
        createLocationCallback()
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationForegroundService", "Service onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LocationForegroundService", "Service onDestroy")
        stopLocationUpdates()
        stopForeground(true)
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    viewModel.shareLocation(
                        location.latitude, 
                        location.longitude,
                        location.accuracy
                    )
                    Log.d("LocationForegroundService", 
                        "Location updated - Lat: ${location.latitude}, Lon: ${location.longitude}, Accuracy: ${location.accuracy}m")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
            setWaitForAccurateLocation(false)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d("LocationForegroundService", "Location updates started")
        } catch (e: Exception) {
            Log.e("LocationForegroundService", "Error starting location updates: ${e.message}")
        }
    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundService() {
        createNotificationChannel()
        
        val notificationIntent = Intent(this, MainChildActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 
            29,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Đang chia sẻ vị trí")
            .setContentText("Phụ huynh đang theo dõi vị trí của bạn")
            .setSmallIcon(R.drawable.notification_icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            startForeground(
                NOTIFICATION_ID, 
                notification,
                FOREGROUND_SERVICE_TYPE_LOCATION
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chia sẻ vị trí",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Thông báo khi đang chia sẻ vị trí với phụ huynh"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("LocationForegroundService", "Location updates stopped")
        } catch (e: Exception) {
            Log.e("LocationForegroundService", "Error stopping location updates: ${e.message}")
        }
    }
}