package com.example.childlocate.ui.child.childstart

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChildViewModel : ViewModel() {
    private val database = FirebaseDatabase.getInstance().reference

    private val _verificationStatus = MutableLiveData<VerificationState>()
    val verificationStatus: LiveData<VerificationState> get() = _verificationStatus

    private val _childId = MutableLiveData<String>()
    val childId: LiveData<String> get() = _childId

    private val _familyId = MutableLiveData<String>()
    val familyId: LiveData<String> get() = _familyId

    private val _parentPhones = MutableLiveData<List<String>>()
    val parentPhones: LiveData<List<String>> get() = _parentPhones

    fun verifyFamilyCodeAndSaveChild(familyCode: String, childName: String) {
        viewModelScope.launch {
            try {
                _verificationStatus.value = VerificationState.Loading
                
                // Step 1: Verify family code với O(1) lookup
                val familyId = verifyFamilyCode(familyCode)
                if (familyId == null) {
                    _verificationStatus.value = VerificationState.Error("Mã gia đình không hợp lệ")
                    return@launch
                }
                
                // Step 2: Generate unique child ID
                val childId = database.push().key!!
                _childId.value = childId
                _familyId.value = familyId!!
                
                // Step 3: Save child data
                saveChildData(childId, familyId, childName, familyCode)

                //step 4: load parent phone
                loadParentPhones(familyId)
                
            } catch (e: Exception) {
                _verificationStatus.value = VerificationState.Error(e.message ?: "Có lỗi xảy ra")
            }
        }
    }
    
    private suspend fun verifyFamilyCode(code: String): String? {
        return try {
            database.child("familyCodes")
                .child(code)
                .get().await()
                .getValue(String::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun loadParentPhones(familyId: String) {
        try {
            val snapshot = database.child("families")
                .child(familyId)
                .child("members")
                .get()
                .await()

            val parentPhones = mutableListOf<String>()

            for (childSnapshot in snapshot.children) {
                val role = childSnapshot.child("role").getValue(String::class.java)
                val phone = childSnapshot.child("phone").getValue(String::class.java) ?:""

                // Kiểm tra là phụ huynh và có số điện thoại hợp lệ
                if (role != "child" && phone.isNotEmpty()) {
                    parentPhones.add(phone)
                }
            }

            if (parentPhones.isEmpty()) {
                _verificationStatus.value = VerificationState.Error("Không tìm thấy số điện thoại phụ huynh")
                return
            }

            _parentPhones.value = parentPhones

        } catch (e: Exception) {
            Log.e("ChildViewModel", "Error loading parent phones: ${e.message}")
            _verificationStatus.value = VerificationState.Error("Lỗi khi tải thông tin phụ huynh: ${e.message}")
        }
    }
    
    private suspend fun saveChildData(childId: String, familyId: String, childName: String, familyCode: String) {
        try {
            // Get device token
            val fcmToken = FirebaseMessaging.getInstance().token.await()
            // Prepare child family member data
            val childFamilyData = mapOf(
                "role" to "child",
                "name" to childName,
                "joinedAt" to ServerValue.TIMESTAMP,

            )

            val childUserData = mapOf(
                "userId" to childId,
                "familyId" to familyId,
                "name" to childName,
                "role" to "child",
                "createdAt" to ServerValue.TIMESTAMP,
                "primaryDeviceToken" to fcmToken
            )

            val deviceInfo = mapOf(
                "deviceName" to getDeviceName(),
                "platform" to "android", 
                "loginTime" to ServerValue.TIMESTAMP,
                "lastSeen" to ServerValue.TIMESTAMP
            )
            
            // Atomic write: Save to both users and families
            val updates = mapOf(
                "users/$childId" to childUserData,
                "families/$familyId/members/$childId" to childFamilyData
            )
            
            database.updateChildren(updates).await()
            _verificationStatus.value = VerificationState.Success(familyCode, familyId)
            
        } catch (e: Exception) {
            _verificationStatus.value = VerificationState.Error("Không thể lưu thông tin: ${e.message}")
            Log.d("ChildViewModel","${e.message}")
        }
    }
    
    private fun getDeviceName(): String {
        return try {
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        } catch (e: Exception) {
            "Unknown Device"
        }
    }
}

sealed class VerificationState {
    data object Loading : VerificationState()
    data class Success(val familyCode: String, val familyId: String) : VerificationState()
    data class Error(val message: String) : VerificationState()
}

