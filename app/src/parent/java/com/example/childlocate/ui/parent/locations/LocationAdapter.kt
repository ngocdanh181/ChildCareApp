package com.example.childlocate.ui.parent.locations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.childlocate.R
import com.example.childlocate.data.model.Location
import com.example.childlocate.databinding.ItemLocationBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class LocationAdapter(
    private val onEditClick: (Location) -> Unit,
    private val onDeleteClick: (Location) -> Unit,
    private val onNotificationToggle: (Location, Boolean) -> Unit,
    private val onItemClick: (Location) -> Unit
) : ListAdapter<Location, LocationAdapter.LocationViewHolder>(LocationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val binding = ItemLocationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LocationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LocationViewHolder(
        private val binding: ItemLocationBinding
    ) : RecyclerView.ViewHolder(binding.root), OnMapReadyCallback {
        
        private var currentLocation: Location? = null
        private var googleMap: GoogleMap? = null
        private var mapInitialized = false
        
        init {
            binding.miniMapView.onCreate(null)
            binding.miniMapView.getMapAsync(this)
            
            binding.root.setOnClickListener {
                toggleMapVisibility()
                currentLocation?.let { onItemClick(it) }
            }
            
            binding.btnEdit.setOnClickListener {
                currentLocation?.let { onEditClick(it) }
            }
            
            binding.btnDelete.setOnClickListener {
                currentLocation?.let { onDeleteClick(it) }
            }
            
            binding.btnNotification.setOnClickListener {
                currentLocation?.let {
                    val newState = !it.notificationsEnabled
                    onNotificationToggle(it, newState)
                }
            }
        }
        
        fun bind(location: Location) {
            currentLocation = location
            
            binding.locationName.text = location.name
            binding.locationAddress.text = location.address
            binding.radiusText.text = "Bán kính: ${location.radius}m"
            
            // Set location type icon
            binding.locationTypeIcon.setImageResource(location.type.getIconResource())
            
            // Set notification icon
            val notificationIcon = if (location.notificationsEnabled) {
                R.drawable.ic_notifications_on
            } else {
                R.drawable.ic_notifications_off
            }
            binding.btnNotification.setImageResource(notificationIcon)
            
            // Update map if visible
            if (binding.miniMapView.visibility == View.VISIBLE && mapInitialized) {
                updateMapWithLocation(location)
            }
        }
        
        private fun toggleMapVisibility() {
            if (binding.miniMapView.visibility == View.VISIBLE) {
                binding.miniMapView.visibility = View.GONE
                binding.divider.visibility = View.GONE
            } else {
                binding.miniMapView.visibility = View.VISIBLE
                binding.divider.visibility = View.VISIBLE
                
                if (mapInitialized) {
                    currentLocation?.let { updateMapWithLocation(it) }
                }
            }
        }
        
        override fun onMapReady(map: GoogleMap) {
            googleMap = map
            mapInitialized = true
            
            map.uiSettings.apply {
                isScrollGesturesEnabled = false
                isZoomGesturesEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                isMapToolbarEnabled = false
                isMyLocationButtonEnabled = false
            }
            
            currentLocation?.let { updateMapWithLocation(it) }
        }
        
        private fun updateMapWithLocation(location: Location) {
            googleMap?.let { map ->
                map.clear()
                
                val position = LatLng(location.latitude, location.longitude)
                
                // Add marker
                map.addMarker(MarkerOptions().position(position).title(location.name))
                
                // Add circle for radius
                map.addCircle(
                    CircleOptions()
                        .center(position)
                        .radius(location.radius.toDouble())
                        .strokeWidth(2f)
                        .strokeColor(binding.root.context.getColor(R.color.primaryColor))
                        .fillColor(binding.root.context.getColor(R.color.primaryColorTransparent))
                )
                
                // Move camera
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
            }
        }
        
        // Map lifecycle methods
        fun onResume() {
            binding.miniMapView.onResume()
        }
        
        fun onPause() {
            binding.miniMapView.onPause()
        }
        
        fun onDestroy() {
            binding.miniMapView.onDestroy()
        }
        
        fun onLowMemory() {
            binding.miniMapView.onLowMemory()
        }
    }
    
    // ViewHolder lifecycle methods for MapView
    private val viewHolderMap = mutableMapOf<Int, LocationViewHolder>()
    
    override fun onViewAttachedToWindow(holder: LocationViewHolder) {
        super.onViewAttachedToWindow(holder)
        viewHolderMap[holder.bindingAdapterPosition] = holder
        holder.onResume()
    }
    
    override fun onViewDetachedFromWindow(holder: LocationViewHolder) {
        super.onViewDetachedFromWindow(holder)
        viewHolderMap.remove(holder.bindingAdapterPosition)
        holder.onPause()
    }
    
    fun onResume() {
        viewHolderMap.values.forEach { it.onResume() }
    }
    
    fun onPause() {
        viewHolderMap.values.forEach { it.onPause() }
    }
    
    fun onDestroy() {
        viewHolderMap.values.forEach { it.onDestroy() }
        viewHolderMap.clear()
    }
    
    fun onLowMemory() {
        viewHolderMap.values.forEach { it.onLowMemory() }
    }
}

class LocationDiffCallback : DiffUtil.ItemCallback<Location>() {
    override fun areItemsTheSame(oldItem: Location, newItem: Location): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Location, newItem: Location): Boolean {
        return oldItem == newItem
    }
}