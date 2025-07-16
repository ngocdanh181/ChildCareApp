package com.example.childlocate.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.childlocate.data.api.RetrofitInstance
import com.example.childlocate.data.model.ChatMessage
import com.example.childlocate.data.model.FcmRequest
import com.example.childlocate.data.model.Message
import com.example.childlocate.data.model.MessageStatus
import com.example.childlocate.data.model.MessageType
import com.example.childlocate.data.model.NotificationPayload
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.random.Random

class ChatRepository(private val context: Context) {
    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference

    // StateFlows for reactive data
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _memberNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val memberNames: StateFlow<Map<String, String>> = _memberNames.asStateFlow()

    private val _memberAvatars = MutableStateFlow<Map<String, String>>(emptyMap())
    val memberAvatars: StateFlow<Map<String, String>> = _memberAvatars.asStateFlow()

    private val _familyName = MutableStateFlow<String>("")
    val familyName: StateFlow<String> = _familyName.asStateFlow()

    // ===== MESSAGE OPERATIONS =====
    suspend fun sendTextMessage(familyId: String, senderId: String, content: String) {
        try {
            val messageId = database.child("messages")
                .child(familyId)
                .child("conversations")
                .push()
                .key ?: throw Exception("Failed to generate message ID")

            val message = ChatMessage(
                id = messageId,
                senderId = senderId,
                type = MessageType.TEXT,
                content = content,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENT
            )

            // Save message
            database.child("messages")
                .child(familyId)
                .child("conversations")
                .child(messageId)
                .setValue(message)
                .await()

            // Update last message
            database.child("messages")
                .child(familyId)
                .child("lastMessage")
                .setValue(mapOf(
                    "messageId" to messageId,
                    "content" to content,
                    "timestamp" to message.timestamp,
                    "senderId" to senderId
                ))
                .await()

            // Send notification
            sendChatNotification(familyId, senderId)

            // Update message status to delivered after a short delay
            delay(1000)
            updateMessageStatus(familyId, messageId, MessageStatus.DELIVERED)

        } catch (e: Exception) {
            throw Exception("Failed to send message: ${e.message}")
        }
    }

    suspend fun sendImageMessage(familyId: String, senderId: String, imageUri: Uri) = withContext(
        Dispatchers.IO)  {
        try {
            val imageId = generateNumberId()
            val storageRef = storage.child("messages/$familyId/$imageId.jpg")

            storageRef.putFile(imageUri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            val messageId = database.child("messages")
                .child(familyId)
                .child("conversations")
                .push()
                .key ?: throw Exception("Failed to generate message ID")

            val message = ChatMessage(
                id = messageId,
                senderId = senderId,
                type = MessageType.IMAGE,
                content = downloadUrl,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENT
            )

            // Save message
            database.child("messages")
                .child(familyId)
                .child("conversations")
                .child(messageId)
                .setValue(message)
                .await()

            // Update last message
            database.child("messages")
                .child(familyId)
                .child("lastMessage")
                .setValue(mapOf(
                    "messageId" to messageId,
                    "content" to "📷 Image",
                    "timestamp" to message.timestamp,
                    "senderId" to senderId
                ))
                .await()

            // Send notification
            sendChatNotification(familyId, senderId)

            // Update message status to delivered after a short delay
            delay(1000)
            updateMessageStatus(familyId, messageId, MessageStatus.DELIVERED)

        } catch (e: Exception) {
            throw Exception("Failed to send image: ${e.message}")
        }
    }

    suspend fun sendAudioMessage(familyId: String, senderId: String, audioFile: File, durationMs: Long) = withContext(
        Dispatchers.IO)  {
        try {
            val audioId = generateNumberId()
            val storageRef = storage.child("messages/$familyId/audio/$audioId.mp3")

            // Upload audio file
            val uploadTask = storageRef.putFile(Uri.fromFile(audioFile))
            uploadTask.await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            val messageId = database.child("messages")
                .child(familyId)
                .child("conversations")
                .push()
                .key ?: throw Exception("Failed to generate message ID")

            val durationSecs = (durationMs / 1000).toInt()

            val message = ChatMessage(
                id = messageId,
                senderId = senderId,
                type = MessageType.AUDIO,
                content = downloadUrl,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENT
            )

            // Save message
            database.child("messages")
                .child(familyId)
                .child("conversations")
                .child(messageId)
                .setValue(message)
                .await()

            // Update last message
            database.child("messages")
                .child(familyId)
                .child("lastMessage")
                .setValue(mapOf(
                    "messageId" to messageId,
                    "content" to "🎵 Tin nhắn thoại (${formatDuration(durationSecs.toLong())})",
                    "timestamp" to message.timestamp,
                    "senderId" to senderId
                ))
                .await()

            // Send notification
            sendChatNotification(familyId, senderId)

            // Update message status to delivered after a short delay
            delay(1000)
            updateMessageStatus(familyId, messageId, MessageStatus.DELIVERED)

            // Clean up
            audioFile.delete()

        } catch (e: Exception) {
            throw Exception("Failed to send audio message: ${e.message}")
        }
    }

    // ===== LOAD DATA OPERATIONS =====
    fun loadMessages(familyId: String) {
        try {
            val chatRef = database.child("messages")
                .child(familyId)
                .child("conversations")
                .orderByChild("timestamp")

            chatRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messageList = snapshot.children.mapNotNull { dataSnapshot ->
                        dataSnapshot.getValue(ChatMessage::class.java)
                    }
                    _messages.value = messageList
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatRepository", "Error loading messages: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error loading messages: ${e.message}")
        }
    }

    fun loadFamilyMembers(familyId: String) {
        try {
            val membersRef = database.child("families/$familyId/members")
            val familyNameRef = database.child("families/$familyId")

            // Load family name
            familyNameRef.child("familyName").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val familyName = snapshot.getValue(String::class.java) ?: ""
                    _familyName.value = familyName
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatRepository", "Error loading family name: ${error.message}")
                }
            })

            // Load members
            membersRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val memberNames = mutableMapOf<String, String>()
                    val memberAvatars = mutableMapOf<String, String>()

                    snapshot.children.forEach { member ->
                        val memberId = member.key ?: return@forEach
                        val name = member.child("name").getValue(String::class.java) ?: "Unknown"
                        val avatarUrl = member.child("avatarUrl").getValue(String::class.java) ?: ""

                        memberNames[memberId] = name
                        memberAvatars[memberId] = avatarUrl
                    }

                    _memberNames.value = memberNames
                    _memberAvatars.value = memberAvatars
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatRepository", "Error loading family members: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error loading family members: ${e.message}")
        }
    }

    // ===== PRIVATE HELPER METHODS =====
    private suspend fun sendChatNotification(familyId: String, senderId: String) = withContext(
        Dispatchers.IO) {
        try {
            // Get sender name
            val senderName = getSenderName(familyId,senderId)

            // Get family member tokens (exclude sender)
            val tokens = getFamilyMemberTokens(familyId, senderId)

            Log.d("ChatRepository", "Tokens to notify: $tokens")

            // Send notification to each token
            /*tokens.forEach { token ->
                sendNotificationToDevice(token, senderName)
            }*/
            sendNotificationToDevice(tokens, senderId, senderName )


        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to send notification: ${e.message}")
        }
    }

    private suspend fun getSenderName(familyId:String,senderId: String): String {
        return try {
            val snapshot = database.child("families")
                .child(familyId)
                .child("members")
                .child(senderId)
                .get()
                .await()

            snapshot.child("name").getValue(String::class.java) ?: "Unknown"
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error getting sender name: ${e.message}")
            "Unknown"
        }
    }

    private suspend fun getFamilyMemberTokens(familyId: String, excludeSenderId: String): List<String> {
        return try {
            val tokens = mutableListOf<String>()

            // Get family members
            val familySnapshot = database.child("families").child(familyId).child("members").get().await()


            familySnapshot.children.forEach { member ->
                val memberId = member.key
                Log.d("ChatRepository", "Checking member: $memberId")
                if (memberId != null && memberId != excludeSenderId) {
                    // Get primary device token for this member
                    val tokenSnapshot = database.child("users/$memberId/primaryDeviceToken").get().await()
                    val token = tokenSnapshot.getValue(String::class.java)
                    if (!token.isNullOrEmpty()) {
                        tokens.add(token)
                    }
                }
            }

            tokens
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error getting family member tokens: ${e.message}")
            emptyList()
        }
    }

    private suspend fun sendNotificationToDevice(tokens: List<String>, senderId: String, senderName: String) {
        try {

            val serviceAccount = context.assets.open("childlocatedemo.json")
            val credentials = GoogleCredentials.fromStream(serviceAccount)
                .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

            val accessToken = credentials.refreshAccessToken().tokenValue
            val authHeader = "Bearer $accessToken"

            Log.d("ChatRepository", "Access token obtained: ${accessToken.take(20)}...")
            for (token in tokens) {
                val request = FcmRequest(
                    message = Message(
                        token = token,
                        notification = NotificationPayload(
                            title = senderName,
                            body = "đã gửi một tin nhắn"
                        )
                    )
                )
                val response = RetrofitInstance.api.sendLocationRequest(authHeader, request)
                if (!response.isSuccessful) {
                    Log.e("ChildRepository", "Failed to send Notification  to token: $token")
                }
            }


        } catch (e: IOException) {
            Log.e("ChatRepository", "Error sending notification: ${e.message}")
        }
    }

    private suspend fun updateMessageStatus(familyId: String, messageId: String, status: MessageStatus) {
        try {
            database.child("messages")
                .child(familyId)
                .child("conversations")
                .child(messageId)
                .child("status")
                .setValue(status)
                .await()
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error updating message status: $e")
        }
    }

    private fun generateNumberId(): String = Random.nextInt(0, 999999999).toString()

    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }
} 