package com.example.childlocate.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.childlocate.utils.ServiceUtils
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class AppLimitWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    private val appLimitManager = AppLimitManager(context)

    override fun doWork(): Result {
        Log.d("AppLimitWorker", "Worker running check")
        val prefs = context.getSharedPreferences("service_state", Context.MODE_PRIVATE)

        if (prefs.getBoolean("is_app_limit_running", false)) {
            // Kiểm tra service có đang chạy không bằng foreground service notification
            if (!isForegroundServiceRunning()) {
                Log.d("AppLimitWorker", "Service not running, restarting")
                runBlocking {
                    try{
                        // Lấy tất cả app limits từ database
                        val appLimits = appLimitManager.getAppLimits()

                        if (appLimits.isNotEmpty()) {
                            // Khởi động lại service với app limits từ database
                            for (appLimit in appLimits) {
                                val intent = Intent(context, AppLimitService::class.java).apply {
                                    action = "START_MONITORING"
                                    putExtra("package_name", appLimit.packageName)
                                    putExtra("daily_limit", appLimit.dailyLimitMinutes)
                                    putExtra("start_time", appLimit.startTime)
                                    putExtra("end_time", appLimit.endTime)
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            }

                            // Đảm bảo ServiceRestartReceiver được setup
                            val restartIntent = Intent(context, ServiceRestartReceiver::class.java).apply {
                                action = "RESTART_APP_LIMIT_SERVICE"
                            }
                            context.sendBroadcast(restartIntent)
                        } else {

                        }
                    }catch(e:Exception){
                        Log.e("AppLimitWorker", "Error restarting service: ${e.message}")
                        return@runBlocking Result.failure()
                    }
                }
            }
        }

        return Result.success()
    }

    private fun isForegroundServiceRunning(): Boolean {
        return ServiceUtils.isServiceRunning(
            context,
            AppLimitService::class.java,
            checkForeground = true
        )
    }
}

object AppLimitWorkerManager {
    private const val WORK_NAME = "app_monitoring_work"
    private const val DEFAULT_INTERVAL = 10L // minutes

    fun scheduleWorker(
        context: Context,
    ) {

        val workRequest = PeriodicWorkRequestBuilder<AppLimitWorker>(
            DEFAULT_INTERVAL, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // Luôn update worker hiện tại
            workRequest
        )

    }

    fun cancelWorker(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.d("AppLimitWorker", "Cancelled worker")
    }
}