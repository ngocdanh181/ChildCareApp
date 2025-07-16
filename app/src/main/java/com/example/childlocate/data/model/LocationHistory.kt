package com.example.childlocate.data.model

data class LocationHistory(
    val timestamp: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val timestampLong: Long = 0L,
    // New fields from LocationWorker classification
    val locationType: String = "OTHER",
    val locationName: String? = null,
    val isInSafeZone: Boolean = false,
    val distanceFromCenter: Int? = null,
    val accuracy: Float = 0f
) {
    // Helper functions for display
    fun getFormattedDate(): String {
        return try {
            val parts = timestamp.split(" ")
            if (parts.size >= 1) parts[0] else timestamp
        } catch (e: Exception) {
            timestamp
        }
    }
    
    fun getFormattedTime(): String {
        return try {
            val parts = timestamp.split(" ")
            if (parts.size >= 2) parts[1] else timestamp
        } catch (e: Exception) {
            timestamp
        }
    }
    
    fun getFormattedDateTime(): String {
        return timestamp
    }
    
    fun getShortAddress(): String {
        return if (isInSafeZone && !locationName.isNullOrEmpty()) {
            "Tại $locationName"
        } else {
            // Return first part of address or coordinates if address is too long
            if (address.length > 50) {
                address.substring(0, 47) + "..."
            } else {
                address
            }
        }
    }

    fun getLocationIcon(): Int {
        return when (locationType.uppercase()) {
            "HOME" -> com.example.childlocate.R.drawable.baseline_home_24
            "SCHOOL" -> com.example.childlocate.R.drawable.ic_school

            else -> com.example.childlocate.R.drawable.ic_other_location
        }
    }

    fun getComplianceMessage(): String {
        return if (isInSafeZone && !locationName.isNullOrEmpty()) {
            "Tại $locationName"
        } else {
            distanceFromCenter?.let { distance ->
                "Địa điểm lạ (cách địa điểm gần nhất ${distance}m)"
            } ?: "Địa điểm lạ"
        }
    }
}
