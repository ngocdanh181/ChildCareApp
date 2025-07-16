package com.example.childlocate.ui.parent.verify

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class VerifyFamilyViewModel : ViewModel() {
    private val database = FirebaseDatabase.getInstance().reference
    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _verificationResult = MutableLiveData<Result<String>>()
    val verificationResult: LiveData<Result<String>> = _verificationResult

    fun verifyFamilyId(familyCode: String) {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            _verificationResult.value = Result.failure(Exception("Người dùng chưa đăng nhập"))
            return
        }
        viewModelScope.launch {
            try {
                // Kiểm tra xem family ID có tồn tại hay khong?
                val snapshot = database.child("familyCodes")
                    .child(familyCode)
                    .get()
                    .await()

                val role = database.child(("users")).child(currentUser.uid).child("role").get().await().getValue(String::class.java)


                if (snapshot.exists()) {
                    val familyId = snapshot.getValue(String::class.java)
                    if(role == "parent_secondary") {
                    // Cập nhật thông tin user hiện tại và join family
                        joinExistingFamily(currentUser.uid, familyId!!)
                    }
                } else {
                    _verificationResult.value = Result.failure(Exception("Không tìm thấy Family ID"))
                }
            } catch (e: Exception) {
                _verificationResult.value = Result.failure(e)
            }
        }
    }

    private suspend fun joinExistingFamily(currentUserId: String, familyId: String) {
        try {
            // Cập nhật familyId cho user hiện tại
            database.child("users").child(currentUserId).child("familyId")
                .setValue(familyId).await()

            // Lấy thông tin user hiện tại để thêm vào family
            val userSnapshot = database.child("users").child(currentUserId).get().await()
            val userName = userSnapshot.child("email").getValue(String::class.java)?.substringBefore("@") ?: "Phụ huynh thứ hai"

            // Thêm user vào family members
            val memberData = mapOf(
                "role" to "parent_secondary",
                "name" to userName,
                "joinedAt" to ServerValue.TIMESTAMP
            )

            database.child("families").child(familyId).child("members").child(currentUserId)
                .setValue(memberData).await()

            _verificationResult.value = Result.success(familyId)

        } catch (e: Exception) {
            _verificationResult.value = Result.failure(Exception("Không thể join family: ${e.message}"))
        }
    }
} 