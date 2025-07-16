package com.example.childlocate.data.api

import com.example.childlocate.data.model.FcmRequest
import com.example.childlocate.data.model.FcmResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST


interface ApiService {
    @POST("v1/projects/childlocatedemo-6c5da/messages:send")
    suspend fun sendLocationRequest(
        @Header("Authorization") authHeader: String,
        @Body requestBody: FcmRequest
    ): Response<FcmResponse>
}
