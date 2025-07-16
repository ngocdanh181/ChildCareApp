package com.example.childlocate.repository

import android.content.Context
import android.util.Log
import com.example.childlocate.data.model.BlockedKeyword
import com.example.childlocate.data.model.BlockedWebsite
import com.example.childlocate.data.model.KeywordCategory
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class WebFilterRepository( private val context: Context) {
    private val database = FirebaseDatabase.getInstance()
    private val keywordsRef = database.getReference("web_filter")
    private val websitesRef = database.getReference("web_filter")
    suspend fun addKeyword(childId: String, keyword: BlockedKeyword){
        return withContext(Dispatchers.IO){
            try{
                val keywordRef = keywordsRef.child(childId).child("keywords").push()
                val keywordWithId = keyword.copy(id = keywordRef.key ?: "")
                keywordRef.setValue(keywordWithId).await()
            }catch (e: Exception){
                Log.e("WebFilterRepo", "Error adding keyword: ${e.message}")
                throw e
            }

        }
    }
    suspend fun deleteKeyword(childId: String, keywordId: String){
        return withContext(Dispatchers.IO){
            try {
                keywordsRef.child(childId).child("keywords")
                    .child(keywordId)
                    .removeValue()
                    .await()
            }catch (e: Exception){
                Log.e("WebFilterRepo", "Error deleting keyword: ${e.message}")
                throw e
            }
        }
    }
    suspend fun getKeywords(childId: String): List<BlockedKeyword> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = keywordsRef.child(childId).child("keywords")
                    .get().await()
                val attemptSnapshot = keywordsRef.child(childId).child("attempts").get().await()

                val keywordsList = mutableListOf<BlockedKeyword>()

                snapshot.children.forEach { keywordSnapshot ->
                    val keywordId = keywordSnapshot.key ?: ""
                    keywordsList.add(
                        BlockedKeyword(
                            id = keywordId,
                            pattern = keywordSnapshot.child("pattern").getValue(String::class.java) ?: "",
                            category = KeywordCategory.valueOf(
                                keywordSnapshot.child("category").getValue(String::class.java)
                                    ?: KeywordCategory.CUSTOM.name
                            ),
                            isRegex = keywordSnapshot.child("isRegex").getValue(Boolean::class.java) ?: false,
                            createdAt = keywordSnapshot.child("createdAt").getValue(Long::class.java)
                                ?: System.currentTimeMillis(),
                            attemptCount = attemptSnapshot.child(keywordId)
                                .getValue(Int::class.java) ?: 0

                        )
                    )
                }
                Log.d("WebFilter","Repository keywords $keywordsList")
                keywordsList
            } catch (e: Exception) {
                Log.e("WebFilterRepo", "Error getting keywords: ${e.message}")
                throw e
            }
        }
    }
    suspend fun resetAllCounters(childId: String) {
        return withContext(Dispatchers.IO) {
            try {
                // Reset attempts node
                keywordsRef.child(childId)
                    .child("attempts")
                    .removeValue()
                    .await()
            } catch (e: Exception) {
                Log.e("WebFilterRepo", "Error resetting counters", e)
                throw e
            }
        }
    }



    suspend fun addBlockedWebsite(childId: String, website: BlockedWebsite){
        return withContext(Dispatchers.IO){
            try{
                val websiteRef = websitesRef.child(childId).child("websites").push()
                val websiteWithId = website.copy(id = websiteRef.key?: "")
                websiteRef.setValue(websiteWithId).await()
            }catch (e:Exception){
                Log.e("WebFilterRepo", "Error adding website: ${e.message}")
                throw e
            }
        }
    }
    suspend fun deleteBlockedWebsite(childId: String,websiteId: String){
        return withContext(Dispatchers.IO){
            try{
                websitesRef.child(childId).child("websites")
                    .child(websiteId)
                    .removeValue()
                    .await()

            }catch (e: Exception){
                Log.e("WebFilterRepo", "Error website keyword: ${e.message}")
            }
        }
    }
    suspend fun getBlockedWebsites(childId: String): List<BlockedWebsite>{
        return withContext(Dispatchers.IO){
            try{
                val snapshot = websitesRef.child(childId).child("websites")
                    .get().await()

                val blockedWebsiteList = mutableListOf<BlockedWebsite>()
                snapshot.children.forEach { blockedWebsiteSnapshot ->
                    blockedWebsiteList.add(
                        BlockedWebsite(
                            id = blockedWebsiteSnapshot.key ?: "",
                            domain = blockedWebsiteSnapshot.child("domain").getValue(String::class.java)?: "",
                            category = KeywordCategory.valueOf(
                                blockedWebsiteSnapshot.child("category").getValue(String::class.java)
                                    ?: KeywordCategory.CUSTOM.name
                            ),
                            createdAt = blockedWebsiteSnapshot.child("createdAt").getValue(Long::class.java)
                                ?: System.currentTimeMillis()
                        )
                    )
                }

                blockedWebsiteList



            }catch (e: Exception){
                Log.e("WebFilterRepo","Error getting websites: ${e.message}")
                throw e
            }
        }
    }


}