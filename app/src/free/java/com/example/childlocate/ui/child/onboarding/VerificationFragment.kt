package com.example.childlocate.ui.child.onboarding

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.childlocate.databinding.FragmentVerificationBinding
import com.example.childlocate.ui.child.childstart.ChildViewModel
import com.example.childlocate.ui.child.childstart.VerificationState
import com.google.gson.Gson


class VerificationFragment : Fragment() {

    private var _binding: FragmentVerificationBinding? = null
    private val binding get() = _binding!!
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    private val viewModel: ChildViewModel by lazy {
        ViewModelProvider(this)[ChildViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Khởi tạo SharedPreferences với context an toàn
        sharedPreferences = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        // Kiểm tra nếu đã lưu trữ childId và familyId trong SharedPreferences
        val savedChildId = sharedPreferences.getString("childId", null)
        val savedFamilyId = sharedPreferences.getString("familyId", null)

        Log.d("VerificationFragment", "Saved Child ID: $savedChildId, Family ID: $savedFamilyId")

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
        viewModel.verificationStatus.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is VerificationState.Loading -> {
                    binding.submitButton.isEnabled = false
                    binding.submitButton.text = "Đang xác thực..."
                    showToast("Đang xác thực mã gia đình...")
                }

                is VerificationState.Success -> {
                    binding.submitButton.isEnabled = true
                    binding.submitButton.text = "Tham gia"
                    showToast("Tham gia gia đình thành công!")

                    // Observe childId để lấy ID và lưu vào SharedPreferences
                    observeChildIdForSuccess(state)
                }

                is VerificationState.Error -> {
                    binding.submitButton.isEnabled = true
                    binding.submitButton.text = "Tham gia"
                    showToast(state.message)

                    // Focus on appropriate field based on error
                    handleErrorFocus(state.message)
                }
            }
        })
    }

    private fun observeChildIdForSuccess(successState: VerificationState.Success) {
        viewModel.childId.observe(viewLifecycleOwner, Observer { childId ->
            if (childId != null) {
                // Use real familyId instead of familyCode
                saveToSharedPreferences(childId, successState.familyId, successState.familyCode)
                navigateToMainActivity(childId, successState.familyId)

                // Remove observer sau khi xử lý xong để tránh multiple calls
                viewModel.childId.removeObservers(viewLifecycleOwner)
            }
        })
        viewModel.parentPhones.observe(viewLifecycleOwner, Observer { parentPhones ->
            if (parentPhones.isNotEmpty()) {
                val json = gson.toJson(parentPhones)
                sharedPreferences.edit()
                    .putString("parentPhones", json)
                    .apply()
            } else {
                Log.d("VerificationFragment", "No parent phones found")
            }
        })
    }

    private fun handleErrorFocus(errorMessage: String) {
        when {
            errorMessage.contains("mã gia đình", ignoreCase = true) -> {
                binding.childIdEditText.error = "Mã gia đình không hợp lệ"
                binding.childIdEditText.requestFocus()
            }
            errorMessage.contains("tên", ignoreCase = true) -> {
                binding.childNameEditText.requestFocus()
            }
        }
    }

    private fun saveToSharedPreferences(childId: String, familyId: String, familyCode: String) {
        sharedPreferences.edit().apply {
            putString("childId", childId)
            putString("familyId", familyId)        // Lưu familyId thực sự
            putString("familyCode", familyCode)    // Lưu thêm familyCode để reference
            apply()
        }

        Log.d("VerificationFragment", "Saved to SharedPreferences - Child ID: $childId, Family ID: $familyId, Family Code: $familyCode")
    }

    private fun navigateToMainActivity(childId: String, familyId: String) {
        (activity as? OnboardingActivity)?.navigateToNextFragment()
        //val intent = Intent(requireContext(), MainChildActivity::class.java).apply {
        //    putExtra("senderId", childId)         // Child ID
        //    putExtra("receiverId", familyId)      // Family ID
        //    putExtra("childId", childId)
        //    putExtra("familyId", familyId)        // Family ID thực sự
        //}
        //startActivity(intent)
        //
        //// Finish activity thay vì fragment
        //requireActivity().finish()
    }

    private fun showToast(message: String) {
        // Kiểm tra context trước khi hiển thị toast
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

//(activity as? OnboardingActivity)?.navigateToNextFragment()