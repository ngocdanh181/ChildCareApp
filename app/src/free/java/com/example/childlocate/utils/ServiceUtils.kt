package com.example.childlocate.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object ServiceUtils {
    private const val TAG = "ServiceUtils"

    fun isServiceRunning(
        context: Context,
        serviceClass: Class<*>,
        checkForeground: Boolean = false
    ): Boolean {
        try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    if (checkForeground) {
                        if (service.foreground) {
                            Log.d(TAG, "Found running foreground service: ${serviceClass.simpleName}")
                            return true
                        }
                    } else {
                        Log.d(TAG, "Found running service: ${serviceClass.simpleName}")
                        return true
                    }
                }
            }
            Log.d(TAG, "Service not running: ${serviceClass.simpleName}")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking service status: ${e.message}")
            return false
        }
    }

    /**
     * Start service một cách an toàn (handle Android O trở lên)
     */
    fun startServiceSafely(context: Context, serviceClass: Class<*>) {
        try {
            val intent = Intent(context, serviceClass)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
                Log.d(TAG, "Started foreground service: ${serviceClass.simpleName}")
            } else {
                context.startService(intent)
                Log.d(TAG, "Started service: ${serviceClass.simpleName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service: ${e.message}")
        }
    }

}