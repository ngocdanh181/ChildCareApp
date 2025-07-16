package com.example.childlocate.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.childlocate.data.model.Child
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ChooseUserViewModel : ViewModel() {
    private val database = FirebaseDatabase.getInstance().reference
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _child = MutableLiveData<Child?>()
    val child: LiveData<Child?> get() = _child


    private val _parentId = MutableLiveData<String?>()
    val parentId: LiveData<String?> get() = _parentId

    fun fetchParentId() {
        // Lấy ID của người dùng hiện tại từ Firebase Authentication
        val userId = firebaseAuth.currentUser?.uid

        // Kiểm tra xem userId có khác null không
        userId?.let { currentUserId ->
            // Tham chiếu đến node của người dùng hiện tại trong cơ sở dữ liệu Firebase
            val userRef = database.child("users").child(currentUserId)

            // Đọc dữ liệu từ cơ sở dữ liệu tại node của người dùng hiện tại
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Lấy giá trị của trường userId (parentId) từ snapshot
                    val parentId = snapshot.child("userId").getValue(String::class.java)

                    // Cập nhật giá trị parentId cho biến _parentId
                    _parentId.value = parentId

                    // Nếu parentId khác null, gọi hàm fetchChild để lấy thông tin của con
                    parentId?.let {
                        fetchChild(it)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Xử lý lỗi nếu có
                    Log.e("FirebaseError", "Error fetching data", error.toException())
                }
            })
        }

    }

    private fun fetchChild(parentId: String) {
        database.child("users").orderByChild("userId").equalTo(parentId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val userSnapshot = snapshot.children.firstOrNull()
                    val childSnapshot = userSnapshot?.child("children")?.children?.firstOrNull()
                    val child = childSnapshot?.getValue(Child::class.java)
                    _child.value = child

                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                }
            })
    }
}

