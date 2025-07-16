package com.example.childlocate.repository

import com.example.childlocate.data.model.Location
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    
    // ===== FIREBASE METHODS (for UI) =====
    
    // Lấy danh sách địa điểm từ Firebase (cho UI)
    fun getLocationsForChild(childId: String): Flow<List<Location>>
    
    // ===== ROOM METHODS (for geofence checking) =====
    
    // Lấy danh sách địa điểm từ Room (nhanh, cho geofence checking)
    suspend fun getLocalLocationsForChild(childId: String): List<Location>
    
    // Lấy Flow từ Room (cho real-time local updates)
    fun getLocalLocationsForChildFlow(childId: String): Flow<List<Location>>
    
    // Lấy một địa điểm cụ thể
    suspend fun getLocation(childId: String, locationId: String): Result<Location>
    
    // ===== DUAL-WRITE METHODS (Firebase + Room) =====
    
    // Thêm địa điểm mới (dual-write)
    suspend fun addLocation(childId: String, location: Location): Result<String>
    
    // Cập nhật địa điểm (dual-write)
    suspend fun updateLocation(childId: String, location: Location): Result<Unit>
    
    // Xóa địa điểm (dual-delete)
    suspend fun deleteLocation(childId: String, locationId: String): Result<Unit>
    
    // Bật/tắt thông báo (dual-update)
    suspend fun toggleNotifications(childId: String, locationId: String, enabled: Boolean): Result<Unit>
    
    // ===== UTILITY METHODS =====
    
    // Force sync from Firebase to Room
    suspend fun syncLocationsFromFirebase(childId: String): Result<Unit>
}

