package com.example.childlocate.service


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.childlocate.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class ModernAppBlocker(private val context: Context) {
    private val TAG = "ModernAppBlocker"

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val packageManager = context.packageManager
    private val appLimitManager = AppLimitManager(context)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var overlayView: View? = null
    private var currentPin = StringBuilder()

    // Cache để tránh spam notifications
    private val lastNotificationTimes = ConcurrentHashMap<String, Long>()
    private val NOTIFICATION_COOLDOWN = 60_000L // 1 phút

    init {
        createNotificationChannel()
    }

    fun blockApp(packageName: String, reason: String) {
        try {
            Log.d(TAG, "Blocking app: $packageName, reason: $reason")
            // 1. Show overlay
            if (!isOverlayShowing()) {
                showPinLockScreen(packageName, getAppName(packageName), reason)
            }
            // 2. Show notification if cooldown passed
            if (shouldShowNotification(packageName)) {
                showBlockingNotification(packageName, reason)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking app: ${e.message}")
        }
    }
    private fun isOverlayShowing(): Boolean {
        return overlayView != null
    }
    private fun showPinLockScreen(packageName: String,appName: String,reason: String){
        if(!Settings.canDrawOverlays(context)){
            Log.e(TAG,"Overlay permission not granted")
            return

        }
        // Chỉ remove overlay cũ nếu nó tồn tại
        if (overlayView != null) {
            removeBlockingOverlay()
        }
        try{
            val inflater = LayoutInflater.from(context)
            overlayView = inflater.inflate(R.layout.app_lock_screen,null).apply{
                //set up ui elements
                findViewById<TextView>(R.id.tvAppName).text = appName
                findViewById<TextView>(R.id.tvReason).text = reason
                val pinDisplay = findViewById<TextView>(R.id.tvPinDisplay)
                // Setup numeric buttons
                for (i in 0..9) {
                    findViewById<Button>(resources.getIdentifier("btn$i", "id", context.packageName))
                        .setOnClickListener {
                            Log.d(TAG, "now current pin is $currentPin")
                            if (currentPin.length < 6) {
                                currentPin.append(i)
                                pinDisplay.text = "*".repeat(currentPin.length)
                            }
                        }
                }
                // Clear button
                findViewById<Button>(R.id.btnClear).setOnClickListener {
                    currentPin.clear()
                    pinDisplay.text = ""
                }
                // Delete button
                findViewById<Button>(R.id.btnDelete).setOnClickListener {
                    if (currentPin.isNotEmpty()) {
                        currentPin.deleteCharAt(currentPin.length - 1)
                        pinDisplay.text = "*".repeat(currentPin.length)
                    }
                }

                // Exit button
                findViewById<Button>(R.id.btnExit).setOnClickListener {
                    removeBlockingOverlay()
                    goToHome()
                }
                // Unlock button
                findViewById<Button>(R.id.btnUnlock).setOnClickListener {
                    scope.launch(Dispatchers.IO) {
                        validatePin(packageName, currentPin.toString())
                    }
                }
                // Setup window params
                val params = WindowManager.LayoutParams().apply {
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    height = WindowManager.LayoutParams.MATCH_PARENT
                    type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_PHONE
                    }
                    flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            //WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

                    dimAmount = 1.0f //set độ trong suốt của overlayout

                    format = PixelFormat.OPAQUE
                    gravity = Gravity.CENTER
                }

                windowManager.addView(this, params)
                //thiet lap che do lang nghe touch chặn tất cả touch events
                setOnTouchListener { v, event ->
                    true
                }


            }

        }catch (e:Exception){
            Log.e("TAG", e.toString())
        }
    }

    private suspend fun validatePin(packageName: String, enteredPin: String) {
        // Thêm SharedPreferences
        val sharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val childId=  sharedPreferences.getString("childId", null)


        try {

            val correctPin = childId?.let { appLimitManager.getAppPin(it) }
            Log.d("TAG","correct pin is $correctPin")
            withContext(Dispatchers.Main) {
                if (enteredPin == correctPin) {
                    // Correct PIN - grant temporary access
                    removeBlockingOverlay()
                    currentPin.clear()
                    // TODO: Implement temporary unblock logic (e.g., 5 minutes)
                    scope.launch {
                        delay(5 * 60 * 1000) // 5 minutes
                        blockApp(packageName, "Thời gian sử dụng tạm thời đã hết")
                    }
                } else {
                    // Wrong PIN
                    Toast.makeText(context, "Mã PIN không đúng", Toast.LENGTH_SHORT).show()
                    currentPin.clear()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating PIN: ${e.message}")
        }
    }

    fun unblockApp(packageName: String) {
        try {
            removeBlockingOverlay()
            clearNotification(packageName)
            lastNotificationTimes.remove(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error unblocking app: ${e.message}")
        }
    }

    fun getCurrentForegroundApp(): String? {
        return try {
            val time = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000,
                time
            )
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current app: ${e.message}")
            null
        }
    }

    private fun removeBlockingOverlay() {
        try {
            overlayView?.let {
                windowManager.removeView(it)
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay: ${e.message}")
        }
    }

    private fun goToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(homeIntent)
    }

    private fun shouldShowNotification(packageName: String): Boolean {
        val lastTime = lastNotificationTimes[packageName] ?: 0L
        val currentTime = System.currentTimeMillis()
        return currentTime - lastTime >= NOTIFICATION_COOLDOWN
    }

    private fun showBlockingNotification(packageName: String, reason: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("Ứng dụng bị chặn")
            .setContentText("${getAppName(packageName)}: $reason")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .build()

        val notificationId = packageName.hashCode()
        notificationManager.notify(notificationId, notification)
        lastNotificationTimes[packageName] = System.currentTimeMillis()
    }

    private fun clearNotification(packageName: String) {
        val notificationId = packageName.hashCode()
        notificationManager.cancel(notificationId)
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Blocking Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for blocked applications"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "app_blocking_channel"
    }
}