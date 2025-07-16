package com.example.childlocate.data.model

data class AudioChunk(
    val sequence: Long = 0,
    val timestamp: Long = 0,
    val data: String = ""
)

data class StreamMetadata(
    val status: String = "",  // "streaming" or "idle"
    val lastUpdate: Long = 0,
    val currentStreamId: String = "",
    val sampleRate: Int = 0,
    val format: String = "",
    val channelConfig: String = ""
)