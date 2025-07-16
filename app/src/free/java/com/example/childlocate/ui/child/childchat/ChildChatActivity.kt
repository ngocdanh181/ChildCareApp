package com.example.childlocate.ui.child.childchat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.childlocate.R

class ChildChatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child_chat)

        // Nhận dữ liệu từ Intent
        val familyId = intent.getStringExtra("receiverId")
        val childId = intent.getStringExtra("senderId")

        // Tạo mới Fragment và truyền dữ liệu vào
        if (savedInstanceState == null) {
            val fragment = ChildChatDetailFragment.newInstance(familyId, childId)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commitNow()
        }


    }
}

/*
/*companion object {
        private const val ARG_PARENT_ID = "parent_id"
        private const val ARG_CHILD_ID = "child_id"

        fun newInstance(parentId: String?, childId: String?) = ChildChatDetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PARENT_ID, parentId)
                putString(ARG_CHILD_ID, childId)
            }
        }
    }

    private lateinit var binding: FragmentDetailChatBinding
    private val viewModel: ChildChatDetailViewModel by lazy {
        ViewModelProvider(this)[ChildChatDetailViewModel::class.java]
    }

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var senderId: String
    private lateinit var receiverId: String

    private var isAtBottom = true

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true) {
            // Permissions granted, open gallery
            openGallery()
        } else {
            // Permissions denied
        }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            viewModel.uploadAvatar(it)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            receiverId = it.getString(ARG_PARENT_ID) ?: ""
            senderId = it.getString(ARG_CHILD_ID) ?: ""
        }
    }
 */