package com.example.childlocate.data.model

data class UserData(
    val userId: String,
    val name: String,
    val email: String,
    val phone: String = "",
    val role: String,
    val avatarUrl: String = "",
    val familyId: String,
    val familyName: String = ""
)

data class FamilyMember(
    val id: String,
    val name: String,
    val role: String,
    val avatarUrl: String = "",
    val phone: String = ""
)

data class NotificationPreferences(
    val safeZoneAlerts: Boolean = true,
    val batteryAlerts: Boolean = true,
    val sosAlerts: Boolean = true
)

data class SecurityPreferences(
    val appLock: Boolean = false,
    val biometricAuth: Boolean = false,
    val pin: String = ""
)

data class TrackingPreferences(
    val updateInterval: Int = 5
)