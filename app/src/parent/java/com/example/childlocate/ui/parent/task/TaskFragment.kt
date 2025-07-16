package com.example.childlocate.ui.parent.task

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.childlocate.R
import com.example.childlocate.databinding.FragmentTaskBinding
import com.example.childlocate.repository.TaskRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TaskFragment: Fragment() {

    private lateinit var binding: FragmentTaskBinding
    private lateinit var tasksAdapter: ParentTaskAdapter

    private val viewModel: TaskViewModel by lazy {
        ViewModelProvider(this)[TaskViewModel::class.java]
    }

    private val navigationArgs: TaskFragmentArgs by navArgs()

    private lateinit var childId: String

    private var selectedDate: LocalDate? = null
    private var selectedTime: LocalTime? = null



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTaskBinding.inflate(inflater, container, false)
        return binding.root
    }
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideBottomNavigation()
        setupToolbar()

        childId = navigationArgs.childId

        setupDatePicker()
        setupTimePicker()
        setupTasksList()
        setupAssignButton()

        viewModel.loadTasksForChild(childId)
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupAssignButton() {

        binding.assignTaskButton.setOnClickListener {
            val taskName = binding.taskNameInput.text.toString()
            if (taskName.isBlank()) {
                binding.taskNameInput.error = "Vui lòng nhập tên nhiệm vụ"
                return@setOnClickListener
            }

            if (selectedDate == null || selectedTime == null) {
                Toast.makeText(context, "Vui lòng chọn ngày và giờ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            val taskDateTime = LocalDateTime.of(selectedDate, selectedTime)
            val formattedDateTime = taskDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

            viewModel.assignTaskWithValidation(childId, taskName, formattedDateTime)

        }
        // Observe the task validation result
        viewModel.taskValidationResult.observe(viewLifecycleOwner) {result->
            when (result) {
                is TaskRepository.TaskValidationResult.Valid -> {
                    Toast.makeText(context, "Nhiệm vụ đã được giao thành công", Toast.LENGTH_SHORT).show()
                    // Xóa dữ liệu đầu vào sau khi thêm thành công
                    binding.taskNameInput.text?.clear()
                    selectedDate = null
                    selectedTime = null
                    updateSelectedDateTime()
                }
                is TaskRepository.TaskValidationResult.InvalidTime -> {
                    Toast.makeText(context, result.reason, Toast.LENGTH_SHORT).show()
                }
                is TaskRepository.TaskValidationResult.TimeConflict -> {
                    val conflictingTask = result.conflictingTask
                    Toast.makeText(context, "Nhiệm vụ đã tồn tại vào thời gian ${conflictingTask.time}", Toast.LENGTH_SHORT).show()
                }
                else->{
                    Toast.makeText(context, "Lỗi: ${TaskRepository.TaskValidationResult.Valid}", Toast.LENGTH_SHORT).show()
                }
            }
        }

    }

    private fun setupTasksList() {
        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            tasksAdapter.submitList(tasks)
        }


        tasksAdapter = ParentTaskAdapter { task, isApproved ->
            viewModel.approveTask(childId, task.id, isApproved)
        }
        binding.tasksRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.tasksRecyclerView.adapter = tasksAdapter
        binding.tasksRecyclerView.setPadding(0, 0, 0, 150)

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupDatePicker() {
        binding.datePickerButton.setOnClickListener {
            val datePickerDialog = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select task date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            datePickerDialog.addOnPositiveButtonClickListener { selection ->
                selectedDate = Instant.ofEpochMilli(selection)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                updateSelectedDateTime()
            }

            datePickerDialog.show(parentFragmentManager, "DATE_PICKER")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupTimePicker() {
        binding.timePickerButton.setOnClickListener {
            val timePickerDialog = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(12)
                .setMinute(0)
                .setTitleText("Select task time")
                .build()

            timePickerDialog.addOnPositiveButtonClickListener {
                selectedTime = LocalTime.of(timePickerDialog.hour, timePickerDialog.minute)
                updateSelectedDateTime()
            }

            timePickerDialog.show(parentFragmentManager, "TIME_PICKER")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateSelectedDateTime() {
        val dateTimeText = when {
            selectedDate != null && selectedTime != null -> {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                LocalDateTime.of(selectedDate, selectedTime).format(formatter)
            }
            selectedDate != null -> {
                "Date: ${selectedDate?.format(DateTimeFormatter.ISO_DATE)}"
            }
            selectedTime != null -> {
                "Time: ${selectedTime?.format(DateTimeFormatter.ISO_TIME)}"
            }
            else -> "No date/time selected"
        }
        binding.selectedDateTime.text = "Selected: $dateTimeText"
    }
    private fun hideBottomNavigation() {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation_view)?.visibility = View.GONE
    }

    private fun showBottomNavigation() {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation_view)?.visibility = View.VISIBLE
    }
    override fun onDestroyView(){
        super.onDestroyView()
        showBottomNavigation() // Show bottom navigation when fragment is destroyed
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }
}