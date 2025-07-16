package com.example.childlocate.repository

import android.content.Context
import android.util.Log
import com.example.childlocate.data.api.RetrofitInstance
import com.example.childlocate.data.model.AppLimit
import com.example.childlocate.data.model.AppUsageInfo
import com.example.childlocate.data.model.Data
import com.example.childlocate.data.model.DayUsageStats
import com.example.childlocate.data.model.FcmRequest
import com.example.childlocate.data.model.Message
import com.example.childlocate.data.model.UsageStatsState
import com.example.childlocate.data.model.WeeklyUsageStats
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UsageStatsRepository(private val context: Context) {

    private val database = FirebaseDatabase.getInstance()

    suspend fun requestUsageUpdate(childId:String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val token = getToken(childId) ?: return@withContext false
                Log.d("UsageStatsRepository", token)
                val serviceAccount: InputStream = context.assets.open("childlocatedemo.json")
                val credentials = GoogleCredentials.fromStream(serviceAccount)
                    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

                val accessToken = credentials.refreshAccessToken().tokenValue
                val authHeader = "Bearer $accessToken"

                val request = FcmRequest(
                    message = Message(
                        token = token,
                        data = Data(request_type = "usage_stats_request")
                    )
                )

                val response = RetrofitInstance.api.sendLocationRequest(authHeader, request)
                return@withContext response.isSuccessful
            } catch (e: IOException) {
                Log.d("FCM", "Error: ${e.message}")
                return@withContext false
            }
        }
    }


    suspend fun getWeeklyUsageStats(childId: String, startDate: String): UsageStatsState {
        return withContext(Dispatchers.IO) {
            try {
                val calendar = Calendar.getInstance()

                val snapshot = database.getReference("usage_stats")
                    .child(childId)
                    .get()
                    .await()

                val dailyStats = mutableMapOf<String, DayUsageStats>()
                // Ensure we have entries for all days of the week
                calendar.timeInMillis = startDate.toLong()
                for(i in 0..6){
                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(calendar.time)

                    val daySnapshot = snapshot.child(dateStr)
                    if (daySnapshot.exists()){
                        val totalTime = daySnapshot.child("total_time").getValue(Long::class.java) ?: 0L
                        val appsList = mutableListOf<AppUsageInfo>()

                        daySnapshot.child("apps").children.forEach { appSnapshot ->
                            appsList.add(
                                AppUsageInfo(
                                    packageName = appSnapshot.child("package_name").getValue(String::class.java) ?: "",
                                    appName = appSnapshot.child("app_name").getValue(String::class.java) ?: "",
                                    usageTime = appSnapshot.child("usage_time").getValue(Long::class.java) ?: 0L,
                                    lastTimeUsed = appSnapshot.child("last_time_used").getValue(Long::class.java) ?: 0L
                                )
                            )
                        }

                        dailyStats[dateStr] = DayUsageStats(
                            date = dateStr,
                            totalTime = totalTime,
                            appUsageList = appsList.sortedByDescending { it.usageTime }
                        )
                    } else {
                        // Add empty stats for days with no data
                        dailyStats[dateStr] = DayUsageStats(
                            date = dateStr,
                            totalTime = 0L,
                            appUsageList = emptyList()
                        )
                    }

                    calendar.add(Calendar.DAY_OF_YEAR, 1)


                }

                UsageStatsState.Success(WeeklyUsageStats(dailyStats))
            } catch (e: Exception) {
                UsageStatsState.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun getWeeklyUsageStatsFlow(childId: String, startDate: String): Flow<UsageStatsState> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val calendar = Calendar.getInstance()
                    val dailyStats = mutableMapOf<String, DayUsageStats>()
                    
                    // Ensure we have entries for all days of the week
                    calendar.timeInMillis = startDate.toLong()
                    for(i in 0..6){
                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(calendar.time)

                        val daySnapshot = snapshot.child(dateStr)
                        if (daySnapshot.exists()){
                            val totalTime = daySnapshot.child("total_time").getValue(Long::class.java) ?: 0L
                            val appsList = mutableListOf<AppUsageInfo>()

                            daySnapshot.child("apps").children.forEach { appSnapshot ->
                                appsList.add(
                                    AppUsageInfo(
                                        packageName = appSnapshot.child("package_name").getValue(String::class.java) ?: "",
                                        appName = appSnapshot.child("app_name").getValue(String::class.java) ?: "",
                                        usageTime = appSnapshot.child("usage_time").getValue(Long::class.java) ?: 0L,
                                        lastTimeUsed = appSnapshot.child("last_time_used").getValue(Long::class.java) ?: 0L
                                    )
                                )
                            }

                            dailyStats[dateStr] = DayUsageStats(
                                date = dateStr,
                                totalTime = totalTime,
                                appUsageList = appsList.sortedByDescending { it.usageTime }
                            )
                        } else {
                            // Add empty stats for days with no data
                            dailyStats[dateStr] = DayUsageStats(
                                date = dateStr,
                                totalTime = 0L,
                                appUsageList = emptyList()
                            )
                        }

                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                    }

                    trySend(UsageStatsState.Success(WeeklyUsageStats(dailyStats)))
                } catch (e: Exception) {
                    trySend(UsageStatsState.Error(e.message ?: "Unknown error"))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(UsageStatsState.Error(error.message))
            }
        }

        val ref = database.getReference("usage_stats").child(childId)
        ref.addValueEventListener(listener)

        awaitClose {
            ref.removeEventListener(listener)
        }
    }


    suspend fun saveAppPin(childId: String, pin: String) {
        withContext(Dispatchers.IO) {
            try {
                database.getReference("app_pins")
                    .child(childId)
                    .setValue(pin)
                    .await()
            } catch (e: Exception) {
                throw e
            }
        }
    }

    private suspend fun getToken(childId:String): String? {
        return withContext(Dispatchers.IO) {
            try {
                database.getReference("users").child(childId).child("primaryDeviceToken").get().await().getValue(String::class.java)

            } catch (e: Exception) {
                Log.e("FCM", "Failed to get FCM token: ${e.message}")
                null
            }
        }
    }

    //load list app limits
    suspend fun getAppLimits(childId: String): Map<String, AppLimit>{
        return withContext(Dispatchers.IO){
            try{
                val snapshot = database.getReference("app_limits")
                    .child(childId)
                    .get()
                    .await()
                val appLimits = mutableMapOf<String, AppLimit>()
                for(limitSnapshot in snapshot.children){
                    try{
                        val encodedPackageName = limitSnapshot.key ?: continue
                        val packageName = encodedPackageName.replace("_", ".")

                        val limit = AppLimit(
                            packageName = packageName,
                            dailyLimitMinutes = limitSnapshot.child("dailyLimitMinutes").getValue(Int::class.java) ?: 0,
                            startTime = limitSnapshot.child("startTime").getValue(String::class.java) ?: "08:00",
                            endTime = limitSnapshot.child("endTime").getValue(String::class.java) ?: "21:00",
                            isEnabled = limitSnapshot.child("isEnabled").getValue(Boolean::class.java) ?: false
                        )
                        if(limit.isEnabled){
                            appLimits[packageName] = limit
                        }
                    }catch (e: Exception) {
                        Log.e("UsageStatsRepository", "Error parsing app limit for  ${e.message}")
                        continue
                    }
                }
                appLimits
            }catch (e: Exception) {
                Log.e("UsageStatsRepository", "Error getting app limits: ${e.message}")
                emptyMap<String, AppLimit>()
            }
        }
    }

    fun getAppLimitsFlow(childId: String) : Flow<Map<String, AppLimit>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val appLimits = mutableMapOf<String, AppLimit>()
                    for (limitSnapshot in snapshot.children) {
                        Log.d("UsageStatsRepository", "Processing limit snapshot: ${limitSnapshot.key}")
                        try {
                            val encodedPackageName = limitSnapshot.key ?: continue
                            val packageName = encodedPackageName.replace("_", ".")

                            val limit = AppLimit(
                                packageName = packageName,
                                dailyLimitMinutes = limitSnapshot.child("dailyLimitMinutes").getValue(Int::class.java) ?: 0,
                                startTime = limitSnapshot.child("startTime").getValue(String::class.java) ?: "08:00",
                                endTime = limitSnapshot.child("endTime").getValue(String::class.java) ?: "21:00",
                                isEnabled = limitSnapshot.child("enabled").getValue(Boolean::class.java) ?: false
                            )
                            Log.d("UsageStatsRepository", "Parsed limit: ${limit.isEnabled} for package: $packageName")
                            if (limit.isEnabled) {
                                appLimits[packageName] = limit
                                Log.d("UsageStatsRepository", "App limit added: ${limit.packageName} - ${limit.dailyLimitMinutes} minutes")
                            }
                        } catch (e: Exception) {
                            Log.e("UsageStatsRepository", "Error parsing app limit: ${e.message}")
                        }
                    }
                    trySend(appLimits)
                } catch (e: Exception) {
                    trySend(emptyMap())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyMap())
            }
        }

        val ref = database.getReference("app_limits").child(childId)
        ref.addValueEventListener(listener)

        awaitClose {
            ref.removeEventListener(listener)
        }
    }
}

