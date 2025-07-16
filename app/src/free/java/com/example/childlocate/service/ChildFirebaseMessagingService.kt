package com.example.childlocate.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.example.childlocate.R
import com.example.childlocate.data.model.AppLimit
import com.example.childlocate.utils.ServiceUtils
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class ChildFirebaseMessagingService : MyFirebaseMessagingService() {
    private lateinit var appLimitManager: AppLimitManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(){
        super.onCreate()
        appLimitManager = AppLimitManager(this)
    }
    override fun handleDataMessage(remoteMessage: RemoteMessage){
        val requestTypeFull = remoteMessage.data["request_type"] ?: return
        val requestParts = requestTypeFull.split("|", limit = 2)
        val appRequestParts = requestTypeFull.split("|")
        val requestType = requestParts[0]
        val additionalInfo = if (requestParts.size > 1) requestParts[1] else ""
        Log.d("FCM", requestType)
        // Check if the message contains data
        if (remoteMessage.data.isNotEmpty()) {
            // Check if the message is a location request
            when (requestType) {
                "location_request" -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                        // Khởi động service
                        val serviceIntent = Intent(this, LocationForegroundService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                    } else {
                        // Xử lý khi không có quyền
                        Log.e("FCM", "Cannot start location service - missing permissions")
                    }
                }
                "stop_location_request" -> {
                    // Stop location foreground service
                    val stopIntent = Intent(this, LocationForegroundService::class.java)
                    stopService(stopIntent)
                }
                "audio_request" -> {
                    val parentId = additionalInfo
                    sendCommandToAudioService("START_RECORDING", parentId)
                    Log.d("FCM_DEBUG", "4. Command sent to service")

                }
                "stop_audio_request" -> {
                    val parentId = additionalInfo
                    sendCommandToAudioService("STOP_RECORDING", parentId)

                }
                "send_task_request" ->{
                    if (additionalInfo.isNotEmpty()) {
                        val taskParts = additionalInfo.split("|")
                        if (taskParts.size == 2) {
                            val (taskName, taskTime) = taskParts
                            Log.d("Task", "taskName: $taskName, taskTime: $taskTime")
                            showTaskNotification(taskName, taskTime)
                            scheduleTaskReminder(taskName, taskTime)
                        } else {
                            Log.e("FCM", "Invalid task information format")
                        }
                    } else {
                        Log.e("FCM", "No additional task information provided")
                    }

                }
                "usage_stats_request" ->{
                    val usageTrackingManager = UsageTrackingManager(this)
                    usageTrackingManager.requestImmediateSync()
                    //usageTrackingManager.stopPeriodicTracking()
                }
                "app_limit_request" -> {
                    if (appRequestParts.size >= 5) {
                        val packageName = appRequestParts[1]
                        val dailyLimitMinutes = appRequestParts[2].toIntOrNull() ?: 0
                        val startTime = appRequestParts[3]
                        val endTime = appRequestParts[4]

                        val restartIntent = Intent(this, ServiceRestartReceiver::class.java).apply {
                            action = "RESTART_APP_LIMIT_SERVICE"
                        }
                        sendBroadcast(restartIntent)

                        handleAppLimitRequest(
                            AppLimit(
                                packageName = packageName,
                                dailyLimitMinutes = dailyLimitMinutes,
                                startTime = startTime,
                                endTime = endTime
                            )
                        )
                    }
                }
                "app_limit_stop" -> {
                    if (appRequestParts.size >= 2) {
                        val packageName = appRequestParts[1]
                        handleAppLimitStop(packageName)
                    }
                }
            }
        }
    }
    private fun handleAppLimitStop(packageName: String) {
        scope.launch {
            // Remove app limit
            appLimitManager.removeAppLimit(packageName)

            // 2. Kiểm tra còn app limits nào không
            val remainingLimits = appLimitManager.getAppLimits()

            if (remainingLimits.isEmpty()) {
                // Nếu không còn app limits nào, dừng service và worker
                stopService(Intent(this@ChildFirebaseMessagingService, AppLimitService::class.java))
                manageAppLimitWorker(false)
            } else {
                // Nếu còn app limits khác, chỉ cần update notification
                val updateIntent = Intent(this@ChildFirebaseMessagingService, AppLimitService::class.java).apply {
                    action = "UPDATE_NOTIFICATION"
                }
                startService(updateIntent)
            }

            // 3. Clear notifications của app bị xóa
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(packageName.hashCode())
        }
    }

    private fun manageAppLimitWorker(shouldRun: Boolean) {
        val workManager = WorkManager.getInstance(this)
        if (shouldRun) {
            AppLimitWorkerManager.scheduleWorker(
                this,
            )
        } else {
            // Cancel worker
            workManager.cancelUniqueWork("app_monitoring_work")
        }
    }

    private fun handleAppLimitRequest(appLimit: AppLimit) {
        Log.d("FCM", appLimit.toString())
        //kiem tra quyen
        if (!Settings.canDrawOverlays(this)) {
            // Nếu chưa có quyền, gửi notification yêu cầu người dùng cấp quyền
            showOverlayPermissionNotification()
            return
        }
        scope.launch{
            try {
                // Lưu vào database trước
                appLimitManager.saveAppLimit(appLimit)
                Log.d("FCM", "Saved app limit to database")

                // Verify database save
                val savedLimits = appLimitManager.getAppLimits()
                Log.d("FCM", "Current app limits in database: $savedLimits")

                // 2. Kiểm tra service có đang chạy không
                val serviceIntent = Intent(this@ChildFirebaseMessagingService, AppLimitService::class.java)
                if (!isForegroundServiceRunning()) {
                    // Nếu chưa chạy, start mới
                    serviceIntent.action = "START_MONITORING"
                    ContextCompat.startForegroundService(this@ChildFirebaseMessagingService,serviceIntent)
                    Log.d("FCM-Start","Start thành công rồi")
                } else {
                    // Nếu đang chạy, chỉ cần update notification
                    serviceIntent.action = "UPDATE_NOTIFICATION"
                    startService(serviceIntent)
                }

                // Start worker
                manageAppLimitWorker(true)
                Log.d("FCM", "Started app limit worker")
            } catch (e: Exception) {
                Log.e("FCM", "Error handling app limit request: ${e.message}", e)
            }
        }


    }

    private fun isForegroundServiceRunning(): Boolean {
        return ServiceUtils.isServiceRunning(
            this@ChildFirebaseMessagingService,
            AppLimitService::class.java,
            checkForeground = true
        )
    }

    private fun showOverlayPermissionNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "app_limit_channel",
                "App Limit Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo về quyền ứng dụng"
                setShowBadge(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        // Tạo intent để mở cài đặt quyền hiển thị trên ứng dụng khác
        val notificationIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, "app_limit_channel")
            .setContentTitle("Cần cấp quyền")
            .setContentText("Nhấn để cấp quyền hiển thị trên ứng dụng khác")
            .setSmallIcon(R.drawable.notification_icon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(15, notification)
    }

    private fun sendCommandToAudioService(command: String, parentId: String) {
        try {
            val intent = Intent("AUDIO_SERVICE_COMMAND")
            intent.setPackage(packageName)  // Add this line
            intent.putExtra("command", command)
            intent.putExtra("parentId", parentId)
            Log.d("FCM_DEBUG", "7. Broadcasting command: $command with deviceId: $parentId")
            sendBroadcast(intent)
            Log.d("FCM_DEBUG", "8. Broadcast sent successfully")
        } catch (e: Exception) {
            Log.e("FCM_DEBUG", "Error sending broadcast: ${e.message}")
            e.printStackTrace()
        }
    }


    private fun scheduleTaskReminder(taskName: String, taskTime: String) {
        val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, TaskReminderReceiver::class.java).apply {
            putExtra("taskName", taskName)
            putExtra("taskTime", taskTime)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            taskName.hashCode(), // Sử dụng hashCode của taskName làm requestCode
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d("task", taskTime)

        val taskDateTime = outputFormat.parse(taskTime)?.time ?: return
        val reminderTime = taskDateTime - (5 * 60 * 1000) // 5 phút trước thời gian nhiệm vụ

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime, pendingIntent)
    }

    private fun showTaskNotification(taskName: String, taskTime: String) {
        Log.d("ShowTask","$taskTime")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "task_notification_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Task Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("New Task Assigned")
            .setContentText("Task: $taskName at $taskTime")
            .setSmallIcon(R.drawable.baseline_add_task_24)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }


}