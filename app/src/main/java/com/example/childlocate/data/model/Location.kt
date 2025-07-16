package com.example.childlocate.data.model

import java.io.Serializable

data class Location(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Int = 100,
    val type: LocationType = LocationType.HOME,
    val notificationsEnabled: Boolean = true,
    val childId: String = "",
    val parentId: String = "",
    val schedule: Schedule? = null,
    val placeId: String = "" // Google Places API place ID
) : Serializable

enum class LocationType {
    HOME, SCHOOL, PARK, RELATIVE, FRIEND, OTHER;
    
    fun getIconResource(): Int {
        return when (this) {
            HOME -> com.example.childlocate.R.drawable.baseline_home_24
            SCHOOL -> com.example.childlocate.R.drawable.ic_school
            PARK -> com.example.childlocate.R.drawable.ic_park
            RELATIVE -> com.example.childlocate.R.drawable.ic_relative
            FRIEND -> com.example.childlocate.R.drawable.ic_friend
            OTHER -> com.example.childlocate.R.drawable.ic_other_location
        }
    }
    
    fun getDisplayName(): String {
        return when (this) {
            HOME -> "Nhà"
            SCHOOL -> "Trường học"
            PARK -> "Công viên"
            RELATIVE -> "Nhà người thân"
            FRIEND -> "Nhà bạn bè"
            OTHER -> "Khác"
        }
    }
}

// Lịch trình cho địa điểm
data class Schedule(
    val enabled: Boolean = false,
    val days: List<DayOfWeek> = emptyList(),
    val startTime: String = "08:00", // Format: "HH:mm"
    val endTime: String = "17:00"    // Format: "HH:mm"
) : Serializable

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;
    
    fun getDisplayName(): String {
        return when (this) {
            MONDAY -> "Thứ 2"
            TUESDAY -> "Thứ 3"
            WEDNESDAY -> "Thứ 4"
            THURSDAY -> "Thứ 5"
            FRIDAY -> "Thứ 6"
            SATURDAY -> "Thứ 7"
            SUNDAY -> "Chủ nhật"
        }
    }
} 