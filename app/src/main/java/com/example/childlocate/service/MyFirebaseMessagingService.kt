package com.example.childlocate.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.childlocate.MapActivity
import com.example.childlocate.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage


abstract class MyFirebaseMessagingService : FirebaseMessagingService() {

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM", "Received message from FCM: ${remoteMessage.data}")

        // Handle notification messages (for chat when app is foreground)
        remoteMessage.notification?.let { notification ->
            handleChatNotification(notification)
        }
        // Handle data messages for child and parent
        if (remoteMessage.data.isNotEmpty()) {
            handleDataMessage(remoteMessage)
        }

    }

    protected abstract fun handleDataMessage(remoteMessage: RemoteMessage) // This method should be implemented in subclasses to handle specific data messages

    private fun handleChatNotification(notification: RemoteMessage.Notification) {
        // Only show notification if app is in foreground
        // (Background notifications are handled automatically by system)
        showChatNotification(
            notification.title ?: "Tin nhắn mới",
            notification.body ?: "Bạn có tin nhắn mới"
        )
    }

    @SuppressLint("MissingPermission")
    private fun showChatNotification(title: String, body: String) {
        val channelId = "chat_notification_channel"
        createNotificationChannel(channelId)

        val notification = createNotification(channelId, title, body)

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotification(channelId: String, title: String, body: String): Notification {
        // Create intent to open MainActivity when notification is clicked
        val notificationIntent = Intent(this, MapActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.notification_icon)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun createNotificationChannel(channelId: String){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chat Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
