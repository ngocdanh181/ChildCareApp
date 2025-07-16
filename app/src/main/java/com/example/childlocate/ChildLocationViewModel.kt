package com.example.childlocate
// LocationViewModel.kt
import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.location.Address
import android.location.Geocoder
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.IOException
import java.util.Locale

class ChildLocationViewModel(application: Application) : AndroidViewModel(application) {
    private val _locationInfo = MutableLiveData<Address?>()
    val locationInfo: MutableLiveData<Address?>
        get() = _locationInfo
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val locationRef: DatabaseReference = database.getReference("locationRealTime")
    private val sharedPreferences = application.getSharedPreferences("MyPrefs", Application.MODE_PRIVATE)
    private val childId: String? = sharedPreferences.getString("childId", null)
    private val familyId: String? = sharedPreferences.getString("familyId",null)



    //Ham chia se vi tri
    fun shareLocation(latitude: Double, longitude: Double, accuracy: Float? = null) {
        val locationData = HashMap<String, Any>()
        locationData["latitude"] = latitude
        locationData["longitude"] = longitude
        locationData["timestamp"] = System.currentTimeMillis()
        
        // Add accuracy if available
        accuracy?.let {
            locationData["accuracy"] = it
        }
        
        // Get battery level
        val batteryLevel = getBatteryLevel()
        batteryLevel?.let {
            locationData["batteryLevel"] = it
        }
        
        Log.d("Location","Lat: $latitude, Lon: $longitude, Battery: $batteryLevel%")

        val userLocationRef = locationRef.child(childId!!)
        userLocationRef?.updateChildren(locationData)
    }

    private fun getBatteryLevel(): Int? {
        return try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                getApplication<Application>().applicationContext.registerReceiver(null, filter)
            }
            
            batteryStatus?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                
                if (level != -1 && scale != -1) {
                    (level * 100 / scale)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("ChildLocationViewModel", "Error getting battery level: ${e.message}")
            null
        }
    }


    fun getSharedLocation(userId: String) {
        locationRef.child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val latitude = snapshot.child("latitude").value as? Double
                val longitude = snapshot.child("longitude").value as? Double
                
                if (latitude != null && longitude != null) {
                    getLocationInfo(latitude, longitude)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                _locationInfo.postValue(null)
            }
        })
    }

    private fun getLocationInfo(latitude: Double, longitude: Double) {
        val geocoder = Geocoder(getApplication<Application>().applicationContext, Locale.getDefault())
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ async approach
            geocoder.getFromLocation(
                latitude,
                longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (addresses.isNotEmpty()) {
                            _locationInfo.postValue(addresses[0])
                        } else {
                            _locationInfo.postValue(null)
                        }
                    }
                    
                    override fun onError(errorMessage: String?) {
                        Log.e("ChildLocationViewModel", "Geocoding error: $errorMessage")
                        _locationInfo.postValue(null)
                    }
                }
            )
        } else {
            // Legacy approach for older Android versions
            try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    _locationInfo.postValue(addresses[0])
                } else {
                    _locationInfo.postValue(null)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                _locationInfo.postValue(null)
            }
        }
    }
}
