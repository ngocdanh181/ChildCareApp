package com.example.childlocate.ui.child.onboarding.anim

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

class ZoomOutPageTransformer : ViewPager2.PageTransformer {
    private val MIN_SCALE = 0.85f
    private val MIN_ALPHA = 0.5f

    override fun transformPage(view: View, position: Float) {
        view.apply {
            val pageWidth = width
            val pageHeight = height

            when {
                position < -1 -> { // Trang nằm ngoài màn hình bên trái
                    alpha = 0f
                }
                position <= 1 -> { // Trang đang trong màn hình hoặc đang di chuyển vào màn hình
                    // Tính toán hiệu ứng phóng to
                    val scaleFactor = MIN_SCALE.coerceAtLeast(1 - abs(position))
                    val vertMargin = pageHeight * (1 - scaleFactor) / 2
                    val horzMargin = pageWidth * (1 - scaleFactor) / 2

                    // Điều chỉnh vị trí trang
                    translationX = if (position < 0) {
                        horzMargin - vertMargin / 2
                    } else {
                        -horzMargin + vertMargin / 2
                    }

                    // Thu nhỏ trang
                    scaleX = scaleFactor
                    scaleY = scaleFactor

                    // Làm mờ trang tùy thuộc vào kích thước thu nhỏ
                    alpha = (MIN_ALPHA +
                            (((scaleFactor - MIN_SCALE) / (1 - MIN_SCALE)) * (1 - MIN_ALPHA)))

                    // Thêm hiệu ứng xoay nhẹ
                    rotation = position * -10
                }
                else -> { // Trang nằm ngoài màn hình bên phải
                    alpha = 0f
                }
            }
        }
    }
}
