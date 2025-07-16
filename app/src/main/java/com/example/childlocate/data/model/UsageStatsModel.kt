package com.example.childlocate.data.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize


sealed class UsageStatsState {
    object Loading : UsageStatsState()
    object RequestingUpdate : UsageStatsState()
    data class Success(val data: WeeklyUsageStats) : UsageStatsState()
    data class Error(val message: String) : UsageStatsState()
    object Empty : UsageStatsState()
}

data class UsageUiState(
    val isRequestingUpdate: Boolean = false,
    val isLoadingData: Boolean = false,
    val weeklyData: WeeklyUsageStats? = null,
    val appLimits: Map<String, AppLimit> = emptyMap(),
    val currentWeek: String = "",
    val error: String? = null
)

data class WeeklyUsageStats(
    val dailyStats: Map<String, DayUsageStats>
)

data class DayUsageStats(
    val date: String,
    val totalTime: Long,
    val appUsageList: List<AppUsageInfo>
)
@Parcelize
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageTime: Long,
    val lastTimeUsed: Long
): Parcelable

data class AppLimit(
    val packageName: String,
    val dailyLimitMinutes: Int,
    val startTime: String,
    val endTime: String,
    val isEnabled: Boolean = true
)


sealed class AppLimitDialogState {
    object Loading : AppLimitDialogState()
    data class Success(val currentLimit: AppLimit?) : AppLimitDialogState()
    data class Error(val message: String) : AppLimitDialogState()
}

// Composite model for UI rendering
data class AppUsageWithLimit(
    val appInfo: AppUsageInfo,
    val hasLimit: Boolean = false,
    val limitInfo: AppLimit? = null
) {
    companion object {
        fun fromAppUsage(appInfo: AppUsageInfo, appLimits: Map<String, AppLimit>): AppUsageWithLimit {
            val limitInfo = appLimits[appInfo.packageName]
            return AppUsageWithLimit(
                appInfo = appInfo,
                hasLimit = limitInfo != null,
                limitInfo = limitInfo
            )
        }
    }
}