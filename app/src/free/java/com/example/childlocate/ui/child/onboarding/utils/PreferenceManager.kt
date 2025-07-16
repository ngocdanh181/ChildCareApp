package com.example.childlocate.ui.child.onboarding.utils

import android.content.Context

class PreferenceManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun setOnboardingCompleted(completed: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun isOnboardingCompleted(): Boolean {
        return sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun saveVerificationCode(code: String){
        sharedPreferences.edit().putString(KEY_VERIFICATION_CODE, code).apply()
    }

    fun getVerificationCode(): String? {
        return sharedPreferences.getString(KEY_VERIFICATION_CODE, null)
    }

    companion object {
        private const val PREF_NAME = "onboarding_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_VERIFICATION_CODE = "verification_code"
    }
}