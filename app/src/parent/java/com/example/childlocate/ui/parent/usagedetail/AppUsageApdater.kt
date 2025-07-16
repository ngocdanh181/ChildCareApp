package com.example.childlocate.ui.parent.usagedetail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.childlocate.R
import com.example.childlocate.data.model.AppUsageInfo
import com.example.childlocate.data.model.AppUsageWithLimit
import com.example.childlocate.databinding.ItemUsageLayoutBinding
import java.util.concurrent.TimeUnit

class AppUsageAdapter(
    private val onAppClicked: (AppUsageInfo) -> Unit
) : ListAdapter<AppUsageWithLimit, AppUsageAdapter.ViewHolder>(AppUsageWithLimitDiffCallback()) {

    class ViewHolder(private val binding: ItemUsageLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AppUsageWithLimit, onAppClicked: (AppUsageInfo) -> Unit) {
            binding.tvAppName.text = item.appInfo.appName
            binding.tvUsageTime.text = formatUsageTime(item.appInfo.usageTime)

            // Load app icon using packageName
            try {
                val packageManager = itemView.context.packageManager
                val icon = packageManager.getApplicationIcon(item.appInfo.packageName)
                binding.ivAppIcon.setImageDrawable(icon)
            } catch (e: Exception) {
                binding.ivAppIcon.setImageResource(R.mipmap.ic_launcher)
            }

            // Apply visual indication for apps with limits
            if (item.hasLimit) {
                // Darker background for apps with time limits
                binding.root.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.accent_green)
                )
                binding.ivTimeIcon.visibility= View.VISIBLE
            } else {
                // Default background
                binding.root.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, android.R.color.transparent)
                )
                binding.ivTimeIcon.visibility = View.GONE
            }

            // Handle click event with callback
            itemView.setOnClickListener {
                onAppClicked(item.appInfo)
            }
        }

        private fun formatUsageTime(timeInMillis: Long): String {
            val hours = TimeUnit.MILLISECONDS.toHours(timeInMillis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % 60
            return if (hours > 0) {
                "$hours giờ $minutes phút"
            } else {
                "$minutes phút"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemUsageLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onAppClicked)
    }
}

class AppUsageWithLimitDiffCallback : DiffUtil.ItemCallback<AppUsageWithLimit>() {
    override fun areItemsTheSame(oldItem: AppUsageWithLimit, newItem: AppUsageWithLimit): Boolean {
        return oldItem.appInfo.packageName == newItem.appInfo.packageName
    }

    override fun areContentsTheSame(oldItem: AppUsageWithLimit, newItem: AppUsageWithLimit): Boolean {
        return oldItem == newItem
    }
}
