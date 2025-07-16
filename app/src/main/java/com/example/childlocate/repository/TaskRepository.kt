package com.example.childlocate.repository

import com.example.childlocate.data.model.Task
import com.google.firebase.database.ValueEventListener

interface TaskRepository {
    
    suspend fun sendTaskRequest(childId: String, taskName: String, taskTime: String): Boolean
    
    suspend fun validateTask(childId: String, taskName: String, taskTime: String): TaskValidationResult
    
    suspend fun assignTask(childId: String, taskName: String, taskTime: String): Boolean
    
    fun observeTasks(childId: String, onTasksChanged: (List<Task>) -> Unit): ValueEventListener
    
    fun removeTasksListener(listener: ValueEventListener)
    
    suspend fun approveTask(childId: String, taskId: String, isApproved: Boolean): Boolean
    
    suspend fun deleteExpiredTasks(childId: String, taskIds: List<String>): Boolean
    
    // Định nghĩa trạng thái cho kết quả xác thực nhiệm vụ
    sealed class TaskValidationResult {
        data object Valid : TaskValidationResult()
        data class TimeConflict(val conflictingTask: Task) : TaskValidationResult()
        data class DuplicateTask(val similarTask: Task) : TaskValidationResult()
        data class InvalidTime(val reason: String) : TaskValidationResult()
    }
}


