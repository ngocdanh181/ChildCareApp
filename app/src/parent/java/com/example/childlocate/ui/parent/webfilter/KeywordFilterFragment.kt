package com.example.childlocate.ui.parent.webfilter

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.childlocate.data.model.BlockedKeyword
import com.example.childlocate.data.model.KeywordCategory
import com.example.childlocate.data.model.WebFilterState
import com.example.childlocate.databinding.FragmentKeywordFilterBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class KeywordFilterFragment:Fragment() {

    companion object {
        private const val ARG_CHILD_ID = "child_id"

        fun newInstance(childId: String): Fragment {
            return KeywordFilterFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHILD_ID, childId)
                }
            }
        }
    }
    private var _binding: FragmentKeywordFilterBinding? = null
    private val binding get() = _binding!!
    private lateinit var childId:String

    private val viewModel: WebFilterViewModel by lazy {
        ViewModelProvider(this)[WebFilterViewModel::class.java]
    }
    private val keywordAdapter = KeywordAdapter { keyword ->
        deleteKeyword(keyword)
    }

    private var selectedCategory= KeywordCategory.CUSTOM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kiểm tra arguments để lấy childId
        childId = arguments?.getString(ARG_CHILD_ID)
            ?: throw IllegalStateException("Child ID is missing in KeywordFilterFragment")

        Log.d("KeywordFilterFragment", "Child ID: $childId")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeywordFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupAddButton()
        setupCategorySpinner()
        observeViewModel()
        binding.resetAllButton.setOnClickListener {
            showResetConfirmDialog()
        }

        viewModel.loadKeywords(childId)

    }
    private fun setupRecyclerView() {
        binding.keywordsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = keywordAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupAddButton() {
        binding.addKeywordButton.setOnClickListener {
            val pattern = binding.keywordInput.text?.toString()?.trim()
            if (pattern.isNullOrEmpty()) {
                binding.keywordInputLayout.error = "Vui lòng nhập từ khóa"
                return@setOnClickListener
            }
            if (binding.regexSwitch.isChecked) {
                try {
                    Regex(pattern)
                } catch (e: Exception) {
                    binding.keywordInputLayout.error = "Regex pattern không hợp lệ"
                    return@setOnClickListener
                }
            }
            Log.d("KeywordFilter",childId)
            viewModel.addKeyword(
                childId = childId,
                pattern = pattern,
                category = selectedCategory,
                isRegex = binding.regexSwitch.isChecked
            )

            // Clear input
            binding.keywordInput.text?.clear()
            binding.regexSwitch.isChecked = false
            binding.categorySpinner.setText(KeywordCategory.CUSTOM.getDisplayName(), false)
            selectedCategory = KeywordCategory.CUSTOM
        }
    }
    private fun setupCategorySpinner() {
        val categories = KeywordCategory.entries.map { it.getDisplayName() }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            categories
        )

        binding.categorySpinner.apply {
            setAdapter(adapter)
            setText(KeywordCategory.CUSTOM.getDisplayName(), false)

            setOnItemClickListener { _, _, position, _ ->
                selectedCategory = KeywordCategory.entries.toTypedArray()[position]
            }
        }
    }

    private fun deleteKeyword(keyword: BlockedKeyword) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xóa từ khóa")
            .setMessage("Bạn có chắc muốn xóa từ khóa này?")
            .setPositiveButton("Xóa") { _, _ ->
                viewModel.deleteKeyword(childId, keyword.id)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: WebFilterState) {
        binding.progressBar.isVisible = state is WebFilterState.Loading
        binding.emptyView.isVisible = state is WebFilterState.Empty
        binding.keywordsRecyclerView.isVisible = state is WebFilterState.Success
        Log.d("Keyword - state", state.toString())
        when (state) {
            is WebFilterState.Success -> {
                keywordAdapter.submitList(state.keywords)

            }
            is WebFilterState.Error -> {
                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
            }
            else -> {}
        }

    }

    private fun showResetConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reset số lần truy cập")
            .setMessage("Bạn có chắc muốn đặt lại số lần truy cập về 0?")
            .setPositiveButton("Reset") { _, _ ->
                viewModel.resetAllCounters(childId)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

}