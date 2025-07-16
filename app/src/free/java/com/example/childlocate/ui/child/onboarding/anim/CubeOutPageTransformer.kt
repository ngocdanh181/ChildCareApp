package com.example.childlocate.ui.child.onboarding.anim

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs


/**
 * Lớp tạo hiệu ứng lật trang như sách
 */
class CubeOutPageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(view: View, position: Float) {
        view.apply {
            val pageWidth = width

            pivotX = if(position < 0) pageWidth.toFloat() else 0f
            pivotY = height * 0.5f

            when {
                position < -1 -> { // Trang nằm ngoài màn hình bên trái
                    alpha = 0f
                }
                position <= 0 -> { // Trang đang di chuyển ra bên trái
                    alpha = 1f
                    translationX = 0f
                    rotationY = 90 * abs(position)
                }
                position <= 1 -> { // Trang đang di chuyển vào từ bên phải
                    alpha = 1f
                    translationX = pageWidth * -position
                    rotationY = 90 * position
                }
                else -> { // Trang nằm ngoài màn hình bên phải
                    alpha = 0f
                }
            }
        }
    }
}

/**
 * Hàm mở rộng để áp dụng animation cho ViewPager2
 */
fun ViewPager2.setupPageAnimation(animationType: PageAnimationType = PageAnimationType.DEPTH) {
    when (animationType) {
        PageAnimationType.DEPTH -> setPageTransformer(DepthPageTransformer())
        PageAnimationType.ZOOM_OUT -> setPageTransformer(ZoomOutPageTransformer())
        PageAnimationType.CUBE -> setPageTransformer(CubeOutPageTransformer())
    }

    // Tăng khoảng cách giữa các trang để hiệu ứng rõ ràng hơn nếu cần
    this.offscreenPageLimit = 1
}

/**
 * Enum định nghĩa các loại animation có sẵn
 */
enum class PageAnimationType {
    DEPTH,
    ZOOM_OUT,
    CUBE
}
