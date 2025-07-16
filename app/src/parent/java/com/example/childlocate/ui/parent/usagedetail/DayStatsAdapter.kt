package com.example.childlocate.ui.parent.usagedetail

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.childlocate.data.model.DayUsageStats
import com.example.childlocate.databinding.ItemDayStatsBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class DayStatsAdapter(private val onDaySelected: (DayUsageStats) -> Unit)
    : ListAdapter<DayUsageStats, DayStatsAdapter.DayViewHolder>(DayDiffCallback) {

    private var selectedPosition = -1

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    override fun submitList(list: List<DayUsageStats>?) {
        super.submitList(list)
        // Reset selection when new list is submitted
        selectedPosition = -1
    }

    fun setSelectedDay(dayStats: DayUsageStats) {
        val newPosition = currentList.indexOfFirst { it.date == dayStats.date }
        if (newPosition != -1) {
            updateSelection(newPosition)
        }
    }

    private fun updateSelection(newPosition: Int) {
        val oldPosition = selectedPosition
        selectedPosition = newPosition
        
        if (oldPosition != -1) {
            notifyItemChanged(oldPosition)
        }
        notifyItemChanged(newPosition)
        
        onDaySelected(currentList[newPosition])
    }

    inner class DayViewHolder(private val binding: ItemDayStatsBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(dayStats: DayUsageStats, position: Int) {
            binding.apply {
                // Format date to display (e.g., "T2" for Monday)
                val calendar = Calendar.getInstance().apply {
                    time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dayStats.date)!!
                }
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dayStats.date)!!

                val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.SUNDAY -> "CN"
                    Calendar.MONDAY -> "T2"
                    Calendar.TUESDAY -> "T3"
                    Calendar.WEDNESDAY -> "T4"
                    Calendar.THURSDAY -> "T5"
                    Calendar.FRIDAY -> "T6"
                    Calendar.SATURDAY -> "T7"
                    else -> ""
                }

                // Calculate usage time in hours
                val hours = TimeUnit.MILLISECONDS.toHours(dayStats.totalTime)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(dayStats.totalTime) % 60
                
                // Display day of week
                dayText.text = dayOfWeek
                // Display time
                timeText.text = String.format(Locale.getDefault(), "%d:%02d", hours, minutes)
                // Display date
                dateText.text = SimpleDateFormat("dd/MM", Locale("vi")).format(date)

                // Update selected state
                root.isSelected = position == selectedPosition
                root.setBackgroundColor(if (position == selectedPosition) Color.LTGRAY else Color.WHITE)
                
                root.setOnClickListener {
                    updateSelection(position)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        return DayViewHolder(
            ItemDayStatsBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }
}

object DayDiffCallback : DiffUtil.ItemCallback<DayUsageStats>() {
    override fun areItemsTheSame(oldItem: DayUsageStats, newItem: DayUsageStats): Boolean {
        return oldItem.date == newItem.date
    }
    
    override fun areContentsTheSame(oldItem: DayUsageStats, newItem: DayUsageStats): Boolean {
        return oldItem == newItem
    }
}