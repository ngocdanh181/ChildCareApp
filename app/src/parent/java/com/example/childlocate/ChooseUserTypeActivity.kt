package com.example.childlocate.ui
// ChooseUserTypeActivity.kt
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import com.example.childlocate.databinding.ActivityChooseUserTypeBinding
import com.example.childlocate.ui.parent.login.LoginActivity
import com.example.childlocate.viewmodel.ChooseUserViewModel

class ChooseUserTypeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChooseUserTypeBinding
    private val viewModel: ChooseUserViewModel by lazy {
        ViewModelProvider(this)[ChooseUserViewModel::class.java]
    }

    private val PERMISSION_REQUEST_CODE_LOCATION = 1001
    private val PERMISSION_REQUEST_CODE_CALL = 1002
    private val PERMISSION_REQUEST_CODE_STORAGE = 1003

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChooseUserTypeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.fetchParentId()

        binding.parentButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra("PARENT_ROLE", "PRIMARY")
            startActivity(intent)
        }
        
        binding.otherParentButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra("PARENT_ROLE", "SECONDARY")
            startActivity(intent)
        }

    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Handle permission granted
                } else {
                    showPermissionDeniedDialog(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
            PERMISSION_REQUEST_CODE_CALL -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Handle permission granted
                } else {
                    showPermissionDeniedDialog(Manifest.permission.CALL_PHONE)
                }
            }
            PERMISSION_REQUEST_CODE_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Handle permission granted
                } else {
                    showPermissionDeniedDialog(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun showPermissionDeniedDialog(permission: String) {
        val permissionName = when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION -> "vị trí"
            Manifest.permission.CALL_PHONE -> "danh bạ"
            Manifest.permission.READ_EXTERNAL_STORAGE -> "camera"
            else -> "quyền"
        }

        AlertDialog.Builder(this)
            .setTitle("Yêu cầu quyền")
            .setMessage("Ứng dụng cần quyền $permissionName để hoạt động. Vui lòng cấp quyền.")
            .setPositiveButton("OK") { _, _ ->
                ActivityCompat.requestPermissions(this, arrayOf(permission), requestCodeFromPermission(permission))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestCodeFromPermission(permission: String): Int {
        return when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION -> PERMISSION_REQUEST_CODE_LOCATION
            Manifest.permission.CALL_PHONE -> PERMISSION_REQUEST_CODE_CALL
            Manifest.permission.READ_EXTERNAL_STORAGE -> PERMISSION_REQUEST_CODE_STORAGE
            else -> -1
        }
    }
}
