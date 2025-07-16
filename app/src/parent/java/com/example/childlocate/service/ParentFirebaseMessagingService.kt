package com.example.childlocate.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.childlocate.R
import com.example.childlocate.ui.parent.MainActivity
import com.google.firebase.messaging.RemoteMessage

class ParentFirebaseMessagingService: MyFirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "WarningForegroundServiceChannel"
        private const val WARNING_NOTIFICATION_ID = 1
        private const val STOP_WARNING_NOTIFICATION_ID = 2

        // Vibration pattern: wait 0ms, vibrate 1000ms, wait 500ms
        private val VIBRATION_PATTERN = longArrayOf(0, 1000, 500)
        private const val VIBRATION_REPEAT_INDEX = 0 // Repeat from index 0
    }
    

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    
    //Đăng kí broadcast receiver
    private val stopAlarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_STOP_ALARM_SOUND") {
                // Gọi trực tiếp method của service
                stopAlarmEffects()
            }
        }
    }

    override fun onCreate(){
        super.onCreate()
        createNotificationChannel()
        // Đăng ký BroadcastReceiver để nhận lệnh dừng âm thanh
        val filter = IntentFilter("ACTION_STOP_ALARM_SOUND")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33 (Android 13) trở lên yêu cầu cờ này
            registerReceiver(stopAlarmReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopAlarmReceiver, filter)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun handleDataMessage(remoteMessage: RemoteMessage) {
        val requestType = remoteMessage.data["request_type"] ?: return

        when (requestType) {
            "warning_request" -> handleWarningRequest()
            "stop_warning_request" -> handleStopWarningRequest()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("MissingPermission")
    private fun handleWarningRequest() {

        maximizeSystemVolume()
        startAlarmSound()
        startVibration()
        showWarningNotification()
    }

    @SuppressLint("MissingPermission")
    private fun handleStopWarningRequest() {
        stopAlarmEffects()
        showStopWarningNotification()
    }

    private fun maximizeSystemVolume() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun startAlarmSound() {
        val alarmSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(applicationContext, alarmSound).apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = true
            play()
        }
    }

    private fun startVibration() {
        vibrator = (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, VIBRATION_REPEAT_INDEX))
            } else {
                @Suppress("DEPRECATION")
                vibrate(VIBRATION_PATTERN, VIBRATION_REPEAT_INDEX)
            }
        }
    }

    private fun stopAlarmEffects() {
        ringtone?.stop()
        vibrator?.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun showWarningNotification() {
        val notification = createWarningNotification(
            title = "Cảnh báo nguy hiểm",
            content = "Con của bạn đang gặp nguy hiểm",
            bigText = "Con của bạn đang gặp nguy hiểm - Yêu cầu kiểm tra ngay lập tức!"
        )

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(WARNING_NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun showStopWarningNotification() {
        val notification = createStopWarningNotification(
            title = "Cảnh báo nguy hiểm",
            content = "Có vẻ như vấn đề đã được xử lí"
        )

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(STOP_WARNING_NOTIFICATION_ID, notification)
        notificationManager.cancel(WARNING_NOTIFICATION_ID) // Cancel warning notification
    }

    private fun createWarningNotification(title: String, content: String, bigText: String): android.app.Notification {
        val pendingIntent = createMainActivityPendingIntent()

        // Intent để tắt âm thanh
        val stopAlarmIntent = Intent("ACTION_STOP_ALARM_SOUND").apply {
            // Đặt component để Intent trở thành explicit
            // Nó sẽ gửi đến BroadcastReceiver của chính bạn
            setClass(this@ParentFirebaseMessagingService, stopAlarmReceiver::class.java) // 'context' ở đây là ParentFirebaseMessagingService.this
        }

        val stopAlarmPendingIntent = PendingIntent.getBroadcast(
            this, // Context
            8, // requestCode khác để tránh conflict
            stopAlarmIntent,
            PendingIntent.FLAG_MUTABLE // Vẫn giữ MUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSound(null) // Handle sound manually with ringtone
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(
                R.drawable.baseline_stop_24, // Icon for the action
                "Dừng âm thanh", // Action title
                stopAlarmPendingIntent // PendingIntent to stop the alarm
            )
            .build()
    }

    private fun createStopWarningNotification(title: String, content: String): android.app.Notification {
        val pendingIntent = createMainActivityPendingIntent()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
    }

    private fun createMainActivityPendingIntent(): PendingIntent {
        val notificationIntent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Warning Request Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for warning request notifications"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Hủy đăng ký receiver
        unregisterReceiver(stopAlarmReceiver)
        stopAlarmEffects()
    }
}