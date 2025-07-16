package com.example.childlocate.ui.child.onboarding.utils

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.example.childlocate.service.WebFilterAccessibilityService


class PermissionManager(private val context: Context) {
    //danh sách các quyền cần xử lý
    companion object {
        //quyền vị trí
        val LOCATION_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        //quyền thông báo
        val NOTIFICATION_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            ""
        }
        //quyền microphone
        val MICROPHONE_PERMISSION = Manifest.permission.RECORD_AUDIO
        //quyền camera
        val CAMERA_PERMISSION = Manifest.permission.CAMERA
        //quyền gửi tin nhắn
        val SMS_PERMISSION = Manifest.permission.SEND_SMS

    }



    fun isLocationPermissionGranted(): Boolean {
        return LOCATION_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    //kiểm tra quyền thông báo
    /*fun hasNotificationPermissions(): Boolean {
        return NOTIFICATION_PERMISSIONS.all { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
    }*/
    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                NOTIFICATION_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Tự động cấp quyền cho phiên bản Android cũ
        }
    }

    //kiểm tra quyền microphone
    /*fun hasMicrophonePermissions(): Boolean {
        return MICROPHONE_PERMISSIONS.all { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
    }*/

    // Kiểm tra quyền microphone
    fun isMicrophonePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            MICROPHONE_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isSmsPermissionGranted() :Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            SMS_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Kiểm tra quyền camera
    fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            CAMERA_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Kiểm tra quyền overlay
    fun isOverlayPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // Tự động cấp quyền cho phiên bản Android cũ
        }
    }

    // Kiểm tra quyền usage stats
    fun isUsageStatsPermissionGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    // Mo cai dat quyen
    fun getOverlaySettingsIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        } else null
    }

    fun getUsageStatsSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    // Kiểm tra quyền accessibility service (từ AccessibilityUtil)
    fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val serviceComponentName = ComponentName(context, WebFilterAccessibilityService::class.java)

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        Log.d("Permission"," Accessibility Service Enabled: $enabledServices and $serviceComponentName")
        val boolean= enabledServices?.contains(serviceComponentName.flattenToString())
            ?: false
        Log.d("Permission"," Accessibility Service Enabled: $boolean")
        return boolean
    }

    // Mở cài đặt accessibility service
    fun getAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }


    //quyền internet:
    fun isInternetAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    //kiem tra xem da bat vi tri chua
    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER)
    }



}