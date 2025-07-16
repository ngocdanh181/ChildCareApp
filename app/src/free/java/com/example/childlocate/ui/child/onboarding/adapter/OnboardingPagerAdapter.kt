package com.example.childlocate.ui.child.onboarding.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.childlocate.ui.child.onboarding.IntroductionFragment1
import com.example.childlocate.ui.child.onboarding.IntroductionFragment2
import com.example.childlocate.ui.child.onboarding.PermissionsFragment
import com.example.childlocate.ui.child.onboarding.VerificationFragment

class OnboardingPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> IntroductionFragment1()
            1 -> IntroductionFragment2()
            2 -> VerificationFragment()
            3 -> PermissionsFragment()
            else -> IntroductionFragment1()
        }
    }
}