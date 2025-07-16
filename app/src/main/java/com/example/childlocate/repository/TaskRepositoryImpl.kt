package com.example.childlocate.repository

import android.content.Context
import android.icu.util.Calendar
import android.util.Log
import com.example.childlocate.data.api.RetrofitInstance
import com.example.childlocate.data.model.Data
import com.example.childlocate.data.model.FcmRequest
import com.example.childlocate.data.model.Message
import com.example.childlocate.data.model.Task
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

class TaskRepositoryImpl(private val context: Context) : TaskRepository {

    private val database = FirebaseDatabase.getInstance().reference
    private lateinit var databaseRef: DatabaseReference

    override suspend fun sendTaskRequest(childId: String, taskName: String, taskTime: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val token = getToken(childId) ?: return@withContext false
                Log.d("FCM", token)

                val serviceAccount: InputStream = context.assets.open("childlocatedemo.json")
                val credentials = GoogleCredentials.fromStream(serviceAccount)
                    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

                val accessToken = credentials.refreshAccessToken().tokenValue
                val authHeader = "Bearer $accessToken"

                val requestType = "send_task_request"
                val additionalInfo = "$taskName|$taskTime"

                val request = FcmRequest(
                    message = Message(
                        token = token,
                        data = Data(request_type = "$requestType|$additionalInfo")
                    )
                )

                Log.d("FCMTask","$request")

                val response = RetrofitInstance.api.sendLocationRequest(authHeader, request)
                return@withContext response.isSuccessful
            } catch (e: IOException) {
                Log.d("FCM", "Error: ${e.message}")
                return@withContext false
            }
        }
    }

    override suspend fun validateTask(childId: String, taskName: String, taskTime: String): TaskRepository.TaskValidationResult {
        return try {
            databaseRef = FirebaseDatabase.getInstance().getReference("tasks/$childId")
            
            // Lấy danh sách nhiệm vụ hiện tại
            val snapshot = databaseRef.get().await()
            val currentTasks = mutableListOf<Task>()

            for (taskSnapshot in snapshot.children) {
                val task = taskSnapshot.getValue(Task::class.java)
                task?.let { currentTasks.add(it) }
            }

            // Kiểm tra thời gian
            val taskTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val firebaseTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val newTaskTime = taskTimeFormat.parse(taskTime)?.time

            val conflictingTimeTask = currentTasks.find { existingTask ->
                if (!existingTask.parentApproved) {
                    val existingTaskTime = firebaseTimeFormat.parse(existingTask.time)?.time ?: 0L
                    val timeDiff = abs(existingTaskTime - newTaskTime!!)
                    // Nếu nhiệm vụ cách nhau ít hơn 1 giờ (1,600,000 milliseconds)
                    timeDiff < 1_600_000
                } else false
            }

            if (conflictingTimeTask != null) {
                return TaskRepository.TaskValidationResult.TimeConflict(conflictingTimeTask)
            }

            // Kiểm tra thời gian hợp lệ (không phải trong quá khứ)
            val currentTime = System.currentTimeMillis()
            if (newTaskTime!! < currentTime) {
                return TaskRepository.TaskValidationResult.InvalidTime("Thời gian nhiệm vụ không thể trong quá khứ")
            }

            TaskRepository.TaskValidationResult.Valid

        } catch (e: Exception) {
            Log.e("TaskRepository", "Error validating task", e)
            TaskRepository.TaskValidationResult.InvalidTime("Lỗi khi kiểm tra nhiệm vụ: ${e.message}")
        }
    }

    override suspend fun assignTask(childId: String, taskName: String, taskTime: String): Boolean {
        return try {
            databaseRef = FirebaseDatabase.getInstance().getReference("tasks/$childId")
            val taskId = databaseRef.push().key ?: return false
            
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val formattedTime = try {
                val date = inputFormat.parse(taskTime)
                outputFormat.format(date!!)
            } catch (e: Exception) {
                taskTime // fallback to original format if parsing fails
            }
            
            val task = Task(taskId, taskName, formattedTime)
            databaseRef.child(taskId).setValue(task).await()
            
            // Send notification to child device
            sendTaskRequest(childId, taskName, formattedTime)
            
            Log.d("TaskRepository", "$taskTime and $formattedTime")
            true
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error assigning task", e)
            false
        }
    }

    override fun observeTasks(childId: String, onTasksChanged: (List<Task>) -> Unit): ValueEventListener {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val firebaseTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        databaseRef = FirebaseDatabase.getInstance().getReference("tasks/$childId")
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val todayTasks = mutableListOf<Task>()
                val expiredTaskIds = mutableListOf<String>()
                
                for (taskSnapshot in snapshot.children) {
                    val task = taskSnapshot.getValue(Task::class.java)
                    if (task != null) {
                        val taskTime = firebaseTimeFormat.parse(task.time)?.time ?: 0L
                        if (taskTime >= today) {
                            todayTasks.add(task) // Chỉ lấy hôm nay
                        } else {
                            expiredTaskIds.add(task.id) // Collect để xóa
                        }
                    }
                }
                
                // Update UI ngay
                onTasksChanged(todayTasks)
                
                // Xóa expired tasks
                if (expiredTaskIds.isNotEmpty()) {
                    GlobalScope.launch {
                        try {
                            deleteExpiredTasks(childId, expiredTaskIds)
                        } catch (e: Exception) {
                            // Không quan tâm lỗi, chỉ log
                            Log.d("TaskRepository", "Failed to cleanup expired tasks: ${e.message}")
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TaskRepository", "Failed to load tasks", error.toException())
            }
        }
        
        databaseRef.addValueEventListener(listener)
        return listener
    }

    override fun removeTasksListener(listener: ValueEventListener) {
        if (::databaseRef.isInitialized) {
            databaseRef.removeEventListener(listener)
        }
    }

    override suspend fun approveTask(childId: String, taskId: String, isApproved: Boolean): Boolean {
        return try {
            val taskRef = FirebaseDatabase.getInstance().getReference("tasks/$childId/$taskId")
            taskRef.child("parentApproved").setValue(isApproved).await()
            Log.d("TaskRepository", "Task approved successfully")
            true
        } catch (e: Exception) {
            Log.e("TaskRepository", "Failed to approve task", e)
            false
        }
    }

    override suspend fun deleteExpiredTasks(childId: String, taskIds: List<String>): Boolean {
        return try {
            val database = FirebaseDatabase.getInstance()
            val updates = mutableMapOf<String, Any?>()

            taskIds.forEach { taskId ->
                updates["tasks/$childId/$taskId"] = null
            }

            database.reference.updateChildren(updates).await()
            Log.d("TaskRepository", "Cleaned up ${taskIds.size} expired tasks")
            true
        } catch (e: Exception) {
            Log.e("TaskRepository", "Failed to delete expired tasks", e)
            false
        }
    }

    private suspend fun getToken(childId: String): String? {
        return try {
            database.child("users").child(childId).child("primaryDeviceToken").get().await().getValue(String::class.java)
        } catch (e: Exception) {
            Log.e("FCM", "Failed to get FCM token: ${e.message}")
            null
        }
    }
}