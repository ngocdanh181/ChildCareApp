package com.example.childlocate.service

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

// UsageTrackingManager.kt
class UsageTrackingManager(context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun requestImmediateSync() {
        val immediateRequest = OneTimeWorkRequestBuilder<UsageStatsWorker>()
            .build()
        workManager.enqueue(immediateRequest)
    }

}