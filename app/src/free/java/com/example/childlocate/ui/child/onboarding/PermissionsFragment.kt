package com.example.childlocate.ui.child.onboarding

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.childlocate.databinding.FragmentPermissionsBinding
import com.example.childlocate.ui.child.onboarding.data.PermissionType
import com.example.childlocate.ui.child.onboarding.utils.PermissionManager
import com.example.childlocate.ui.child.onboarding.utils.PreferenceManager

class PermissionsFragment : Fragment() {
    // View Binding
    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!

    //view model
    private val viewModel: OnboardingViewModel by lazy {
        OnboardingViewModel(PreferenceManager(requireContext()))
    }
    private lateinit var permissionManager: PermissionManager

    // Launcher cho quyền vị trí
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // Xử lý khi tất cả quyền vị trí được cấp
            viewModel.updatePermissionStatus(PermissionType.LOCATION, true)
            updatePermissionUI()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Yêu cầu vị trí nền cho Android 10+
                requestBackgroundLocationPermission()
            }
        } else {
            // Xử lý khi không được cấp quyền
            Toast.makeText(requireContext(), "Cần quyền vị trí để theo dõi trẻ", Toast.LENGTH_SHORT)
                .show()
        }
    }

    //launcher cho vị trí nền
    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Xử lý khi quyền vị trí nền được cấp
            viewModel.updatePermissionStatus(PermissionType.BACKGROUND_LOCATION, true)
            updatePermissionUI()
        } else {
            // Xử lý khi không được cấp quyền
            Toast.makeText(
                requireContext(),
                "Cần quyền vị trí nền để theo dõi trẻ",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    //launcher cho quyền thông báo
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Xử lý khi quyền thông báo được cấp
            viewModel.updatePermissionStatus(PermissionType.NOTIFICATION, true)
            updatePermissionUI()
        } else {
            // Xử lý khi không được cấp quyền
            Toast.makeText(
                requireContext(),
                "Cần quyền thông báo để theo dõi trẻ",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Launcher cho quyền micro
    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Xử lý khi quyền micro được cấp
            viewModel.updatePermissionStatus(PermissionType.MICROPHONE, true)
            updatePermissionUI()
        } else {
            // Xử lý khi quyền micro không được cấp
            Toast.makeText(
                requireContext(),
                "Cần quyền microphone để giao tiếp",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Launcher cho quyền camera
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Xử lý khi quyền camera được cấp
            viewModel.updatePermissionStatus(PermissionType.CAMERA, true)
            updatePermissionUI()
        } else {
            // Xử lý khi quyền camera không được cấp
            Toast.makeText(
                requireContext(),
                "Cần quyền camera để chụp ảnh và gọi video",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    //launcher cho quyền gửi message
    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Xử lý khi quyền gửi tin nhắn được cấp
            viewModel.updatePermissionStatus(PermissionType.SMS, true)
            updatePermissionUI()
        } else {
            // Xử lý khi quyền gửi tin nhắn không được cấp
            Toast.makeText(
                requireContext(),
                "Cần quyền gửi tin nhắn để giúp trẻ gửi cảnh báo kịp thời trong trường hợp không có mạng",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    //launcher cho quyen overlay
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Xử lý khi quyền overlay được cấp
            val isGranted = permissionManager.isOverlayPermissionGranted()
            viewModel.updatePermissionStatus(PermissionType.OVERLAY, isGranted)
            updatePermissionUI()
        } else {
            // Xử lý khi không được cấp quyền
            Toast.makeText(
                requireContext(),
                "Cần quyền overlay để theo dõi trẻ",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    //launcher cho usageStats
    private val usageStatsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ){
        if (it.resultCode == Activity.RESULT_OK) {
            // Xử lý khi quyền usage stats được cấp
            val isGranted = permissionManager.isUsageStatsPermissionGranted()
            viewModel.updatePermissionStatus(PermissionType.USAGE_STATS, isGranted)
            updatePermissionUI()
        } else {
            // Xử lý khi không được cấp quyền
            Toast.makeText(
                requireContext(),
                "Cần quyền usage stats để theo dõi trẻ",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    //launcher cho accessibility
    private val accessibilityPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Kiểm tra lại trạng thái accessibility service
        val isGranted = permissionManager.isAccessibilityServiceEnabled()
        viewModel.updatePermissionStatus(PermissionType.ACCESSIBILITY, isGranted)
        updatePermissionUI()
        
        if (!isGranted) {
            Toast.makeText(
                requireContext(),
                "Cần bật dịch vụ trợ năng để quản lý ứng dụng",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        permissionManager = PermissionManager(requireContext())
        // Đảm bảo activity cha biết chúng ta đang sử dụng điều hướng riêng
        if (activity is OnboardingActivity) {
            (activity as OnboardingActivity).hideNavigationButtons()
        }

        //setup ui
        setupUI()
        setupObservers()

        //cap nhat ui cho quyen hien tai
        updatePermissionUI()
    }

    private fun setupUI() {
        binding.btnGrantPermission.setOnClickListener {
            handleGrantPermission()
        }
        binding.btnNext.setOnClickListener {
            if (viewModel.isLastPermissionStep()) {
                // Nếu đây là bước cuối cùng, hoàn thành quá trình cấp quyền
                viewModel.completeOnboarding()
                if(activity is OnboardingActivity){
                    (activity as OnboardingActivity).navigateToMainActivity()
                }
            } else {
                // Nếu không, chuyển đến bước tiếp theo
                viewModel.nextPermissionStep()
                updatePermissionUI()
            }
        }

        binding.btnPrevious.setOnClickListener {
            viewModel.previousPermissionStep()
            updatePermissionUI()
        }
    }

    private fun setupObservers() {
        // Theo dõi trạng thái của bước quyền hiện tại
        viewModel.currentPermissionStep.observe(viewLifecycleOwner) { step ->
            // Cập nhật chỉ báo tiến độ
            val totalSteps = viewModel.permissionSteps.value?.size ?: 1
            binding.progressBar.max = totalSteps * 100
            binding.progressBar.progress = (step + 1) * 100
            binding.tvProgressText.text = "Quyền ${step + 1}/$totalSteps"

            // Cập nhật nút điều hướng
            binding.btnPrevious.visibility =
                if (viewModel.isFirstPermissionStep()) View.INVISIBLE else View.VISIBLE
            binding.btnNext.text =
                if (viewModel.isLastPermissionStep()) "Hoàn thành" else "Tiếp tục"

            // Cập nhật UI cho quyền hiện tại
            updatePermissionUI()
        }
    }

    private fun updatePermissionUI() {
        val currentStep = viewModel.getCurrentPermissionStep() ?: return

        // Cập nhật hình ảnh
        binding.ivPermissionImage.setImageResource(currentStep.imageResId)

        // Cập nhật tiêu đề và mô tả
        binding.tvPermissionTitle.text = currentStep.title
        binding.tvPermissionDescription.text = currentStep.description

        // Cập nhật trạng thái nút cấp quyền
        val isGranted = when (currentStep.type) {
            PermissionType.LOCATION -> permissionManager.isLocationPermissionGranted()
            PermissionType.NOTIFICATION -> permissionManager.isNotificationPermissionGranted()
            PermissionType.MICROPHONE -> permissionManager.isMicrophonePermissionGranted()
            PermissionType.CAMERA -> permissionManager.isCameraPermissionGranted()
            PermissionType.ACCESSIBILITY -> permissionManager.isAccessibilityServiceEnabled()
            PermissionType.OVERLAY -> permissionManager.isOverlayPermissionGranted()
            PermissionType.USAGE_STATS -> permissionManager.isUsageStatsPermissionGranted()
            PermissionType.SMS -> permissionManager.isSmsPermissionGranted()
            else -> false
        }

        // Cập nhật trạng thái quyền trong ViewModel
        viewModel.updatePermissionStatus(currentStep.type, isGranted)

        if (isGranted) {
            binding.btnGrantPermission.text = "Đã cấp quyền"
            binding.btnGrantPermission.isEnabled = false
        } else {
            binding.btnGrantPermission.text = "Cấp quyền"
            binding.btnGrantPermission.isEnabled = true
        }
    }

    private fun handleGrantPermission() {
        val currentStep = viewModel.getCurrentPermissionStep() ?: return

        when (currentStep.type) {
            PermissionType.LOCATION -> requestLocationPermission()
            PermissionType.NOTIFICATION -> requestNotificationPermission()
            PermissionType.MICROPHONE -> requestMicrophonePermission()
            PermissionType.CAMERA -> requestCameraPermission()
            PermissionType.SMS -> smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            PermissionType.ACCESSIBILITY -> {
                val intent = permissionManager.getAccessibilitySettingsIntent()
                accessibilityPermissionLauncher.launch(intent)
            }
            PermissionType.OVERLAY -> {
                val intent = permissionManager.getOverlaySettingsIntent()
                intent?.let { overlayPermissionLauncher.launch(it) }
            }
            PermissionType.USAGE_STATS -> {
                val intent = permissionManager.getUsageStatsSettingsIntent()
                usageStatsPermissionLauncher.launch(intent)
            }
            else -> {}
        }
    }

    private fun requestLocationPermission() {
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Không cần yêu cầu quyền thông báo cho Android dưới 13
            viewModel.updatePermissionStatus(PermissionType.NOTIFICATION, true)
            updatePermissionUI()
        }
    }

    private fun requestMicrophonePermission() {
        microphonePermissionLauncher.launch(PermissionManager.MICROPHONE_PERMISSION)
    }

    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch(PermissionManager.CAMERA_PERMISSION)
    }


    override fun onResume() {
        super.onResume()

        // Cập nhật UI khi quay lại fragment sau khi cấp quyền từ cài đặt hệ thống
        updatePermissionUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}