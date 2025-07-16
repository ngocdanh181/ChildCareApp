package com.example.childlocate.ui.parent.task

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


class ParentTaskAdapter(
    private val onCheckBoxClick: ((Task, Boolean) ->Unit)?=null) : ListAdapter<Task, ParentTaskAdapter.TaskViewHolder>(ParentTaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemTaskBinding.inflate(inflater, parent, false)
        return TaskViewHolder(binding, onCheckBoxClick)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TaskViewHolder(
        private val binding: ItemTaskBinding,
        private val onCheckBoxClick: ((Task, Boolean) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            binding.apply {
                // Thiết lập text cơ bản
                tvTaskName.text = task.name
                tvDueTime.text = task.time

                // Thiết lập trạng thái checkbox
                taskCheckbox.isChecked = task.parentApproved

                // Chỉ enable checkbox khi trẻ đã đánh dấu hoàn thành
                taskCheckbox.isEnabled = task.childCompleted && !task.parentApproved

                // Cập nhật UI dựa trên trạng thái
                updateTaskStatus(task)

            }
        }

        private fun ItemTaskBinding.updateTaskStatus(task: Task) {
            when {
                task.parentApproved -> {
                    // Task đã được duyệt
                    tvTaskName.apply {
                        paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        setTextColor(Color.GRAY)
                    }
                    tvStatus.apply {
                        text = "Đã duyệt hoàn thành"
                        setTextColor(Color.GREEN)
                        visibility = View.VISIBLE
                    }
                    taskCheckbox.apply {
                        isEnabled = false
                        alpha = 0.7f
                    }
                    root.alpha = 0.8f
                }
                task.childCompleted -> {
                    // Task đang chờ duyệt
                    tvTaskName.apply {
                        paintFlags = paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                        setTextColor(Color.BLACK)
                    }
                    tvStatus.apply {
                        text = "Đang chờ duyệt"
                        setTextColor(Color.BLUE)
                        visibility = View.VISIBLE
                    }
                    taskCheckbox.apply {
                        isEnabled = true
                        alpha = 1.0f
                    }
                    root.alpha = 1.0f
                }
                else -> {
                    // Task chưa được hoàn thành
                    tvTaskName.apply {
                        paintFlags = paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                        setTextColor(Color.BLACK)
                    }
                    tvStatus.apply {
                        text = "Chưa hoàn thành"
                        setTextColor(Color.GRAY)
                        visibility = View.VISIBLE
                    }
                    taskCheckbox.apply {
                        isEnabled = false
                        alpha = 0.5f
                    }
                    root.alpha = 1.0f
                }
            }
        }

    }
}

class ParentTaskDiffCallback : DiffUtil.ItemCallback<Task>() {
    override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean = oldItem == newItem
}
