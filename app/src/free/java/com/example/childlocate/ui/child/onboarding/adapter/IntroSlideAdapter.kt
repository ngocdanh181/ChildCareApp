package com.example.childlocate.ui.child.onboarding.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.childlocate.databinding.ItemIntroSlideBinding
import com.example.childlocate.ui.child.onboarding.data.IntroSlide

class IntroSlideAdapter(private val slides: List<IntroSlide>) :
    RecyclerView.Adapter<IntroSlideAdapter.SlideViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val binding = ItemIntroSlideBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SlideViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
        val slide = slides[position]
        holder.bind(slide)
    }

    override fun getItemCount(): Int = slides.size

    inner class SlideViewHolder(private val binding: ItemIntroSlideBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(slide: IntroSlide) {
            binding.apply{
                ivSlideImage.setImageResource(slide.image)
                tvSlideTitle.text = slide.title
                tvSlideDescription.text = slide.description
            }

        }
    }
}