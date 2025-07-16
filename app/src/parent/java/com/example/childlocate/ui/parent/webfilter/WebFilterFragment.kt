package com.example.childlocate.ui.parent.webfilter

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.childlocate.R
import com.example.childlocate.databinding.FragmentWebFilterBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayoutMediator

class WebFilterFragment : Fragment() {
    private var _binding: FragmentWebFilterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WebFilterViewModel by lazy {
        ViewModelProvider(this)[WebFilterViewModel::class.java]
    }

    private val navigationArgs: WebFilterFragmentArgs by navArgs()

    private lateinit var childId: String


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideBottomNavigation()
        setupToolbar()

        // Get childId from navigation args
        childId = navigationArgs.childId
        Log.d("WebFilterFragment", childId)
        // Su dung ViewPager()
        setupViewPager()

    }

    private fun setupViewPager() {
        //val pagerAdapter = WebFilterPagerAdapter(this)
        val pagerAdapter = WebFilterPagerAdapter(this, childId) // Truyền childId vào constructor
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Từ khóa"
                1 -> "Trang web"
                else -> null
            }
        }.attach()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun hideBottomNavigation() {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation_view)?.visibility = View.GONE
    }

    private fun showBottomNavigation() {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation_view)?.visibility = View.VISIBLE
    }



    override fun onDestroyView() {
        super.onDestroyView()
        showBottomNavigation()
        _binding = null
    }
}