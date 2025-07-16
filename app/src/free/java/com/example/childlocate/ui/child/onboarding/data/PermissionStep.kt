package com.example.childlocate.ui.child.onboarding.data

data class PermissionStep (
    val type: PermissionType,
    val title: String,
    val description: String,
    val imageResId: Int,
    var isGranted: Boolean = false
)
enum class PermissionType {
    LOCATION,
    BACKGROUND_LOCATION,
    NOTIFICATION,
    MICROPHONE,
    CAMERA,
    ACCESSIBILITY,
    OVERLAY,
    USAGE_STATS,
    SMS
}