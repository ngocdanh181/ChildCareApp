package com.example.childlocate.ui.parent.locations

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.childlocate.R
import com.example.childlocate.data.model.Location
import com.example.childlocate.databinding.FragmentLocationsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LocationsFragment : Fragment() {

    private var _binding: FragmentLocationsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: LocationsViewModel by viewModels()
    private lateinit var adapter: LocationAdapter

    private var parentId: String = ""
    private var childId: String = ""

    private val navigationArgs: LocationsFragmentArgs by navArgs()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationsBinding.inflate(inflater, container, false)
        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        parentId = navigationArgs.parentId
        childId = navigationArgs.childId
        val bottomNavigationView = requireActivity().findViewById<View>(R.id.bottom_navigation_view)
        bottomNavigationView.visibility = View.GONE
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }
    
    private fun setupRecyclerView() {
        adapter = LocationAdapter(
            onEditClick = { location ->
                navigateToEditLocation(location)
            },
            onDeleteClick = { location ->
                showDeleteConfirmationDialog(location)
            },
            onNotificationToggle = { location, enabled ->
                viewModel.toggleNotifications(location.id, enabled)
            },
            onItemClick = { location ->
                // Xử lý khi nhấn vào item (mở rộng để hiển thị bản đồ)
            }
        )
        
        binding.locationsRecyclerView.adapter = adapter
    }
    
    private fun setupListeners() {
        binding.fabAddLocation.setOnClickListener {
            navigateToAddLocation()
        }

        binding.toolbar.setNavigationOnClickListener {
            val bottomNavigationView = requireActivity().findViewById<View>(R.id.bottom_navigation_view)
            bottomNavigationView.visibility = View.VISIBLE
            findNavController().navigateUp()
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.locations.collectLatest { locations ->
                        adapter.submitList(locations)
                        updateEmptyView(locations)
                    }
                }
                
                launch {
                    viewModel.errorMessage.collectLatest { errorMessage ->
                        errorMessage?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                            viewModel.clearError()
                        }
                    }
                }
            }
        }
    }
    
    private fun updateEmptyView(locations: List<Location>) {
        if (locations.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.locationsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.locationsRecyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun showDeleteConfirmationDialog(location: Location) {
        AlertDialog.Builder(requireContext())
            .setTitle("Xóa địa điểm")
            .setMessage("Bạn có chắc chắn muốn xóa địa điểm '${location.name}'?")
            .setPositiveButton("Xóa") { _, _ ->
                viewModel.deleteLocation(location.id)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    
    private fun navigateToAddLocation() {
        // Điều hướng đến màn hình thêm địa điểm
        val action = LocationsFragmentDirections.actionLocationsFragmentToAddEditLocationFragment2("",parentId,childId)
        findNavController().navigate(action)
        val bottomNavigationView = requireActivity().findViewById<View>(R.id.bottom_navigation_view)
        bottomNavigationView.visibility = View.GONE
        Toast.makeText(requireContext(), "Chức năng thêm địa điểm đang được phát triển", Toast.LENGTH_SHORT).show()
    }
    
    private fun navigateToEditLocation(location: Location) {
        // Điều hướng đến màn hình chỉnh sửa địa điểm
         val action = LocationsFragmentDirections.actionLocationsFragmentToAddEditLocationFragment2(location.id,parentId,childId)
         findNavController().navigate(action)
        val bottomNavigationView = requireActivity().findViewById<View>(R.id.bottom_navigation_view)
        bottomNavigationView.visibility = View.GONE
        Toast.makeText(requireContext(), "Chức năng chỉnh sửa địa điểm đang được phát triển", Toast.LENGTH_SHORT).show()
    }
    
    override fun onResume() {
        super.onResume()
        adapter.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        adapter.onPause()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        adapter.onDestroy()
        _binding = null

    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        adapter.onLowMemory()
    }
} 