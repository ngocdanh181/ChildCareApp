package com.example.childlocate.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.example.childlocate.data.model.BlockedKeyword
import com.example.childlocate.ui.child.main.MainChildActivity
import com.google.firebase.database.FirebaseDatabase

class WebFilterAccessibilityService : AccessibilityService() {
    private var webFilterManager: ChildWebFilterManager ? = null
    private var keyWords: List<BlockedKeyword> =emptyList()
    private val database = FirebaseDatabase.getInstance()
    private val keywordsRef = database.getReference("web_filter")
    private lateinit var childId: String
    private var isInitialized = false


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        try{
            initializeService()
        }catch(e:Exception){
            Log.e("WebFilter", "Error initializing service: ${e.message}")
        }

    }

    private fun initializeService() {
        try {
            val sharedPreferences = applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            childId = sharedPreferences.getString("childId", "") ?: ""

            if (childId.isEmpty()) {
                Log.e("WebFilter", "Child ID is empty, service cannot function")
                return
            }

            webFilterManager = ChildWebFilterManager()

            webFilterManager?.startMonitoring(childId) { loadedKeywords ->
                try {
                    keyWords = loadedKeywords
                    isInitialized = true
                    Log.d("WebFilter", "Service initialized with ${keyWords.size} keywords")
                } catch (e: Exception) {
                    Log.e("WebFilter", "Error loading keywords", e)
                }
            }

        } catch (e: Exception) {
            Log.e("WebFilter", "Error in initializeService", e)
        }
    }


    override fun onServiceConnected() {
        Log.d("WebFilter", "Service connected - setting up accessibility info")

        try {
            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                notificationTimeout = 100
            }
            serviceInfo = info

            // Re-initialize if needed
            if (!isInitialized) {
                initializeService()
            }

            Log.d("WebFilter", "Service connected successfully")
        } catch (e: Exception) {
            Log.e("WebFilter", "Error in onServiceConnected", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                handleTextChange(event)
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                handleViewFocus(event)
            }
            else -> {
                Log.d("WebFilter", "Unhandled event type: ${event.eventType}")
            }
        }
    }

    private fun handleTextChange(event: AccessibilityEvent) {
        // Extract text more comprehensively
        val extractedTexts = mutableListOf<String>()

        // Try multiple text extraction methods
        event.text.forEach { charSequence ->
            charSequence.toString().takeIf { it.isNotBlank() }?.let { extractedTexts.add(it) }
        }

        event.source?.let { source ->
            source.text?.toString()?.takeIf { it.isNotBlank() }?.let { extractedTexts.add(it) }
        }

        // Log all extracted texts for debugging
        extractedTexts.forEach { text ->
            keyWords.forEach { keyword ->
                if (webFilterManager!!.isBlocked(text, listOf(keyword))) {
                    // Tăng counter
                    incrementKeywordCounter(keyword.id)
                    // Handle blocking
                    handleBlockedText(event.source)
                    return
                }
            }
        }
    }

    private fun incrementKeywordCounter(keywordId: String) {
        val attemptsRef = keywordsRef
            .child(childId)
            .child("attempts")
            .child(keywordId)

        // Đọc giá trị hiện tại
        attemptsRef.get().addOnSuccessListener { snapshot ->
            val currentCount = snapshot.getValue(Int::class.java) ?: 0
            // Increment và update
            attemptsRef.setValue(currentCount + 1)
                .addOnSuccessListener {
                    Log.d("WebFilter", "Counter incremented successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("WebFilter", "Counter increment failed", e)
                }
        }
    }
    private fun handleBlockedText(source: AccessibilityNodeInfo?) {
        source?.let {
            try {
                // Clear text in editable fields
                if (it.isEditable) {
                    it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            ""
                        )
                    })
                }

                // Go back and show warning
                performGlobalAction(GLOBAL_ACTION_BACK)
                showBlockedContentWarning()
            } catch (e: Exception) {
                Log.e("WebFilter", "Error handling blocked text: ${e.message}")
            }
        }
    }
    private fun handleViewFocus(event: AccessibilityEvent) {
        val source = event.source ?: return
        try {
            when (source.className) {
                "android.webkit.WebView" -> monitorWebViewContent(source)
                //"android.widget.EditText" -> monitorEditTextContent(source)
            }
        } finally {
            source.recycle()
        }
    }

    private fun monitorWebViewContent(source: AccessibilityNodeInfo) {
        val webContent = source.text?.toString() ?: return
        keyWords.forEach { keyword ->
            if (webFilterManager!!.isBlocked(webContent, keyWords)) {
                // Tăng counter
                incrementKeywordCounter(keyword.id)

                // Go back and show warning
                performGlobalAction(GLOBAL_ACTION_BACK)
                showBlockedContentWarning()
            }
        }
    }


    private fun showBlockedContentWarning() {
        val intent = Intent(this, MainChildActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("show_warning", true)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.d("WebFilter", "Service interrupted")
        isInitialized = false
    }
    override fun onDestroy() {
        Log.d("WebFilter", "Service destroyed")
        isInitialized = false
        webFilterManager = null
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d("WebFilter", "Service unbound")
        isInitialized = false
        return super.onUnbind(intent)
    }
}
