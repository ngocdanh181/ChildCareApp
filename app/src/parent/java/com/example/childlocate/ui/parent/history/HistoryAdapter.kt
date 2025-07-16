package com.example.childlocate.ui.parent.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.childlocate.R
import com.example.childlocate.data.model.LocationHistory
import com.example.childlocate.databinding.ItemHistoryLocationBinding

class HistoryAdapter(
    private val onLocationClick: (LocationHistory) -> Unit = {}
) : ListAdapter<LocationHistory, HistoryAdapter.LocationViewHolder>(LocationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val binding = ItemHistoryLocationBinding.inflate(
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
        private val binding: ItemHistoryLocationBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(locationHistory: LocationHistory) {
            binding.apply {
                // Basic info
                timestampTextView.text = locationHistory.getFormattedDateTime()
                addressTextView.text = locationHistory.getShortAddress()
                timeTextView.text = locationHistory.getFormattedTime()
                
                // Set location icon based on type
                locationIcon.setImageResource(locationHistory.getLocationIcon())
                
                // Show compliance indicators based on safe zone status
                complianceIndicator?.visibility = android.view.View.VISIBLE
                complianceText?.visibility = android.view.View.VISIBLE
                
                if (locationHistory.isInSafeZone) {
                    // Known location - green indicator
                    complianceIndicator?.setImageResource(R.drawable.ic_check_circle)
                    complianceIndicator?.setColorFilter(
                        ContextCompat.getColor(root.context, R.color.successColor)
                    )
                    complianceText?.text = locationHistory.getComplianceMessage()
                    complianceText?.setTextColor(
                        ContextCompat.getColor(root.context, R.color.successColor)
                    )
                    
                    // Set location icon color to match safe zone
                    locationIcon.setColorFilter(
                        ContextCompat.getColor(root.context, R.color.successColor)
                    )
                } else {
                    // Unknown location - orange/red indicator
                    complianceIndicator?.setImageResource(R.drawable.ic_warning)
                    complianceIndicator?.setColorFilter(
                        ContextCompat.getColor(root.context, R.color.warningColor)
                    )
                    complianceText?.text = locationHistory.getComplianceMessage()
                    complianceText?.setTextColor(
                        ContextCompat.getColor(root.context, R.color.warningColor)
                    )
                    
                    // Set location icon color to match warning
                    locationIcon.setColorFilter(
                        ContextCompat.getColor(root.context, R.color.warningColor)
                    )
                }
                
                // Set click listener
                root.setOnClickListener {
                    onLocationClick(locationHistory)
                }
            }
        }
    }

    private class LocationDiffCallback : DiffUtil.ItemCallback<LocationHistory>() {
        override fun areItemsTheSame(oldItem: LocationHistory, newItem: LocationHistory): Boolean {
            return oldItem.timestamp == newItem.timestamp && 
                   oldItem.latitude == newItem.latitude && 
                   oldItem.longitude == newItem.longitude
        }

        override fun areContentsTheSame(oldItem: LocationHistory, newItem: LocationHistory): Boolean {
            return oldItem == newItem
        }
    }
}
