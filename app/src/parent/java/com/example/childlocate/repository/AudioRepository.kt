package com.example.childlocate.repository


import android.content.Context
import android.util.Log
import com.example.childlocate.data.api.RetrofitInstance
import com.example.childlocate.data.model.Data
import com.example.childlocate.data.model.FcmRequest
import com.example.childlocate.data.model.Message
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

class AudioRepository(
    private val context: Context
) {
    private val database = FirebaseDatabase.getInstance()


    suspend fun sendAudioRequest(childId:String, parentId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val token = getToken(childId) ?: return@withContext false
                Log.d("AudioRepository", token)
                val serviceAccount: InputStream = context.assets.open("childlocatedemo.json")
                val credentials = GoogleCredentials.fromStream(serviceAccount)
                    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

                val accessToken = credentials.refreshAccessToken().tokenValue
                val authHeader = "Bearer $accessToken"

                val requestType = "audio_request"

                val request = FcmRequest(
                    message = Message(
                        token = token,
                        data = Data(request_type = "$requestType|$parentId")
                    )
                )

                val response = RetrofitInstance.api.sendLocationRequest(authHeader, request)
                return@withContext response.isSuccessful
            } catch (e: IOException) {
                Log.d("FCM", "Error: ${e.message}")
                return@withContext false
            }
        }
    }

    suspend fun sendStopAudioRequest(childId:String, parentId:String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val token = getToken(childId) ?: return@withContext false
                Log.d("AudioRepository", token)
                //val token = "fFD8NeENQbuK663pnrYxVM:APA91bHTYhN_MQeCP5kLT5sLgYX5RLcx2LzTBg2AztbYcKw1YYEYMfZy0Whivysmu_bS9n0CXji92LwvWQVLxklUyIj5Iz9IqYpD6sp7W2FBYG8r3m4lqxbAfZ-Rfo2xps7cNj8kD8fh"
                val serviceAccount: InputStream = context.assets.open("childlocatedemo.json")
                val credentials = GoogleCredentials.fromStream(serviceAccount)
                    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

                val accessToken = credentials.refreshAccessToken().tokenValue
                val authHeader = "Bearer $accessToken"

                val requestType = "stop_audio_request"

                val request = FcmRequest(
                    message = Message(
                        token = token,
                        data = Data(request_type = "$requestType|$parentId")
                    )
                )

                val response = RetrofitInstance.api.sendLocationRequest(authHeader, request)
                return@withContext response.isSuccessful
            } catch (e: IOException) {
                Log.d("FCM", "Error: ${e.message}")
                return@withContext false
            }
        }
    }


    private suspend fun getToken(childId:String): String? {
        return withContext(Dispatchers.IO) {
            try {
                database.getReference("users").child(childId).child("primaryDeviceToken").get().await().getValue(String::class.java)

            } catch (e: Exception) {
                Log.e("FCM", "Failed to get FCM token: ${e.message}")
                null
            }
        }
    }
}






