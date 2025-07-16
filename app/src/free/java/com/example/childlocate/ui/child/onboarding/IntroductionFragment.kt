package com.example.childlocate.ui.child.onboarding

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.childlocate.R
import com.example.childlocate.databinding.FragmentIntroductionBinding
import com.example.childlocate.ui.child.onboarding.data.IntroSlide
import com.example.childlocate.ui.child.onboarding.utils.PreferenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class IntroductionFragment : Fragment() {
    private var _binding: FragmentIntroductionBinding? = null
    private val binding get() = _binding!!

    private var autoSlideJob: Job? = null
    private var currentPosition = 0
    private var isPaused = false

    private val viewModel: OnboardingViewModel by lazy {
        OnboardingViewModel(PreferenceManager(requireContext()))
    }

    private val introSlides by lazy {
        listOf(
            IntroSlide(
                R.drawable.ic_friend, // Thay thế bằng hình ảnh phù hợp
                "Chào mừng đến với ứng dụng Theo dõi trẻ",
                "Ứng dụng giúp phụ huynh theo dõi và bảo vệ con cái mọi lúc mọi nơi"
            ),
            IntroSlide(
                R.drawable.ic_friend, // Thay thế bằng hình ảnh vị trí
                "Luôn được kết nối",
                "Phụ huynh có thể biết vị trí của bạn để đảm bảo an toàn"
            ),
            IntroSlide(
                R.drawable.ic_friend, // Thay thế bằng hình ảnh tin nhắn
                "Giao tiếp dễ dàng",
                "Liên lạc với phụ huynh bất cứ khi nào bạn cần"
            ),
            IntroSlide(
                R.drawable.ic_friend, // Thay thế bằng hình ảnh an toàn
                "An toàn là trên hết",
                "Cảnh báo phụ huynh khi bạn cần giúp đỡ"
            )
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIntroductionBinding.inflate(inflater, container, false)

        // Thiết lập touch listener để dừng/tiếp tục auto slide
        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isPaused = true
                    false
                }
                MotionEvent.ACTION_UP -> {
                    isPaused = false
                    startAutoSlide()
                    false
                }
                else -> false
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupStoryProgressBar()
        showSlide(currentPosition)
        setupNavigation()
        startAutoSlide()
    }

    private fun setupStoryProgressBar() {
        binding.storyProgressContainer.removeAllViews()

        for (i in introSlides.indices) {
            val progressBar = View(requireContext())
            val params = LinearLayout.LayoutParams(
                0,
                resources.getDimensionPixelSize(R.dimen.story_progress_height),
                1f
            )
            params.marginEnd = resources.getDimensionPixelSize(R.dimen.story_progress_margin)
            progressBar.layoutParams = params
            progressBar.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_story_progress_inactive)

            binding.storyProgressContainer.addView(progressBar)
        }
    }

    private fun updateStoryProgress(position: Int) {
        for (i in 0 until binding.storyProgressContainer.childCount) {
            val progressBar = binding.storyProgressContainer.getChildAt(i)
            if (i < position) {
                // Đã hoàn thành
                progressBar.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_story_progress_complete)
            } else if (i == position) {
                // Đang hiển thị
                progressBar.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_story_progress_active)
            } else {
                // Chưa hiển thị
                progressBar.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_story_progress_inactive)
            }
        }
    }

    private fun showSlide(position: Int) {
        if (position < 0 || position >= introSlides.size) return

        currentPosition = position
        val slide = introSlides[position]

        // Animation cho nội dung đang hiển thị (fade out)
        val fadeOutImage = ObjectAnimator.ofFloat(binding.slideImage, "alpha", 1f, 0f)
        val fadeOutTitle = ObjectAnimator.ofFloat(binding.slideTitle, "alpha", 1f, 0f)
        val fadeOutDesc = ObjectAnimator.ofFloat(binding.slideDescription, "alpha", 1f, 0f)

        val fadeOutSet = AnimatorSet()
        fadeOutSet.playTogether(fadeOutImage, fadeOutTitle, fadeOutDesc)
        fadeOutSet.duration = 200

        // Thiết lập nội dung mới và animation (fade in)
        fadeOutSet.addListener(onEnd = {
            binding.slideImage.setImageResource(slide.image)
            binding.slideTitle.text = slide.title
            binding.slideDescription.text = slide.description

            val fadeInImage = ObjectAnimator.ofFloat(binding.slideImage, "alpha", 0f, 1f)
            val fadeInTitle = ObjectAnimator.ofFloat(binding.slideTitle, "alpha", 0f, 1f)
            val fadeInDesc = ObjectAnimator.ofFloat(binding.slideDescription, "alpha", 0f, 1f)

            val translateImage = ObjectAnimator.ofFloat(binding.slideImage, "translationX", 100f, 0f)
            val translateTitle = ObjectAnimator.ofFloat(binding.slideTitle, "translationX", 100f, 0f)
            val translateDesc = ObjectAnimator.ofFloat(binding.slideDescription, "translationX", 100f, 0f)

            val fadeInSet = AnimatorSet()
            fadeInSet.playTogether(fadeInImage, fadeInTitle, fadeInDesc,
                translateImage, translateTitle, translateDesc)
            fadeInSet.duration = 300
            fadeInSet.interpolator = AccelerateDecelerateInterpolator()
            fadeInSet.start()
        })

        fadeOutSet.start()
        updateStoryProgress(position)
    }

    private fun setupNavigation() {
        // Xử lý vuốt sang trái
        binding.nextArea.setOnClickListener {
            navigateToNextSlide()
        }

        // Xử lý vuốt sang phải
        binding.prevArea.setOnClickListener {
            if (currentPosition > 0) {
                showSlide(currentPosition - 1)
            }
        }
    }

    private fun navigateToNextSlide() {
        if (currentPosition < introSlides.size - 1) {
            showSlide(currentPosition + 1)
        } else {
            // Chuyển sang màn hình tiếp theo (VerificationFragment)
            (activity as? OnboardingActivity)?.let {
                it.binding.viewPager.currentItem = 1 // Chuyển sang Fragment thứ 2
            }
        }
    }

    private fun startAutoSlide() {
        autoSlideJob?.cancel()
        autoSlideJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(AUTO_SLIDE_DELAY)
                if (!isPaused && isAdded) {
                    activity?.runOnUiThread {
                        if (currentPosition < introSlides.size - 1) {
                            showSlide(currentPosition + 1)
                        } else if (isAdded) {
                            // Chuyển sang màn hình tiếp theo sau khi hiển thị hết các slides
                            (activity as? OnboardingActivity)?.let {
                                it.binding.viewPager.currentItem = 1 // Chuyển sang Fragment thứ 2
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        autoSlideJob?.cancel()
    }

    override fun onResume() {
        super.onResume()
        if (!isPaused) {
            startAutoSlide()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autoSlideJob?.cancel()
        _binding = null
    }

    companion object {
        private const val AUTO_SLIDE_DELAY = 4000L
    }
}

// Extension function cho animation listener
private fun android.animation.Animator.addListener(
    onStart: () -> Unit = {},
    onEnd: () -> Unit = {},
    onCancel: () -> Unit = {},
    onRepeat: () -> Unit = {}
): android.animation.Animator.AnimatorListener {
    val listener = object : android.animation.Animator.AnimatorListener {
        override fun onAnimationStart(animation: android.animation.Animator) = onStart()
        override fun onAnimationEnd(animation: android.animation.Animator) = onEnd()
        override fun onAnimationCancel(animation: android.animation.Animator) = onCancel()
        override fun onAnimationRepeat(animation: android.animation.Animator) = onRepeat()
    }
    addListener(listener)
    return listener
}