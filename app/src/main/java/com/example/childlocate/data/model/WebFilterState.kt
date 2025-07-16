package com.example.childlocate.data.model

sealed class WebFilterState {
    data object Loading : WebFilterState()
    data object Empty : WebFilterState()
    data class Success(val keywords: List<BlockedKeyword>) : WebFilterState()
    data class Error(val message: String) : WebFilterState()
}

sealed class WebsiteFilterState {
    data object Loading : WebsiteFilterState()
    data object Empty : WebsiteFilterState()
    data class Success(val websites: List<BlockedWebsite>) : WebsiteFilterState()
    data class Error(val message: String) : WebsiteFilterState()
}


data class BlockedKeyword(
    val id: String = "",
    val pattern: String,
    val category: KeywordCategory,
    val isRegex: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    var attemptCount: Int = 0  // Thêm trường đếm số lần
)

enum class KeywordCategory {
    ADULT_CONTENT,
    VIOLENCE,
    GAMBLING,
    DRUGS,
    CUSTOM;

    fun getDisplayName(): String = when(this) {
        ADULT_CONTENT -> "Nội dung người lớn"
        VIOLENCE -> "Bạo lực"
        GAMBLING -> "Cờ bạc"
        DRUGS -> "Ma túy"
        CUSTOM -> "Tùy chỉnh"
    }
}

data class BlockedWebsite(
    val id:String ="",
    val domain:String,
    val category: KeywordCategory,
    val createdAt: Long = System.currentTimeMillis()
)
