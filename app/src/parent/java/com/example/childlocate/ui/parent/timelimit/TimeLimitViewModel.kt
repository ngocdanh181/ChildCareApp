package com.example.childlocate.ui.parent.timelimit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.AppLimit
import com.example.childlocate.data.model.AppLimitDialogState
import com.example.childlocate.repository.TimeLimitRepository
import kotlinx.coroutines.launch

class TimeLimitViewModel(application: Application): AndroidViewModel(application)
{
    private val _state = MutableLiveData<AppLimitDialogState>()
    val state: LiveData<AppLimitDialogState> = _state
    private val repository = TimeLimitRepository(application)
    
    // Flag to distinguish between loading and saving operations
    private var isSaving = false

    fun loadAppLimits(childId: String, packageName: String) {
        viewModelScope.launch {
            isSaving = false // This is a load operation
            _state.value = AppLimitDialogState.Loading
            try {
                val appLimit = repository.getAppLimit(childId, packageName)
                _state.value = AppLimitDialogState.Success(appLimit)
            } catch (e: Exception) {
                _state.value = AppLimitDialogState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun setAppLimit(childId: String, appLimit: AppLimit) {
        viewModelScope.launch {
            isSaving = true // This is a save operation
            _state.value = AppLimitDialogState.Loading
            try {
                repository.setAppLimit(childId, appLimit)
                _state.value = AppLimitDialogState.Success(appLimit)
            } catch (e: Exception) {
                _state.value = AppLimitDialogState.Error(e.message ?: "Unknown error")
                isSaving = false // Reset flag on error
            }
        }
    }
    
    fun isSavingOperation(): Boolean = isSaving
    
    fun resetSavingFlag() {
        isSaving = false
    }
}