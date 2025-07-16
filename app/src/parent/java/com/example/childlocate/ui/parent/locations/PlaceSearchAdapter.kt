package com.example.childlocate.ui.parent.locations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.childlocate.databinding.ItemPlaceSearchResultBinding

class PlaceSearchAdapter(
    private val onPlaceClick: (PlaceSearchResult) -> Unit
) : ListAdapter<PlaceSearchResult, PlaceSearchAdapter.PlaceViewHolder>(PlaceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val binding = ItemPlaceSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlaceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PlaceViewHolder(
        private val binding: ItemPlaceSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPlaceClick(getItem(position))
                }
            }
        }
        
        fun bind(place: PlaceSearchResult) {
            binding.placeName.text = place.name
            binding.placeAddress.text = place.address
        }
    }
}

class PlaceDiffCallback : DiffUtil.ItemCallback<PlaceSearchResult>() {
    override fun areItemsTheSame(oldItem: PlaceSearchResult, newItem: PlaceSearchResult): Boolean {
        return oldItem.placeId == newItem.placeId
    }

    override fun areContentsTheSame(oldItem: PlaceSearchResult, newItem: PlaceSearchResult): Boolean {
        return oldItem == newItem
    }
} 