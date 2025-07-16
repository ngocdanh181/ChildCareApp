package com.example.childlocate


import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle

import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.childlocate.databinding.ActivityMapBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMapBinding
    private val viewModel: ChildLocationViewModel by lazy {
        ViewModelProvider(this)[ChildLocationViewModel::class.java]
    }
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var userLatitude: Double? = null
    private var userLongitude: Double? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var googleMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        val userId = "1" // Replace with actual userId of the user sharing location
        viewModel.getSharedLocation(userId)
        viewModel.locationInfo.observe(this, Observer { address ->
            address?.let {
                latitude = address.latitude
                longitude = address.longitude
                Log.d("Maps", "Latitude: $latitude, Longitude: $longitude")
                addMarkerToMap()

            } ?: run {
                Toast.makeText(this, "Failed to get shared location", Toast.LENGTH_SHORT).show()
            }
        })

        getCurrentLocation()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        addMarkerToMap()
    }
    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                userLatitude = it.latitude
                userLongitude = it.longitude
                Log.d("Maps", "User Latitude: $userLatitude, User Longitude: $userLongitude")
                addMarkerToMap()
            }
        }
    }
    private fun addMarkerToMap() {
        if (::googleMap.isInitialized) {
            // Xóa tất cả các điểm đánh dấu hiện tại
            googleMap.clear()

            // Thêm điểm đánh dấu cho vị trí hiện tại của người dùng
            userLatitude?.let { lat ->
                userLongitude?.let { lon ->
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lon))
                            .title("Vị trí của bạn")
                    )
                    // Di chuyển camera đến vị trí hiện tại của người dùng
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 15f))
                }
            }

            // Thêm điểm đánh dấu cho vị trí từ Firebase
            latitude?.let { lat ->
                longitude?.let { lon ->
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lon))
                            .title("Vị trí được chia sẻ")
                    )
                }
            }
        }
    }
}
