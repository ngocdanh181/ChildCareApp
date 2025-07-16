package com.example.childlocate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.childlocate.R
import com.example.childlocate.data.model.AppLimit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean

class AppLimitService : Service() {
    //them scope cho coroutines
    companion object {
        private const val FOREGROUND_SERVICE_ID = 3212
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isRunning = AtomicBoolean(false)
     val CHECK_INTERVAL = 1000L  // 1 giây

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var packageManager: PackageManager
    private var wakeLock: PowerManager.WakeLock? = null
    private val TAG = "AppLimitService"
    private lateinit var appLimitManager: AppLimitManager
    private lateinit var appBlocker: ModernAppBlocker

    // Map để lưu trữ notification ID cho mỗi package
    private val notificationIds = mutableMapOf<String, Int>()

    // Thêm biến để track ngày hiện tại
    private var currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

    private val sharedPrefs by lazy {
        getSharedPreferences("app_limit_prefs", Context.MODE_PRIVATE)
    }

    private val handler = Handler(Looper.getMainLooper())

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (isRunning.get()) {
                try {
                    checkAndBlockIfNeeded()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in checkRunnable: ${e.message}")
                }
                handler.postDelayed(this, CHECK_INTERVAL)
            }
        }
    }



    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AppLimitService::WakeLock"
            )
            wakeLock?.acquire(CHECK_INTERVAL + 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        createNotificationChannel()

        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        packageManager = applicationContext.packageManager
        appLimitManager = AppLimitManager(this)
        appBlocker = ModernAppBlocker(this)

        // Save service state
        getSharedPreferences("service_state", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_app_limit_running", true)
            .apply()

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand: ${intent?.action}")

        when (intent?.action) {
            "START_MONITORING" -> {
                startMonitoring()
            }
            "UPDATE_NOTIFICATION" -> {
                startForegroundService()
            }
            "STOP_MONITORING" -> {
                val packageName = intent.getStringExtra("package_name")
                if (packageName != null) {
                    scope.launch {
                        appLimitManager.removeAppLimit(packageName)
                        if (appLimitManager.getAppLimits().isEmpty()) {
                            stopMonitoring()
                        }
                    }
                } else {
                    stopMonitoring()
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Service onTaskRemoved")

        // Gửi broadcast để restart service
        val broadcastIntent = Intent("RESTART_APP_LIMIT_SERVICE")
        sendBroadcast(broadcastIntent)
    }

    private fun checkAndBlockIfNeeded() {
        scope.launch{
            try{
                // Kiểm tra xem đã sang ngày mới chưa
                val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                if (today != currentDay) {
                    // Reset lại thời gian sử dụng và dừng service
                    Log.d(TAG, "New day detected, stopping monitoring")
                    stopMonitoring()
                    return@launch
                }

                val appLimits = appLimitManager.getAppLimits()
                val currentApp = appBlocker.getCurrentForegroundApp()

                for (appLimit in appLimits) {
                    if (appLimit.packageName == currentApp){
                        Log.d("AppLimitService", "$appLimit")
                        val blockReason = when {
                            !isWithinAllowedTimeRange(appLimit) -> "Ngoài khung giờ cho phép"
                            hasExceededDailyLimit(appLimit) -> "Đã vượt quá thời gian cho phép hôm nay"
                            else -> null
                        }
                        if (blockReason != null) {
                            Log.d(TAG,blockReason)
                        }

                        if (blockReason != null ) {
                            appBlocker.blockApp(appLimit.packageName, blockReason)
                        } else {
                            appBlocker.unblockApp(appLimit.packageName)
                        }
                    }

                }
            }catch (e:Exception){
                Log.e(TAG, "Error in checkAndBlockIfNeeded: ${e.message}")
            }
        }

    }



    private fun isWithinAllowedTimeRange(appLimit: AppLimit): Boolean {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentTime = currentHour * 60 + currentMinute

        val (startHour, startMinute) = appLimit.startTime.split(":").map { it.toInt() }
        val (endHour, endMinute) = appLimit.endTime.split(":").map { it.toInt() }

        val startTime = startHour * 60 + startMinute
        val endTime = endHour * 60 + endMinute

        return currentTime in startTime..endTime
    }

    private fun hasExceededDailyLimit(appLimit:AppLimit): Boolean {
        val totalUsageToday = getTotalUsageToday(appLimit.packageName)
        return totalUsageToday >= appLimit.dailyLimitMinutes * 60 * 1000
    }

    private fun getTotalUsageToday(packageName: String): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            calendar.timeInMillis,
            System.currentTimeMillis()
        )
        val totalTime = stats.find { it.packageName == packageName }
            ?.totalTimeInForeground ?: 0L
        Log.d("TAG", "Get total UsageToday for $packageName is : $totalTime ")

        return stats.find { it.packageName == packageName }
            ?.totalTimeInForeground ?: 0L
    }

    private fun startMonitoring() {
        if (!isRunning.get()) {
            isRunning.set(true)
            acquireWakeLock()
            startForegroundService()
            handler.post(checkRunnable)
            Log.d(TAG, "Monitoring started")
        }
    }

    private fun stopMonitoring() {
        Log.d(TAG, "Stopping monitoring")
        isRunning.set(false)
        appBlocker.unblockApp(packageName)
        handler.removeCallbacks(checkRunnable)
        releaseWakeLock()
        startForegroundService()

        // Clear service state
        getSharedPreferences("service_state", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_app_limit_running", false)
            .apply()

        sharedPrefs.edit().putBoolean("is_running", false).apply()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "app_limit_channel",
            "App Limit Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for app usage limits"
            enableLights(true)
            lightColor = Color.RED
            setSound(null, null)
            vibrationPattern = longArrayOf(0, 500, 1000)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundService() {
        scope.launch {
            val appLimits = appLimitManager.getAppLimits()

            if (appLimits.isNotEmpty()) {
                val appNames = appLimits.joinToString(", ") { getAppName(it.packageName) }
                val notification = createServiceNotification(appNames)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(FOREGROUND_SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(FOREGROUND_SERVICE_ID, notification)
                }
            } else {
                stopSelf()
            }
        }
    }
    private fun createServiceNotification(appNames: String): Notification {
        return NotificationCompat.Builder(this, "app_limit_channel")
            .setContentTitle("Đang giám sát ứng dụng")
            .setContentText("Đang theo dõi: $appNames")
            .setSmallIcon(R.drawable.notification_icon)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun clearAllAppNotifications() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationIds.forEach { (packageName, id) ->
            notificationManager.cancel(id)
            Log.d(TAG, "Cleared notification for $packageName with ID $id")
        }
        notificationIds.clear()
    }

    override fun onDestroy(){
        super.onDestroy()
        clearAllAppNotifications()
        scope.cancel() // Cancel all coroutines when service is destroyed

    }

    override fun onBind(intent: Intent?): IBinder? = null
}