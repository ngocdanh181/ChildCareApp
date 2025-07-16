package com.example.childlocate.ui.parent.webfilter

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.childlocate.data.model.BlockedWebsite
import com.example.childlocate.data.model.KeywordCategory
import com.example.childlocate.data.model.WebsiteFilterState
import com.example.childlocate.databinding.FragmentWebsiteFilterBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.Locale

class WebsiteFilterFragment : Fragment() {

    companion object {
        private const val ARG_CHILD_ID = "child_id"

        fun newInstance(childId: String): Fragment {
            return WebsiteFilterFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHILD_ID, childId)
                }
            }
        }
    }
    private var _binding: FragmentWebsiteFilterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WebFilterViewModel by activityViewModels()
    private val websiteAdapter = BlockedWebsiteAdapter { website ->
        deleteWebsite(website)
    }

    private var selectedCategory = KeywordCategory.CUSTOM

    private lateinit var childId:String
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
        _binding = FragmentWebsiteFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupCategorySpinner()
        setupAddButton()
        observeViewModel()

        viewModel.loadBlockedWebsite (childId)
    }

    private fun setupRecyclerView() {
        binding.websitesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = websiteAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupCategorySpinner() {
        val categories = KeywordCategory.entries.map { it.getDisplayName() }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            categories
        )

        binding.websiteCategorySpinner.apply {
            setAdapter(adapter)
            setText(KeywordCategory.CUSTOM.getDisplayName(), false)

            setOnItemClickListener { _, _, position, _ ->
                selectedCategory = KeywordCategory.entries[position]
            }
        }
    }
    private fun isValidDomain(domain: String): Boolean{
        //chỉ cho phép chữ cái, chữ số, và dấu chấm
        val domainRegex = "^[a-zA-Z0-9.-]+$".toRegex()
        return domain.matches(domainRegex)
    }

    private fun setupAddButton() {
        binding.addWebsiteButton.setOnClickListener {
            val domain = binding.websiteInput.text?.toString()?.trim()
                ?.lowercase(Locale.ROOT) // chuyển về lowercase

            when {
                domain.isNullOrEmpty() -> {
                    binding.websiteInputLayout.error = "Vui lòng nhập tên miền"
                }
                !isValidDomain(domain) -> {
                    binding.websiteInputLayout.error = "Tên miền không hợp lệ"
                }
                else -> {
                    viewModel.addBlockedWebsite(
                        childId = childId,
                        domain = domain,
                        category = selectedCategory
                    )
                    // Clear input
                    binding.websiteInput.text?.clear()
                    binding.websiteCategorySpinner.setText(KeywordCategory.CUSTOM.getDisplayName(), false)
                    selectedCategory = KeywordCategory.CUSTOM
                }
            }

        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState1.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: WebsiteFilterState) {
        binding.websiteProgressBar.isVisible = state is WebsiteFilterState.Loading
        binding.emptyWebsitesView.isVisible = state is WebsiteFilterState.Empty
        binding.websitesRecyclerView.isVisible = state is WebsiteFilterState.Success

        when (state) {
            is WebsiteFilterState.Success -> {
                websiteAdapter.submitList(state.websites)
            }
            is WebsiteFilterState.Error -> {
                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    private fun deleteWebsite(website: BlockedWebsite) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xóa trang web")
            .setMessage("Bạn có chắc muốn xóa trang web này?")
            .setPositiveButton("Xóa") { _, _ ->
                viewModel.deleteBlockedWebsite(
                    childId = childId,
                    websiteBlockedId = website.id
                )
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}

