package com.example.childlocate.ui.parent.timelimit

import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.example.childlocate.R
import com.example.childlocate.data.model.AppLimit
import com.example.childlocate.data.model.AppLimitDialogState
import com.example.childlocate.data.model.AppUsageInfo
import com.example.childlocate.databinding.DialogTimeLimitBinding
import java.util.Calendar

class TimeLimitDialog : DialogFragment() {
    private var _binding: DialogTimeLimitBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TimeLimitViewModel by lazy {
        ViewModelProvider(this)[TimeLimitViewModel::class.java]
    }

    private var appInfo: AppUsageInfo? = null
    private var childId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTimeLimitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get arguments safely
        appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_APP_INFO, AppUsageInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable(ARG_APP_INFO)
        }
        
        childId = arguments?.getString(ARG_CHILD_ID)

        setupUI()
        setupListeners()
        observeViewModel()

        Log.d("TimeLimitDialog", "childId: $childId")
        Log.d("TimeLimitDialog", "appInfo: $appInfo")
        
        // Load current limits
        appInfo?.let { info ->
            childId?.let { id ->
                viewModel.loadAppLimits(id, info.packageName)
            }
        }
    }

    private fun setupUI() {
        appInfo?.let { info ->
            binding.tvAppName.text = info.appName
            try {
                val icon = requireContext().packageManager.getApplicationIcon(info.packageName)
                binding.ivAppIcon.setImageDrawable(icon)
            } catch (e: Exception) {
                binding.ivAppIcon.setImageResource(R.mipmap.ic_launcher)
            }
        }

        // Setup time pickers
        binding.tvStartTime.setOnClickListener {
            showTimePicker(true)
        }

        binding.tvEndTime.setOnClickListener {
            showTimePicker(false)
        }
        
        // Setup switch listener
        binding.switchLimit.setOnCheckedChangeListener { _, isChecked ->
            binding.cardDailyLimit.isEnabled = isChecked
            binding.cardTimeRange.isEnabled = isChecked
            binding.etHours.isEnabled = isChecked
            binding.etMinutes.isEnabled = isChecked
            binding.tvStartTime.isEnabled = isChecked
            binding.tvEndTime.isEnabled = isChecked

            // Update UI appearance based on state
            updateUIState(isChecked)
        }
    }

    private fun updateUIState(enabled: Boolean) {
        val alpha = if (enabled) 1.0f else 0.5f
        binding.cardDailyLimit.alpha = alpha
        binding.cardTimeRange.alpha = alpha
    }

    private fun setupListeners() {
        binding.btnApply.setOnClickListener {
            val isEnabled = binding.switchLimit.isChecked
            val hours = binding.etHours.text.toString().toIntOrNull() ?: 0
            val minutes = binding.etMinutes.text.toString().toIntOrNull() ?: 0
            val totalMinutes = hours * 60 + minutes

            appInfo?.let { info ->
                childId?.let { id ->
                    val appLimit = AppLimit(
                        packageName = info.packageName,
                        dailyLimitMinutes = totalMinutes,
                        startTime = binding.tvStartTime.text.toString(),
                        endTime = binding.tvEndTime.text.toString(),
                        isEnabled = isEnabled
                    )
                    Log.d("appLimit", appLimit.toString())
                    viewModel.setAppLimit(id, appLimit)
                }
            }
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun showTimePicker(isStartTime: Boolean) {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                val timeStr = String.format("%02d:%02d", hour, minute)
                if (isStartTime) {
                    binding.tvStartTime.text = timeStr
                } else {
                    binding.tvEndTime.text = timeStr
                }
            },
            currentHour,
            currentMinute,
            true
        ).show()
    }

    private fun observeViewModel() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AppLimitDialogState.Success -> {
                    state.currentLimit?.let { limit ->
                        binding.switchLimit.isChecked = limit.isEnabled
                        val hours = limit.dailyLimitMinutes / 60
                        val minutes = limit.dailyLimitMinutes % 60
                        binding.etHours.setText(hours.toString())
                        binding.etMinutes.setText(minutes.toString())
                        binding.tvStartTime.text = limit.startTime
                        binding.tvEndTime.text = limit.endTime
                    } ?: run {
                        // Nếu chưa có giới hạn, set các giá trị mặc định
                        binding.switchLimit.isChecked = false
                        binding.etHours.setText("2")
                        binding.etMinutes.setText("0")
                        binding.tvStartTime.text = "08:00"
                        binding.tvEndTime.text = "21:00"
                        updateUIState(false)
                    }
                    
                    // Only dismiss dialog if this was a save operation
                    if (viewModel.isSavingOperation()) {
                        Toast.makeText(requireContext(), "Đã áp dụng giới hạn thành công", Toast.LENGTH_SHORT).show()
                        viewModel.resetSavingFlag() // Reset flag after successful save
                        dismiss()
                    }
                }
                is AppLimitDialogState.Error -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    Log.d("AppLimit", state.message)
                }
                AppLimitDialogState.Loading -> {
                    // Show loading if needed
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_APP_INFO = "app_info"
        private const val ARG_CHILD_ID = "child_id"

        fun newInstance(appInfo: AppUsageInfo, childId: String) = TimeLimitDialog().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_APP_INFO, appInfo)
                putString(ARG_CHILD_ID, childId)
            }
        }
    }
}