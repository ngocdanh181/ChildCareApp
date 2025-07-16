package com.example.childlocate.ui.child.sos.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.icu.text.SimpleDateFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.example.childlocate.R
import com.example.childlocate.ui.child.main.MainChildActivity
import com.example.childlocate.ui.child.main.MainChildViewModel
import com.example.childlocate.ui.child.sos.widget.SOSWidgetProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

/**
 * Service xử lý việc gửi và hủy cảnh báo khẩn cấp
 * Đã được cập nhật từ IntentService sang LifecycleService vì IntentService đã bị loại bỏ
 */
class SOSService : LifecycleService() {
    private val TAG = "SOSService"
    private lateinit var viewModel: MainChildViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sosManager: SOSManager

    // Tạo CoroutineScope để xử lý các tác vụ bất đồng bộ
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var familyId: String
    private lateinit var phones: List<String>
    private val gson = Gson()




    override fun onCreate() {
        super.onCreate()
        viewModel = MainChildViewModel(application)
        sharedPreferences = getSharedPreferences("SOS_DATA", MODE_PRIVATE)
        sosManager = SOSManager(this)
        // Lấy parentID từ shared preferences
        val sharedPreferences1 = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        familyId = sharedPreferences1.getString("familyId", null).toString()
        val json = sharedPreferences1.getString("parentPhones", null)
        phones = json?.let {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(it, type)
        } ?: emptyList()


        // Tạo và hiển thị notification khi service chạy ở foreground
        // Sử dụng FOREGROUND_SERVICE_TYPE_SPECIAL_USE từ Android 14 (API 34)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE // Thêm FOREGROUND_SERVICE_TYPE từ API 34
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Xử lý intent
        intent?.let {
            when (it.action) {
                ACTION_SEND_SOS -> {
                    // Sử dụng coroutine để xử lý tác vụ ở background
                    serviceScope.launch {
                        handleSendSOS()
                    }
                }
                ACTION_CANCEL_SOS -> {
                    // Xử lý hủy cảnh báo SOS
                    serviceScope.launch {
                        handleCancelSOS()
                    }
                }
                else -> {}
            }
        }

        // Return START_STICKY để service được khởi động lại nếu bị hệ thống kill
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null // Service này không cung cấp binding
    }

    private fun handleSendSOS() {
        Log.d(TAG, "handleSendSOS")
        // Đặt trạng thái SOS là đang hoạt động
        setSosActive(true)

        // Cập nhật widget để hiển thị trạng thái mới
        updateWidgets()

        // Tạo rung để xác nhận đã nhận tín hiệu SOS
        vibrate()


        Log.d("childRepository", "family ID: $familyId")
        if (familyId.isEmpty()) {
            showErrorNotification("Không tìm thấy ID của phụ huynh")
            stopSelf() // Dừng service khi không có parentID
            return
        }
        // Kiểm tra kết nối internet
        if (!isInternetAvailable()) {
           //Gửi tin nhắn qua SMS
            sendSOSviaSMS()
            return
        }

        // Gửi cảnh báo đến phụ huynh
        sendWarningToParent(familyId)
    }

    private fun sendSOSviaSMS() {
        // Tạo tin nhắn SOS
        val sosMessage = createSOSMessage()

        // Gửi SMS đến từng số
        sendSMSToMultipleNumbers(phones, sosMessage)
    }

    private fun createSOSMessage(): String {
        val currentTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        return "CẢNH BÁO SOS \n" +
                "Con bạn đang gặp tình huống khẩn cấp!\n" +
                "Thời gian: $currentTime\n" +
                "Vui lòng liên hệ ngay với con!"
    }
    private fun sendSMSToMultipleNumbers(numbers: List<String>, message: String) {
        val smsManager: SmsManager = getSystemService(SmsManager::class.java)

        var successCount = 0
        var failCount = 0

        numbers.forEach { phoneNumber ->
            val parts = smsManager.divideMessage(message)
            try {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                successCount++
                Log.d(TAG, "SMS sent successfully to: $phoneNumber")
            } catch (e: Exception) {
                failCount++
                Log.e(TAG, "Failed to send SMS to: $phoneNumber", e)
            }
        }

        // Hiển thị kết quả
        val resultMessage = "Đã gửi SMS SOS: $successCount success, $failCount 0 fail"
        Log.d(TAG, resultMessage)

        // Hiển thị thông báo thành công
        showSuccessNotification("Đã gửi cảnh báo SOS qua SMS đến phụ huynh")
    }


    private fun handleCancelSOS() {
        Log.d(TAG, "handleCancelSOS")

        // Đặt trạng thái SOS là không hoạt động
        setSosActive(false)

        // Cập nhật widget để hiển thị trạng thái mới
        updateWidgets()

        // Tạo rung để xác nhận đã hủy tín hiệu SOS
        vibrate()


        if (familyId.isNotEmpty()) {
            // Nếu có kết nối internet, gửi yêu cầu hủy ngay lập tức
            if (isInternetAvailable()) {
                viewModel.stopSendWarningToParent(familyId)
                showSuccessNotification("Đã hủy cảnh báo khẩn cấp")
            } else {
                sendStopSOSviaSMS()
            }
        }

        // Dừng service sau khi xử lý hủy cảnh báo
        stopSelf()
    }

    private fun sendStopSOSviaSMS() {
        // Tạo tin nhắn SOS
        val sosMessage = createStopSOSMessage()

        // Gửi SMS đến từng số
        sendSMSToMultipleNumbers(phones, sosMessage)
    }

    private fun createStopSOSMessage(): String {
        val currentTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        return "CẢNH BÁO SOS \n" +
                "Co ve van de cua con ban da duoc xu li!\n" +
                "Thời gian: $currentTime\n"
    }

    private fun sendWarningToParent(familyId: String) {
        // Wake lock để đảm bảo CPU tiếp tục hoạt động để hoàn thành yêu cầu
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "childlocate:SOSWakeLock"
        )

        try {
            wakeLock.acquire(30000) // Timeout 30 giây

            // Gửi cảnh báo sử dụng ViewModel
            viewModel.sendWarningToParent(familyId)

            // Lưu cảnh báo với timestamp
            val timestamp = System.currentTimeMillis()
            sosManager.saveWarning(timestamp, true)

            // Hiển thị thông báo
            showSuccessNotification("Đã gửi cảnh báo khẩn cấp đến phụ huynh")

            Log.d(TAG, "SOS warning sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SOS warning", e)
            showErrorNotification("Không thể gửi cảnh báo: ${e.message}")
            // Lưu để thử lại sau
            val timestamp = System.currentTimeMillis()
            sosManager.saveWarning(timestamp, false)
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
            // KHÔNG dừng service để người dùng có thể hủy cảnh báo nếu muốn
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Từ Android 12 trở lên, sử dụng phương thức mới để lấy Vibrator
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Mẫu rung SOS: 3 ngắn, 3 dài, 3 ngắn
            val pattern = longArrayOf(0, 200, 200, 200, 200, 200, 200, 600, 200, 600, 200, 600, 200, 200, 200, 200, 200, 200, 200)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            val pattern = longArrayOf(0, 200, 200, 200, 200, 200, 200, 600, 200, 600, 200, 600, 200, 200, 200, 200, 200, 200, 200)
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun createNotification(): Notification {
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

        // Tạo intent để hủy cảnh báo SOS
        val cancelIntent = Intent(this, SOSService::class.java).apply {
            action = ACTION_CANCEL_SOS
        }

        val cancelPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this, 1, cancelIntent, pendingIntentFlags
            )
        } else {
            PendingIntent.getService(
                this, 1, cancelIntent, pendingIntentFlags
            )
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SOS Khẩn cấp")
            .setContentText("Đang gửi tín hiệu SOS...")
            .setSmallIcon(R.drawable.baseline_notifications_24)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_cancel, "Hủy SOS", cancelPendingIntent) // Thêm nút hủy SOS
            .build()
    }

    private fun showSuccessNotification(message: String) {
        showNotification("SOS - Thành công", message, NotificationCompat.PRIORITY_DEFAULT)
    }

    private fun showWarningNotification(message: String) {
        showNotification("SOS - Chờ kết nối", message, NotificationCompat.PRIORITY_DEFAULT)
    }

    private fun showErrorNotification(message: String) {
        showNotification("SOS - Lỗi", message, NotificationCompat.PRIORITY_HIGH)
    }

    private fun showNotification(title: String, message: String, priority: Int) {
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
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.baseline_notifications_24)
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_RESULT_ID, notification)
    }

    private fun createNotificationChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "sos_channel"
            val channelName = "SOS Alerts"
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            return channelId
        }
        return ""
    }

    //cap nhat widget
    private fun updateWidgets(){
        val intent = Intent(this, SOSWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE

            // Lấy danh sách tất cả các widget đã được thêm vào màn hình
            val appWidgetManager = AppWidgetManager.getInstance(this@SOSService)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(this@SOSService, SOSWidgetProvider::class.java)
            )

            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        }

        // Gửi broadcast để cập nhật widget
        sendBroadcast(intent)
    }



    // Đặt trạng thái SOS
    private fun setSosActive(active: Boolean) {
        sharedPreferences.edit().putBoolean("is_sos_active", active).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SOSService destroyed")
    }

    companion object {
        const val ACTION_SEND_SOS = "com.example.childlocate.ui.child.sos.service.action.SEND_SOS"
        const val ACTION_CANCEL_SOS = "com.example.childlocate.ui.child.sos.service.action.CANCEL_SOS"
        private const val NOTIFICATION_ID = 2001
        private const val NOTIFICATION_RESULT_ID = 2002

        // Kiểm tra trạng thái SOS
        fun isSosActive(context: Context): Boolean {
            return context.getSharedPreferences("SOS_DATA", Context.MODE_PRIVATE)
                .getBoolean("is_sos_active", false)
        }

        // Hàm tiện ích để khởi động service gửi SOS
        fun startSendSOS(context: Context) {
            val intent = Intent(context, SOSService::class.java).apply {
                action = ACTION_SEND_SOS
            }

            // Sử dụng startForegroundService để đảm bảo service chạy ở foreground
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        // Hàm tiện ích để hủy cảnh báo SOS
        fun cancelSOS(context: Context) {
            val intent = Intent(context, SOSService::class.java).apply {
                action = ACTION_CANCEL_SOS
            }

            // Sử dụng startForegroundService để đảm bảo service chạy ở foreground
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}