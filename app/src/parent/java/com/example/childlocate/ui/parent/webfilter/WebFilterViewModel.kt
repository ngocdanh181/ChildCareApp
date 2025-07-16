package com.example.childlocate.ui.parent.webfilter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.BlockedKeyword
import com.example.childlocate.data.model.BlockedWebsite
import com.example.childlocate.data.model.KeywordCategory
import com.example.childlocate.data.model.WebFilterState
import com.example.childlocate.data.model.WebsiteFilterState
import com.example.childlocate.repository.WebFilterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WebFilterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WebFilterRepository(application)
    private val _uiState = MutableStateFlow<WebFilterState>(WebFilterState.Loading)
    private val _uiState1 = MutableStateFlow<WebsiteFilterState>(WebsiteFilterState.Loading)

    val uiState: StateFlow<WebFilterState> =_uiState.asStateFlow()
    val uiState1: StateFlow<WebsiteFilterState> = _uiState1.asStateFlow()

    fun addKeyword(childId: String, pattern: String, category: KeywordCategory, isRegex: Boolean = false){
        viewModelScope.launch {
            try{
                _uiState.value = WebFilterState.Loading
                val keyword = BlockedKeyword(
                    pattern = pattern,
                    category = category,
                    isRegex = isRegex
                )
                repository.addKeyword(childId, keyword)
                loadKeywords(childId)
            }catch (e:Exception){
                _uiState.value = WebFilterState.Error(e.message ?: "Unknown error")
            }
        }
    }
    fun deleteKeyword(childId: String, keywordId: String){
        viewModelScope.launch {
            try {
                _uiState.value = WebFilterState.Loading
                repository.deleteKeyword(childId,keywordId)
                loadKeywords(childId)
            }catch (e: Exception){
                _uiState.value = WebFilterState.Error(e.message ?: "Unknown error")
            }
        }
    }
     fun loadKeywords(childId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = WebFilterState.Loading
                val keywords = repository.getKeywords(childId)
                _uiState.value = if (keywords.isEmpty()) {
                    WebFilterState.Empty
                } else {
                    WebFilterState.Success(keywords)
                }
            } catch (e: Exception) {
                _uiState.value = WebFilterState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetAllCounters(childId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = WebFilterState.Loading
                repository.resetAllCounters(childId)
                loadKeywords(childId)  // Reload để update UI
            } catch (e: Exception) {
                _uiState.value = WebFilterState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun addBlockedWebsite(childId: String, domain:String,category: KeywordCategory){
        viewModelScope.launch {
            try{
                _uiState1.value = WebsiteFilterState.Loading
                val blockedWebsite = BlockedWebsite(
                    domain = domain,
                    category = category,
                )
                repository.addBlockedWebsite (childId, blockedWebsite)
                loadBlockedWebsite(childId)

            }catch (e:Exception){
                _uiState1.value = WebsiteFilterState.Error(e.message ?: "Unknown error")
            }
        }
    }

     fun loadBlockedWebsite(childId: String) {
         viewModelScope.launch {
             try {
                 _uiState1.value = WebsiteFilterState.Loading
                 val websites = repository.getBlockedWebsites(childId)
                 _uiState1.value = if (websites.isEmpty()) {
                     WebsiteFilterState.Empty
                 } else {
                     WebsiteFilterState.Success(websites)
                 }
             } catch (e: Exception) {
                 _uiState1.value = WebsiteFilterState.Error(e.message ?: "Unknown error")
             }
         }
    }

    fun deleteBlockedWebsite(childId: String, websiteBlockedId: String){
        viewModelScope.launch {
            try{
                _uiState1.value = WebsiteFilterState.Loading

                repository.deleteBlockedWebsite(childId, websiteBlockedId)

            }catch (e:Exception){
                _uiState1.value = WebsiteFilterState.Error(e.message ?: "Unknown error")
            }
        }
    }

}