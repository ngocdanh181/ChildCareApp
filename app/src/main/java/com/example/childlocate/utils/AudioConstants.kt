package com.example.childlocate.utils

import android.media.AudioFormat

// Constants.kt
object AudioConstants {
    const val SAMPLE_RATE = 44100
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val CHUNK_SIZE = 4096
    const val NOTIFICATION_ID = 1011
    const val CHANNEL_ID = "audio_streaming_channel"
}