package com.example.childlocate.ui.child.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.childlocate.R
import com.example.childlocate.databinding.FragmentIntroduction1Binding

class IntroductionFragment1 : Fragment() {
    private var _binding: FragmentIntroduction1Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIntroduction1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupContent()
    }

    private fun setupContent() {
        binding.apply {
            // Set content for welcome and connection theme
            heroImage.setImageResource(R.drawable.ic_friend) // Replace with appropriate welcome image
            titleText.text = "Chào mừng đến với ChildLocate"
            subtitleText.text = "Kết nối an toàn giữa con và gia đình"
            descriptionText.text = "Ứng dụng giúp phụ huynh theo dõi và bảo vệ con cái mọi lúc mọi nơi. Luôn được kết nối để đảm bảo an toàn cho bạn."
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 