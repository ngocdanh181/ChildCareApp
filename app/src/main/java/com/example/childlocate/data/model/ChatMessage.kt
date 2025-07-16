package com.example.childlocate.data.model

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val type: MessageType = MessageType.TEXT,
    val content: String = "", // text content hoặc URL của media
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT
)

enum class MessageType {
    TEXT, IMAGE, AUDIO
}

enum class MessageStatus {
    SENT, DELIVERED, READ
}