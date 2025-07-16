package com.example.childlocate.ui.child.sos.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.childlocate.R

class SOSWidgetGuideActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SOSWidget12345"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_soswidget_guide)

        // Thiết lập toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "SOS Widget Guide"

        val addWidgetButton = findViewById<Button>(R.id.add_widget_button)
        val widgetStatus = findViewById<TextView>(R.id.widget_status)

        // Kiểm tra xem widget đã được thêm chưa
        if (isWidgetAdded()) {
            widgetStatus.text = "Widget đã được thêm"
            addWidgetButton.isEnabled = false
            Log.d(TAG, "Widget already added, button disabled")
        } else {
            widgetStatus.text = "Widget chưa được thêm"
            addWidgetButton.isEnabled = true
            Log.d(TAG, "Widget not added, button enabled")
        }

        addWidgetButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Sử dụng requestPinAppWidget cho Android 8.0+
                val appWidgetManager = AppWidgetManager.getInstance(this)
                val provider = ComponentName(this, SOSWidgetProvider::class.java)

                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                    // PendingIntent để nhận callback khi widget được thêm
                    val successCallback = android.app.PendingIntent.getBroadcast(
                        this, 0,
                        Intent(this, SOSWidgetProvider::class.java),
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )

                    appWidgetManager.requestPinAppWidget(provider, null, successCallback)
                    Log.d(TAG, "Requested to pin widget using requestPinAppWidget")
                } else {
                    Toast.makeText(this, "Thiết bị không hỗ trợ ghim widget", Toast.LENGTH_LONG).show()
                    Log.w(TAG, "Device does not support pinning widgets")


                }
            } else {
                // Sử dụng widget picker cho phiên bản cũ hơn
                Toast.makeText(
                    this,
                    "Vui lòng thêm widget SOS bằng cách nhấn vào biểu tượng ứng dụng và giữ trên màn hình chính, chọn Widgets, " +
                            "sau đó tìm và kéo widget SOS vào màn hình",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }



    private fun isWidgetAdded(): Boolean {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(this, SOSWidgetProvider::class.java)
        )
        Log.d(TAG, "Checking widget, found appWidgetIds: ${appWidgetIds.joinToString()}")
        return appWidgetIds.isNotEmpty()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}