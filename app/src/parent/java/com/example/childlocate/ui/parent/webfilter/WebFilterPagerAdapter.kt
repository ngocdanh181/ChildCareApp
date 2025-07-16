package com.example.childlocate.ui.parent.webfilter

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class WebFilterPagerAdapter(fragment: Fragment, private val childId:String) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        Log.d("WebFilterPagerAdapter", childId)
        return when (position) {
            0 -> KeywordFilterFragment.newInstance(childId)  // Tạo fragment với argument
            1 -> WebsiteFilterFragment.newInstance(childId)
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}