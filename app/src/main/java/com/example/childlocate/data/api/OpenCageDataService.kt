package com.example.childlocate.data.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenCageDataService {
    @GET("geocode/v1/json")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("key") apiKey: String,
        @Query("countrycode") countryCode: String = "vn",
        @Query("language") language: String = "vi",
        @Query("limit") limit: Int = 10
    ): Response<OpenCageResponse>
}

data class OpenCageResponse(
    val results: List<OpenCageResult>,
    val status: OpenCageStatus
)

data class OpenCageStatus(
    val code: Int,
    val message: String
)

data class OpenCageResult(
    val formatted: String,
    val geometry: OpenCageGeometry,
    val components: OpenCageComponents
)

data class OpenCageGeometry(
    val lat: Double,
    val lng: Double
)

data class OpenCageComponents(
    val country: String?,
    val state: String?,
    val city: String?,
    val suburb: String?,
    val road: String?,
    val postcode: String?,
    val house_number: String?,
    val amenity: String?,
    val building_name: String?,
    val place_name: String?,
    val city_district: String?,
    val formatted: String?,
    val highway: String?
)