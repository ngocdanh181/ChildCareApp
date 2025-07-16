package com.example.childlocate.ui

import androidx.appcompat.app.AppCompatActivity

class ForgotPasswordActivity : AppCompatActivity() {

   /* private lateinit var binding: ActivityForgotPasswordBinding
    private val viewModel: ResetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.sendOtpButton.setOnClickListener {
            val email = binding.emailEditText.text.toString()
            viewModel.sendOtp(email)
        }

        viewModel.otpSentStatus.observe(this, Observer { isSuccess ->
            if (isSuccess) {
                Toast.makeText(this, "OTP Sent Successfully", Toast.LENGTH_SHORT).show()
                binding.otpEditText.visibility = View.VISIBLE
                binding.newPasswordEditText.visibility = View.VISIBLE
                binding.resetPasswordButton.visibility = View.VISIBLE
            } else {
                Toast.makeText(this, "Failed to send OTP", Toast.LENGTH_SHORT).show()
            }
        })

        binding.resetPasswordButton.setOnClickListener {
            val otp = binding.otpEditText.text.toString()
            val newPassword = binding.newPasswordEditText.text.toString()
            if (verifyOtp(otp)) {
                viewModel.resetPassword(otp, newPassword)
            } else {
                Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.resetPasswordStatus.observe(this, Observer { isSuccess ->
            if (isSuccess) {
                Toast.makeText(this, "Password Reset Successfully", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Failed to reset password", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun verifyOtp(inputOtp: String): Boolean {
        val sharedPreferences = getSharedPreferences("otp_prefs", Context.MODE_PRIVATE)
        val savedOtp = sharedPreferences.getString("otp", null)
        return inputOtp == savedOtp
    }*/
}
