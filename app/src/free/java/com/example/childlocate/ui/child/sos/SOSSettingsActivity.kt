package com.example.childlocate.ui.child.sos

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.example.childlocate.R
import com.example.childlocate.ui.child.sos.service.FallDetectionService
import com.example.childlocate.ui.child.sos.service.SOSGestureService
import com.example.childlocate.ui.child.sos.widget.SOSWidgetGuideActivity

class SOSSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sossettings)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Cài đặt SOS"

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SOSSettingsFragment())
            .commit()

        // Kiểm tra quyền Battery Optimization khi khởi động
        checkBatteryOptimization()
    }

    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Cải thiện độ tin cậy")
                    .setMessage("Để đảm bảo nút SOS hoạt động trong mọi tình huống, kể cả khi pin yếu, bạn cần tắt tối ưu hóa pin cho ứng dụng này.")
                    .setPositiveButton("Cài đặt ngay") { _, _ ->
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Để sau", null)
                    .show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class SOSSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.sos_preferences, rootKey)

            // Xử lý preference Widget màn hình chính
            findPreference<Preference>("home_screen_widget")?.setOnPreferenceClickListener {
                startActivity(Intent(activity, SOSWidgetGuideActivity::class.java))
                true
            }

            // Xử lý preference Cử chỉ khẩn cấp
            val gesturePreference = findPreference<SwitchPreferenceCompat>("emergency_gesture")
            gesturePreference?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    // Bật dịch vụ cử chỉ khẩn cấp
                    val serviceIntent = Intent(activity, SOSGestureService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activity?.startForegroundService(serviceIntent)
                    } else {
                        activity?.startService(serviceIntent)
                    }

                    // Lưu trạng thái
                    activity?.getSharedPreferences("SOS_PREFS", Context.MODE_PRIVATE)?.edit()?.
                    putBoolean("emergency_gesture_enabled", true)?.apply()

                    Toast.makeText(activity, "Đã bật cử chỉ khẩn cấp", Toast.LENGTH_SHORT).show()
                } else {
                    // Tắt dịch vụ cử chỉ khẩn cấp
                    activity?.stopService(Intent(activity, SOSGestureService::class.java))

                    // Lưu trạng thái
                    activity?.getSharedPreferences("SOS_PREFS", Context.MODE_PRIVATE)?.edit()?.
                    putBoolean("emergency_gesture_enabled", false)?.apply()

                    Toast.makeText(activity, "Đã tắt cử chỉ khẩn cấp", Toast.LENGTH_SHORT).show()
                }
                true
            }
            //xử lý preference emergency_accelerometer phát hiện va đập
            val accelerometerPreference = findPreference<SwitchPreferenceCompat>("emergency_accelerometer")
            accelerometerPreference?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    // Bật dịch vụ cử chỉ khẩn cấp
                    val serviceIntent = Intent(activity, FallDetectionService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activity?.startForegroundService(serviceIntent)
                    } else {
                        activity?.startService(serviceIntent)
                    }

                    // Lưu trạng thái
                    activity?.getSharedPreferences("SOS_PREFS", Context.MODE_PRIVATE)?.edit()?.
                    putBoolean("emergency_accelerometer_enabled", true)?.apply()

                    Toast.makeText(activity, "Đã bật nền phát hiện va đập khẩn cấp", Toast.LENGTH_SHORT).show()
                } else {
                    // Tắt dịch vụ cử chỉ khẩn cấp
                    activity?.stopService(Intent(activity, FallDetectionService::class.java))

                    // Lưu trạng thái
                    activity?.getSharedPreferences("SOS_PREFS", Context.MODE_PRIVATE)?.edit()?.
                    putBoolean("emergency_accelerometer_enabled", false)?.apply()

                    Toast.makeText(activity, "Đã tắt nền phát hiện va đập khẩn cấp", Toast.LENGTH_SHORT).show()
                }
                true
            }

            // Xử lý preference Quick Settings Tile
            findPreference<Preference>("quick_settings_tile")?.setOnPreferenceClickListener {
                // Hiển thị hướng dẫn cách thêm Quick Settings Tile
                AlertDialog.Builder(requireContext())
                    .setTitle("Thêm nút SOS vào Quick Settings")
                    .setMessage("Để thêm nút SOS vào thanh Quick Settings:\n\n" +
                            "1. Vuốt xuống để mở thanh thông báo\n" +
                            "2. Vuốt xuống lần nữa để mở rộng Quick Settings\n" +
                            "3. Nhấn vào nút chỉnh sửa (biểu tượng bút chì)\n" +
                            "4. Tìm và kéo tile \"SOS Khẩn cấp\" vào vị trí mong muốn")
                    .setPositiveButton("Đã hiểu", null)
                    .show()
                true
            }


            // Xử lý preference Battery Optimization
            findPreference<Preference>("battery_optimization")?.setOnPreferenceClickListener {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity?.packageName}")
                }
                startActivity(intent)
                true
            }

            // Kiểm tra xem dịch vụ cử chỉ đã chạy chưa
            updateGesturePreferenceState()
        }

        private fun updateGesturePreferenceState() {
            val gesturePreference = findPreference<SwitchPreferenceCompat>("emergency_gesture")
            val isServiceRunning = isServiceRunning(SOSGestureService::class.java)
            gesturePreference?.isChecked = isServiceRunning
        }

        private fun isServiceRunning(serviceClass: Class<*>): Boolean {
            val manager = activity?.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            manager?.let {
                for (service in it.getRunningServices(Integer.MAX_VALUE)) {
                    if (serviceClass.name == service.service.className) {
                        return true
                    }
                }
            }
            return false
        }
    }
}