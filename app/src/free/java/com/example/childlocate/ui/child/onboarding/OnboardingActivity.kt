package com.example.childlocate.ui.child.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.childlocate.R
import com.example.childlocate.databinding.ActivityOnboardingBinding
import com.example.childlocate.ui.child.main.MainChildActivity
import com.example.childlocate.ui.child.onboarding.adapter.OnboardingPagerAdapter
import com.example.childlocate.ui.child.onboarding.utils.PreferenceManager

class OnboardingActivity : AppCompatActivity() {

    private var _binding: ActivityOnboardingBinding? = null
    val binding get() = _binding!!
    private lateinit var adapter: OnboardingPagerAdapter
    private lateinit var preferenceManager : PreferenceManager
    private val viewModel: OnboardingViewModel by lazy {
        OnboardingViewModel(PreferenceManager(this))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)
        //kiem tra xem da hoan thanh onboarding chua
        /*if(preferenceManager.isOnboardingCompleted()){
            navigateToMainActivity()
            return
        }*/
        setupViewPager()
        setupButtons()
        observeViewModel()

    }
    private fun setupViewPager() {
        adapter = OnboardingPagerAdapter(this)
        binding.viewPager.adapter = adapter

        // Thêm callback cho viewpager
        binding.viewPager.apply{
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    viewModel.setPage(position)

                    // Ẩn các nút của activity khi đang ở trang verification hoặc permissions
                    if (position == 2 || position == 3) {
                        binding.btnNext.visibility = View.GONE
                        binding.btnSkip.visibility = View.GONE
                    } else {
                        updateButtons(position)
                    }
                    //updateIndicators(position)
                    updateButtons(position)
                }
            })
        }

        // Tạo các indicators
        //setupIndicators(adapter.itemCount)
    }

    private fun updateIndicators(position: Int) {
        val childCount = binding.indicatorLayout.childCount
        for (i in 0 until childCount) {
            val imageView = binding.indicatorLayout.getChildAt(i) as ImageView
            if (i == position) {
                imageView.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.baseline_arrow_back_ios_24)
                )
            } else {
                imageView.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.back_arrow)
                )
            }
        }
    }

    private fun updateButtons(position: Int){
        when(position){
            0->{
                binding.btnSkip.visibility = View.VISIBLE
                binding.btnNext.text = getString(R.string.next)
            }
            adapter.itemCount - 1 -> {
                binding.btnSkip.visibility = View.GONE
                binding.btnNext.text = "Hoàn thành"
            }
            2 -> {
                // Verification fragment - hide buttons as it has its own UI
                binding.btnNext.visibility = View.GONE
                binding.btnSkip.visibility = View.GONE
            }
            3 -> {
                // Permissions fragment - hide buttons as it has its own UI
                binding.btnNext.visibility = View.GONE
                binding.btnSkip.visibility = View.GONE
            }
            else -> {
                binding.btnSkip.visibility = View.VISIBLE
                binding.btnNext.text = "Tiếp tục"
            }
        }
    }

    private fun setupButtons(){
        binding.btnNext.setOnClickListener{
            val currentItem = binding.viewPager.currentItem
            if (currentItem < adapter.itemCount - 1) {
                binding.viewPager.currentItem = currentItem + 1
            } else {
                viewModel.completeOnboarding()
                navigateToMainActivity()
            }
        }
        binding.btnSkip.setOnClickListener {
            viewModel.completeOnboarding()
            navigateToMainActivity()
        }
    }

    fun hideNavigationButtons() {
        binding.btnNext.visibility = View.GONE
        binding.btnSkip.visibility = View.GONE
    }

    private fun observeViewModel() {
        viewModel.currentPage.observe(this) { page ->
            binding.viewPager.currentItem = page
        }
    }

    fun navigateToMainActivity() {
        // Chuyển đến ChildIdActivity
        startActivity(Intent(this, MainChildActivity::class.java))
        finish()
    }
    
    fun navigateToNextFragment() {
        val currentItem = binding.viewPager.currentItem
        if (currentItem < adapter.itemCount - 1) {
            binding.viewPager.currentItem = currentItem + 1
        } else {
            viewModel.completeOnboarding()
            navigateToMainActivity()
        }
    }

}