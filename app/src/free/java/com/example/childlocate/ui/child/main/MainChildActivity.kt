package com.example.childlocate.ui.child.main

//import com.example.childlocate.service.WebFilterVpnService
import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.example.childlocate.MyFirebaseManager
import com.example.childlocate.data.model.Task
import com.example.childlocate.databinding.ActivityMainChildBinding
import com.example.childlocate.service.AudioStreamingForegroundService
import com.example.childlocate.ui.child.childchat.ChildChatActivity
import com.example.childlocate.ui.child.main.LocationWorker.Companion.notificationId
import com.example.childlocate.ui.child.onboarding.utils.PermissionManager
import com.example.childlocate.ui.child.sos.SOSSettingsActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.TimeUnit


class MainChildActivity : AppCompatActivity() {
    // Sử dụng ActivityResultLauncher thay cho onActivityResult()
    /*private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "Quyền VPN bị từ chối", Toast.LENGTH_SHORT).show()
        }
    }*/

    // Launcher cho quyền vị trí
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Yêu cầu vị trí nền cho Android 10+
                requestBackgroundLocationPermission()
            }
        } else {
            // Xử lý khi không được cấp quyền
            Toast.makeText(this, "Cần quyền vị trí để theo dõi trẻ", Toast.LENGTH_SHORT)
                .show()
        }
    }

    //launcher cho vị trí nền
    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scheduleLocationWorker()
        } else {
            // Xử lý khi không được cấp quyền
            Toast.makeText(
                this,
                "Cần quyền vị trí nền để theo dõi trẻ",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Launcher cho accessibility service
    private val accessibilityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (permissionManager.isAccessibilityServiceEnabled()) {

        }
    }

    // Launcher cho overlay permission
    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Kiểm tra lại trạng thái overlay permission
        if (permissionManager.isOverlayPermissionGranted()) {
            Toast.makeText(this, "Quyền overlay đã được cấp", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher cho usage stats permission
    private val usageStatsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Kiểm tra lại trạng thái usage stats permission
        if (permissionManager.isUsageStatsPermissionGranted()) {
            Toast.makeText(this, "Quyền usage stats đã được cấp", Toast.LENGTH_SHORT).show()
        }
    }

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startMicrophoneService()
            binding.microphoneSwitch.isChecked = true
        } else {
            Toast.makeText(this, "Cần quyền microphone để sử dụng tính năng này", Toast.LENGTH_SHORT).show()
            binding.microphoneSwitch.isChecked = false
        }
    }

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {

        } else {
            // Từ chối quyền
            Toast.makeText(this, "Cần quyền gửi SMS để sử dụng tính năng này", Toast.LENGTH_LONG).show()
        }
    }


    private lateinit var binding: ActivityMainChildBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var permissionManager: PermissionManager
    private var isMicrophoneServiceRunning = false
    private lateinit var sharedPreferences: SharedPreferences

    private val viewModel: MainChildViewModel by lazy {
        ViewModelProvider(this)[MainChildViewModel::class.java]
    }
    private lateinit var parentId: String
    private lateinit var childId: String
    private var phones : List<String> = emptyList()
    private val gson = Gson()
    private var alertDialog1: AlertDialog? = null


    private var isAlertVisible = false

    private lateinit var tasksAdapter: TaskAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainChildBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        permissionManager = PermissionManager(this)
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        childId = sharedPreferences.getString("childId", null).toString()
        parentId = sharedPreferences.getString("familyId", null).toString()
        val json = sharedPreferences.getString("parentPhones", null)
        phones = json?.let {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(it, type)
        } ?: emptyList()
        if(phones.isEmpty()){
            viewModel.loadParentPhones(parentId)
        }
        val isSharing = sharedPreferences.getBoolean("isLocationSharingEnabled", false)
        binding.locationSwitch.isChecked = isSharing
        
        // Kiểm tra trạng thái microphone service
        val isMicrophoneEnabled = sharedPreferences.getBoolean("isMicrophoneServiceEnabled", false)
        binding.microphoneSwitch.isChecked = isMicrophoneEnabled
        updateMicrophoneUI()

        Log.d("ChildMainActivity","$childId and $parentId")
        //chuyen sang audioStreamingForeground Service
        //startPersistentService()


        val PERMISSION_REQUEST_CODE=2000
        requestPermissions(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.PACKAGE_USAGE_STATS
            ),
            PERMISSION_REQUEST_CODE
        )

        if (permissionManager.isSmsPermissionGranted()) {
            //startMicrophoneService()
        } else {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }



        MyFirebaseManager.initFirebase(this)
        checkPermissionsAndConditions()

        setupRecyclerView()
        observeTasks()

        // Gọi loadTasksForChild với childId thích hợp
        viewModel.loadTasksForChild(childId)
        Log.d("MainActivity","$childId")

        binding.btnChat.setOnClickListener {
            val intent = Intent(this, ChildChatActivity::class.java).apply {
                putExtra("senderId", childId)
                putExtra("receiverId", parentId)
            }
            startActivity(intent)
        }

        binding.btnCall.setOnClickListener {
            //val phoneNumber = "0987654321"
            //val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            //startActivity(callIntent)
            //requestVpnPermissionAndStart()
        }

        binding.locationSwitch.setOnCheckedChangeListener{_, isChecked ->
            if (isChecked) {
                binding.locationStatus.text = "Đang chia sẻ vị trí 15 phút mỗi lần"
                checkPermissionsAndConditions()
                scheduleLocationWorker()
            } else {
                binding.locationStatus.text = "Không chia sẻ vị trí"
                stopLocationSharing()
            }
            // Lưu lại trạng thái mới vào SharedPreferences
            sharedPreferences.edit().putBoolean("isLocationSharingEnabled", isChecked).apply()
        }

        binding.microphoneSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (permissionManager.isMicrophonePermissionGranted()) {
                    startMicrophoneService()
                } else {
                    microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            } else {
                stopMicrophoneService()
            }
            sharedPreferences.edit().putBoolean("isMicrophoneServiceEnabled", isChecked).apply()
        }

        //chuyen sang setting SOS
        binding.sosSettingsButton.setOnClickListener {
            val intent = Intent(this, SOSSettingsActivity::class.java)
            startActivity(intent)
        }

        //yeu cau quyen UsageStatsPermission
        requestUsageStatsPermission()
        // Kiểm tra quyền overlay khi khởi động app
        checkOverlayPermission()
        //check quyen accessbility service
        checkAccessibilityService()

        //startVpnService()
        // Handle warning from accessibility service
        if (intent.getBooleanExtra("show_warning", false)) {
            showBlockedContentWarning()
        }
    }

    /*private fun requestVpnPermissionAndStart() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // Chưa được cấp quyền, yêu cầu người dùng cho phép
            vpnPermissionLauncher.launch(intent)
        } else {
            // Đã được cấp quyền, tiến hành khởi động ngay
            startVpnService()
        }
    }

    private fun startVpnService() {
        val vpnIntent = Intent(this, WebFilterVpnService::class.java)
        // Từ API 26 trở đi, cần dùng startForegroundService() để khởi động service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(vpnIntent)
        } else {
            startService(vpnIntent)
        }
    }*/

    private fun checkAccessibilityService() {
        if (!permissionManager.isAccessibilityServiceEnabled()) {
            alertDialog1=AlertDialog.Builder(this)
                .setTitle("Yêu cầu quyền")
                .setMessage("Ứng dụng cần quyền truy cập đặc biệt để giới hạn thời gian sử dụng ứng dụng. Vui lòng bật dịch vụ trong cài đặt.")
                .setPositiveButton("Đi đến Cài đặt") { _, _ ->
                    val intent = permissionManager.getAccessibilitySettingsIntent()
                    accessibilityLauncher.launch(intent)
                }
                .setNegativeButton("Để sau", null)
                .setCancelable(false)
                .show()
        }
    }

    private fun showBlockedContentWarning() {
        AlertDialog.Builder(this)
            .setTitle("Cảnh báo")
            .setMessage("Nội dung bị chặn do vi phạm quy định")
            .setPositiveButton("Đã hiểu", null)
            .show()
    }
    private fun checkOverlayPermission() {
        if (!permissionManager.isOverlayPermissionGranted()) {
            // Hiển thị dialog giải thích về quyền
            AlertDialog.Builder(this)
                .setTitle("Cần cấp quyền")
                .setMessage("Ứng dụng cần quyền hiển thị trên ứng dụng khác để có thể giới hạn thời gian sử dụng ứng dụng con")
                .setPositiveButton("Cấp quyền") { _, _ ->
                    val intent = permissionManager.getOverlaySettingsIntent()
                    intent?.let { overlayLauncher.launch(it) }
                }
                .setNegativeButton("Để sau", null)
                .show()
        }
    }


    private fun requestUsageStatsPermission() {
        if (!permissionManager.isUsageStatsPermissionGranted()) {
            val intent = permissionManager.getUsageStatsSettingsIntent()
            usageStatsLauncher.launch(intent)
        }
    }

    private fun observeTasks() {
        viewModel.tasks.observe(this) { tasks ->
            tasksAdapter.submitList(tasks)
        }
        viewModel.parentPhones.observe(this) { phones ->
            if (phones.isNotEmpty()) {
                val json = gson.toJson(phones)
                sharedPreferences.edit()
                    .putString("parentPhones", json)
                    .apply()
                Log.d("MainChild", "Parent phones updated: $phones")
            } else {
                Log.d("MainChild", "No parent phones found")
            }
        }

        tasksAdapter.setOnTaskStatusChangeListener(object : TaskAdapter.OnTaskStatusChangeListener {
            override fun onTaskStatusChanged(task: Task, isCompleted: Boolean) {
                viewModel.updateTaskStatus(childId, task.id, isCompleted)
            }
        })
    }

    private fun setupRecyclerView() {
        tasksAdapter = TaskAdapter()
        binding.taskRecyclerView.adapter = tasksAdapter
    }

    private fun checkPermissionsAndConditions() {
        if (!permissionManager.isInternetAvailable()) {
            Toast.makeText(this, "Không có kết nối Internet", Toast.LENGTH_SHORT).show()
            return
        }

        if (!permissionManager.isLocationEnabled()) {
            Toast.makeText(this, "Dịch vụ định vị chưa bật", Toast.LENGTH_SHORT).show()
            return
        }

        if(!permissionManager.isLocationPermissionGranted()){
            requestLocationPermissions()
        }

    }


    private fun stopLocationSharing() {
        WorkManager.getInstance(this).cancelAllWorkByTag(LocationWorker.TAG)
        Log.d("Location", "Location share stopped")
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.cancel(notificationId)
    }

    private fun scheduleLocationWorker() {
        val locationRequest = PeriodicWorkRequest.Builder(LocationWorker::class.java,
            15, TimeUnit.MINUTES)
            .addTag(LocationWorker.TAG)
            .build()
        WorkManager.getInstance(this).enqueue(locationRequest)
        Log.d("SecondActivity", "Location sharing scheduled in 15 minutes")
    }
    private fun requestLocationPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            PermissionManager.LOCATION_PERMISSIONS
        }

        locationPermissionLauncher.launch(permissions)
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }





    private fun startMicrophoneService() {
        if (!permissionManager.isMicrophonePermissionGranted()) {
            Toast.makeText(this, "Cần quyền microphone để sử dụng tính năng này", Toast.LENGTH_SHORT).show()
            binding.microphoneSwitch.isChecked = false
            return
        }
        
        try {
            val intent = Intent(this, AudioStreamingForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            isMicrophoneServiceRunning = true
            updateMicrophoneUI()
            Log.d("MainChildActivity", "Microphone service started successfully")
        } catch (e: Exception) {
            Log.e("MainChildActivity", "Error starting microphone service: ${e.message}")
            Toast.makeText(this, "Không thể khởi động dịch vụ ghi âm", Toast.LENGTH_SHORT).show()
            binding.microphoneSwitch.isChecked = false
            isMicrophoneServiceRunning = false
            updateMicrophoneUI()
        }
    }
    
    private fun stopMicrophoneService() {
        try {
            val intent = Intent(this, AudioStreamingForegroundService::class.java)
            stopService(intent)
            isMicrophoneServiceRunning = false
            updateMicrophoneUI()
            Log.d("MainChildActivity", "Microphone service stopped")
        } catch (e: Exception) {
            Log.e("MainChildActivity", "Error stopping microphone service: ${e.message}")
        }
    }
    
    private fun updateMicrophoneUI() {
        if (isMicrophoneServiceRunning || binding.microphoneSwitch.isChecked) {
            binding.microphoneStatus.text = "Sẵn sàng nhận lệnh"
            binding.microphoneWarning.visibility = View.VISIBLE
        } else {
            binding.microphoneStatus.text = "Đang tắt"
            binding.microphoneWarning.visibility = View.GONE
        }
    }
    
    private fun checkMicrophoneServiceStatus() {
        // Kiểm tra xem service có đang chạy không
        isMicrophoneServiceRunning = isServiceRunning(AudioStreamingForegroundService::class.java)
        updateMicrophoneUI()
        
        // Sync switch với trạng thái thực tế
        if (binding.microphoneSwitch.isChecked != isMicrophoneServiceRunning) {
            binding.microphoneSwitch.isChecked = isMicrophoneServiceRunning
        }
    }
    
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onStart() {
        super.onStart()
        checkPermissionsAndConditions()
        checkMicrophoneServiceStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        alertDialog1?.dismiss()
    }

}
