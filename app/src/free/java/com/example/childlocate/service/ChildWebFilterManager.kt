package com.example.childlocate.service

import android.util.Log
import com.example.childlocate.data.model.BlockedKeyword
import com.example.childlocate.data.model.KeywordCategory
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.Normalizer

class ChildWebFilterManager {
    private val database = FirebaseDatabase.getInstance()
    private val keywordsRef = database.getReference("web_filter")
    private lateinit var childId1: String


    fun startMonitoring(childId: String, onKeywordsLoaded: (List<BlockedKeyword>) -> Unit) {
        childId1 = childId
        val keywords = mutableListOf<BlockedKeyword>()
        val keywordsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                keywords.clear()
                snapshot.child(childId).child("keywords").children.forEach { keywordSnapshot ->
                    try {
                        val keyword = BlockedKeyword(
                            id = keywordSnapshot.key ?: "",
                            pattern = keywordSnapshot.child("pattern").getValue(String::class.java) ?: "",
                            category = KeywordCategory.valueOf(
                                keywordSnapshot.child("category").getValue(String::class.java)
                                    ?: KeywordCategory.CUSTOM.name
                            ),
                            isRegex = keywordSnapshot.child("isRegex").getValue(Boolean::class.java) ?: false,
                            createdAt = keywordSnapshot.child("createdAt").getValue(Long::class.java)
                                ?: System.currentTimeMillis()
                        )
                        keywords.add(keyword)
                        Log.d("WebFilter", "Loaded keyword: ${keyword.pattern}")
                    } catch (e: Exception) {
                        Log.e("WebFilter", "Error parsing keyword: ${e.message}")
                    }
                }
                Log.d("WebFilter", "Keywords updated. Total: ${keywords.size}")
                onKeywordsLoaded(keywords) // Gửi danh sách từ khóa tới callback
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("WebFilter", "Database error: ${error.message}")
            }
        }

        keywordsRef.addValueEventListener(keywordsListener)
    }



    private fun removeVietnameseAccents(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }


    fun isBlocked(text: String, keywords: List<BlockedKeyword>): Boolean {
        Log.d("WebFilter", "isBlocked() called with text: $text")
        Log.d("WebFilter", "$keywords dang co bao nhieu")
        val normalizedText = removeVietnameseAccents(text.lowercase())

        keywords.forEach { keyword ->
            Log.d("WebFilter","${keyword.pattern} block")
            try {
                val normalizedKeyword = removeVietnameseAccents(keyword.pattern.lowercase())
                Log.d("WebFilter", "Checking against keyword: ${keyword.pattern}, isRegex: ${keyword.isRegex}")
                val isMatch = if (keyword.isRegex) {
                    text.contains(keyword.pattern.toRegex())
                } else {
                    //text.contains("\\b${Regex.escape(keyword.pattern)}\\b".toRegex(RegexOption.IGNORE_CASE))
                    // So sánh chuỗi thông thường không dấu
                    normalizedText.contains("\\b${Regex.escape(normalizedKeyword)}\\b".toRegex())

                }
                Log.d("WebFilter", "Match result for '${keyword.pattern}': $isMatch")
                if (isMatch) return true
            } catch (e: Exception) {
                Log.e("WebFilter", "Error checking keyword ${keyword.pattern}: ${e.message}")
            }
        }
        return false
    }
}
