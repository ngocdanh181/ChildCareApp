package com.example.childlocate.ui.child.onboarding

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.childlocate.R
import com.example.childlocate.ui.child.onboarding.data.PermissionStep
import com.example.childlocate.ui.child.onboarding.data.PermissionType
import com.example.childlocate.ui.child.onboarding.utils.PreferenceManager
import com.google.firebase.database.FirebaseDatabase

class OnboardingViewModel(private val preferenceManager: PreferenceManager) {
    private val database = FirebaseDatabase.getInstance().reference

    private val _verificationStatus = MutableLiveData<Boolean>()
    val verificationStatus: LiveData<Boolean> get() = _verificationStatus

    private val _childId = MutableLiveData<String>()
    val childId: LiveData<String> get() = _childId

    //trang thai man hinh onboarding hien tai
    private val _currentPage = MutableLiveData<Int>()
    val currentPage: LiveData<Int> = _currentPage

    //trang thai kiem tra ma xac thuc
    private val _verificationState = MutableLiveData<VerificationState>()
    val verificationState: LiveData<VerificationState> = _verificationState

    //trang thai cua buoc quyen hien tai
    private val _currentPermissionStep = MutableLiveData<Int>()
    val currentPermissionStep: LiveData<Int> = _currentPermissionStep
    //danh sach cac buoc quyen
    private val _permissionSteps = MutableLiveData<List<PermissionStep>>()
    val permissionSteps: LiveData<List<PermissionStep>> = _permissionSteps
    //trang thai hoan thanh tat ca cac buoc quyen
    private val _allPermissionsGranted = MutableLiveData<Boolean>()
    val allPermissionsGranted: LiveData<Boolean> = _allPermissionsGranted

    init {
        _currentPage.value = 0
        _currentPermissionStep.value = 0
        _verificationState.value = VerificationState.IDLE
        _allPermissionsGranted.value = false

        // Khởi tạo danh sách các bước quyền
        _permissionSteps.value = listOf(
            PermissionStep(
                PermissionType.LOCATION,
                "Quyền vị trí",
                "Ứng dụng cần quyền vị trí để phụ huynh có thể biết vị trí của bạn mọi lúc, kể cả khi ứng dụng không mở",
                R.drawable.baseline_add_location_24
            ),
            PermissionStep(
                PermissionType.NOTIFICATION,
                "Quyền thông báo",
                "Ứng dụng cần quyền thông báo để gửi thông tin quan trọng và cảnh báo đến bạn",
                R.drawable.baseline_notifications_24
            ),
            PermissionStep(
                PermissionType.MICROPHONE,
                "Quyền microphone",
                "Ứng dụng cần quyền microphone để bạn có thể giao tiếp với phụ huynh qua tính năng đàm thoại.\n\n⚠️ Từ Android 14, quyền microphone bị hạn chế. Để sử dụng ghi âm realtime, cần luôn bật service này.",
                R.drawable.baseline_chat_bubble_outline_24
            ),
            PermissionStep(
                PermissionType.CAMERA,
                "Quyền camera",
                "Ứng dụng cần quyền camera để bạn có thể chụp ảnh và gọi video với phụ huynh",
                R.drawable.baseline_camera_alt_24
            ),
            PermissionStep(
                PermissionType.SMS,
                "Quyền gửi tin nhắn",
                "Ứng dụng cần quyền gửi tin nhắn để bạn có thể gửi thông báo khẩn đến phụ huynh",
                R.drawable.baseline_chat_24
            ),
            PermissionStep(
                PermissionType.ACCESSIBILITY,
                "Quyền dịch vụ trợ năng",
                "Ứng dụng cần quyền dịch vụ trợ năng để giúp phụ huynh quản lý thời gian sử dụng ứng dụng và lọc nội dung không phù hợp",
                R.drawable.baseline_accessibility_24
            ),
            PermissionStep(
                PermissionType.OVERLAY,
                "Quyền hiển thị trên các ứng dụng khác",
                "Ứng dụng cần quyền này để hiển thị thông báo quan trọng khi bạn đang sử dụng ứng dụng khác",
                R.drawable.ic_overlay
            ),
            PermissionStep(
                PermissionType.USAGE_STATS,
                "Quyền theo dõi sử dụng",
                "Ứng dụng cần quyền này để phụ huynh có thể giúp bạn quản lý thời gian sử dụng thiết bị",
                R.drawable.ic_usage
            )
        )

    }



    fun nextPage() {
        _currentPage.value = _currentPage.value?.plus(1)
    }

    fun previousPage() {
        _currentPage.value = _currentPage.value?.minus(1)
    }

    fun setPage(page: Int) {
        _currentPage.value = page
    }
    fun isLastPage(position: Int): Boolean {
        return position == 3
    }

    fun nextPermissionStep() {
        val currentStep = _currentPermissionStep.value ?: 0
        val totalSteps = _permissionSteps.value?.size ?: 0

        if (currentStep < totalSteps - 1) {
            _currentPermissionStep.value = currentStep + 1
        }
    }

    fun previousPermissionStep() {
        val currentStep = _currentPermissionStep.value ?: 0
        if (currentStep > 0) {
            _currentPermissionStep.value = currentStep - 1
        }
    }

    fun getCurrentPermissionStep(): PermissionStep? {
        val steps = _permissionSteps.value
        val currentIndex = _currentPermissionStep.value

        if (steps != null && currentIndex != null && currentIndex < steps.size) {
            return steps[currentIndex]
        }
        return null
    }

    fun isLastPermissionStep(): Boolean {
        val currentStep = _currentPermissionStep.value ?: 0
        val totalSteps = _permissionSteps.value?.size ?: 0
        return currentStep == totalSteps - 1
    }

    fun isFirstPermissionStep(): Boolean {
        val currentStep = _currentPermissionStep.value ?: 0
        return currentStep == 0
    }

    fun updatePermissionStatus(type: PermissionType, granted: Boolean) {
        val currentSteps = _permissionSteps.value?.toMutableList() ?: return
        val index = currentSteps.indexOfFirst { it.type == type }

        if (index != -1) {
            currentSteps[index] = currentSteps[index].copy(isGranted = granted)
            _permissionSteps.value = currentSteps

            // Kiểm tra xem tất cả quyền đã được cấp chưa
            _allPermissionsGranted.value = currentSteps.all { it.isGranted }
        }
    }


    fun completeOnboarding() {
        preferenceManager.setOnboardingCompleted(true)
    }




    // Enum class cho trạng thái xác nhận
    enum class VerificationState {
        IDLE, SUCCESS, ERROR
    }


}

class OnboardingViewModelFactory(private val preferenceManager: PreferenceManager) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OnboardingViewModel(preferenceManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}