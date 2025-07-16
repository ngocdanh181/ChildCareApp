package com.example.childlocate.ui.parent.detailchat

import android.app.Application
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.childlocate.repository.ChatRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DetailChatViewModel(application: Application) : AndroidViewModel(application) {
    private val chatRepository = ChatRepository(application)

    // Expose repository StateFlows
    val messages = chatRepository.messages
    val memberNames = chatRepository.memberNames
    val memberAvatars = chatRepository.memberAvatars
    val familyName = chatRepository.familyName

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Recording states
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
        this.familyId = receiverId
        Log.d("DetailChatViewModel", "Family ID: $familyId va Sender ID: $senderId")
        
        // Load data from repository
        chatRepository.loadMessages(familyId)
        chatRepository.loadFamilyMembers(familyId)
    }

    fun sendTextMessage(messageText: String) {
        viewModelScope.launch {
            try {
                chatRepository.sendTextMessage(familyId, senderId, messageText)
            } catch (e: Exception) {
                _recordingError.value = "Không thể gửi tin nhắn: ${e.localizedMessage}"
            }
        }
    }

    fun uploadImageMessage(uri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                chatRepository.sendImageMessage(familyId, senderId, uri)
            } catch (e: Exception) {
                _recordingError.value = "Không thể tải lên ảnh: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }

    // ===== AUDIO RECORDING METHODS (unchanged) =====
    fun startRecording() {
        viewModelScope.launch {
            try {
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
                    MediaRecorder()
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

                        startDurationCounter()
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

                val duration = _recordingDuration.value
                if (duration < 1000) {
                    _recordingError.value = "Tin nhắn âm thanh quá ngắn"
                    audioFile?.delete()
                    audioFile = null
                    _recordingState.value = RecordingState.IDLE
                    return@launch
                }

                audioFile?.let { file ->
                    _recordingState.value = RecordingState.UPLOADING
                    // Use repository for audio upload
                    try {
                        chatRepository.sendAudioMessage(familyId, senderId, file, duration)
                    } catch (e: Exception) {
                        _recordingError.value = "Không thể tải tin nhắn thoại: ${e.localizedMessage}"
                    }
                }

                _recordingState.value = RecordingState.IDLE
                _recordingDuration.value = 0L
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

    fun resetError() {
        _recordingError.value = null
    }

    override fun onCleared() {
        super.onCleared()
        releaseRecorder()
        audioFile?.delete()
    }

    enum class RecordingState {
        IDLE,
        STARTING,
        RECORDING,
        STOPPING,
        CANCELLING,
        UPLOADING
    }
}