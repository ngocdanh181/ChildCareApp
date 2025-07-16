package com.example.childlocate.ui.child.sos.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.example.childlocate.R
import com.example.childlocate.ui.child.main.MainChildActivity
import java.util.concurrent.atomic.AtomicInteger

class SOSGestureService : LifecycleService() {
    private val TAG = "SOSWidget12345GestureService"

    // Đếm số lần nhấn nút nguồn
    private val pressCount = AtomicInteger(0)

    //tạo chuỗi nhấn nut
    private val buttonPressPattern = StringBuilder()

    // Thời gian giữa các lần nhấn (ms)
    private val PRESS_TIMEOUT = 1200L

    // Handler để reset đếm sau khi hết thời gian
    private val handler = Handler(Looper.getMainLooper())

    // BroadcastReceiver để lắng nghe sự kiện màn hình bật/tắt
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                handlePowerButtonPress()
            }
        }
    }
    //broadcast receiver để lắng nghe sự kiện nút volume
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                val prevVolume = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1)
                val currentVolume = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
                if (prevVolume != -1 && currentVolume != -1 && prevVolume != currentVolume) {
                    if (currentVolume > prevVolume) {
                        // Nút tăng âm lượng
                        handleButtonPress("U")
                    } else {
                        // Nút giảm âm lượng
                        handleButtonPress("D")
                    }
                }
            }
        }
    }

    // Runnable để reset đếm
    private val resetCounter = Runnable {
        val count = pressCount.getAndSet(0)
        Log.d(TAG, "Reset counter after timeout, count was: $count")
    }

    // Runnable để reset mẫu
    private val resetPattern = Runnable {
        Log.d(TAG, "Reset pattern after timeout, pattern was: $buttonPressPattern")
        buttonPressPattern.clear()
    }

    override fun onCreate() {
        super.onCreate()
        registerScreenReceiver()
        //registerVolumeReceiver()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "SOS Gesture Service started")
        return START_STICKY
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun registerVolumeReceiver(){
        val filter = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
        }
        registerReceiver(screenReceiver, filter)
    }
    private fun handleButtonPress(buttonType :String){
        // Thêm nút mới vào mẫu
        buttonPressPattern.append(buttonType)
        Log.d(TAG, "Button press pattern: $buttonPressPattern")

        // Hủy bỏ reset đã lên lịch và đặt lịch mới
        handler.removeCallbacks(resetPattern)
        handler.postDelayed(resetPattern, PRESS_TIMEOUT)

        // Kiểm tra các mẫu SOS
        checkSOSPatterns()
    }

    private fun checkSOSPatterns() {
        val pattern = buttonPressPattern.toString()

        //check mẫu ududud (3 lần up down liên tiếp )
        if (pattern.contains("DDDDD")) {
            Log.d(TAG, "SOS gesture detected! Sending SOS signal")
            buttonPressPattern.clear()
            sendSOSSignal()
            return
        }
    }

    private fun handlePowerButtonPress() {
        // Tăng bộ đếm
        val count = pressCount.incrementAndGet()
        Log.d(TAG, "Power button pressed, count: $count")

        // Đặt lịch reset chỉ khi đây là lần nhấn đầu tiên
        if (count == 1) {
            handler.postDelayed(resetCounter, PRESS_TIMEOUT * 5) // Cho phép thời gian cho 5 lần nhấn
        }

        // Kiểm tra xem đã đạt ngưỡng chưa
        if (count >= 5) {
            // Hủy bỏ reset đã lên lịch
            handler.removeCallbacks(resetCounter)

            // Reset counter
            pressCount.set(0)

            // Gửi tín hiệu SOS
            Log.d(TAG, "SOS gesture detected! Sending SOS signal")
            sendSOSSignal()
        }
    }

    private fun sendSOSSignal() {
        // Sử dụng phương thức static helper từ SOSService
        SOSService.startSendSOS(this)

        // Wake up device if screen is off
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "childlocate:SOSWakeLock"
            )
            wakeLock.acquire(5000) // Acquire for 5 seconds
            try {
                // Đảm bảo release wakeLock sau khi sử dụng
                wakeLock.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wakeLock", e)
            }
        }
    }

    private fun startForegroundService() {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        } else {
            ""
        }

        val openAppIntent = Intent(this, MainChildActivity::class.java)
        val pendingIntentFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SOS Khẩn cấp")
            .setContentText("Nhấn nút nguồn 5 lần liên tiếp để gửi tín hiệu SOS")
            .setSmallIcon(R.drawable.baseline_notifications_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        // Sử dụng FOREGROUND_SERVICE_TYPE_SPECIAL_USE từ Android 14
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "sos_gesture_channel"
            val channelName = "SOS Gesture Service"
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            return channelId
        }
        return ""
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        handler.removeCallbacks(resetCounter)
        Log.d(TAG, "SOS Gesture Service destroyed")
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

    }
}