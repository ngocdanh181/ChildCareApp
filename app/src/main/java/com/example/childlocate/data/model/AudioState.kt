package com.example.childlocate.data.model

sealed class AudioState {
    object Idle : AudioState()
    object RequestingRecording : AudioState()
    object Loading : AudioState()
    object Playing : AudioState()
    object Completed : AudioState()
    data class ReadyToPlay(val recording: AudioRecording) : AudioState()
    data class Error(val message: String) : AudioState()
}

/*sealed class AudioButtonState {
    object Idle : AudioButtonState()
    object Connecting : AudioButtonState()
    object Listening : AudioButtonState()
    data class Error(val message: String) : AudioButtonState()
}*/

data class AudioRecording(
    val url: String = "",
    val timestamp: Long = 0,
    val duration: Int = 0,
    val status: String = ""
)
