package com.example.childlocate.ui.child.sos.service

import android.content.Context
import java.util.Date

/**
 * Quản lý lưu trữ và theo dõi các cảnh báo SOS
 */
class SOSManager(private val context: Context) {
    private val sharedPreferences = context.getSharedPreferences("SOS_DATA", Context.MODE_PRIVATE)

    /**
     * Lưu thông tin cảnh báo
     * @param timestamp Thời điểm gửi cảnh báo
     * @param isDelivered Trạng thái đã gửi thành công chưa
     */
    fun saveWarning(timestamp: Long, isDelivered: Boolean = false) {
        sharedPreferences.edit()
            .putLong("last_warning_timestamp", timestamp)
            .putBoolean("last_warning_delivered", isDelivered)
            .apply()
    }

    /**
     * Đánh dấu cảnh báo đã được gửi thành công
     */
    fun markWarningAsDelivered() {
        sharedPreferences.edit()
            .putBoolean("last_warning_delivered", true)
            .apply()
    }

    /**
     * Lấy danh sách các cảnh báo chưa được gửi
     * @return Danh sách timestamp của các cảnh báo chưa gửi
     */
    fun getPendingWarnings(): List<Long> {
        val lastWarningTimestamp = sharedPreferences.getLong("last_warning_timestamp", 0)
        val isDelivered = sharedPreferences.getBoolean("last_warning_delivered", true)

        return if (!isDelivered && lastWarningTimestamp > 0) {
            listOf(lastWarningTimestamp)
        } else {
            emptyList()
        }
    }

    /**
     * Kiểm tra xem có cảnh báo nào đang chờ gửi hay không
     * @return true nếu có cảnh báo đang chờ gửi
     */
    fun hasPendingWarnings(): Boolean {
        return getPendingWarnings().isNotEmpty()
    }

    /**
     * Lấy thời điểm cảnh báo cuối cùng
     * @return timestamp hoặc 0 nếu chưa có cảnh báo nào
     */
    fun getLastWarningTimestamp(): Long {
        return sharedPreferences.getLong("last_warning_timestamp", 0)
    }

    /**
     * Xóa lịch sử cảnh báo
     */
    fun clearWarningHistory() {
        sharedPreferences.edit().clear().apply()
    }
}