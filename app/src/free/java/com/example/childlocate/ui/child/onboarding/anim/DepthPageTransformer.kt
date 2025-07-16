package com.example.childlocate.ui.child.onboarding.anim

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * Lớp tạo hiệu ứng cho ViewPager2 trong IntroductionFragment
 * Hiệu ứng này tạo chuyển đổi với độ sâu và độ mờ khi lướt giữa các trang
 */
class DepthPageTransformer : ViewPager2.PageTransformer {
    private val MIN_SCALE = 0.75f
    private val MIN_ALPHA = 0.5f

    override fun transformPage(view: View, position: Float) {
        view.apply {
            val pageWidth = width
            when {
                // Trang nằm ngoài màn hình bên phải
                position < -1 -> {
                    alpha = 0f
                }
                // Trang đang di chuyển ra khỏi màn hình bên trái
                position <= 0 -> {
                    // Giữ nguyên trang ở vị trí gốc khi di chuyển
                    alpha = 1f
                    translationX = 0f
                    translationZ = 0f
                    scaleX = 1f
                    scaleY = 1f
                }
                // Trang đang di chuyển vào màn hình từ bên phải
                position <= 1 -> {
                    // Làm mờ dần trang khi nó di chuyển
                    alpha = maxOf(MIN_ALPHA, 1 - position)

                    // Chống lại chuyển động mặc định của trang sang trái
                    translationX = pageWidth * -position

                    // Chia tỷ lệ trang xuống
                    val scaleFactor = (MIN_SCALE + (1 - MIN_SCALE) * (1 - abs(position)))
                    scaleX = scaleFactor
                    scaleY = scaleFactor

                    // Thêm hiệu ứng độ sâu
                    translationZ = -position * 5
                }
                // Trang nằm ngoài màn hình bên trái
                else -> {
                    alpha = 0f
                }
            }
        }
    }
}