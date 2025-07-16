package com.example.childlocate.service

// UsageStatsWorker.kt
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class UsageStatsWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val sharedPreferences = applicationContext.getSharedPreferences("MyPrefs",Context.MODE_PRIVATE)
            val isFirstRun = sharedPreferences.getBoolean("isFirstRun", true)
            val lastSyncTime = sharedPreferences.getLong("lastSyncTime", 0L)
            
            // Log để debug
            Log.d("UsageStats", "Worker started - isFirstRun: $isFirstRun, lastSyncTime: $lastSyncTime")
            
            if (isFirstRun){
                // Lần đầu chạy - thu thập dữ liệu của 7 ngày gần nhất
                Log.d("UsageStats", "First run - collecting historical data")
                collectHistoricalData()
                
                // Đảm bảo save thành công
                val editor = sharedPreferences.edit()
                editor.putBoolean("isFirstRun", false)
                editor.putLong("lastSyncTime", System.currentTimeMillis())
                val saveSuccess = editor.commit() // Dùng commit() để đảm bảo save ngay
                
                Log.d("UsageStats", "First run completed - save success: $saveSuccess")
            }else{
                // Chạy hàng ngày - chỉ thu thập dữ liệu từ lần cuối đồng bộ
                Log.d("UsageStats", "Regular run - collecting data ")
                collectDataSinceLastSync(lastSyncTime)
                
                // Cập nhật thời gian đồng bộ cuối
                val editor = sharedPreferences.edit()
                editor.putLong("lastSyncTime", System.currentTimeMillis())
                val saveSuccess = editor.commit() // Dùng commit() để đảm bảo save ngay
                
                Log.d("UsageStats", "Regular run completed - save success: $saveSuccess")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("UsageStats", "Worker failed", e)
            Result.retry()
        }
    }
    private fun collectHistoricalData(){
        // Thu thập dữ liệu 7 ngày gần nhất
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis

        // Lùi 7 ngày
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val startTime = calendar.timeInMillis

        collectAndUploadUsageStats(startTime, endTime)
    }

    private fun collectDataSinceLastSync(lastSyncTime : Long){
        // Thu thập dữ liệu từ thời điểm đồng bộ cuối cùng đến hiện tại
        val currentTime = System.currentTimeMillis()

        // DEFENSIVE PROGRAMMING: Tránh lỗi 1970
        // Nếu lastSyncTime = 0 hoặc quá cũ (>7 ngày), chỉ lấy 7 ngày gần nhất
        val maxAllowedAge = 7 * 24 * 60 * 60 * 1000L // 7 ngày
        
        val safeStartTime = if (lastSyncTime == 0L || 
                                lastSyncTime < 0L || 
                                currentTime - lastSyncTime > maxAllowedAge) {
            
            Log.w("UsageStats", "lastSyncTime invalid ($lastSyncTime) or too old, using 7 days ago")
            
            // Lấy 7 ngày gần nhất thay vì từ lastSyncTime không hợp lệ
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -7)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
            
        } else if (currentTime - lastSyncTime > 24 * 60 * 60 * 1000) {
            // Nếu thời gian từ lần đồng bộ cuối > 24h, có thể thiết bị đã tắt
            // Thu thập thêm 1 ngày để đảm bảo an toàn
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = lastSyncTime
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            // Set về 00:00:00 của ngày đó
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        } else {
            // Set lastSyncTime về đầu ngày để đảm bảo không miss data
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = lastSyncTime
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        }

        Log.d("UsageStats", "lastSyncTime: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastSyncTime))}")
        Log.d("UsageStats", "safeStartTime: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(safeStartTime))}")

        collectAndUploadUsageStats(safeStartTime, currentTime)
    }

    private fun collectAndUploadUsageStats(startTime: Long, endTime: Long) {
        val usageStatsManager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val sharedPreferences = applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val childId = sharedPreferences.getString("childId", null) ?: return

        // Log thời gian bắt đầu và kết thúc
        Log.d("UsageStats", "Start time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(startTime))}")
        Log.d("UsageStats", "End time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(endTime))}")

        // Lặp từ startTime đến endTime theo từng ngày
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = startTime
        
        // Set về 00:00:00 của ngày bắt đầu
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        while (calendar.timeInMillis < endTime) {
            val dayStart = calendar.timeInMillis
            
            // Chuyển sang ngày tiếp theo
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = calendar.timeInMillis.coerceAtMost(endTime)
            
            val dayStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                dayStart,
                dayEnd
            )

            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Date(dayStart))

            Log.d("UsageStats", "Processing day: $dateStr, apps found: ${dayStats.size}")
            uploadDayStats(childId, dateStr, dayStats)
        }
    }


    private fun uploadDayStats(childId: String, dateStr: String, dayStats: List<UsageStats>) {
        // VALIDATION: Đảm bảo không bao giờ tạo data trước 2020
        if (dateStr.startsWith("19") || dateStr.startsWith("200") || dateStr.startsWith("201")) {
            Log.e("UsageStats", "INVALID DATE DETECTED: $dateStr - Skipping upload to prevent 1970 issue")
            return
        }
        
        val usageData = mutableMapOf<String, Any>()
        var totalTime = 0L

        dayStats
        .filter { it.totalTimeInForeground > 0 }
        .groupBy { it.packageName } // Group by package name để tổng hợp thời gian cho mỗi app
        .forEach { (packageName, stats) ->
            val totalAppTime = stats.sumOf { it.totalTimeInForeground }
            val lastTimeUsed = stats.maxOf { it.lastTimeUsed }
            val firstTimeUsed = stats.minOf { it.firstTimeStamp }

            val safePackageName = packageName.replace(".", "_")
            val appData = mapOf(
                "package_name" to packageName,
                "app_name" to getAppName(packageName),
                "usage_time" to totalAppTime,
                "last_time_used" to lastTimeUsed,
                "first_time_used" to firstTimeUsed
            )
            usageData[safePackageName] = appData
            totalTime += totalAppTime
        }

        val dayStatsData = mapOf(
            "date" to dateStr,
            "total_time" to totalTime,
            "apps" to usageData,
            "timestamp" to System.currentTimeMillis()
        )

        Log.d("UsageStats", "Uploading data for date: $dateStr, total apps: ${usageData.size}, total time: ${totalTime}ms")

        FirebaseDatabase.getInstance()
            .getReference("usage_stats")
            .child(childId)
            .child(dateStr)
            .setValue(dayStatsData)
            .addOnSuccessListener {
                Log.d("UsageStats", "Successfully uploaded data for $dateStr")
            }
            .addOnFailureListener { e ->
                Log.e("UsageStats", "Failed to upload data for $dateStr", e)
                throw e
            }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = applicationContext.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}