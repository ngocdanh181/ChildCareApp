package com.example.childlocate.ui.parent.webfilter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.childlocate.data.model.BlockedKeyword
import com.example.childlocate.databinding.ItemKeywordBinding

class KeywordAdapter(
    private val onDeleteClick: (BlockedKeyword) -> Unit
) : ListAdapter<BlockedKeyword, KeywordAdapter.KeywordViewHolder>(KeywordDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeywordViewHolder {
        val binding = ItemKeywordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return KeywordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: KeywordViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class KeywordViewHolder(
        private val binding: ItemKeywordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(keyword: BlockedKeyword) {
            binding.apply {
                keywordText.text = keyword.pattern
                categoryText.text = keyword.category.getDisplayName()
                regexIndicator.isVisible = keyword.isRegex
                countTime.text = keyword.attemptCount.toString()
                deleteButton.setOnClickListener { onDeleteClick(keyword) }
            }
        }
    }

    class KeywordDiffCallback : DiffUtil.ItemCallback<BlockedKeyword>() {
        // Chỉ so sánh ID
        override fun areItemsTheSame(oldItem: BlockedKeyword, newItem: BlockedKeyword) =
            oldItem.id == newItem.id

        // So sánh nội dung
        override fun areContentsTheSame(oldItem: BlockedKeyword, newItem: BlockedKeyword) =
            oldItem.pattern == newItem.pattern &&
                    oldItem.category == newItem.category &&
                    oldItem.isRegex == newItem.isRegex
    }
}