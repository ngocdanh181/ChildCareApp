package com.example.childlocate.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.childlocate.data.model.LocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Query("SELECT * FROM safe_locations WHERE childId = :childId ORDER BY lastUpdated DESC")
    suspend fun getLocationsForChild(childId: String): List<LocationEntity>

    @Query("SELECT * FROM safe_locations WHERE childId = :childId ORDER BY lastUpdated DESC")
    fun getLocationsForChildFlow(childId: String): Flow<List<LocationEntity>>

    @Query("SELECT * FROM safe_locations WHERE id = :locationId")
    suspend fun getLocationById(locationId: String): LocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocations(locations: List<LocationEntity>)

    @Update
    suspend fun updateLocation(location: LocationEntity)

    @Delete
    suspend fun deleteLocation(location: LocationEntity)

    @Query("DELETE FROM safe_locations WHERE id = :locationId")
    suspend fun deleteLocationById(locationId: String)

    @Query("DELETE FROM safe_locations WHERE childId = :childId")
    suspend fun deleteAllForChild(childId: String)

    @Query("DELETE FROM safe_locations")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM safe_locations WHERE childId = :childId")
    suspend fun getLocationCountForChild(childId: String): Int

    @Query("SELECT * FROM safe_locations WHERE childId = :childId AND notificationsEnabled = 1")
    suspend fun getEnabledLocationsForChild(childId: String): List<LocationEntity>

    @Query("UPDATE safe_locations SET notificationsEnabled = :enabled WHERE id = :locationId")
    suspend fun updateNotificationStatus(locationId: String, enabled: Boolean)

    @Query("SELECT * FROM safe_locations WHERE lastUpdated > :timestamp")
    suspend fun getLocationsUpdatedAfter(timestamp: Long): List<LocationEntity>
}