package com.example.childlocate.ui.parent.history

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.childlocate.R
import com.example.childlocate.data.model.LocationHistory
import com.example.childlocate.databinding.FragmentHistoryBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment(), OnMapReadyCallback {

    private lateinit var binding: FragmentHistoryBinding
    private val viewModel: HistoryViewModel by lazy {
        ViewModelProvider(this)[HistoryViewModel::class.java]
    }
    private lateinit var locationAdapter: HistoryAdapter
    private lateinit var googleMap: GoogleMap
    private var isMapVisible = false

    private val args : HistoryFragmentArgs by navArgs()
    private lateinit var childId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childId = args.childId!!

        // Hide bottom navigation
        hideBottomNavigation()

        if (!isInternetAvailable()) {
            showError("Không có kết nối Internet")
            return
        }

        setupUI()
        setupRecyclerView()
        setupMap()
        setupObservers()
        setupClickListeners()
        
        // Load data for the specific child
        viewModel.loadLocationHistory(childId)
    }



    private fun hideBottomNavigation() {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation_view)?.visibility = View.GONE
    }

    private fun showBottomNavigation() {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation_view)?.visibility = View.VISIBLE
    }

    private fun setupUI() {
        // Setup toolbar with back button
        binding.toolbar.apply {
            setNavigationIcon(R.drawable.baseline_arrow_back_24)
            setNavigationOnClickListener {
                findNavController().navigateUp()
            }
            title = "Lịch sử vị trí"
        }
        
        // Initially show list view
        binding.historyRecyclerView.visibility = View.VISIBLE
        binding.historyMapContainer.visibility = View.GONE
    }

    private fun setupRecyclerView() {
        locationAdapter = HistoryAdapter { locationHistory ->
            // Handle item click - show on map or navigate to detail
            showLocationOnMap(locationHistory)
        }
        
        binding.historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                reverseLayout = true
                stackFromEnd = true
            }
            adapter = locationAdapter
            clipToPadding = false
            setPadding(0, 0, 0, 150)
        }
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.historyMapContainer) as SupportMapFragment?
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction()
                    .replace(R.id.historyMapContainer, it)
                    .commit()
            }
        mapFragment.getMapAsync(this)
    }

    private fun setupObservers() {
        // Observe filtered history
        viewModel.filteredHistory.observe(viewLifecycleOwner) { locationHistory ->
            updateLocationList(locationHistory)
            updateEmptyState(locationHistory.isEmpty())
        }

        // Observe location summary for display
        viewModel.locationSummary.observe(viewLifecycleOwner) { summary ->
            updateSummaryDisplay(summary)
        }

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observe errors
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                showError(it)
                viewModel.clearError()
            }
        }

        // Observe selected date
        viewModel.selectedDate.observe(viewLifecycleOwner) { date ->
            binding.selectedDateText.text = "Ngày: $date"
        }
    }

    private fun updateLocationList(locationHistory: List<LocationHistory>) {
        locationAdapter.submitList(locationHistory)
    }

    private fun updateSummaryDisplay(summary: com.example.childlocate.data.model.LocationSummary?) {
        summary?.let {
            // Update summary card
            binding.knownLocationCount.text = it.knownLocationCount.toString()
            binding.unknownLocationCount.text = it.unknownLocationCount.toString()
            binding.complianceRate.text = "${it.knownLocationRate.toInt()}%"
            
            // Update toolbar subtitle
            val summaryText = "Tổng: ${it.totalPoints} điểm"
            binding.toolbar.subtitle = summaryText
        } ?: run {
            // Reset to default values
            binding.knownLocationCount.text = "0"
            binding.unknownLocationCount.text = "0"
            binding.complianceRate.text = "0%"
            binding.toolbar.subtitle = null
        }
    }

    private fun setupClickListeners() {
        // FAB for switching between list and map view
        binding.fabShowHistory.setOnClickListener {
            toggleView()
        }

        // Date picker button
        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        // Quick filter buttons
        binding.btnToday.setOnClickListener {
            viewModel.loadTodayHistory()
        }

        binding.btnYesterday.setOnClickListener {
            viewModel.loadYesterdayHistory()
        }

        binding.btnThisWeek.setOnClickListener {
            viewModel.loadThisWeekHistory()
        }

        // Refresh button
        binding.btnRefresh.setOnClickListener {
            viewModel.refreshData()
        }
    }

    private fun toggleView() {
        isMapVisible = !isMapVisible
        if (isMapVisible) {
            binding.historyRecyclerView.visibility = View.GONE
            binding.historyMapContainer.visibility = View.VISIBLE
            binding.fabShowHistory.setImageResource(R.drawable.baseline_list_24)
            
            // Update map with current filtered data
            viewModel.filteredHistory.value?.let { locationHistory ->
                updateMapMarkers(locationHistory)
            }
        } else {
            binding.historyRecyclerView.visibility = View.VISIBLE
            binding.historyMapContainer.visibility = View.GONE
            binding.fabShowHistory.setImageResource(R.drawable.baseline_map_24)
        }
    }

    private fun showDatePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Chọn ngày")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val selectedDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(selection))
            viewModel.filterByDate(selectedDate)
        }

        datePicker.show(parentFragmentManager, "DATE_PICKER")
    }

    private fun showLocationOnMap(locationHistory: LocationHistory) {
        if (!isMapVisible) {
            toggleView()
        }
        
        if (::googleMap.isInitialized) {
            val location = LatLng(locationHistory.latitude, locationHistory.longitude)
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 16f))
            
            // Add a special marker for the selected location
            googleMap.addMarker(
                MarkerOptions()
                    .position(location)
                    .title("Vị trí được chọn")
                    .snippet("${locationHistory.getFormattedDateTime()}\n${locationHistory.address}")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.historyRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) {
        binding.textError?.apply {
            visibility = View.VISIBLE
            text = message
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onMapReady(mMap: GoogleMap) {
        googleMap = mMap
        googleMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = false
        }
    }

    private fun updateMapMarkers(locationHistory: List<LocationHistory>) {
        if (::googleMap.isInitialized) {
            googleMap.clear()
            
            if (locationHistory.isEmpty()) return
            
            // Add markers for each location
            locationHistory.forEachIndexed { index, location ->
                val markerColor = if (location.isInSafeZone) {
                    BitmapDescriptorFactory.HUE_GREEN
                } else {
                    BitmapDescriptorFactory.HUE_ORANGE
                }
                
                val title = if (location.isInSafeZone && !location.locationName.isNullOrEmpty()) {
                    " ${location.locationName}"
                } else {
                    " Địa điểm lạ"
                }
                
                googleMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(location.latitude, location.longitude))
                        .title(title)
                        .snippet("${location.getFormattedDateTime()}\n${location.getShortAddress()}")
                        .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                )
            }
            
            // Focus on the most recent location
            locationHistory.firstOrNull()?.let { mostRecent ->
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(mostRecent.latitude, mostRecent.longitude), 
                        14f
                    )
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Show bottom navigation when leaving this fragment
        showBottomNavigation()
    }
}



/*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.childlocate.databinding.FragmentHistoryBinding

class HistoryFragment : Fragment() {

    private lateinit var binding: FragmentHistoryBinding
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var locationAdapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        locationAdapter = HistoryAdapter()
        val layoutManager = LinearLayoutManager(context)
        layoutManager.reverseLayout = true
        layoutManager.stackFromEnd = true
        binding.historyRecyclerView.layoutManager = layoutManager
        binding.historyRecyclerView.adapter = locationAdapter

        binding.historyRecyclerView.clipToPadding = false
        binding.historyRecyclerView.setPadding(0, 0, 0, 150)

        val userId = "1"  // Replace with actual user ID
        viewModel.loadLocationHistory(userId)

        viewModel.locationHistory.observe(viewLifecycleOwner, Observer { locationHistory ->
            locationAdapter.submitList(locationHistory)
        })
    }
}
*/