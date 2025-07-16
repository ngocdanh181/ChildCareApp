package com.example.childlocate.utils


/**
 * Bộ lọc nhiễu Low Pass cho dữ liệu cảm biến
 * @param alpha Hệ số lọc (0-1): 0 = không lọc, 1 = không thay đổi
 */
class LowPassFilter(private var alpha: Float) {
    private var output: FloatArray? = null
    private var initialized = false

    /**
     * Áp dụng bộ lọc nhiễu cho dữ liệu đầu vào
     * @param input Mảng dữ liệu đầu vào
     * @return Mảng dữ liệu đã lọc
     */
    fun filter(input: FloatArray): FloatArray {
        if (!initialized) {
            output = input.clone()
            initialized = true
            return output!!
        }

        for (i in input.indices) {
            output!![i] = alpha * output!![i] + (1 - alpha) * input[i]
        }
        return output!!
    }

    /**
     * Đặt lại trạng thái bộ lọc
     */
    fun reset() {
        initialized = false
        output = null
    }

    /**
     * Điều chỉnh hệ số lọc
     * @param alpha Hệ số lọc mới (0-1)
     */
    fun setAlpha(alpha: Float) {
        this.alpha = alpha
    }

    /**
     * Bộ lọc khác - Kalman đơn giản
     * Có thể sử dụng thay thế cho Low Pass Filter để có kết quả tốt hơn
     */
    companion object {
        /**
         * Tạo bộ lọc Kalman đơn giản cho 1 chiều
         * @param measurement Giá trị đo được
         * @param processNoise Nhiễu quy trình (Q) - mặc định 0.01f
         * @param measurementNoise Nhiễu đo lường (R) - mặc định 0.1f
         * @param estimatedError Ước tính lỗi ban đầu (P) - mặc định 1f
         */
        class SimpleKalmanFilter(
            private var processNoise: Float = 0.01f,
            private var measurementNoise: Float = 0.1f,
            private var estimatedError: Float = 1f
        ) {
            private var lastEstimate = 0f
            private var initialized = false

            fun filter(measurement: Float): Float {
                // Trường hợp đầu tiên
                if (!initialized) {
                    lastEstimate = measurement
                    initialized = true
                    return measurement
                }

                // Dự đoán
                val prediction = lastEstimate
                estimatedError += processNoise

                // Cập nhật
                val kalmanGain = estimatedError / (estimatedError + measurementNoise)
                val currentEstimate = prediction + kalmanGain * (measurement - prediction)
                estimatedError = (1 - kalmanGain) * estimatedError

                lastEstimate = currentEstimate
                return currentEstimate
            }

            fun reset() {
                initialized = false
                lastEstimate = 0f
                estimatedError = 1f
            }
        }
    }
}