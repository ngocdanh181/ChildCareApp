package com.example.childlocate.viewmodel
/*
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.childlocate.repository.AuthRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch


class ResetViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository()

    private val _otpSentStatus = MutableLiveData<Boolean>()
    val otpSentStatus: LiveData<Boolean> = _otpSentStatus

    private val _resetPasswordStatus = MutableLiveData<Boolean>()
    val resetPasswordStatus: LiveData<Boolean> = _resetPasswordStatus

    fun sendOtp(email: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                _otpSentStatus.postValue(false)
                return@addOnCompleteListener
            }

            val token = task.result
            viewModelScope.launch {
                try {
                    val response = authRepository.sendOtpRequest(getApplication(), token, email)
                    if (response.isSuccessful) {
                        _otpSentStatus.postValue(true)
                    } else {
                        _otpSentStatus.postValue(false)
                    }
                } catch (e: Exception) {
                    _otpSentStatus.postValue(false)
                }
            }
        }
    }

    fun resetPassword(otp: String, newPassword: String) {
        viewModelScope.launch {
            try {
                val response = authRepository.resetPassword(otp, newPassword)
                if (response.isSuccessful) {
                    _resetPasswordStatus.postValue(true)
                } else {
                    _resetPasswordStatus.postValue(false)
                }
            } catch (e: Exception) {
                _resetPasswordStatus.postValue(false)
            }
        }
    }
}


/*
class ResetViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository()

    private val _otpSentStatus = MutableLiveData<Boolean>()
    val otpSentStatus: LiveData<Boolean> = _otpSentStatus

    private val _resetPasswordStatus = MutableLiveData<Boolean>()
    val resetPasswordStatus: LiveData<Boolean> = _resetPasswordStatus

    fun sendOtp(email: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                _otpSentStatus.postValue(false)
                return@addOnCompleteListener
            }

            val token = task.result
            viewModelScope.launch {
                try {
                    val response = authRepository.sendOtpRequest(getApplication(),token, email)
                    if (response.isSuccessful) {
                        _otpSentStatus.postValue(true)
                    } else {
                        _otpSentStatus.postValue(false)
                    }
                } catch (e: Exception) {
                    _otpSentStatus.postValue(false)
                }
            }
        }
    }

    fun resetPassword(otp: String, newPassword: String) {
        viewModelScope.launch {
            try {
                val response = authRepository.resetPassword(otp, newPassword)
                if (response.isSuccessful) {
                    _resetPasswordStatus.postValue(true)
                } else {
                    _resetPasswordStatus.postValue(false)
                }
            } catch (e: Exception) {
                _resetPasswordStatus.postValue(false)
            }
        }
    }
}*/
*/
