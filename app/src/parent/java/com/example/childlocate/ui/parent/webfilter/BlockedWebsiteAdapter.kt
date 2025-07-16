package com.example.childlocate.ui.parent.webfilter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.childlocate.data.model.BlockedWebsite
import com.example.childlocate.databinding.ItemWebsiteBlockedBinding

class BlockedWebsiteAdapter(
    private val onDeleteClick: (BlockedWebsite) -> Unit
) : ListAdapter<BlockedWebsite, BlockedWebsiteAdapter.WebsiteViewHolder>(WebsiteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WebsiteViewHolder {
        val binding = ItemWebsiteBlockedBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return WebsiteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WebsiteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class WebsiteViewHolder(
        private val binding: ItemWebsiteBlockedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(website: BlockedWebsite) {
            binding.apply {
                websiteText.text = website.domain
                categoryText.text = website.category.getDisplayName()
                deleteButton.setOnClickListener { onDeleteClick(website) }
            }
        }
    }

    class WebsiteDiffCallback : DiffUtil.ItemCallback<BlockedWebsite>() {
        override fun areItemsTheSame(oldItem: BlockedWebsite, newItem: BlockedWebsite) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: BlockedWebsite, newItem: BlockedWebsite) =
            oldItem.domain == newItem.domain &&
                    oldItem.category == newItem.category
    }
}