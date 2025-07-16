// AudioStreamingForegroundService.kt
package com.example.childlocate.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.childlocate.R
import com.example.childlocate.utils.AudioConstants
import com.example.childlocate.utils.AudioConstants.NOTIFICATION_ID
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch


// AudioStreamingService.kt
class AudioStreamingForegroundService : Service() {
    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private val database = FirebaseDatabase.getInstance()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var childId: String

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob()) // <-- CoroutineScope cho Service
    private var recordingJob: Job? = null

    private var recordingThread: Thread? = null
    private var isReceiverRegistered = false
    private var bufferSize = AudioRecord.getMinBufferSize(
        AudioConstants.SAMPLE_RATE,
        AudioConstants.CHANNEL_CONFIG,
        AudioConstants.AUDIO_FORMAT
    )

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val command = intent?.getStringExtra("command")
            val parentId = intent?.getStringExtra("parentId")

            when (command) {
                "START_RECORDING" -> {
                    if (parentId != null) {
                        Log.d("SERVICE_DEBUG", "3. Starting recording for device: $parentId")
                        startStreaming(parentId)
                    } else {
                        Log.e("SERVICE_DEBUG", "Device ID is null")
                    }
                }
                "STOP_RECORDING" -> {
                    if (parentId != null) {
                        stopStreamingOnly(parentId)
                    }
                }
            }
        }

    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()

            // Kiểm tra quyền FOREGROUND_SERVICE_MICROPHONE cho Android 14+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e("SERVICE_DEBUG", "Missing FOREGROUND_SERVICE_MICROPHONE permission")
                    stopSelf()
                    return
                }
            }

            // Khởi động foreground service với notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }

            sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
            childId = sharedPreferences.getString("childId", null).toString()
            // Đăng ký BroadcastReceiver
            registerBroadcastReceiver()
            Log.d("SERVICE_DEBUG", "6. Service successfully created")
        } catch (e: Exception) {
            Log.e("SERVICE_DEBUG", "Error in onCreate: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun registerBroadcastReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter("AUDIO_SERVICE_COMMAND")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(broadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(broadcastReceiver, filter)
            }
            isReceiverRegistered = true
            Log.d("SERVICE_DEBUG", "5. BroadcastReceiver registered")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AudioConstants.CHANNEL_ID,
                "Audio Streaming Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for streaming audio"
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, com.example.childlocate.ui.child.main.MainChildActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            26,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Action để tắt service
        val stopIntent = Intent(this, AudioStreamingForegroundService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            27,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AudioConstants.CHANNEL_ID)
            .setContentTitle("🎤 Ghi âm từ xa")
            .setContentText("Sẵn sàng nhận lệnh từ phụ huynh")
            .setSmallIcon(R.drawable.baseline_mic_24)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.baseline_stop_24, "Tắt", stopPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Dịch vụ đang chạy để nhận lệnh ghi âm từ phụ huynh. Nhấn để mở ứng dụng hoặc tắt dịch vụ."))
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP_SERVICE" -> {
                Log.d("SERVICE_DEBUG", "Stop service requested from notification")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun startStreaming(parentId: String) {
        if (isRecording) return

        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AudioConstants.SAMPLE_RATE,
                AudioConstants.CHANNEL_CONFIG,
                AudioConstants.AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioStreaming", "Failed to initialize AudioRecord")
                return
            }

            isRecording = true
            audioRecord?.startRecording()

            //recordingThread = Thread {
            //    streamAudio(childId)
            //}.apply {
            //    start()
            //}
            recordingJob = serviceScope.launch {
                createAudioStream()
                    .catch { e ->
                        Log.e("AudioStreaming", "Stream error: ${e.message}")
                    }
                    .collect { chunk ->
                        sendAudioChunk(childId, chunk)
                    }
            }

            // Update status in Firebase
            updateStreamingStatus(parentId, true)

        } catch (e: Exception) {
            Log.e("AudioStreaming", "Error starting streaming: ${e.message}")
            stopSelf()
        }
    }
    private fun createAudioStream() = callbackFlow {
        val buffer = ByteArray(AudioConstants.CHUNK_SIZE)

        val recordingThread = Thread {
            try {
                while (isRecording) {  // Thêm check isClosedForSend
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readSize > 0) {
                        val encodedChunk = Base64.encodeToString(buffer, Base64.DEFAULT)
                        trySend(encodedChunk)
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioStreaming", "Recording thread error: ${e.message}")
                close(e)
            }
        }.apply {
            name = "AudioRecordingThread"  // Đặt tên cho dễ debug
        }

        recordingThread.start()

        awaitClose {
            Log.d("AudioStreaming", "Stopping audio stream...")
            isRecording = false
            recordingThread.interrupt()  // Thêm interrupt
            recordingThread.join(1000)
            Log.d("AudioStreaming", "Audio stream stopped")
        }
    }.flowOn(Dispatchers.IO)  // Data processing trên IO


    private fun sendAudioChunk(childId: String, chunk: String) {
        val timestamp = System.currentTimeMillis()
        val chunkRef = database.reference
            .child("audio_streams")
            .child(childId)
            .child(timestamp.toString())

        chunkRef.setValue(chunk)
            .addOnFailureListener { e ->
                Log.e("AudioStreaming", "Failed to send chunk: ${e.message}")
            }

        // Clean up old chunks after 5 seconds
        serviceScope.launch(Dispatchers.IO) {
            delay(5000)
            chunkRef.removeValue()
                .addOnFailureListener { e ->
                    Log.e("AudioStreaming", "Failed to remove chunk: ${e.message}")
                }
        }
        //handler.postDelayed({
        //    chunkRef.removeValue()
        //}, 5000)
    }

    private fun updateStreamingStatus(parentId: String, isStreaming: Boolean) {
        val updates = hashMapOf<String, Any>(
            "requestedBy" to parentId,
            "isStreaming" to isStreaming
        )

        database.reference
            .child("streaming_status")
            .child(childId)
            .updateChildren(updates)
    }

    private fun stopStreamingOnly(parentId: String) {
        if (!isRecording) return

        isRecording = false
        cleanupRecording()
        updateStreamingStatus(parentId, false)
    }

    private fun cleanupRecording() {
        try {
            //recordingThread?.join(1000)
            recordingJob?.cancel() //
            audioRecord?.apply {
                stop()
                release()
            }
            audioRecord = null
        } catch (e: Exception) {
            Log.e("AudioStreaming", "Error cleaning up recording: ${e.message}")
        }
    }

    override fun onDestroy() {
        try {
            // Hủy đăng ký BroadcastReceiver
            if (isReceiverRegistered) {
                unregisterReceiver(broadcastReceiver)
                isReceiverRegistered = false
            }

            // Cleanup các resource khác
            //handler.removeCallbacksAndMessages(null)
            cleanupRecording()
            serviceScope.cancel()
            super.onDestroy()
        } catch (e: Exception) {
            Log.e("SERVICE_DEBUG", "Error in onDestroy: ${e.message}")
        }
    }


    override fun onBind(intent: Intent?) = null
}