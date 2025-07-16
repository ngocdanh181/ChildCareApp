package com.example.childlocate.ui.parent.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.childlocate.R
import com.example.childlocate.databinding.ActivitySignupBinding
import com.example.childlocate.ui.parent.MainActivity
import com.example.childlocate.ui.WelcomeActivity
import com.example.childlocate.ui.parent.verify.VerifyFamilyActivity

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private val viewModel: AuthViewModel by lazy {
        ViewModelProvider(this)[AuthViewModel::class.java]
    }
    
    private lateinit var parentRole: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get parent role from intent
        parentRole = intent.getStringExtra("PARENT_ROLE") ?: "PRIMARY"

        setupPasswordValidation()
        setupClickListeners()
        observeAuthState()
    }

    private fun setupPasswordValidation() {
        // Email validation - only when user finishes typing or loses focus
        binding.emailEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val email = binding.emailEditText.text.toString()
                if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    binding.emailInputLayout.error = "Email không hợp lệ"
                } else {
                    binding.emailInputLayout.error = null
                }
            }
        }

        // Password validation - only when user finishes typing or loses focus
        binding.passwordEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val password = binding.passwordEditText.text.toString()
                if (password.isNotEmpty()) {
                    val error = viewModel.validatePassword(password)
                    if (error != null) {
                        binding.passwordInputLayout.error = "Mật khẩu chưa đủ mạnh"
                        binding.passwordInputLayout.setErrorTextColor(
                            ContextCompat.getColorStateList(this@SignupActivity, R.color.red)
                        )
                    } else {
                        binding.passwordInputLayout.error = null
                        binding.passwordInputLayout.setHelperText("Mật khẩu mạnh ✓")
                        binding.passwordInputLayout.setHelperTextColor(
                            ContextCompat.getColorStateList(this@SignupActivity, R.color.green)
                        )
                    }
                } else {
                    binding.passwordInputLayout.error = null
                    binding.passwordInputLayout.helperText = null
                }
            }
        }

        // Also validate when user types enough characters (minimum length check)
        binding.passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val password = s.toString()
                // Only show positive feedback when password becomes strong
                if (password.length >= 8) {
                    val error = viewModel.validatePassword(password)
                    if (error == null) {
                        binding.passwordInputLayout.error = null
                        binding.passwordInputLayout.setHelperText("Mật khẩu mạnh ✓")
                        binding.passwordInputLayout.setHelperTextColor(
                            ContextCompat.getColorStateList(this@SignupActivity, R.color.green)
                        )
                    }
                } else {
                    // Clear helper text when password is too short
                    binding.passwordInputLayout.helperText = null
                    binding.passwordInputLayout.error = null
                }
            }
        })
    }

    private fun setupClickListeners() {
        binding.signUpButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString()

            // Validation trước khi submit
            if (email.isEmpty()) {
                binding.emailInputLayout.error = "Vui lòng nhập email"
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                binding.passwordInputLayout.error = "Vui lòng nhập mật khẩu"
                return@setOnClickListener
            }

            // Disable button để tránh multiple clicks
            binding.signUpButton.isEnabled = false
            binding.signUpButton.text = "Đang xử lý..."

            viewModel.registerUser(email, password, parentRole)
        }

        binding.signInText.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun observeAuthState() {
        viewModel.authState.observe(this, Observer { state ->
            // Re-enable button
            binding.signUpButton.isEnabled = true
            binding.signUpButton.text = "Đăng ký"

            when (state) {
                is AuthState.ShowWelcome -> {
                    // This should only happen for PRIMARY parent
                    Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()
                    
                    val intent = Intent(this, WelcomeActivity::class.java)
                    intent.putExtra("FAMILY_CODE", state.familyCode)
                    startActivity(intent)
                    finish()
                }

                is AuthState.NeedsFamilyVerification -> {
                    // This happens for SECONDARY parent after successful registration
                    Toast.makeText(this, "Đăng ký thành công! Vui lòng xác nhận Family ID.", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, VerifyFamilyActivity::class.java))
                    finish()
                }

                is AuthState.LoggedIn -> {
                    // Fallback case
                    Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()
                    when (parentRole) {
                        "PRIMARY" -> {
                            startActivity(Intent(this, MainActivity::class.java))
                        }
                        "SECONDARY" -> {
                            startActivity(Intent(this, VerifyFamilyActivity::class.java))
                        }
                    }
                    finish()
                }

                is AuthState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    
                    // Generic error handling - don't give too much detail
                    when {
                        state.message.contains("email", ignoreCase = true) -> {
                            binding.emailEditText.requestFocus()
                        }
                        state.message.contains("password", ignoreCase = true) || 
                        state.message.contains("mật khẩu", ignoreCase = true) -> {
                            binding.passwordEditText.requestFocus()
                        }
                    }
                }

                else -> {}
            }
        })
    }
}
