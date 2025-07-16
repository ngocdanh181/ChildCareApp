// AudioPlayer.kt
package com.example.childlocate.ui.parent.home

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import com.example.childlocate.utils.AudioConstants
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase



// AudioStreamPlayer.kt
class AudioPlayer {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private val database = FirebaseDatabase.getInstance()
    private var listener: ChildEventListener? = null
    private val bufferSize = AudioTrack.getMinBufferSize(
        AudioConstants.SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioConstants.AUDIO_FORMAT
    )

    fun startPlaying(childId: String, onError: (String) -> Unit) {
        if (isPlaying) return

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioConstants.AUDIO_FORMAT)
                    .setSampleRate(AudioConstants.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            isPlaying = true

            listener = createStreamListener(onError)
            database.reference
                .child("audio_streams")
                .child(childId)
                .addChildEventListener(listener!!)

        } catch (e: Exception) {
            onError(e.message ?: "Failed to initialize audio player")
            stopPlaying()
        }
    }

    private fun createStreamListener(onError: (String) -> Unit) = object : ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
            try {
                snapshot.getValue(String::class.java)?.let { encodedChunk ->
                    val audioData = Base64.decode(encodedChunk, Base64.DEFAULT)
                    audioTrack?.write(audioData, 0, audioData.size)
                }
            } catch (e: Exception) {
                onError("Error playing audio chunk: ${e.message}")
            }
        }

        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
        override fun onChildRemoved(snapshot: DataSnapshot) {}
        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
        override fun onCancelled(error: DatabaseError) {
            onError("Stream cancelled: ${error.message}")
        }
    }

    fun stopPlaying() {
        isPlaying = false
        listener?.let { listener ->
            database.reference
                .child("audio_streams")
                .removeEventListener(listener)
        }
        audioTrack?.apply {
            stop()
            flush()
            release()
        }
        audioTrack = null
    }
}

// StreamingState.kt
sealed class StreamingState {
    data object Idle : StreamingState()
    data object Connecting : StreamingState()
    data object Listening : StreamingState()
    data object Other: StreamingState()

    data class Error(val message: String) : StreamingState()
}


/*class AudioPlayer {
    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = 8192
        private const val MIN_BUFFER_SIZE = 3 // Số lượng chunks tối thiểu trước khi phát
    }

    private var audioTrack: AudioTrack? = null
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private var isPlaying = false

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        audioTrack = AudioTrack.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build()
            )
            .setBufferSizeInBytes(BUFFER_SIZE)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }
    // AudioPlayer.kt (tiếp theo)
    @RequiresApi(Build.VERSION_CODES.O)
    fun playChunk(chunk: AudioChunk) {
        try {
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                initAudioTrack()
            }

            val decompressedData = decompressAudioData(Base64.getDecoder().decode(chunk.data))
            audioQueue.offer(decompressedData)

            if (!isPlaying && audioQueue.size >= MIN_BUFFER_SIZE) {
                startPlayingFromQueue()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error playing chunk: ${e.message}")
        }
    }

    private fun startPlayingFromQueue() {
        isPlaying = true
        audioTrack?.play()

        Thread {
            while (isPlaying && audioQueue.isNotEmpty()) {
                audioQueue.poll()?.let { audioData ->
                    audioTrack?.write(audioData, 0, audioData.size)
                }
            }
            isPlaying = false
        }.start()
    }

    private fun decompressAudioData(compressedData: ByteArray): ByteArray {
        val gzipInputStream = GZIPInputStream(ByteArrayInputStream(compressedData))
        return gzipInputStream.readBytes()
    }

    fun stop() {
        isPlaying = false
        audioQueue.clear()
        audioTrack?.apply {
            stop()
            release()
        }
        audioTrack = null
    }
}*/