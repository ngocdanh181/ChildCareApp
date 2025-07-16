package com.example.childlocate.ui.parent.verify

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.childlocate.databinding.ActivityVerifyFamilyBinding
import com.example.childlocate.ui.parent.MainActivity

class VerifyFamilyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVerifyFamilyBinding
    private val viewModel: VerifyFamilyViewModel by lazy {
        ViewModelProvider(this)[VerifyFamilyViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyFamilyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        binding.verifyButton.setOnClickListener {
            val familyId = binding.familyIdEditText.text.toString().trim()
            if (familyId.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập Family ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.verifyFamilyId(familyId)
        }

        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.verificationResult.observe(this) { result ->
            result.fold(
                onSuccess = { familyId ->
                    // Chuyển đến MainActivity với role parent_secondary
                    val intent = Intent(this, MainActivity::class.java).apply {
                    }
                    startActivity(intent)
                    finish()
                },
                onFailure = { error ->
                    Toast.makeText(this, "Lỗi: ${error.message}", Toast.LENGTH_SHORT).show()
                    Log.d("VerifyFamilyActivity", "Error: ${error.message}")
                }
            )
        }
    }
} 