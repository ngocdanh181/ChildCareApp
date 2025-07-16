package com.example.childlocate.repository

import android.content.Context
import android.util.Log
import com.example.childlocate.data.dao.LocationDao
import com.example.childlocate.data.database.LocationDatabase
import com.example.childlocate.data.model.DayOfWeek
import com.example.childlocate.data.model.Location
import com.example.childlocate.data.model.LocationType
import com.example.childlocate.data.model.Schedule
import com.example.childlocate.data.model.toEntity
import com.example.childlocate.data.model.toLocation
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class LocationRepositoryImpl(private val context: Context) : LocationRepository {
    private val database = FirebaseDatabase.getInstance()
    private val roomDb = LocationDatabase.getInstance(context)
    private val locationDao: LocationDao = roomDb.locationDao()
    private val TAG = "LocationRepositoryImpl"

    // ===== FIREBASE METHODS (for UI) =====

    // Lấy danh sách địa điểm từ Firebase (cho UI)
    override fun getLocationsForChild(childId: String): Flow<List<Location>> = callbackFlow {
        val locationsRef = database.getReference("locations/$childId")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val locations = mutableListOf<Location>()

                for (childSnapshot in snapshot.children) {
                    try {
                        val locationId = childSnapshot.key ?: continue

                        // Đọc từng trường của snapshot riêng biệt
                        val name = childSnapshot.child("name").getValue(String::class.java) ?: ""
                        val address = childSnapshot.child("address").getValue(String::class.java) ?: ""
                        val latitude = childSnapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                        val longitude = childSnapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                        val radius = childSnapshot.child("radius").getValue(Int::class.java) ?: 100
                        val type = childSnapshot.child("type").getValue(String::class.java) ?: "HOME"
                        val notificationsEnabled = childSnapshot.child("notificationsEnabled").getValue(Boolean::class.java) ?: true
                        val childIdValue = childSnapshot.child("childId").getValue(String::class.java) ?: ""
                        val parentId = childSnapshot.child("parentId").getValue(String::class.java) ?: ""
                        val placeId = childSnapshot.child("placeId").getValue(String::class.java) ?: ""

                        // ✅ Schedule = null luôn (không parse schedule nữa)
                        val schedule = null

                        val location = Location(
                            id = locationId,
                            name = name,
                            address = address,
                            latitude = latitude,
                            longitude = longitude,
                            radius = radius,
                            type = try { LocationType.valueOf(type) } catch (e: Exception) { LocationType.OTHER },
                            notificationsEnabled = notificationsEnabled,
                            childId = childIdValue,
                            parentId = parentId,
                            schedule = schedule, // Always null
                            placeId = placeId
                        )

                        locations.add(location)
                        Log.d(TAG, "Location Added: ${location.name} with ID: ${location.id}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error: ${e.message}", e)
                        continue
                    }
                }

                // Sync to Room database in background
                syncToRoom(childId, locations)

                trySend(locations)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Cant get list locations: ${error.message}")
                trySend(emptyList())
            }
        }

        locationsRef.addValueEventListener(listener)

        awaitClose {
            locationsRef.removeEventListener(listener)
        }
    }

    // ===== ROOM METHODS (for geofence checking) =====

    // Lấy danh sách địa điểm từ Room (nhanh, cho geofence checking)
    override suspend fun getLocalLocationsForChild(childId: String): List<Location> = withContext(Dispatchers.IO) {
        try {
            val entities = locationDao.getLocationsForChild(childId)
            val locations = entities.map { it.toLocation() }
            Log.d(TAG, "Loaded ${locations.size} local locations for child: $childId")
            return@withContext locations
        } catch (e: Exception) {
            Log.e(TAG, "Error loading local locations: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    // Lấy Flow từ Room (cho real-time local updates)
    override fun getLocalLocationsForChildFlow(childId: String): Flow<List<Location>> {
        return locationDao.getLocationsForChildFlow(childId).map { entities ->
            entities.map { it.toLocation() }
        }
    }

    // Sync Firebase data to Room
    private fun syncToRoom(childId: String, locations: List<Location>) {
        try {
            // Run in background thread
            CoroutineScope(Dispatchers.IO).launch {
                // Clear old data for this child
                locationDao.deleteAllForChild(childId)

                // Insert new data
                val entities = locations.map { it.toEntity() }
                locationDao.insertLocations(entities)

                Log.d(TAG, "Synced ${locations.size} locations to Room for child: $childId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing to Room: ${e.message}", e)
        }
    }

    // Lấy một địa điểm cụ thể
    override suspend fun getLocation(childId: String, locationId: String): Result<Location> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, " ID: $locationId cho childId: $childId")
            val snapshot = database.getReference("locations/$childId/$locationId").get().await()

            if (!snapshot.exists()) {
                Log.e(TAG, "cannot see location ID: $locationId")
                return@withContext Result.failure(Exception("Không tìm thấy địa điểm"))
            }

            // Đọc từng trường của snapshot riêng biệt
            val name = snapshot.child("name").getValue(String::class.java) ?: ""
            val address = snapshot.child("address").getValue(String::class.java) ?: ""
            val latitude = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
            val longitude = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
            val radius = snapshot.child("radius").getValue(Int::class.java) ?: 100
            val type = snapshot.child("type").getValue(String::class.java) ?: "HOME"
            val notificationsEnabled = snapshot.child("notificationsEnabled").getValue(Boolean::class.java) ?: true
            val childIdValue = snapshot.child("childId").getValue(String::class.java) ?: ""
            val parentId = snapshot.child("parentId").getValue(String::class.java) ?: ""
            val placeId = snapshot.child("placeId").getValue(String::class.java) ?: ""

            // ✅ Schedule = null luôn (không parse schedule nữa)
            val schedule = null

            val location = Location(
                id = locationId,
                name = name,
                address = address,
                latitude = latitude,
                longitude = longitude,
                radius = radius,
                type = try { LocationType.valueOf(type) } catch (e: Exception) { LocationType.OTHER },
                notificationsEnabled = notificationsEnabled,
                childId = childIdValue,
                parentId = parentId,
                schedule = schedule, // Always null
                placeId = placeId
            )

            Log.d(TAG, "get location successfully: ${location.name}")
            Result.success(location)
        } catch (e: Exception) {
            Log.e(TAG, "error: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ===== DUAL-WRITE METHODS (Firebase + Room) =====

    // Thêm địa điểm mới (dual-write)
    override suspend fun addLocation(childId: String, location: Location): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Adding new location for childId: $childId")
            val locationsRef = database.getReference("locations/$childId")
            val newLocationRef = locationsRef.push()
            val locationId = newLocationRef.key ?: return@withContext Result.failure(Exception("Cannot create ID"))

            Log.d(TAG, "New location ID: $locationId")

            // Create location with generated ID
            val locationWithId = location.copy(id = locationId, childId = childId, schedule = null)

            // 1. Save to Firebase (without schedule)
            val locationData = mapOf(
                "name" to locationWithId.name,
                "address" to locationWithId.address,
                "latitude" to locationWithId.latitude,
                "longitude" to locationWithId.longitude,
                "radius" to locationWithId.radius,
                "type" to locationWithId.type.name,
                "notificationsEnabled" to locationWithId.notificationsEnabled,
                "childId" to childId,
                "parentId" to locationWithId.parentId,
                "placeId" to locationWithId.placeId
                // ✅ Không save schedule vào Firebase
            )

            // Save to Firebase
            newLocationRef.setValue(locationData).await()

            // 2. Save to Room (with schedule = null)
            val entity = locationWithId.toEntity()
            locationDao.insertLocation(entity)

            Log.d(TAG, "Successfully added location: $locationId")
            Result.success(locationId)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding location: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Cập nhật địa điểm (dual-write)
    override suspend fun updateLocation(childId: String, location: Location): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating location: ${location.id} for childId: $childId")

            // 1. Update Firebase (without schedule)
            val locationData = mapOf(
                "name" to location.name,
                "address" to location.address,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "radius" to location.radius,
                "type" to location.type.name,
                "notificationsEnabled" to location.notificationsEnabled,
                "childId" to childId,
                "parentId" to location.parentId,
                "placeId" to location.placeId
                // ✅ Không update schedule vào Firebase
            )

            // Update Firebase
            database.getReference("locations/$childId/${location.id}")
                .updateChildren(locationData).await()

            // 2. Update Room (with schedule = null)
            val entity = location.copy(childId = childId, schedule = null).toEntity()
            locationDao.insertLocation(entity) // INSERT OR REPLACE

            Log.d(TAG, "Successfully updated location: ${location.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating location: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Xóa địa điểm (dual-delete)
    override suspend fun deleteLocation(childId: String, locationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Deleting location: $locationId for childId: $childId")

            // 1. Delete from Firebase
            database.getReference("locations/$childId/$locationId")
                .removeValue().await()

            // 2. Delete from Room
            locationDao.deleteLocationById(locationId)

            Log.d(TAG, "Successfully deleted location: $locationId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting location: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Bật/tắt thông báo (dual-update)
    override suspend fun toggleNotifications(childId: String, locationId: String, enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Toggling notifications for location: $locationId to $enabled")

            // 1. Update Firebase
            database.getReference("locations/$childId/$locationId/notificationsEnabled")
                .setValue(enabled).await()

            // 2. Update Room
            locationDao.updateNotificationStatus(locationId, enabled)

            Log.d(TAG, "Successfully toggled notifications for location: $locationId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling notifications: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ===== UTILITY METHODS =====

    // Force sync from Firebase to Room
    override suspend fun syncLocationsFromFirebase(childId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Force syncing locations from Firebase for child: $childId")

            val snapshot = database.getReference("locations/$childId").get().await()
            val locations = mutableListOf<Location>()

            for (childSnapshot in snapshot.children) {
                try {
                    val locationId = childSnapshot.key ?: continue
                    // Parse location data (same logic as in getLocationsForChild)
                    val name = childSnapshot.child("name").getValue(String::class.java) ?: ""
                    val address = childSnapshot.child("address").getValue(String::class.java) ?: ""
                    val latitude = childSnapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                    val longitude = childSnapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                    val radius = childSnapshot.child("radius").getValue(Int::class.java) ?: 100
                    val type = childSnapshot.child("type").getValue(String::class.java) ?: "HOME"
                    val notificationsEnabled = childSnapshot.child("notificationsEnabled").getValue(Boolean::class.java) ?: true
                    val childIdValue = childSnapshot.child("childId").getValue(String::class.java) ?: ""
                    val parentId = childSnapshot.child("parentId").getValue(String::class.java) ?: ""
                    val placeId = childSnapshot.child("placeId").getValue(String::class.java) ?: ""

                    // ✅ Schedule = null luôn
                    val schedule = null

                    val location = Location(
                        id = locationId,
                        name = name,
                        address = address,
                        latitude = latitude,
                        longitude = longitude,
                        radius = radius,
                        type = try { LocationType.valueOf(type) } catch (e: Exception) { LocationType.OTHER },
                        notificationsEnabled = notificationsEnabled,
                        childId = childIdValue,
                        parentId = parentId,
                        schedule = schedule, // Always null
                        placeId = placeId
                    )

                    locations.add(location)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing location: ${e.message}", e)
                    continue
                }
            }

            // Clear and insert new data
            locationDao.deleteAllForChild(childId)
            val entities = locations.map { it.toEntity() }
            locationDao.insertLocations(entities)

            Log.d(TAG, "Successfully synced ${locations.size} locations from Firebase")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing from Firebase: ${e.message}", e)
            Result.failure(e)
        }
    }
}