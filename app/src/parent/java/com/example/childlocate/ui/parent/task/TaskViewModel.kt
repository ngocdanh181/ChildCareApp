package com.example.childlocate.ui.parent.task

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.Task
import com.example.childlocate.repository.TaskRepository
import com.example.childlocate.repository.TaskRepositoryImpl
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch

class TaskViewModel(application: Application): AndroidViewModel(application) {

    private val _tasks = MutableLiveData<List<Task>>()
    val tasks: LiveData<List<Task>> get() = _tasks

    private val repository: TaskRepository = TaskRepositoryImpl(application)

    // Tạo biến để lưu trữ listener
    private var tasksListener: ValueEventListener? = null

    // Thêm trạng thái kiểm tra trùng lặp
    private val _taskValidationResult = MutableLiveData<TaskRepository.TaskValidationResult>()
    val taskValidationResult: LiveData<TaskRepository.TaskValidationResult> get() = _taskValidationResult

    fun assignTaskWithValidation(childId: String, taskName: String, taskTime: String) {
        viewModelScope.launch {
            val validationResult = repository.validateTask(childId, taskName, taskTime)
            _taskValidationResult.postValue(validationResult)

            if (validationResult is TaskRepository.TaskValidationResult.Valid) {
                val success = repository.assignTask(childId, taskName, taskTime)
                if (!success) {
                    _taskValidationResult.postValue(
                        TaskRepository.TaskValidationResult.InvalidTime("Lỗi khi giao nhiệm vụ")
                    )
                }
            }
        }
    }

    fun loadTasksForChild(childId: String) {
        // Nếu đã có listener, xóa nó trước khi lắng nghe
        removeTasksListener()
        
        tasksListener = repository.observeTasks(childId) { tasksList ->
            _tasks.value = tasksList
        }
    }

    private fun removeTasksListener() {
        tasksListener?.let { listener ->
            repository.removeTasksListener(listener)
        }
        tasksListener = null
    }

    fun approveTask(childId: String, taskId: String, isApproved: Boolean) {
        viewModelScope.launch {
            val success = repository.approveTask(childId, taskId, isApproved)
            if (success) {
                Log.d("TaskViewModel", "Task approved successfully")
            } else {
                Log.e("TaskViewModel", "Failed to approve task")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        removeTasksListener()
    }
}
