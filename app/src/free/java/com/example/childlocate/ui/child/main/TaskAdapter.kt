package com.example.childlocate.ui.child.main

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.childlocate.data.model.Task
import com.example.childlocate.databinding.ItemTaskBinding

class TaskAdapter : ListAdapter<Task, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    interface OnTaskStatusChangeListener {
        fun onTaskStatusChanged(task: Task, isCompleted: Boolean)
    }

    private var listener: OnTaskStatusChangeListener? = null

    fun setOnTaskStatusChangeListener(listener: OnTaskStatusChangeListener) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemTaskBinding.inflate(inflater, parent, false)
        return TaskViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TaskViewHolder(
        private val binding: ItemTaskBinding,
        private val listener: OnTaskStatusChangeListener?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            binding.apply {
                // Thiết lập text cơ bản
                tvTaskName.text = task.name
                tvDueTime.text = task.time

                // Thiết lập trạng thái checkbox
                taskCheckbox.isChecked = task.childCompleted

                // Disable checkbox nếu đã được phụ huynh duyệt
                taskCheckbox.isEnabled = !task.parentApproved

                // Cập nhật UI dựa trên trạng thái
                updateTaskStatus(task)

                // Thêm animation và xử lý sự kiện khi check
                setupCheckboxListener(task)
            }
        }

        private fun ItemTaskBinding.updateTaskStatus(task: Task) {
            when {
                task.parentApproved -> {
                    // Task đã được phụ huynh duyệt
                    tvTaskName.apply {
                        paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        setTextColor(Color.GRAY)
                    }
                    tvStatus.apply {
                        text = "Đã hoàn thành và được duyệt"
                        setTextColor(Color.GREEN)
                        visibility = View.VISIBLE
                    }
                    root.alpha = 0.8f
                }
                task.childCompleted -> {
                    // Task đã hoàn thành, đang chờ duyệt
                    tvTaskName.apply {
                        paintFlags = paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                        setTextColor(Color.BLACK)
                    }
                    tvStatus.apply {
                        text = "Đang chờ phụ huynh duyệt"
                        setTextColor(Color.BLUE)
                        visibility = View.VISIBLE
                    }
                    root.alpha = 1.0f
                }
                else -> {
                    // Task chưa hoàn thành
                    tvTaskName.apply {
                        paintFlags = paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                        setTextColor(Color.BLACK)
                    }
                    tvStatus.apply {
                        text = "Chưa hoàn thành"
                        setTextColor(Color.GRAY)
                        visibility = View.VISIBLE
                    }
                    root.alpha = 1.0f
                }
            }
        }

        private fun ItemTaskBinding.setupCheckboxListener(task: Task) {
            taskCheckbox.setOnCheckedChangeListener(null)
            taskCheckbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !task.parentApproved) {
                    // Animation khi check
                    AnimatorSet().apply {
                        val scaleX = ObjectAnimator.ofFloat(root, "scaleX", 1f, 1.05f, 1f)
                        val scaleY = ObjectAnimator.ofFloat(root, "scaleY", 1f, 1.05f, 1f)
                        playTogether(scaleX, scaleY)
                        duration = 300
                        interpolator = OvershootInterpolator()
                        start()
                    }
                }
                listener?.onTaskStatusChanged(task, isChecked)
            }
        }
    }
}



class TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
    override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean = oldItem == newItem
}
