package com.example.childlocate.ui.child.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.childlocate.R
import com.example.childlocate.databinding.FragmentIntroduction2Binding

class IntroductionFragment2 : Fragment() {
    private var _binding: FragmentIntroduction2Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIntroduction2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupContent()
    }

    private fun setupContent() {
        binding.apply {
            // Set content for features and benefits theme
            heroImage.setImageResource(R.drawable.ic_friend) // Replace with appropriate features image
            titleText.text = "Giao tiếp và Bảo vệ"
            subtitleText.text = "Liên lạc dễ dàng, an toàn mọi lúc"
            descriptionText.text = "Giao tiếp với phụ huynh bất cứ khi nào bạn cần. Cảnh báo phụ huynh khi bạn cần giúp đỡ. An toàn là trên hết."
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 