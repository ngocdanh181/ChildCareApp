package com.example.childlocate.data.model

data class FcmRequest(
    val message: Message
)

data class NotificationPayload(
    val title: String,
    val body: String
)

data class Message(
    val token: String,
    val data: Data? = null,
    val notification: NotificationPayload? = null
)

data class Data(
    val request_type: String,
)

