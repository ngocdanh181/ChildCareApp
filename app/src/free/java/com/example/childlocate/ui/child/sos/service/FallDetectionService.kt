package com.example.childlocate.ui.child.sos.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.childlocate.R
import com.example.childlocate.ui.child.main.MainChildActivity
import com.example.childlocate.utils.LowPassFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.sqrt

class FallDetectionService : Service(), SensorEventListener {
    companion object {
        private const val TAG = "SOSWidget12345FallDetectionService"
        private const val CHANNEL_ID = "FallDetectionChannel"
        private const val NOTIFICATION_ID = 1001

        // Các ngưỡng cho trẻ em 5-8 tuổi - điều chỉnh để chính xác hơn
        private const val IMPACT_THRESHOLD = 50.0f // ~2.5g - ngưỡng cao hơn để tránh cảnh báo sai
        private const val STABLE_DURATION = 3000L // 2 giây - thời gian ổn định sau khi té ngã
        private const val GYRO_ROTATION_THRESHOLD = 12.0f // rad/s - cần có sự xoay đáng kể
        private const val FALL_PATTERN_WINDOW = 500L // Cửa sổ thời gian để phát hiện mẫu té ngã (0.5 giây)

        // Ngưỡng xác định ổn định
        private const val STABILITY_THRESHOLD = 2.0f // sai số so với trọng lực
    }

    // Đối tượng quản lý cảm biến
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // Dữ liệu cảm biến
    private val accelerometerValues = FloatArray(3)
    private val gyroscopeValues = FloatArray(3)
    private var filteredAccValues = FloatArray(3)
    private var filteredGyroValues = FloatArray(3)

    // Bộ lọc nhiễu
    private lateinit var accelFilter: LowPassFilter
    private lateinit var gyroFilter: LowPassFilter

    // Các biến trạng thái
    private var fallDetectionInProgress = false
    private var fallDetectionStartTime = 0L
    private var significantRotationDetected = false
    private var significantImpactDetected = false
    private var isMonitoring = false
    private var alertInProgress = false
    private var fallConditionsMet = false

    // Lưu trữ giá trị gia tốc và quay gần nhất để phân tích mẫu
    private val recentAccelerationValues = CopyOnWriteArrayList<Float>()
    private val recentGyroValues = CopyOnWriteArrayList<Float>()
    private val historyTimeWindow = 1000L // 1 giây lịch sử

    // Handler để xử lý thời gian
    //private val handler = Handler(Looper.getMainLooper())

    //su dung coroutine scope va job
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)



    // Runnable để kiểm tra định kỳ dữ liệu của cảm biến
    /*private val sensorDataChecker = object : Runnable {
        override fun run() {
            // Làm sạch dữ liệu lịch sử quá cũ
            cleanupSensorHistory()

            // Lên lịch cho lần chạy tiếp theo
            handler.postDelayed(this, 1000)
        }
    }*/

    override fun onCreate() {
        super.onCreate()

        // Khởi tạo bộ lọc nhiễu
        accelFilter = LowPassFilter(0.2f)
        gyroFilter = LowPassFilter(0.3f)

        // Khởi tạo quản lý cảm biến
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        createNotificationChannel()

        // Bắt đầu kiểm tra định kỳ
        //handler.post(sensorDataChecker)
        startPeriodicDataCleanup()

        Log.d(TAG, "Fall detection service created")
    }

    private fun startPeriodicDataCleanup() {
        serviceScope.launch {
            while(isActive){
                // Làm sạch dữ liệu lịch sử quá cũ
                cleanupSensorHistory()

                // Chờ 1 giây trước khi kiểm tra lại
               delay(1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Khởi tạo dịch vụ trên nền với notification
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification("Đang theo dõi trẻ", "Ứng dụng đang hoạt động"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        } else {
            startForeground(NOTIFICATION_ID, createNotification("Đang theo dõi trẻ", "Ứng dụng đang hoạt động"))

        }

        // Đăng ký cảm biến với tần số lấy mẫu cao hơn để phát hiện chính xác
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        isMonitoring = true
        Log.d(TAG, "Fall detection service started")

        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isMonitoring || alertInProgress) {
            return
        }

        val timestamp = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Áp dụng bộ lọc nhiễu cho dữ liệu gia tốc
                System.arraycopy(event.values, 0, accelerometerValues, 0, 3)
                filteredAccValues = accelFilter.filter(accelerometerValues)

                // Tính toán chuẩn L2 của gia tốc
                val accelerationMagnitude = calculateMagnitude(filteredAccValues)

                // Lưu giá trị vào lịch sử
                recentAccelerationValues.add(accelerationMagnitude)

                // Log giá trị gia tốc lớn để debug
                if (accelerationMagnitude > 12) {
                    Log.d(TAG, "High acceleration detected: $accelerationMagnitude m/s²")
                }


                // Xử lý phát hiện té ngã dựa trên mẫu gia tốc và xoay
                processAccelerationForFallDetection(accelerationMagnitude, timestamp)
            }

            Sensor.TYPE_GYROSCOPE -> {
                // Áp dụng bộ lọc nhiễu cho dữ liệu con quay hồi chuyển
                System.arraycopy(event.values, 0, gyroscopeValues, 0, 3)
                filteredGyroValues = gyroFilter.filter(gyroscopeValues)

                // Tính toán chuẩn L2 của vận tốc góc
                val rotationMagnitude = calculateMagnitude(filteredGyroValues)

                // Lưu giá trị vào lịch sử
                recentGyroValues.add(rotationMagnitude)

                // Log giá trị góc quay lớn để debug
                if (rotationMagnitude > 10f) {
                    Log.d(TAG, "High rotation detected: $rotationMagnitude rad/s")
                }

                // Xử lý phát hiện sự xoay cho té ngã
                processGyroscopeForFallDetection(rotationMagnitude, timestamp)
            }
        }

        // Kiểm tra nếu cả gia tốc và xoay đều được phát hiện trong cùng một cửa sổ thời gian
        checkForFallPattern(timestamp)
    }

    // Làm sạch dữ liệu lịch sử cũ
    private fun cleanupSensorHistory() {
        if (recentAccelerationValues.size > 100) { // Giới hạn kích thước cho hiệu suất
            val keepSize = 50 // Giữ lại 50 giá trị mới nhất
            val removeCount = recentAccelerationValues.size - keepSize
            repeat(removeCount) {
                if (recentAccelerationValues.isNotEmpty()) {
                    recentAccelerationValues.removeAt(0)
                }
            }
        }

        if (recentGyroValues.size > 100) {
            val keepSize = 50
            val removeCount = recentGyroValues.size - keepSize
            repeat(removeCount) {
                if (recentGyroValues.isNotEmpty()) {
                    recentGyroValues.removeAt(0)
                }
            }
        }
    }

    // Xử lý dữ liệu gia tốc cho phát hiện té ngã
    private fun processAccelerationForFallDetection(accelerationMagnitude: Float, timestamp: Long) {
        // Phát hiện va chạm mạnh - bước đầu tiên của mẫu té ngã
        if (accelerationMagnitude > IMPACT_THRESHOLD && !fallDetectionInProgress) {
            fallDetectionInProgress = true
            fallDetectionStartTime = timestamp
            significantImpactDetected = true

            Log.d(TAG, "Fall detection - STEP 1: Strong impact detected (${accelerationMagnitude} m/s²)")

            // Đặt thời gian chờ để xác nhận mẫu té ngã hoàn chỉnh

            serviceScope.launch {
                delay(FALL_PATTERN_WINDOW + STABLE_DURATION)
                if (fallDetectionInProgress && !fallConditionsMet) {
                    // Kiểm tra nếu mẫu té ngã không hoàn thành
                    resetFallDetectionState()
                    Log.d(TAG, "Fall detection canceled - incomplete fall pattern")
                }
            }
        }
    }

    // Xử lý dữ liệu con quay hồi chuyển cho phát hiện té ngã
    private fun processGyroscopeForFallDetection(rotationMagnitude: Float, timestamp: Long) {
        // Chỉ xét xoay nếu đang trong quá trình phát hiện té ngã
        if (fallDetectionInProgress && !significantRotationDetected &&
            timestamp - fallDetectionStartTime < FALL_PATTERN_WINDOW) {

            if (rotationMagnitude > GYRO_ROTATION_THRESHOLD) {
                significantRotationDetected = true
                Log.d(TAG, "Fall detection - STEP 2: Significant rotation detected (${rotationMagnitude} rad/s)")
            }
        }
    }

    // Kiểm tra mẫu té ngã hoàn chỉnh
    private fun checkForFallPattern(timestamp: Long) {
        if (!fallDetectionInProgress || alertInProgress) {
            return
        }

        // Cả hai điều kiện: va chạm và xoay đã được phát hiện
        if (significantImpactDetected && significantRotationDetected) {
            val timeElapsed = timestamp - fallDetectionStartTime
            fallConditionsMet = true
            // Kiểm tra ổn định chỉ sau khi đủ thời gian - hiện tại đang kiểm tra cả khi chưa đủ thời gian
            if (timeElapsed > FALL_PATTERN_WINDOW + STABLE_DURATION) {
                if (checkDeviceStability()) {
                    triggerAlert()
                } else {
                    resetFallDetectionState()
                }
            }
        }


    }

    // Kiểm tra tính ổn định của thiết bị (thường là nằm im sau khi té)
    /*private fun checkDeviceStability(): Boolean {
        synchronized(recentAccelerationValues) {
            if (recentAccelerationValues.isEmpty()) {
                Log.d(TAG, "No recent acceleration data for stability check")
                return false
            }

            // Lấy trung bình của 5 giá trị gần nhất
            val recentValues = recentAccelerationValues.takeLast(minOf(5, recentAccelerationValues.size))
            val averageAcc = recentValues.average().toFloat()

            // Tính độ lệch chuẩn để đánh giá độ ổn định tốt hơn
            val variance = recentValues.map { (it - averageAcc) * (it - averageAcc) }.average().toFloat()
            val stdDev = sqrt(variance)

            // Kiểm tra xem thiết bị có ổn định không (gần với trọng lực ~9.8 m/s²)
            val isStable = kotlin.math.abs(averageAcc - 9.8f) < STABILITY_THRESHOLD && stdDev < 1.0f

            Log.d(TAG, "Stability check: Avg=$averageAcc, StdDev=$stdDev, isStable=$isStable")

            return isStable
        }
    }*/

    private fun checkDeviceStability(): Boolean {
        if (recentAccelerationValues.isEmpty()) return false

        // Lấy các giá trị gần nhất
        val recentValues = recentAccelerationValues.takeLast(10)

        // Kiểm tra sự dao động - độ ổn định là khi các giá trị gần nhau
        val maxChange = recentValues.maxOrNull()!! - recentValues.minOrNull()!!
        val isConsistent = maxChange < 8.0f // Cho phép thay đổi trong khoảng 8 m/s²

        Log.d(TAG, "Stability check: MaxChange=$maxChange, isStable=$isConsistent")

        return isConsistent
    }

    // Reset trạng thái phát hiện té ngã
    private fun resetFallDetectionState() {
        fallDetectionInProgress = false
        significantImpactDetected = false
        significantRotationDetected = false
        fallConditionsMet = false
    }

    // Kích hoạt cảnh báo
    private fun triggerAlert() {
        Log.d(TAG, "ALERT TRIGGERED: Fall detected and confirmed!")
        alertInProgress = true
        resetFallDetectionState()

        // Rung thiết bị
        vibrate()

        // Cập nhật thông báo
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            NOTIFICATION_ID,
            createNotification("Phát hiện té ngã!", "Hãy kiểm tra trẻ ngay!")
        )

        // Gửi yêu cầu đến dịch vụ SOS
        SOSService.startSendSOS(this)

        Log.d(TAG, "SOS request sent from fall detection service")

        // Reset trạng thái sau 10 giây
        serviceScope.launch {
            delay(10000)
            resetAlertState()
        }
    }

    // Reset trạng thái cảnh báo
    private fun resetAlertState() {
        alertInProgress = false

        // Cập nhật thông báo về trạng thái bình thường
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            NOTIFICATION_ID,
            createNotification("Đang theo dõi trẻ", "Ứng dụng đang hoạt động")
        )

        Log.d(TAG, "Alert state reset, continuing monitoring")
    }

    // Tính chuẩn L2 của vector
    private fun calculateMagnitude(vector: FloatArray): Float {
        return sqrt(
            vector[0] * vector[0] +
                    vector[1] * vector[1] +
                    vector[2] * vector[2]
        )
    }

    // Rung thiết bị
    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator

            if (vibrator.hasVibrator()) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 500, 500, 500, 500),
                        intArrayOf(0, 255, 0, 255, 0, 255),
                        -1
                    )
                )
            }
        } else {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 500, 500, 500, 500, 500),
                            intArrayOf(0, 255, 0, 255, 0, 255),
                            -1
                        )
                    )
                } else {
                    // Mô hình rung cho phiên bản cũ
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 500, 500, 500, 500, 500), -1)
                }
            }
        }
    }

    // Tạo kênh thông báo (yêu cầu cho Android 8.0+)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kênh theo dõi té ngã",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo từ ứng dụng theo dõi té ngã"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Tạo thông báo cho Foreground Service
    private fun createNotification(title: String, text: String): Notification {
        val notificationIntent = Intent(this, MainChildActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Không cần xử lý
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()

        // Hủy đăng ký cảm biến
        sensorManager.unregisterListener(this)

        // Dừng tất cả các handler
        serviceJob.cancel()

        isMonitoring = false
        Log.d(TAG, "Fall detection service stopped")
    }
}