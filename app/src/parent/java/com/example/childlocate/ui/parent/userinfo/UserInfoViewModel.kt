package com.example.childlocate.ui.parent.userinfo

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.FamilyMember
import com.example.childlocate.data.model.UserData
import com.example.childlocate.repository.SettingsRepository
import kotlinx.coroutines.launch

class UserInfoViewModel : ViewModel() {
    private val repository = SettingsRepository()

    // User data
    private val _userData = MutableLiveData<UserData>()
    val userData: LiveData<UserData> = _userData

    // Family members
    private val _familyMembers = MutableLiveData<List<FamilyMember>>()
    val familyMembers: LiveData<List<FamilyMember>> = _familyMembers

    // App version
    private val _appVersion = MutableLiveData<String>()
    val appVersion: LiveData<String> = _appVersion

    // Password change result
    private val _passwordChangeResult = MutableLiveData<String>()
    val passwordChangeResult: LiveData<String> = _passwordChangeResult

    // Logout status
    private val _logoutStatus = MutableLiveData<Boolean>()
    val logoutStatus: LiveData<Boolean> = _logoutStatus

    // Upload avatar result
    private val _avatarUploadResult = MutableLiveData<Result<String>>()
    val avatarUploadResult: LiveData<Result<String>> = _avatarUploadResult

    // Member operation result
    private val _memberOperationResult = MutableLiveData<Result<String>>()
    val memberOperationResult: LiveData<Result<String>> = _memberOperationResult

    // family code status
    private val _familyCodeStatus = MutableLiveData<String>()
    val familyCodeStatus: LiveData<String> = _familyCodeStatus

    // Fetch user data
    fun fetchUserData() {
        viewModelScope.launch {
            try {
                val userData = repository.getUserData()
                _userData.value = userData
            } catch (e: Exception) {
                // Xử lý lỗi
            }
        }
    }
    //FetchFamilyCode
    fun fetchFamilyCode(){
        viewModelScope.launch{
            try{
                val familyCode = repository.getFamilyCode()
                _familyCodeStatus.value = familyCode ?: "Chưa có mã gia đình"
            } catch (e: Exception) {
                _familyCodeStatus.value = "Lỗi khi lấy mã gia đình: ${e.message}"
            }
        }
    }

    // Fetch family members
    fun fetchFamilyMembers() {
        viewModelScope.launch {
            try {
                val members = repository.getFamilyMembers()
                
                // Sắp xếp thành viên theo vai trò: phụ huynh chính trước, phụ huynh phụ sau, và cuối cùng là trẻ em
                val sortedMembers = members.sortedWith(compareBy { 
                    when (it.role) {
                        "parent_primary" -> 0   // Phụ huynh chính hiển thị đầu tiên
                        "parent_secondary" -> 1 // Phụ huynh phụ hiển thị thứ hai
                        "child" -> 2            // Trẻ em hiển thị cuối cùng
                        else -> 3               // Các vai trò khác (nếu có)
                    }
                })
                
                _familyMembers.value = sortedMembers
            } catch (e: Exception) {
                // Xử lý lỗi
            }
        }
    }

    // Update profile
    fun updateProfile(memberId: String, name: String, phone: String) {
        viewModelScope.launch {
            try {
                val success = repository.updateProfile(memberId, name, phone)
                if (success) {
                    fetchUserData()
                    fetchFamilyMembers()
                    _memberOperationResult.value = Result.success("Cập nhật thông tin thành công")
                } else {
                    _memberOperationResult.value = Result.failure(Exception("Không thể cập nhật thông tin"))
                }
            } catch (e: Exception) {
                _memberOperationResult.value = Result.failure(e)
            }
        }
    }

    // Upload avatar 
    fun uploadAvatar(memberId: String, uri: Uri) {
        viewModelScope.launch {
            try {
                val avatarUrl = repository.uploadAvatar(memberId, uri)
                _avatarUploadResult.value = Result.success(avatarUrl)
                
                // Refresh data
                fetchUserData()
                fetchFamilyMembers()
            } catch (e: Exception) {
                _avatarUploadResult.value = Result.failure(e)
            }
        }
    }

    // Change password
    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            try {
                val result = repository.changePassword(currentPassword, newPassword)
                result.fold(
                    onSuccess = {
                        _passwordChangeResult.value = "Đổi mật khẩu thành công"
                    },
                    onFailure = { error ->
                        _passwordChangeResult.value = "Không thể đổi mật khẩu: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                _passwordChangeResult.value = "Không thể đổi mật khẩu: ${e.message}"
            }
        }
    }

    // Delete family member
    fun deleteFamilyMember(memberId: String) {
        viewModelScope.launch {
            try {
                val success = repository.deleteFamilyMember(memberId)
                if (success) {
                    fetchFamilyMembers()
                    _memberOperationResult.value = Result.success("Xóa thành viên thành công")
                } else {
                    _memberOperationResult.value = Result.failure(Exception("Không thể xóa thành viên"))
                }
            } catch (e: Exception) {
                _memberOperationResult.value = Result.failure(e)
            }
        }
    }

    // Add family member
    fun addFamilyMember(name: String, phone: String, role: String) {
        viewModelScope.launch {
            try {
                val memberId = repository.addFamilyMember(name, phone, role)
                fetchFamilyMembers()
                _memberOperationResult.value = Result.success("Thêm thành viên thành công")
            } catch (e: Exception) {
                _memberOperationResult.value = Result.failure(e)
            }
        }
    }

    // Fetch app version
    fun fetchAppVersion(context: Context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            _appVersion.value = packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            _appVersion.value = "Unknown"
        }
    }

    // Logout
    fun logout() {
        viewModelScope.launch{
            repository.logoutUser()
            _logoutStatus.value = true
        }
    }
}
