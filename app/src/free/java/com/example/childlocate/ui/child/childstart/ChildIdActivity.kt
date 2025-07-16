package com.example.childlocate.ui.child.childstart

// ChildIdActivity.kt
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.childlocate.databinding.ActivityChildIdBinding
import com.example.childlocate.ui.child.main.MainChildActivity

class ChildIdActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChildIdBinding
    private val viewModel: ChildViewModel by lazy {
        ViewModelProvider(this)[ChildViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildIdBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Kiểm tra nếu đã lưu trữ childId và familyId trong SharedPreferences
        val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val savedChildId = sharedPreferences.getString("childId", null)
        val savedFamilyId = sharedPreferences.getString("familyId", null)

        Log.d("ChildActivity", "Saved Child ID: $savedChildId, Family ID: $savedFamilyId")

        if (savedChildId != null && savedFamilyId != null) {
            // Nếu đã lưu trữ, chuyển người dùng đến MainChildActivity
            navigateToMainActivity(savedChildId, savedFamilyId)
            return
        }

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.submitButton.setOnClickListener {
            val familyCode = binding.childIdEditText.text.toString().trim().uppercase()
            val childName = binding.childNameEditText.text.toString().trim()

            // Validation
            if (familyCode.isEmpty()) {
                binding.childIdEditText.error = "Vui lòng nhập mã gia đình"
                return@setOnClickListener
            }

            if (childName.isEmpty()) {
                binding.childNameEditText.error = "Vui lòng nhập tên"
                return@setOnClickListener
            }

            if (familyCode.length != 6) {
                binding.childIdEditText.error = "Mã gia đình phải có 6 ký tự"
                return@setOnClickListener
            }

            // Clear previous errors
            binding.childIdEditText.error = null
            binding.childNameEditText.error = null

            // Start verification
            viewModel.verifyFamilyCodeAndSaveChild(familyCode, childName)
        }
    }

    private fun observeViewModel() {
        viewModel.verificationStatus.observe(this, Observer { state ->
            when (state) {
                is VerificationState.Loading -> {
                    binding.submitButton.isEnabled = false
                    binding.submitButton.text = "Đang xác thực..."
                    Toast.makeText(this, "Đang xác thực mã gia đình...", Toast.LENGTH_SHORT).show()
                }

                is VerificationState.Success -> {
                    binding.submitButton.isEnabled = true
                    binding.submitButton.text = "Tham gia"
                    
                    Toast.makeText(this, "Tham gia gia đình thành công!", Toast.LENGTH_SHORT).show()
                    
                    // Get child ID and family ID to save to SharedPreferences
                    viewModel.childId.observe(this, Observer { childId ->
                        if (childId != null) {
                            // Use real familyId instead of familyCode
                            saveToSharedPreferences(childId, state.familyId, state.familyCode)
                            navigateToMainActivity(childId, state.familyId)
                        }
                    })
                }

                is VerificationState.Error -> {
                    binding.submitButton.isEnabled = true
                    binding.submitButton.text = "Tham gia"
                    
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    
                    // Focus on appropriate field based on error
                    when {
                        state.message.contains("mã gia đình", ignoreCase = true) -> {
                            binding.childIdEditText.error = "Mã gia đình không hợp lệ"
                            binding.childIdEditText.requestFocus()
                        }
                        state.message.contains("tên", ignoreCase = true) -> {
                            binding.childNameEditText.requestFocus()
                        }
                    }
                }
            }
        })
    }

    private fun saveToSharedPreferences(childId: String, familyId: String, familyCode: String) {
        val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        sharedPreferences.edit().apply {
            putString("childId", childId)
            putString("familyId", familyId)        // ← Lưu familyId thực sự
            putString("familyCode", familyCode)   // ← Lưu thêm familyCode để reference
            apply()
        }
        
        Log.d("ChildActivity", "Saved to SharedPreferences - Child ID: $childId, Family ID: $familyId, Family Code: $familyCode")
    }

    private fun navigateToMainActivity(childId: String, familyId: String) {
        val intent = Intent(this, MainChildActivity::class.java).apply {
            putExtra("senderId", childId)         // Child ID
            putExtra("receiverId", familyId)      // Family ID 
            putExtra("childId", childId)
            putExtra("familyId", familyId)        // Family ID thực sự
        }
        startActivity(intent)
        finish()
    }
}


