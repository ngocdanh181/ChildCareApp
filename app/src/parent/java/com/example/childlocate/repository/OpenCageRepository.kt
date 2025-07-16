package com.example.childlocate.repository

import android.util.Log
import com.example.childlocate.data.api.OpenCageComponents
import com.example.childlocate.data.api.RetrofitInstance
import com.example.childlocate.ui.parent.locations.PlaceSearchResult
import java.io.IOException

class OpenCageRepository {
    private val TAG = "OpenCageRepository"
    private val apiKey: String

    constructor(apiKey: String) {
        this.apiKey = apiKey
    }

    suspend fun searchPlaces(query: String): Result<List<PlaceSearchResult>> {
        return try {
            Log.d(TAG, "Searching places with query: $query")

            val response = RetrofitInstance.opencageApi.searchPlaces(
                query = query,
                apiKey = apiKey
            )

            if (response.isSuccessful) {
                val results = response.body()?.results?.map { result ->
                    // Tạo tên địa điểm từ các thành phần có sẵn
                    //val name = buildLocationName(result.components)
                    val name = extractMeaningfulName(result.components)
                    // Cắt name và address
                    val parts = result.formatted.split(", ", limit = 2) // Chia chuỗi thành 2 phần
                    val name1 = parts.getOrNull(0) ?: ""  // Lấy phần đầu tiên làm name
                    val address1 = parts.getOrNull(1) ?: "" // Lấy phần còn lại làm address
                    PlaceSearchResult(
                        placeId = "${result.geometry.lat},${result.geometry.lng}",
                        name = name1,
                        address = address1,//result.formatted?.replace("${extractMeaningfulName(result.components)}, ", "") ?: "", // Loại bỏ tên khỏi địa chỉ
                        latitude = result.geometry.lat,
                        longitude = result.geometry.lng
                    )
                } ?: emptyList()

                Log.d(TAG, "Found ${results.size} results")
                Result.success(results)
            } else {
                Log.e(TAG, "Error: ${response.code()} - ${response.message()}")
                Result.failure(IOException("API error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during search: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun extractMeaningfulName(components: OpenCageComponents): String {
        return when {
            // Ưu tiên tên trường học/đại học từ amenity
            components.amenity?.contains("university|school|college".toRegex(RegexOption.IGNORE_CASE)) == true -> {
                components.amenity
            }
            //tên highway
            // Tên tòa nhà/cơ sở
            !components.highway.isNullOrBlank() -> components.highway

            // Tên tòa nhà/cơ sở
            !components.building_name.isNullOrBlank() -> components.building_name
            // Tên địa điểm cụ thể (place_name)
            !components.place_name.isNullOrBlank() -> components.place_name
            // Tên cơ sở từ amenity (nếu không phải trường học)
            !components.amenity.isNullOrBlank() -> components.amenity
            // Tên đường phố (chỉ dùng làm phương án cuối)
            else ->  "Địa điểm không xác định"
        }
    }


}