package com.example.childlocate.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.childlocate.databinding.ActivityWelcomeBinding
import com.example.childlocate.ui.parent.MainActivity

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private var familyCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get family code from intent
        familyCode = intent.getStringExtra("FAMILY_CODE") ?: ""
        
        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        if (familyCode.isNotEmpty()) {
            binding.familyCodeText.text = familyCode
        } else {
            binding.familyCodeText.text = "ERRO01"
            Toast.makeText(this, "Có lỗi xảy ra với mã gia đình", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupClickListeners() {
        binding.copyCodeButton.setOnClickListener {
            copyToClipboard()
        }

        binding.shareCodeButton.setOnClickListener {
            shareCode()
        }

        binding.continueButton.setOnClickListener {
            navigateToMainActivity()
        }

        binding.skipButton.setOnClickListener {
            navigateToMainActivity()
        }

    }

    private fun copyToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Family Code", familyCode)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, "Đã sao chép mã gia đình!", Toast.LENGTH_SHORT).show()
        
        // Visual feedback
        binding.copyCodeButton.text = "Đã sao chép ✓"
        binding.copyCodeButton.postDelayed({
            binding.copyCodeButton.text = "Sao chép"
        }, 2000)
    }

    private fun shareCode() {
        val shareText = """
            🏠 Tham gia gia đình của bạn trên ứng dụng Child Locate!
            
            Mã gia đình: $familyCode
            
            📱 Tải ứng dụng và nhập mã này để kết nối với gia đình.
            
            - Phụ huynh: Chọn "Phụ huynh phụ" và nhập mã
            - Con em: Chọn "Con" và nhập mã
        """.trimIndent()

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Mời tham gia gia đình - Child Locate")
        }

        startActivity(Intent.createChooser(shareIntent, "Chia sẻ mã gia đình"))
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
} 