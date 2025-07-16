package com.example.childlocate.ui.parent.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.childlocate.repository.AudioRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// AudioStreamViewModel.kt
class AudioStreamViewModel(application: Application) : AndroidViewModel(application) {
    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    private val _focusedChild = MutableLiveData<String?>()
    val focusedChild: LiveData<String?> get() = _focusedChild

    //lấy uuid của phụ huynh
    private val firebaseAuth : FirebaseAuth = FirebaseAuth.getInstance()
    private var parentId: String = firebaseAuth.currentUser?.uid ?: ""


    private val audioRepository: AudioRepository by lazy {
        AudioRepository(application) // Khởi tạo repository
    }


    private var audioPlayer: AudioPlayer? = null

    fun selectChild(child: String) {
        _focusedChild.value = child
        viewModelScope.launch {
            checkStreamingStatus(parentId)
        }
    }

    fun requestRecording(childId: String){
        viewModelScope.launch {
            _streamingState.value = StreamingState.Connecting
            try{
                val success = audioRepository.sendAudioRequest(childId, parentId)
                if (!success) {
                    _streamingState.value = StreamingState.Error("Failed to request recording")
                    return@launch
                }
                startListening(childId)
            }catch (e: Exception){
                _streamingState.value = StreamingState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun startListening(childId: String) {
        viewModelScope.launch {
            try {
                _streamingState.value = StreamingState.Connecting

                audioPlayer = AudioPlayer()
                audioPlayer?.startPlaying(childId) { error ->
                    viewModelScope.launch {
                        _streamingState.value = StreamingState.Error(error)
                    }
                }
                _streamingState.value = StreamingState.Listening
            } catch (e: Exception) {
                _streamingState.value = StreamingState.Error(e.message ?: "Unknown error")
            }
        }
    }
    fun stopRecording(childId: String) {
        viewModelScope.launch {
            try {
                val success = audioRepository.sendStopAudioRequest(childId, parentId)
                if (!success) {
                    _streamingState.value = StreamingState.Error("Failed to stop recording")
                    return@launch
                }
                stopListening()
                _streamingState.value = StreamingState.Idle
            } catch (e: Exception) {
                _streamingState.value = StreamingState.Error(e.message ?: "Unknown error")
            }
        }
    }


    private fun stopListening() {
        audioPlayer?.stopPlaying()
        audioPlayer = null
    }

    private suspend fun checkStreamingStatus(parentId: String) {
        withContext(Dispatchers.IO) {
            try {
                val childId = _focusedChild.value // Lấy childId, giữ nguyên là null nếu chưa chọn
                if (childId.isNullOrEmpty()) {
                    // Nếu không có childId được chọn hoặc là chuỗi rỗng
                    _streamingState.value = StreamingState.Idle // Hoặc Error("No child selected")
                    return@withContext // Thoát khỏi coroutine
                }

                val childRef = FirebaseDatabase.getInstance()
                    .reference
                    .child("streaming_status")
                    .child(childId)

                val requestedParentId = childRef.child("requestedBy").get().await().getValue(String::class.java)
                val isStreaming = childRef.child("isStreaming").get().await().getValue(Boolean::class.java) ?: false

                if (isStreaming && requestedParentId == parentId) {
                    _streamingState.value = StreamingState.Listening // Trẻ đang phát và đúng phụ huynh yêu cầu
                } else if (isStreaming && !requestedParentId.equals(parentId)) {
                    _streamingState.value = StreamingState.Other // Trẻ đang phát nhưng không phải do phụ huynh hiện tại yêu cầu
                } else {
                    // Trường hợp isStreaming là false, hoặc requestedParentId là null (ngay cả khi isStreaming là false)
                    _streamingState.value = StreamingState.Idle // Không có ai stream hoặc không phải mình stream
                }

            } catch (e: Exception) {
                // Xử lý lỗi khi truy cập Firebase
                _streamingState.value = StreamingState.Error("Failed to check streaming status: ${e.message}")
            }
        }
    }

    fun cancelConnection() {
        if (_streamingState.value is StreamingState.Connecting) {
            stopListening()
            _streamingState.value = StreamingState.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
