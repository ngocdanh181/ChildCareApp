package com.example.childlocate.repository

import android.content.Context
import android.util.Log
import com.example.childlocate.data.api.RetrofitInstance
import com.example.childlocate.data.model.Data
import com.example.childlocate.data.model.FcmRequest
import com.example.childlocate.data.model.Message
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

class ChildRepository(private val context: Context) {
    private val database = FirebaseDatabase.getInstance().reference
    private val functions = FirebaseFunctions.getInstance()
    suspend fun sendWarningToParent(familyId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Get all parent device tokens
                val parentTokens = getAllParentDeviceTokens(familyId)
                if (parentTokens.isEmpty()) {
                    Log.e("ChildRepository", "No parent device tokens found")
                    return@withContext false
                }

                val serviceAccount: InputStream = context.assets.open("childlocatedemo.json")
                val credentials = GoogleCredentials.fromStream(serviceAccount)
                    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

                val accessToken = credentials.refreshAccessToken().tokenValue
                //val accessToken1 = getFcmAccessToken()?: return@withContext false
                val authHeader = "Bearer $accessToken"

                // Send to all parent devices
                var allSuccessful = true
                for (token in parentTokens) {
                    val request = FcmRequest(
                        message = Message(
                            token = token,
                            data = Data(request_type = "warning_request")
                        )
                    )
                    val response = RetrofitInstance.api.sendLocationRequest(authHeader, request)
                    if (!response.isSuccessful) {
                        allSuccessful = false
                        Log.e("ChildRepository", "Failed to send warning to token: $token")
                    }
                }
                return@withContext allSuccessful
            } catch (e: IOException) {
                Log.d("ChildRepository", "Error is: ${e.message}")
                return@withContext false
            }
        }
    }

    suspend fun stopWarningToParent(familyId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Get all parent device tokens
                val parentTokens = getAllParentDeviceTokens(familyId)
                if (parentTokens.isEmpty()) {
                    Log.e("ChildRepository", "No parent device tokens found")
                    return@withContext false
                }

                val serviceAccount: InputStream = context.assets.open("childlocatedemo.json")
                val credentials = GoogleCredentials.fromStream(serviceAccount)
                    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

                val accessToken = credentials.refreshAccessToken().tokenValue
                val authHeader = "Bearer $accessToken"

                // Send stop warning to all parent devices
                var allSuccessful = true
                for (token in parentTokens) {
                    val request = FcmRequest(
                        message = Message(
                            token = token,
                            data = Data(request_type = "stop_warning_request")
                        )
                    )
                    val response = RetrofitInstance.api.sendLocationRequest(authHeader, request)
                    if (!response.isSuccessful) {
                        allSuccessful = false
                        Log.e("ChildRepository", "Failed to send stop warning to token: $token")
                    }
                }
                return@withContext allSuccessful
            } catch (e: IOException) {
                Log.d("ChildRepository", "Error: ${e.message}")
                return@withContext false
            }
        }
    }

    private suspend fun getAllParentDeviceTokens(familyId: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val tokens = mutableListOf<String>()
                
                // Get all family members
                val familySnapshot = database.child("families").child(familyId).child("members").get().await()
                
                for (memberSnapshot in familySnapshot.children) {
                    val memberId = memberSnapshot.key ?: continue
                    val memberRole = memberSnapshot.child("role").getValue(String::class.java)
                    
                    // Skip children, only get parents
                    if (memberRole == "child") continue
                    
                    // Get all device tokens for this parent
                    val userSnapshot = database.child("users").child(memberId).get().await()
                    Log.d("ChildRepository", "Processing user: $memberId")
                    
                    // Get primary device token
                    userSnapshot.child("primaryDeviceToken").getValue(String::class.java)?.let { token ->
                        tokens.add(token)
                    }
                    
                    // Get all active device tokens
                    val activeDevicesSnapshot = userSnapshot.child("activeDevices")
                    for (deviceSnapshot in activeDevicesSnapshot.children) {
                        val deviceToken = deviceSnapshot.key ?: continue
                        if (!tokens.contains(deviceToken)) {
                            tokens.add(deviceToken)
                        }
                    }
                }
                
                Log.d("ChildRepository", "Found ${tokens.size} parent device tokens")
                return@withContext tokens
            } catch (e: Exception) {
                Log.e("ChildRepository", "Failed to get parent device tokens: ${e.message}")
                return@withContext emptyList()
            }

        }
    }
    private suspend fun getFcmAccessToken(): String? {
        return withContext(Dispatchers.IO){
            try{
                val result = functions
                    .getHttpsCallable("getFcmAccessToken")
                    .call()
                    .await()
                val response = result.data as? Map<*, *>
                Log.d("ChildRepository", "Response from cloud function: $response")
                val success = response?.get("success") as? Boolean ?: false

                if (success) {
                    response?.get("accessToken") as? String
                } else {
                    Log.e("ChildRepository", "Failed to get access token from cloud function")
                    null
                }
            }catch(e: Exception){
                Log.e("ChildRepository", "Failed to get FCM access token: ${e.message}")
                return@withContext null
            }
        }

    }
}