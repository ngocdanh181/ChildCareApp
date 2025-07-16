// ServiceRestartReceiver.kt
package com.example.childlocate.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.childlocate.utils.ServiceUtils

class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ServiceRestart","Received action: ${intent.action}")
        when(intent.action) {
            "RESTART_APP_LIMIT_SERVICE" -> {
                Log.d("ServiceRestart", "AppLimit restart requested")
                // Chỉ setup monitoring mechanisms, không start service trực tiếp
                // vì service đã được start từ FirebaseMessaging
                setupMonitoringMechanisms(context)
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d("ServiceRestart", "Device booted, checking services")
                restartAllServices(context)
            }

            "CHECK_AND_RESTART_SERVICES" -> {
                checkAndRestartServices(context)
            }
        }
    }

    private fun setupMonitoringMechanisms(context: Context) {
        // 1. Schedule WorkManager task
        AppLimitWorkerManager.scheduleWorker(context)

        // 2. Setup AlarmManager as backup
        setupAlarmManager(context)
    }


    private fun startServiceSafely(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun restartAllServices(context: Context) {
        val prefs = context.getSharedPreferences("service_state", Context.MODE_PRIVATE)

        if (prefs.getBoolean("is_app_limit_running", false)) {
            startServiceSafely(context, Intent(context, AppLimitService::class.java))
            setupMonitoringMechanisms(context)
        }
    }

    private fun setupAlarmManager(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ServiceRestartReceiver::class.java).apply {
            action = "CHECK_AND_RESTART_SERVICES"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            19,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set repeating alarm every 15 minutes
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis(),
            15 * 60 * 1000L, // 15 minutes
            pendingIntent
        )
    }

    // Trong ServiceRestartReceiver
    private fun checkAndRestartServices(context: Context) {
        // Thêm thời gian kiểm tra cuối để tránh khởi động quá thường xuyên
        val prefs = context.getSharedPreferences("service_state", Context.MODE_PRIVATE)
        val lastCheckTime = prefs.getLong("last_service_check", 0)
        val currentTime = System.currentTimeMillis()

        // Chỉ kiểm tra cách nhau ít nhất 1 phút
        if (currentTime - lastCheckTime > 60000 &&
            prefs.getBoolean("is_app_limit_running", false) &&
            !ServiceUtils.isServiceRunning(context, AppLimitService::class.java)) {

            ServiceUtils.startServiceSafely(context, AppLimitService::class.java)

            // Lưu thời gian kiểm tra
            prefs.edit().putLong("last_service_check", currentTime).apply()
        }
    }

}