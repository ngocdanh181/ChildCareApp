package com.example.childlocate.data.api


import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    private const val BASE_URL = "https://fcm.googleapis.com/"
    private const val OPENCAGE_BASE_URL = "https://api.opencagedata.com/"

    private val fcmClient = OkHttpClient.Builder().build() //client cho fcm api
    private val opencageClient = OkHttpClient.Builder().build() //client cho opencage api


    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(fcmClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    val opencageApi: OpenCageDataService by lazy {
        Retrofit.Builder()
            .baseUrl(OPENCAGE_BASE_URL)
            .client(opencageClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenCageDataService::class.java)
    }
}

