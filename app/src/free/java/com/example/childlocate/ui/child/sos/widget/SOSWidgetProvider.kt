package com.example.childlocate.ui.child.sos.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import com.example.childlocate.R
import com.example.childlocate.ui.child.sos.service.SOSService

class SOSWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Cập nhật tất cả widget đã thêm
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
    //nhan broadcast va cap nhat widget
    override fun onReceive(context: Context, intent: Intent){
        super.onReceive(context,intent)
        if(intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE){
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, SOSWidgetProvider::class.java)
            )

            // Cập nhật từng widget
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    // Sử dụng onEnabled để thực hiện các tác vụ khi widget được thêm lần đầu
    // Cải thiện trải nghiệm người dùng bằng cách chuẩn bị widget ngay khi được thêm vào
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Ghi log hoặc thực hiện các tác vụ khởi tạo khi widget được bật
    }

    // Xử lý khi widget bị xóa để giải phóng tài nguyên
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Dọn dẹp tài nguyên khi widget bị xóa
    }

    companion object {
        /**
         * Cập nhật một widget SOS
         */
        private const val TAG = "SOSWidget12345Provider"

        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Log.d(TAG, "Updating widget with appWidgetId: $appWidgetId")
            // Kiểm tra trạng thái SOS hiện tại
            val isSOSActive = SOSService.isSosActive(context)

            // Tạo RemoteViews để hiển thị widget
            val views = RemoteViews(context.packageName, R.layout.sos_widget)

            // Tạo Intent dựa trên trạng thái hiện tại
            val intent = Intent(context, SOSService::class.java).apply {
                action = if (isSOSActive) {
                    // Nếu SOS đang hoạt động, intent sẽ hủy SOS
                    SOSService.ACTION_CANCEL_SOS
                } else {
                    // Nếu SOS không hoạt động, intent sẽ gửi SOS
                    SOSService.ACTION_SEND_SOS
                }
            }

            Log.d(TAG, "Created intent for SOSService with action: ${SOSService.ACTION_SEND_SOS}")


            // Sử dụng PendingIntent với FLAG_IMMUTABLE để tăng cường bảo mật theo yêu cầu của Android 12+
            // FLAG_IMMUTABLE đảm bảo PendingIntent không thể bị sửa đổi bởi các ứng dụng khác
            val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            // Luôn sử dụng getForegroundService cho Android 8.0+ và getService cho phiên bản cũ hơn
            // Android 8.0+ yêu cầu sử dụng ForegroundService để tuân thủ các giới hạn chạy nền
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    appWidgetId, // Sử dụng appWidgetId làm requestCode để tránh ghi đè PendingIntent
                    intent,
                    pendingIntentFlags
                )
            } else {
                PendingIntent.getService(
                    context,
                    appWidgetId, // Sử dụng appWidgetId làm requestCode để tránh ghi đè PendingIntent
                    intent,
                    pendingIntentFlags
                )
            }

            // Cập nhật giao diện widget dựa trên trạng thái
            if (isSOSActive) {
                // Nếu SOS đang hoạt động, hiển thị nút HỦY SOS
                views.setTextViewText(R.id.widget_button, "HỦY SOS")
                // Thay đổi màu nền
                //views.setInt(R.id.widget_button, "setBackgroundResource", R.drawable.bg_cancel_sos_button)
            } else {
                // Nếu SOS không hoạt động, hiển thị nút SOS
                views.setTextViewText(R.id.widget_button, "SOS")
                // Khôi phục màu nền mặc định
                //views.setInt(R.id.widget_button, "setBackgroundResource", R.drawable.bg_sos_button)
            }


            // Đặt PendingIntent sẽ được gọi khi nhấn vào widget
            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)

            // Cập nhật widget với RemoteViews đã được cấu hình
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}