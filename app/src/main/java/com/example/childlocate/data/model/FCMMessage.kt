package com.example.childlocate.data.model

// FCMMessage.kt

data class FCMMessage(
    val message: MessageData
) {
    data class MessageData(
        val token: String,
        val data: Data,
        val notification: Notification? = null
    ) {
        data class Data(
            val request_type: String,
            val otp: String? = null
        )

        data class Notification(
            val title: String,
            val body: String
        )
    }
}


