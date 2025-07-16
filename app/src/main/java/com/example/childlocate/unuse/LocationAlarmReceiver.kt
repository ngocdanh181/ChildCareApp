package com.example.childlocate.unuse

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.childlocate.MyFirebaseManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.util.HashMap


class LocationAlarmReceiver : BroadcastReceiver() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRef: DatabaseReference

    override fun onReceive(context: Context, intent: Intent) {
        MyFirebaseManager.initFirebase(context)
        val firebaseApp = MyFirebaseManager.getFirebaseApp()
        if (firebaseApp == null) {
            Log.d("Firebase","Have not inited")
        } else {
            val database: FirebaseDatabase = FirebaseDatabase.getInstance()
            Log.d("Firebase","Khoi tao thanh cong")
            locationRef = database.getReference("locations")
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            getLastLocation(context, fusedLocationClient)
        }
    }

    private fun getLastLocation(context: Context,fusedLocationClient: FusedLocationProviderClient) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        Log.d("Location","Getted Location")
                        shareLocation(location.latitude, location.longitude)
                    } ?: run {

                    }
                }
        }
    }

    private fun shareLocation(latitude: Double, longitude: Double) {
        val userId = "1"
        val timestamp = System.currentTimeMillis().toString()

        val locationData = HashMap<String, Any>()
        locationData["latitude"] = latitude
        locationData["longitude"] = longitude

        val locationListRef = FirebaseDatabase.getInstance().getReference("locations/$userId/locationsList")
        locationListRef.child(timestamp).setValue(locationData)
            .addOnSuccessListener {
                Log.d(
                    "LocationAlarmReceiver",
                    "Location shared successfully: Latitude $latitude, Longitude $longitude"
                )
            }
            .addOnFailureListener { e ->
                Log.e("LocationAlarmReceiver", "Failed to share location: ${e.message}")
            }
    }
}
