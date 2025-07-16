package com.example.childlocate.ui.parent.userinfo

import android.Manifest
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Lớp quản lý quyền truy cập cho module UserInfo
 */
object PermissionManager {

    /**
     * Yêu cầu quyền truy cập bộ nhớ để đọc ảnh
     * @param requestPermissionLauncher ActivityResultLauncher đã đăng ký để xử lý kết quả
     */
    fun requestStoragePermission(
        requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    ) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) sử dụng quyền READ_MEDIA_IMAGES thay vì READ_EXTERNAL_STORAGE
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+) vẫn cần READ_EXTERNAL_STORAGE
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            // Các phiên bản Android cũ hơn
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        requestPermissionLauncher.launch(permissions)
    }

    /**
     * Yêu cầu quyền truy cập vị trí
     * @param requestPermissionLauncher ActivityResultLauncher đã đăng ký để xử lý kết quả
     * @param includeBackgroundLocation Có yêu cầu quyền vị trí nền hay không
     */
    fun requestLocationPermission(
        requestPermissionLauncher: ActivityResultLauncher<Array<String>>,
        includeBackgroundLocation: Boolean = false
    ) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Thêm quyền vị trí nền nếu cần (Android 10+)
        if (includeBackgroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    /**
     * Yêu cầu quyền truy cập thông báo (Android 13+)
     * @param requestPermissionLauncher ActivityResultLauncher đã đăng ký để xử lý kết quả
     */
    fun requestNotificationPermission(
        requestPermissionLauncher: ActivityResultLauncher<String>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
} 