package com.example.childlocate.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "safe_locations")
data class LocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Int,
    val type: String, // LocationType.name
    val notificationsEnabled: Boolean,
    val childId: String,
    val parentId: String,
    val placeId: String,
    // Schedule fields (flattened)
    val scheduleEnabled: Boolean = false,
    val scheduleStartTime: String = "08:00",
    val scheduleEndTime: String = "17:00",
    val scheduleDays: String = "", // JSON string của List<DayOfWeek>
    val lastUpdated: Long = System.currentTimeMillis()
)

// Extension functions để convert giữa Location và LocationEntity
fun Location.toEntity(): LocationEntity {
    val gson = Gson()
    val scheduleDaysJson = schedule?.days?.map { it.name }?.let { gson.toJson(it) } ?: ""

    return LocationEntity(
        id = id,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
        radius = radius,
        type = type.name,
        notificationsEnabled = notificationsEnabled,
        childId = childId,
        parentId = parentId,
        placeId = placeId,
        scheduleEnabled = schedule?.enabled ?: false,
        scheduleStartTime = schedule?.startTime ?: "08:00",
        scheduleEndTime = schedule?.endTime ?: "17:00",
        scheduleDays = scheduleDaysJson,
        lastUpdated = System.currentTimeMillis()
    )
}

fun LocationEntity.toLocation(): Location {
    val gson = Gson()
    val daysList = if (scheduleDays.isNotEmpty()) {
        try {
            val type = object : TypeToken<List<String>>() {}.type
            val dayNames: List<String> = gson.fromJson(scheduleDays, type)
            dayNames.mapNotNull {
                try { DayOfWeek.valueOf(it) } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    } else {
        emptyList()
    }

    val schedule = if (scheduleEnabled) {
        Schedule(
            enabled = scheduleEnabled,
            days = daysList,
            startTime = scheduleStartTime,
            endTime = scheduleEndTime
        )
    } else null

    return Location(
        id = id,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
        radius = radius,
        type = try { LocationType.valueOf(type) } catch (e: Exception) { LocationType.OTHER },
        notificationsEnabled = notificationsEnabled,
        childId = childId,
        parentId = parentId,
        schedule = schedule,
        placeId = placeId
    )
}