package com.example.childlocate.ui.child.childchat

import android.app.Application
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.ChatMessage
import com.example.childlocate.data.model.MessageStatus
import com.example.childlocate.data.model.MessageType
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class ChildChatDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _familyName = MutableStateFlow<String>("")
    val familyName: StateFlow<String> = _familyName.asStateFlow()

    private val _memberNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val memberNames: StateFlow<Map<String, String>> = _memberNames.asStateFlow()

    private val _memberAvatars = MutableStateFlow<Map<String, String>>(emptyMap())
    val memberAvatars: StateFlow<Map<String, String>> = _memberAvatars.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Thêm StateFlow cho trạng thái ghi âm
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    private val _recordingError = MutableStateFlow<String?>(null)
    val recordingError: StateFlow<String?> = _recordingError.asStateFlow()

    private var senderId: String = ""
    private var familyId: String = ""
    private var receiverId: String = ""

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var recordingStartTime: Long = 0
    private val MAX_RECORDING_DURATION_MS = TimeUnit.MINUTES.toMillis(5) // 5 phút

    fun setChatParticipants(senderId: String, receiverId: String) {
        this.senderId = senderId
        this.receiverId = receiverId
        // Giả sử familyId là receiverId trong trường hợp chat 1-1
        this.familyId = receiverId
        Log.d("DetailChatViewModel", "Family ID: $familyId va Sender ID: $senderId")
        loadFamilyMembers()
        loadMessages()
    }

    fun sendMessage(messageText: String) {
        viewModelScope.launch {
            try {
                val messageId = database.child("messages")
                    .child(familyId)
                    .child("conversations")
                    .push()
                    .key ?: return@launch

                val message = ChatMessage(
                    id = messageId,
                    senderId = senderId,
                    type = MessageType.TEXT,
                    content = messageText,
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
                        "content" to messageText,
                        "timestamp" to message.timestamp,
                        "senderId" to senderId
                    ))
                    .await()

                // Update message status to delivered after a short delay
                delay(1000)
                updateMessageStatus(messageId, MessageStatus.DELIVERED)
            } catch (e: Exception) {
                // Log error
                _recordingError.value = "Không thể gửi tin nhắn: ${e.localizedMessage}"
            }
        }
    }

    fun uploadMessage(uri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val imageId = generateNumberId()
                val storageRef = storage.child("messages/$familyId/$imageId.jpg")

                storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                val messageId = database.child("messages")
                    .child(familyId)
                    .child("conversations")
                    .push()
                    .key ?: return@launch

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

                // Update message status to delivered after a short delay
                delay(1000)
                updateMessageStatus(messageId, MessageStatus.DELIVERED)
            } catch (e: Exception) {
                _recordingError.value = "Không thể tải lên ảnh: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun updateMessageStatus(messageId: String, status: MessageStatus) {
        try {
            database.child("messages")
                .child(familyId)
                .child("conversations")
                .child(messageId)
                .child("status")
                .setValue(status)
                .await()
        } catch (e: Exception) {
            // Log error
            _recordingError.value = "Không thể cập nhật trạng thái tin nhắn: ${e.localizedMessage}"
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            try {
                val chatRef = database.child("messages")
                    .child(familyId)
                    .child("conversations")
                    .orderByChild("timestamp")

                val snapshot = chatRef.get().await()
                val messageList = snapshot.children.mapNotNull { dataSnapshot ->
                    dataSnapshot.getValue(ChatMessage::class.java)
                }
                _messages.value = messageList
                Log.d("DetailChatViewModel", "Loaded messages: $messageList")

                // Listen for new messages
                chatRef.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val updatedMessages = snapshot.children.mapNotNull { dataSnapshot ->
                            dataSnapshot.getValue(ChatMessage::class.java)
                        }
                        _messages.value = updatedMessages
                    }

                    override fun onCancelled(error: DatabaseError) {
                        _recordingError.value = "Không thể tải tin nhắn: ${error.message}"
                        Log.d("DetailChatViewModel", "Error loading messages: ${error.message}")
                    }
                })
            } catch (e: Exception) {
                _recordingError.value = "Không thể tải tin nhắn: ${e.localizedMessage}"
                Log.d("DetailChatViewModel", "Error loading messages: ${e.localizedMessage}")
            }
        }
    }

    private fun loadFamilyMembers() {
        viewModelScope.launch {
            try {
                val membersRef = database.child("families/$familyId/members")
                val familyNameRef = database.child("families/$familyId")
                val snapshot = membersRef.get().await()
                val familyNameSnapshot = familyNameRef.child("familyName").get().await()
                val familyName = familyNameSnapshot.getValue(String::class.java) ?: ""
                _familyName.value = familyName

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
                Log.d("DetailChatViewModel", "Loaded family members: $memberNames")
            } catch (e: Exception) {
                _recordingError.value = "Không thể tải thông tin thành viên: ${e.localizedMessage}"
            }
        }
    }

    private fun generateNumberId(): String = Random.nextInt(0, 999999999).toString()

    fun getPhoneNumber(): String? {
        // Có thể lấy số điện thoại từ memberNames nếu có
        return _memberNames.value[receiverId]?.let { name ->
            // Implement logic to get phone from name or ID
            null // Chỉ là placeholder
        }
    }

    fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }

    fun startRecording() {
        viewModelScope.launch {
            try {
                // Kiểm tra xem đã đang ghi âm hay không
                if (_recordingState.value != RecordingState.IDLE) {
                    _recordingError.value = "Đang ghi âm, không thể bắt đầu ghi mới"
                    return@launch
                }

                _recordingState.value = RecordingState.STARTING

                audioFile = createTempAudioFile()

                mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    MediaRecorder(getApplication())
                } else {
                    @Suppress("DEPRECATION")
                    (MediaRecorder())
                }

                mediaRecorder?.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128000) // 128kbps
                    setAudioSamplingRate(44100) // 44.1kHz
                    setOutputFile(audioFile?.absolutePath)

                    try {
                        prepare()
                        start()

                        recordingStartTime = System.currentTimeMillis()
                        _recordingState.value = RecordingState.RECORDING

                        // Bắt đầu đếm thời gian ghi âm
                        startDurationCounter()

                        // Kiểm tra thời gian ghi âm tối đa
                        startRecordingTimeout()
                    } catch (e: IOException) {
                        _recordingError.value = "Lỗi chuẩn bị ghi âm: ${e.localizedMessage}"
                        releaseRecorder()
                        _recordingState.value = RecordingState.IDLE
                    }
                }
            } catch (e: Exception) {
                _recordingError.value = "Không thể bắt đầu ghi âm: ${e.localizedMessage}"
                releaseRecorder()
                _recordingState.value = RecordingState.IDLE
            }
        }
    }

    private fun startDurationCounter() {
        viewModelScope.launch {
            while (_recordingState.value == RecordingState.RECORDING) {
                _recordingDuration.value = System.currentTimeMillis() - recordingStartTime
                delay(100) // Cập nhật mỗi 100ms
            }
        }
    }

    private fun startRecordingTimeout() {
        viewModelScope.launch {
            delay(MAX_RECORDING_DURATION_MS)
            if (_recordingState.value == RecordingState.RECORDING) {
                _recordingError.value = "Đã đạt thời gian ghi âm tối đa (5 phút)"
                stopRecording()
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            try {
                if (_recordingState.value != RecordingState.RECORDING) {
                    return@launch
                }

                _recordingState.value = RecordingState.STOPPING

                mediaRecorder?.apply {
                    try {
                        stop()
                    } catch (e: IllegalStateException) {
                        _recordingError.value = "Lỗi dừng ghi âm: ${e.localizedMessage}"
                    }
                }

                releaseRecorder()

                // Kiểm tra nếu thời gian ghi âm quá ngắn
                val duration = _recordingDuration.value
                if (duration < 1000) { // Nếu ghi âm dưới 1 giây
                    _recordingError.value = "Tin nhắn âm thanh quá ngắn"
                    audioFile?.delete()
                    audioFile = null
                    _recordingState.value = RecordingState.IDLE
                    return@launch
                }

                audioFile?.let { file ->
                    _recordingState.value = RecordingState.UPLOADING
                    uploadAudioMessage(file)
                }

                _recordingState.value = RecordingState.IDLE
            } catch (e: Exception) {
                _recordingError.value = "Lỗi khi dừng ghi âm: ${e.localizedMessage}"
                releaseRecorder()
                _recordingState.value = RecordingState.IDLE
            }
        }
    }

    fun cancelRecording() {
        viewModelScope.launch {
            try {
                // Chỉ hủy nếu đang ghi
                if (_recordingState.value == RecordingState.RECORDING ||
                    _recordingState.value == RecordingState.STARTING) {

                    _recordingState.value = RecordingState.CANCELLING

                    try {
                        mediaRecorder?.stop()
                    } catch (e: IllegalStateException) {
                        // Có thể xảy ra nếu ghi âm chưa bắt đầu hoặc đã dừng
                    }

                    releaseRecorder()
                    audioFile?.delete()
                    audioFile = null
                }

                _recordingState.value = RecordingState.IDLE
                _recordingDuration.value = 0L
            } catch (e: Exception) {
                _recordingError.value = "Lỗi khi hủy ghi âm: ${e.localizedMessage}"
                releaseRecorder()
                _recordingState.value = RecordingState.IDLE
            }
        }
    }

    private fun releaseRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
    }

    fun getRecordingDuration(): Long {
        return _recordingDuration.value
    }

    private fun createTempAudioFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getApplication<Application>().cacheDir
        return File.createTempFile(
            "AUDIO_${timeStamp}_",
            ".mp3",
            storageDir
        )
    }

    private suspend fun uploadAudioMessage(audioFile: File) {
        try {
            _isLoading.value = true
            val audioId = generateNumberId()
            val storageRef = storage.child("messages/$familyId/audio/$audioId.mp3")

            // Tải file lên Firebase Storage
            val uploadTask = storageRef.putFile(Uri.fromFile(audioFile))

            // Đăng ký listeners để theo dõi quá trình upload
            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                // Có thể thêm StateFlow cho progress nếu muốn hiển thị tiến trình
            }

            uploadTask.await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            val messageId = database.child("messages")
                .child(familyId)
                .child("conversations")
                .push()
                .key ?: return

            // Tính toán và lưu thời lượng audio
            val durationSecs = (_recordingDuration.value / 1000).toInt()

            val message = ChatMessage(
                id = messageId,
                senderId = senderId,
                type = MessageType.AUDIO,
                content = downloadUrl,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENT,
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

            // Update message status to delivered after a short delay
            delay(1000)
            updateMessageStatus(messageId, MessageStatus.DELIVERED)

        } catch (e: Exception) {
            _recordingError.value = "Không thể tải tin nhắn thoại: ${e.localizedMessage}"
        } finally {
            // Clean up
            audioFile.delete()
            _isLoading.value = false
            _recordingDuration.value = 0L
        }
    }

    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    fun resetError() {
        _recordingError.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // Giải phóng tài nguyên khi ViewModel bị hủy
        releaseRecorder()
        audioFile?.delete()
    }

    // Enum cho trạng thái ghi âm
    enum class RecordingState {
        IDLE,
        STARTING,
        RECORDING,
        STOPPING,
        CANCELLING,
        UPLOADING
    }
}