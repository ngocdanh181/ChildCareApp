package com.example.childlocate.ui.parent.userinfo

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.childlocate.R
import com.example.childlocate.databinding.FragmentUserInfoBinding
import com.example.childlocate.ui.ChooseUserTypeActivity

class UserInfoFragment : Fragment() {

    private lateinit var binding: FragmentUserInfoBinding
    private val viewModel: UserInfoViewModel by viewModels()
    private lateinit var familyMemberAdapter: FamilyMemberAdapter
    
    // Biến lưu ID của thành viên đang được chọn để cập nhật avatar
    private var selectedMemberId: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions[android.Manifest.permission.READ_MEDIA_IMAGES] == true
        } else {
            permissions[android.Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        
        if (hasPermission) {
            openGallery()
        } else {
            Toast.makeText(requireContext(), "Quyền truy cập ảnh bị từ chối", Toast.LENGTH_SHORT).show()
        }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // Sử dụng selectedMemberId nếu có, nếu không thì sử dụng ID của người dùng hiện tại
            val memberId = selectedMemberId ?: viewModel.userData.value?.userId ?: return@let
            viewModel.uploadAvatar(memberId, it)
            
            // Reset selectedMemberId sau khi upload
            selectedMemberId = null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentUserInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFamilyMembersRecyclerView()
        observeViewModel()
        setupClickListeners()
        hideUnusedFeatures()

        viewModel.fetchUserData()
        viewModel.fetchFamilyMembers()
        viewModel.fetchFamilyCode()
        viewModel.fetchAppVersion(requireContext())
    }

    private fun setupFamilyMembersRecyclerView() {
        familyMemberAdapter = FamilyMemberAdapter(
            onEditClick = { member ->
                showEditProfileDialog(member.id, member.name, member.phone)
            },
            onDeleteClick = { member ->
                showDeleteMemberConfirmation(member.id, member.name)
            },
            onAvatarClick = { member ->
                showAvatarOptionsDialog(member.id, member.name)
            }
        )

        binding.familyMembersRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = familyMemberAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.userData.observe(viewLifecycleOwner) { user ->
            binding.nameTextView.text = user.name
            binding.emailTextView.text = user.email

            if (user.avatarUrl.isNotEmpty()) {
                Glide.with(this).load(user.avatarUrl).into(binding.imageAvatar)
                binding.clickToChooseAvatarTextView.visibility = View.GONE
            } else {
                binding.imageAvatar.setImageResource(R.drawable.baseline_person_2_24)
                binding.clickToChooseAvatarTextView.visibility = View.VISIBLE
            }
        }

        viewModel.familyMembers.observe(viewLifecycleOwner) { members ->
            familyMemberAdapter.submitList(members)
            Log.d("UserInfoFragment", "Family members updated: ${members} members")
        }
        viewModel.familyCodeStatus.observe(viewLifecycleOwner){
            binding.userIdTextView.text = "Mã gia đình: ${it }"
        }

        binding.appVersionTextView.text = getString(R.string.app_version)

        viewModel.passwordChangeResult.observe(viewLifecycleOwner) { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        viewModel.logoutStatus.observe(viewLifecycleOwner) { isLoggedOut ->
            if (isLoggedOut) {
                val intent = Intent(activity, ChooseUserTypeActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            }
        }

        viewModel.avatarUploadResult.observe(viewLifecycleOwner) { result ->
            result.fold(
                onSuccess = {
                    Toast.makeText(requireContext(), "Cập nhật avatar thành công", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Toast.makeText(requireContext(), "Lỗi: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }

        viewModel.memberOperationResult.observe(viewLifecycleOwner) { result ->
            result.fold(
                onSuccess = { message ->
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Toast.makeText(requireContext(), "Lỗi: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun setupClickListeners() {
        // Avatar của người dùng hiện tại
        binding.imageAvatar.setOnClickListener {
            // Reset selectedMemberId để đảm bảo upload cho người dùng hiện tại
            selectedMemberId = null
            PermissionManager.requestStoragePermission(requestPermissionLauncher)
        }

        binding.editAvatarIcon.setOnClickListener {
            // Reset selectedMemberId để đảm bảo upload cho người dùng hiện tại
            selectedMemberId = null
            PermissionManager.requestStoragePermission(requestPermissionLauncher)
        }

        // Password
        binding.changePasswordTextView.setOnClickListener {
            showChangePasswordDialog()
        }

        // Edit Profile
        binding.editProfileTextView.setOnClickListener {
            val userId = viewModel.userData.value?.userId ?: return@setOnClickListener
            val name = viewModel.userData.value?.name ?: ""
            val phone = viewModel.userData.value?.phone ?: ""
            showEditProfileDialog(userId, name, phone)
        }

        // Family Members
        binding.addFamilyMemberButton.setOnClickListener {
            showAddFamilyMemberDialog()
        }

        // Support & About
        binding.termsOfServiceTextView.setOnClickListener {
            // Điều hướng đến trang Điều khoản dịch vụ
            Toast.makeText(requireContext(), "Chức năng này sẽ được phát triển sau", Toast.LENGTH_SHORT).show()
        }

        binding.privacyPolicyTextView.setOnClickListener {
            // Điều hướng đến trang Chính sách bảo mật
            Toast.makeText(requireContext(), "Chức năng này sẽ được phát triển sau", Toast.LENGTH_SHORT).show()
        }

        binding.contactSupportTextView.setOnClickListener {
            // Mở ứng dụng email để liên hệ hỗ trợ
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:support@childlocate.com")
                putExtra(Intent.EXTRA_SUBJECT, "Yêu cầu hỗ trợ")
            }
            startActivity(Intent.createChooser(intent, "Gửi email"))
        }

        // Logout
        binding.logoutButton.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun hideUnusedFeatures() {
        // Ẩn các chức năng tracking, notification, security
        binding.cardTrackingSettings.visibility = View.GONE
        binding.cardNotifications.visibility = View.GONE 
        binding.cardPrivacySecurity.visibility = View.GONE
    }
    
    /**
     * Hiển thị dialog tùy chọn khi nhấn vào avatar của thành viên
     */
    private fun showAvatarOptionsDialog(memberId: String, memberName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Avatar của $memberName")
            .setItems(arrayOf("Thay đổi avatar", "Hủy")) { dialog, which ->
                when (which) {
                    0 -> {
                        // Lưu ID của thành viên được chọn
                        selectedMemberId = memberId
                        PermissionManager.requestStoragePermission(requestPermissionLauncher)
                    }
                    1 -> dialog.dismiss()
                }
            }
            .create()
            .show()
    }

    private fun openGallery() {
        getContent.launch("image/*")
    }

    private fun showChangePasswordDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_change_password, null)
        val currentPasswordEditText = dialogView.findViewById<EditText>(R.id.current_password_edit_text)
        val newPasswordEditText = dialogView.findViewById<EditText>(R.id.new_password_edit_text)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.change_password)
            .setView(dialogView)
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                val currentPassword = currentPasswordEditText.text.toString()
                val newPassword = newPasswordEditText.text.toString()
                
                if (currentPassword.isEmpty() || newPassword.isEmpty()) {
                    Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (newPassword.length < 6) {
                    Toast.makeText(requireContext(), "Mật khẩu mới phải có ít nhất 6 ký tự", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                viewModel.changePassword(currentPassword, newPassword)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun showEditProfileDialog(memberId: String, currentName: String, currentPhone: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_profile, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.name_edit_text)
        val phoneEditText = dialogView.findViewById<EditText>(R.id.phone_edit_text)

        // Điền giá trị hiện tại
        nameEditText.setText(currentName)
        phoneEditText.setText(currentPhone)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.edit_profile)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val name = nameEditText.text.toString()
                val phone = phoneEditText.text.toString()
                
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "Tên không được để trống", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                viewModel.updateProfile(memberId, name, phone)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun showAddFamilyMemberDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_family_member, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.name_edit_text)
        val phoneEditText = dialogView.findViewById<EditText>(R.id.phone_edit_text)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_family_member)
            .setView(dialogView)
            .setPositiveButton(R.string.add) { dialog, _ ->
                val name = nameEditText.text.toString()
                val phone = phoneEditText.text.toString()
                
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "Tên không được để trống", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Mặc định role là "child" khi thêm thành viên mới
                viewModel.addFamilyMember(name, phone, "child")
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun showDeleteMemberConfirmation(memberId: String, memberName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_member)
            .setMessage(getString(R.string.delete_member_confirmation, memberName))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteFamilyMember(memberId)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
            .show()
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirmation)
            .setPositiveButton(R.string.yes) { _, _ ->
                viewModel.logout()
            }
            .setNegativeButton(R.string.no, null)
            .create()
            .show()
    }
}