package com.example.childlocate.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.childlocate.ui.parent.MainActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ParentSplashActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No setContentView() - using windowBackground from theme
        
        lifecycleScope.launch {

            // Small delay for splash visibility (optional)
            delay(100)
            
            // Navigate to appropriate screen
            navigateToParentScreen()
        }
    }

    
    private fun navigateToParentScreen() {
        val user = FirebaseAuth.getInstance().currentUser
        
        val intent = if (user == null) {
            // Not logged in - go to user type selection
            Intent(this, ChooseUserTypeActivity::class.java)
        } else {
            // Already logged in - go to main parent activity
            Intent(this, MainActivity::class.java)
        }
        
        startActivity(intent)
        finish()
    }
} 