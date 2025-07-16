package com.example.childlocate.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.childlocate.ui.child.onboarding.OnboardingActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FreeSplashActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No setContentView() - using windowBackground from theme
        
        lifecycleScope.launch {
            // Initialize child app components
            initializeChildApp()
            
            // Small delay for splash visibility (optional)
            delay(200)
            
            // Navigate to appropriate screen
            navigateToChildScreen()
        }
    }
    
    private suspend fun initializeChildApp() {
        // Initialize child-specific components if needed
        // This could include:
        // - Check permissions
        // - Initialize databases
        // - Setup services
    }
    
    private fun navigateToChildScreen() {
        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val childId = prefs.getString("childId", null)
        val hasCompletedOnboarding = prefs.getBoolean("hasCompletedOnboarding", false)
        
        val intent = Intent(this, OnboardingActivity::class.java) /*when {
            childId.isNullOrEmpty() && !hasCompletedOnboarding -> {
                // First time user - go to onboarding
                Intent(this, OnboardingActivity::class.java)
            }
            childId.isNullOrEmpty() && hasCompletedOnboarding -> {
                // Completed onboarding but no child ID - go to child ID setup
                Intent(this, ChildIdActivity::class.java)
            }
            else -> {
                // Has child ID - go to main child activity
                Intent(this, MainChildActivity::class.java)
            }
        }*/
        
        startActivity(intent)
        finish()
    }
} 