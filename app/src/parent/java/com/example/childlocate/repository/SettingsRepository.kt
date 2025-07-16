package com.example.childlocate.repository

import android.net.Uri
import android.util.Log
import com.example.childlocate.data.model.FamilyMember
import com.example.childlocate.data.model.UserData
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SettingsRepository {
    private val TAG = "SettingsRepository"
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference

    private var currentFamilyID : String = ""


    // Get current user data
    suspend fun getUserData(): UserData = withContext(Dispatchers.IO) {
        val userId = firebaseAuth.currentUser?.uid ?: throw Exception("User not logged in")

        // Get user details from users node
        val userSnapshot = database.child("users")
            .child(userId)
            .get()
            .await()

        val email = userSnapshot.child("email").getValue(String::class.java) ?: ""
        val familyId = userSnapshot.child("familyId").getValue(String::class.java) ?: ""
        val role = userSnapshot.child("role").getValue(String::class.java) ?: ""

        currentFamilyID = familyId

        // Get family data
        val familySnapshot = database.child("families")
            .child(familyId)
            .get()
            .await()

        val familyName = familySnapshot.child("familyName").getValue(String::class.java) ?: ""

        // Lấy thông tin của thành viên
        var name = ""
        var phone = ""
        var avatarUrl = ""

        // Tìm member thông qua memberId trong family
        val memberSnapshot = familySnapshot.child("members").child(userId)
        if (memberSnapshot.exists()) {
            name = memberSnapshot.child("name").getValue(String::class.java) ?: ""
            phone = memberSnapshot.child("phone").getValue(String::class.java) ?: ""
            avatarUrl = memberSnapshot.child("avatarUrl").getValue(String::class.java) ?: ""
        }

        UserData(
            userId = userId,
            name = name,
            email = email,
            phone = phone,
            role = role,
            avatarUrl = avatarUrl,
            familyId = familyId,
            familyName = familyName
        )
    }
    //get familyCode
    suspend fun getFamilyCode():String{
        val userId= firebaseAuth.currentUser?.uid ?: throw Exception("User not logged in")
        val userSnapshot = database.child("users").child(userId).get().await()
        val familyId = userSnapshot.child("familyId").getValue(String::class.java) ?: ""
        if (familyId.isEmpty()) {
            throw Exception("User is not part of any family")
        }
        return database.child("families").child(familyId).child("familyCode").get().await().getValue(String::class.java) ?: ""
    }

    // Get family members
    suspend fun getFamilyMembers(): List<FamilyMember> = withContext(Dispatchers.IO) {
        val userId = firebaseAuth.currentUser?.uid ?: throw Exception("User not logged in")


        val userSnapshot = database.child("users").child(userId).get().await()
        val familyId = userSnapshot.child("familyId").getValue(String::class.java) ?: ""
        
        val members = mutableListOf<FamilyMember>()

        val familySnapshot = database.child("families").child(familyId).get().await()
        val membersSnapshot = familySnapshot.child("members")
        val primaryParentId = familySnapshot.child("primaryParentId").getValue(String::class.java) ?: ""

        // Lấy tất cả thành viên trong family
        for (memberSnapshot in membersSnapshot.children) {
            val memberId = memberSnapshot.key ?: continue
            val name = memberSnapshot.child("name").getValue(String::class.java) ?: ""
            val avatarUrl = memberSnapshot.child("avatarUrl").getValue(String::class.java) ?: ""
            val phone = memberSnapshot.child("phone").getValue(String::class.java) ?: ""
            val role = memberSnapshot.child("role").getValue(String::class.java) ?: "unknown"

            members.add(
                FamilyMember(
                    id = memberId,
                    name = name,
                    role = role,
                    avatarUrl = avatarUrl,
                    phone = phone,
                )
            )
        }

        members
    }

    // Update profile
    suspend fun updateProfile(memberId: String, name: String, phone: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val userId = firebaseAuth.currentUser?.uid ?: return@withContext false

            val userSnapshot = database.child("users").child(userId).get().await()
            val familyId = userSnapshot.child("familyId").getValue(String::class.java) ?: ""
            val currentUserRole = userSnapshot.child("role").getValue(String::class.java) ?: ""

            // Check if current user is permitted to update this member
            val canEdit = isAllowedToEditMember(currentUserRole, memberId, userId, familyId)
            if (!canEdit) return@withContext false

            val memberRef = database.child("families")
                .child(familyId)
                .child("members")
                .child(memberId)

            val updates = hashMapOf<String, Any>(
                "name" to name,
                "phone" to phone
            )

            memberRef.updateChildren(updates).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile", e)
            false
        }
    }

    // Upload avatar for specific member
    suspend fun uploadAvatar(memberId: String, uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val userId = firebaseAuth.currentUser?.uid ?: throw Exception("User not logged in")


            val userSnapshot = database.child("users").child(userId).get().await()
            val familyId = userSnapshot.child("familyId").getValue(String::class.java) ?: ""
            val currentUserRole = userSnapshot.child("role").getValue(String::class.java) ?: ""

            // Check if current user is permitted to update this member
            val canEdit = isAllowedToEditMember(currentUserRole, memberId, userId, familyId)
            if (!canEdit) throw Exception("Not authorized to edit this member")

            // Sử dụng cấu trúc lưu trữ theo familyId/memberId
            val avatarRef = storage.child("avatars/$familyId/$memberId.jpg")

            avatarRef.putFile(uri).await()
            val downloadUrl = avatarRef.downloadUrl.await().toString()

            // Update the avatar URL in the database
            database.child("families")
                .child(familyId)
                .child("members")
                .child(memberId)
                .child("avatarUrl")
                .setValue(downloadUrl)
                .await()

            downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading avatar", e)
            throw e
        }
    }

    // Change password
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = firebaseAuth.currentUser ?: return@withContext Result.failure(Exception("User not logged in"))
            val email = user.email ?: return@withContext Result.failure(Exception("User email is null"))

            // Re-authenticate user
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()

            // Update password
            user.updatePassword(newPassword).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error changing password", e)
            Result.failure(e)
        }
    }

    // Delete family member
    suspend fun deleteFamilyMember(memberId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val userId = firebaseAuth.currentUser?.uid ?: return@withContext false

            val userSnapshot = database.child("users").child(userId).get().await()
            val familyId = userSnapshot.child("familyId").getValue(String::class.java) ?: ""
            val currentUserRole = userSnapshot.child("role").getValue(String::class.java) ?: ""

            val familySnapshot = database.child("families").child(familyId).get().await()
            val primaryParentId = familySnapshot.child("primaryParentId").getValue(String::class.java) ?: ""

            // Only primary parent can delete members
            if (currentUserRole != "parent_primary") {
                return@withContext false
            }

            // Check if the member being deleted is the primary parent - can't delete self
            if (memberId == primaryParentId) {
                return@withContext false
            }

            // Delete the member's avatar from storage
            try {
                storage.child("avatars/$familyId/$memberId.jpg").delete().await()
            } catch (e: Exception) {
                // Avatar might not exist, continue with deletion
                Log.w(TAG, "Avatar doesn't exist or could not be deleted", e)
            }

            // Delete the member from family
            database.child("families")
                .child(familyId)
                .child("members")
                .child(memberId)
                .removeValue()
                .await()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting family member", e)
            false
        }
    }

    // Check if current user is allowed to edit a specific member
    private suspend fun isAllowedToEditMember(currentUserRole: String, memberId: String, userId: String, familyId: String): Boolean {
        // Primary parent can edit everyone
        if (currentUserRole == "parent_primary") return true

        // Secondary parent can edit themselves and children, but not primary parent
        if (currentUserRole == "parent_secondary") {
            // Check if target is primary parent
            val familySnapshot = database.child("families").child(familyId).get().await()
            val primaryParentId = familySnapshot.child("primaryParentId").getValue(String::class.java) ?: ""

            if (memberId == primaryParentId) return false

            // If editing own profile
            if (memberId == userId) return true

            // If editing child profile
            return isChildMember(memberId, familyId)
        }

        // All users can edit their own profile
        return memberId == userId
    }

    // Check if member is a child
    private suspend fun isChildMember(memberId: String, familyId: String): Boolean {
        // Kiểm tra xem memberId có tồn tại trong users không
        val userSnapshot = database.child("users").child(memberId).get().await()

        // Nếu không có tài khoản user, đó là trẻ em
        if (!userSnapshot.exists()) return true

        // Nếu có tài khoản, kiểm tra role
        val role = userSnapshot.child("role").getValue(String::class.java) ?: ""

        return role != "parent_primary" && role != "parent_secondary"
    }

    // Add new family member
    suspend fun addFamilyMember(name: String, phone: String, role: String): String = withContext(Dispatchers.IO) {
        try {
            val userId = firebaseAuth.currentUser?.uid ?: throw Exception("User not logged in")


            val userSnapshot = database.child("users").child(userId).get().await()
            val familyId = userSnapshot.child("familyId").getValue(String::class.java) ?: ""
            val currentUserRole = userSnapshot.child("role").getValue(String::class.java) ?: ""

            // Only primary parent or secondary parent can add members
            if (currentUserRole != "parent_primary" && currentUserRole != "parent_secondary") {
                throw Exception("Not authorized to add family members")
            }

            // For secondary parent, they can only add children
            if (currentUserRole == "parent_secondary" && role != "child") {
                throw Exception("Secondary parents can only add children")
            }

            // Generate a unique member ID
            val memberId = database.child("families")
                .child(familyId)
                .child("members")
                .push()
                .key ?: throw Exception("Failed to create member ID")

            // Create member object
            val member = hashMapOf(
                "name" to name,
                "phone" to phone,
                "avatarUrl" to ""
            )

            // Add member to family
            database.child("families")
                .child(familyId)
                .child("members")
                .child(memberId)
                .setValue(member)
                .await()

            memberId
        } catch (e: Exception) {
            Log.e(TAG, "Error adding family member", e)
            throw e
        }
    }

    // Logout
    fun logout() {
        firebaseAuth.signOut()
    }

    suspend fun logoutUser(): Boolean {
        return try {
            val currentUser = firebaseAuth.currentUser
            if (currentUser != null) {
               val fcmToken = FirebaseMessaging.getInstance().token.await()
                database.child("users").child(currentUser.uid)
                    .child("activeDevices").child(fcmToken)
                    .removeValue().await()
            }

            firebaseAuth.signOut()
            true
        } catch (e: Exception) {
            // Force logout anyway
            firebaseAuth.signOut()
            true // Indicate there was an error but logout still happened
        }
    }
}