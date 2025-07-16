package com.example.childlocate.service

import android.content.Context
import android.util.Log
import com.example.childlocate.data.model.AppLimit
import com.example.childlocate.data.model.AppLimitDatabase
import com.example.childlocate.data.model.AppLimitEntity
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AppLimitManager(context: Context) {
    private val database = AppLimitDatabase.getInstance(context)
    private val database1 = FirebaseDatabase.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO+ SupervisorJob())
    //ham load pin
    suspend fun getAppPin(childId: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = database1.getReference("app_pins")
                    .child(childId)
                    .get()
                    .await()
                snapshot.getValue(String::class.java) ?: ""
            } catch (e: Exception) {
                Log.e("AppLimitManager", "Error getting PIN: ${e.message}")
                ""
            }
        }
    }

    //Ham them AppLimit
    suspend fun saveAppLimit(appLimit: AppLimit) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("AppLimitManager", "Converting AppLimit to Entity: $appLimit")
                val entity = AppLimitEntity(
                    packageName = appLimit.packageName,
                    dailyLimitMinutes = appLimit.dailyLimitMinutes,
                    startTime = appLimit.startTime,
                    endTime = appLimit.endTime
                )
                Log.d("AppLimitManager", "Inserting entity into database: $entity")
                database.appLimitDao().insertAppLimit(entity)

                // Verify immediately after insert
                val saved = database.appLimitDao().getAllAppLimits()
                Log.d("AppLimitManager", "Verification - Database contents after save: $saved")
            } catch (e: Exception) {
                Log.e("AppLimitManager", "Error saving app limit", e)
                throw e
            }
        }
    }

    //Ham xoa AppLimit
    fun removeAppLimit(packageName: String) {
        scope.launch {
            database.appLimitDao().deleteAppLimit(packageName)
        }
    }
    //Ham lay tat ca danh sach
    suspend fun getAppLimits(): List<AppLimit> {
        return withContext(Dispatchers.IO) {
            database.appLimitDao().getAllAppLimits().map { it.toAppLimit() }
        }
    }

    private fun AppLimitEntity.toAppLimit() = AppLimit(
        packageName = packageName,
        dailyLimitMinutes = dailyLimitMinutes,
        startTime = startTime,
        endTime = endTime
    )
}
