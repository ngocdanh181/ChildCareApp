package com.example.childlocate.ui.parent.login

// AuthViewModel.kt
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

class AuthViewModel : ViewModel() {
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    fun loginUser(email: String, password: String, role: String = "PRIMARY") {
        viewModelScope.launch {
            try {
                val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                val uid = result.user?.uid ?: throw Exception("User ID not found")
                
                val user = getUserData(uid)
                handleMultiDeviceLogin(uid)
                
                // For SECONDARY parent, check if they need family verification
                if (role == "SECONDARY" && (user.familyId.isEmpty() || user.familyId == "null")) {
                    _authState.value = AuthState.NeedsFamilyVerification
                } else {
                    _authState.value = AuthState.LoggedIn(user)
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun registerUser(email: String, password: String, role: String = "PRIMARY") {
        // Validate input trước khi gửi lên Firebase
        val passwordError = validatePassword(password)
        if (passwordError != null) {
            _authState.value = AuthState.Error(passwordError)
            return
        }

        if (!isValidEmail(email)) {
            _authState.value = AuthState.Error("Email không hợp lệ")
            return
        }

        viewModelScope.launch {
            try {
                val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                val uid = result.user?.uid ?: throw Exception("User ID not found")
                
                when (role) {
                    "PRIMARY" -> {
                        registerPrimaryParent(email, uid)
                    }
                    "SECONDARY" -> {
                        registerSecondaryParent(email, uid)
                    }
                }
                
                handleMultiDeviceLogin(uid)
                
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
            }
        }
    }

    private suspend fun registerPrimaryParent(email: String, uid: String) {
        // Tạo family với code unique
        val familyId = database.push().key!!
        val familyCode = generateFamilyCode()
        
        val user = User(
            email = email,
            userId = uid,
            role = "parent_primary",
            familyId = familyId
        )
        
        saveUserAndFamily(user, familyId, familyCode)
        _authState.value = AuthState.ShowWelcome(familyCode)
    }

    private suspend fun registerSecondaryParent(email: String, uid: String) {
        // Tạo user không có familyId
        val user = User(
            email = email,
            userId = uid,
            role = "parent_secondary",
            familyId = "" // Sẽ được cập nhật sau khi verify
        )
        
        // Lưu user vào database
        database.child("users").child(uid).setValue(user).await()
        _authState.value = AuthState.NeedsFamilyVerification
    }

    // Password validation function
    fun validatePassword(password: String): String? {
        return when {
            password.length < 8 -> "Mật khẩu phải có ít nhất 8 ký tự"
            !password.contains(Regex("[A-Z]")) -> "Mật khẩu phải có ít nhất 1 chữ hoa"
            !password.contains(Regex("[a-z]")) -> "Mật khẩu phải có ít nhất 1 chữ thường"
            !password.contains(Regex("[0-9]")) -> "Mật khẩu phải có ít nhất 1 số"
            !password.contains(Regex("[^A-Za-z0-9]")) -> "Mật khẩu phải có ít nhất 1 ký tự đặc biệt"
            else -> null // Valid password
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun generateFamilyCode(): String {
        val pushKey = database.push().key!!
        val hash = abs(pushKey.hashCode())
        return hash.toString(36).padStart(6, '0').take(6).uppercase()
    }

    private suspend fun saveUserAndFamily(user: User, familyId: String, familyCode: String) {
        val familyData = mapOf(
            "familyCode" to familyCode,
            "familyName" to "Gia đình mới",
            "primaryParentId" to user.userId,
            "createdAt" to ServerValue.TIMESTAMP,
            "members" to mapOf(
                user.userId to mapOf(
                    "role" to "parent_primary",
                    "name" to user.email.substringBefore("@"), // Temporary name
                    "joinedAt" to ServerValue.TIMESTAMP
                )
            )
        )

        // Atomic write với familyCode mapping cho O(1) lookup
        val updates = mapOf(
            "users/${user.userId}" to user,
            "families/$familyId" to familyData,
            "familyCodes/$familyCode" to familyId  // ← KEY: O(1) lookup mapping
        )
        
        database.updateChildren(updates).await()
    }

    private suspend fun handleMultiDeviceLogin(userId: String) {
        try {
            val fcmToken = FirebaseMessaging.getInstance().token.await()
            val deviceInfo = mapOf(
                "deviceName" to getDeviceName(),
                "platform" to "android",
                "loginTime" to ServerValue.TIMESTAMP,
                "lastSeen" to ServerValue.TIMESTAMP
            )

            
            // Simple approach: Update both primary token and device list
            val updates = mapOf(
                "users/$userId/primaryDeviceToken" to fcmToken,
                "users/$userId/activeDevices/$fcmToken" to deviceInfo
            )
            
            database.updateChildren(updates).await()
                
        } catch (e: Exception) {
            // Non-critical, continue login
        }
    }

    private fun getDeviceName(): String {
        return try {
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        } catch (e: Exception) {
            "Unknown Device"
        }
    }

    private suspend fun getUserData(userId: String): User {
        return database.child("users").child(userId).get().await().getValue(User::class.java)
            ?: throw Exception("User data not found")
    }

    // Helper function để verify family code với O(1) lookup
    suspend fun verifyFamilyCode(code: String): String? {
        return try {
            database.child("familyCodes")
                .child(code)
                .get().await()
                .getValue(String::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun logoutUser() {
        viewModelScope.launch {
            try {
                val currentUser = firebaseAuth.currentUser
                if (currentUser != null) {
                    // Remove current device from active devices
                    val fcmToken = FirebaseMessaging.getInstance().token.await()
                    database.child("users").child(currentUser.uid)
                        .child("activeDevices").child(fcmToken)
                        .removeValue().await()
                }
                
                firebaseAuth.signOut()
                _authState.value = AuthState.LoggedOut
                
            } catch (e: Exception) {
                // Force logout anyway
                firebaseAuth.signOut()
                _authState.value = AuthState.LoggedOut
            }
        }
    }
}

sealed class AuthState {
    data object LoggedOut : AuthState()
    data class LoggedIn(val user: User) : AuthState()
    data class ShowWelcome(val familyCode: String) : AuthState()
    data object NeedsFamilyVerification : AuthState()
    data class Error(val message: String) : AuthState()
}

data class User(
    val email: String = "",
    val userId: String = "",
    val role: String = "",
    val familyId: String = ""
)