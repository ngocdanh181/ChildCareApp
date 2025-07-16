package com.example.childlocate.ui.parent.login

// LoginActivity.kt

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.childlocate.databinding.ActivityLoginBinding
import com.example.childlocate.ui.ForgotPasswordActivity
import com.example.childlocate.ui.WelcomeActivity
import com.example.childlocate.ui.parent.MainActivity
import com.example.childlocate.ui.parent.verify.VerifyFamilyActivity


class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by lazy {
        ViewModelProvider(this)[AuthViewModel::class.java]
    }
    
    private lateinit var parentRole: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get parent role from intent
        parentRole = intent.getStringExtra("PARENT_ROLE") ?: "PRIMARY"

        setupClickListeners()
        observeAuthState()
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            if (isInternetAvailable()) {
                val email = binding.emailEditText.text.toString().trim()
                val password = binding.passwordEditText.text.toString()

                // Basic validation
                if (email.isEmpty()) {
                    binding.emailInputLayout.error = "Vui lòng nhập email"
                    return@setOnClickListener
                }

                if (password.isEmpty()) {
                    binding.passwordInputLayout.error = "Vui lòng nhập mật khẩu"
                    return@setOnClickListener
                }

                // Clear previous errors
                binding.emailInputLayout.error = null
                binding.passwordInputLayout.error = null

                // Disable button và show loading
                binding.loginButton.isEnabled = false
                binding.loginButton.text = "Đang đăng nhập..."

                viewModel.loginUser(email, password, parentRole)
            } else {
                Toast.makeText(this, "Không có kết nối Internet", Toast.LENGTH_SHORT).show()
            }
        }

        binding.signUpText.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            intent.putExtra("PARENT_ROLE", parentRole)
            startActivity(intent)
        }

        binding.forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    private fun observeAuthState() {
        viewModel.authState.observe(this, Observer { state ->
            // Re-enable button
            binding.loginButton.isEnabled = true
            binding.loginButton.text = "Đăng nhập"

            when (state) {
                is AuthState.LoggedIn -> {
                    Toast.makeText(this, "Đăng nhập thành công", Toast.LENGTH_SHORT).show()
                    
                    // Different navigation based on role
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

                is AuthState.ShowWelcome -> {
                    // This should only happen for PRIMARY parent registration
                    Toast.makeText(this, "Đăng nhập thành công", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, WelcomeActivity::class.java)
                    intent.putExtra("FAMILY_CODE", state.familyCode)
                    startActivity(intent)
                    finish()
                }

                is AuthState.NeedsFamilyVerification -> {
                    // For SECONDARY parent who just registered/logged in
                    startActivity(Intent(this, VerifyFamilyActivity::class.java))
                    finish()
                }

                is AuthState.Error -> {
                    // Generic error message for security - don't reveal specific details
                    val genericMessage = when {
                        state.message.contains("network", ignoreCase = true) ||
                        state.message.contains("timeout", ignoreCase = true) -> 
                            "Lỗi kết nối. Vui lòng thử lại."
                        else -> "Email hoặc mật khẩu không đúng"
                    }
                    
                    Toast.makeText(this, genericMessage, Toast.LENGTH_LONG).show()
                    
                    // Clear previous errors and focus on email for retry
                    binding.emailInputLayout.error = null
                    binding.passwordInputLayout.error = null
                    binding.emailEditText.requestFocus()
                }

                else -> {}
            }
        })
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
