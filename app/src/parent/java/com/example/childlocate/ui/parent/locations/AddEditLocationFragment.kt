package com.example.childlocate.ui.parent.locations

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.childlocate.R
import com.example.childlocate.data.model.LocationType
import com.example.childlocate.databinding.FragmentAddEditLocationBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AddEditLocationFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentAddEditLocationBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: AddEditLocationViewModel by viewModels()


    private var googleMap: GoogleMap? = null
    private lateinit var placeSearchAdapter: PlaceSearchAdapter

    
    private var searchTextWatcher: TextWatcher? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditLocationBinding.inflate(inflater, container, false)
        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner
        
        // Update toolbar title based on mode
        binding.toolbar.title = if (viewModel.isEditMode()) "Chỉnh sửa địa điểm" else "Thêm địa điểm mới"
        
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMapView()
        setupLocationTypeChips()
        setupRadiusSeekBar()
        setupSearchView()
        setupListeners()
        observeViewModel()
    }
    
    private fun setupMapView() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapView1) as SupportMapFragment?
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction()
                    .replace(R.id.mapView1, it)
                    .commit()
            }
        mapFragment.getMapAsync(this)
    }
    
    private fun setupLocationTypeChips() {
        binding.homeChip.setOnClickListener { viewModel.locationType.value = LocationType.HOME }
        binding.schoolChip.setOnClickListener { viewModel.locationType.value = LocationType.SCHOOL }
        binding.parkChip.setOnClickListener { viewModel.locationType.value = LocationType.PARK }
        binding.relativeChip.setOnClickListener { viewModel.locationType.value = LocationType.RELATIVE }
        binding.friendChip.setOnClickListener { viewModel.locationType.value = LocationType.FRIEND }
        binding.otherChip.setOnClickListener { viewModel.locationType.value = LocationType.OTHER }
    }
    
    private fun setupRadiusSeekBar() {
        binding.radiusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val radius = if (progress < 50) 50 else progress
                viewModel.radius.value = radius
                binding.radiusLabel.text = "Bán kính thông báo: ${radius}m"
                
                // Update circle on map
                updateMapCircle()
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }


    private fun setupSearchView() {
        // Setup search adapter
        placeSearchAdapter = PlaceSearchAdapter { place ->
            viewModel.selectPlace(place)
            binding.searchResultsRecyclerView.visibility = View.GONE
            //hien thi thong bao
            Snackbar.make(
                binding.root,
                "Bạn có thể điều chỉnh vị trí chính xác trên bản đồ",
                Snackbar.LENGTH_LONG
            ).show()

            //ban do tu dong cap nhat khi chon dia diem
            updateMapWithLocation(place.latitude, place.longitude)
        }

        binding.searchResultsRecyclerView.adapter = placeSearchAdapter

        // Setup search text watcher
        searchTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.let { query ->
                    if (query.length >= 3) {
                        viewModel.searchPlace(query)
                        binding.searchResultsRecyclerView.visibility = View.VISIBLE
                    } else {
                        binding.searchResultsRecyclerView.visibility = View.GONE
                    }
                }
            }
        }

        binding.searchEditText.addTextChangedListener(searchTextWatcher)

        // Setup search icon click
        binding.searchInputLayout.setEndIconOnClickListener {
            val query = binding.searchEditText.text.toString()
            if (query.isNotEmpty()) {
                viewModel.searchPlace(query)
                binding.searchResultsRecyclerView.visibility = View.VISIBLE
            }
        }
    }
    
    private fun setupListeners() {
        binding.toolbar.apply {
            setNavigationIcon(R.drawable.baseline_arrow_back_24)
            setNavigationOnClickListener {
                findNavController().navigateUp()
            }
        }
        
        binding.saveButton.setOnClickListener {
            viewModel.saveLocation()
        }
        
        binding.notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.notificationsEnabled.value = isChecked
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                //trang thai loading
                launch {
                    viewModel.isLoading.collectLatest { isLoading ->
                        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                //trang thai loi
                launch {
                    viewModel.errorMessage.collectLatest { errorMessage ->
                        errorMessage?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                            viewModel.clearError()
                        }
                    }
                }
                //trang thai cap nhat thanh cong
                launch {
                    viewModel.saveSuccess.collectLatest { success ->
                        if (success) {
                            Toast.makeText(
                                requireContext(),
                                if (viewModel.isEditMode()) "Địa điểm đã được cập nhật" else "Địa điểm mới đã được thêm",
                                Toast.LENGTH_SHORT
                            ).show()
                            findNavController().navigateUp()
                        }
                    }
                }
                //trang thai check ket qua
                launch {
                    viewModel.searchResults.collectLatest { results ->
                        Log.d("AddEdit", "Search results: ${results}")
                        placeSearchAdapter.submitList(results)
                        binding.searchResultsRecyclerView.visibility = 
                            if (results.isNotEmpty() && binding.searchEditText.text.toString().length >= 3) 
                                View.VISIBLE else View.GONE
                    }
                }
                
                //trang thai tim kiem
                launch {
                    viewModel.isSearching.collectLatest { isSearching ->
                        // Show search progress in search icon
                        if (isSearching && binding.searchEditText.text.toString().length >= 3) {
                            binding.searchInputLayout.endIconDrawable = null
                            // Could add progress indicator here if needed
                        } else {
                            binding.searchInputLayout.setEndIconDrawable(R.drawable.ic_search)
                        }
                    }
                }
                
                //trang thai chon dia diem
                launch {
                    viewModel.sourceLocation.collectLatest{source->
                        if (source != null) {
                            binding.locationSourceText.text = source.toString()
                            binding.locationSourceText.visibility = View.VISIBLE
                        } else {
                            binding.locationSourceText.visibility = View.GONE
                        }
                    }
                }

                // Theo dõi thay đổi về latitude và longitude
                launch {
                    viewModel.latitude.collectLatest { latitude ->
                        if (latitude != 0.0 && viewModel.longitude.value != 0.0) {
                            updateMapWithLocation(latitude, viewModel.longitude.value)
                        }
                    }
                }

                launch {
                    viewModel.longitude.collectLatest { longitude ->
                        if (longitude != 0.0 && viewModel.latitude.value != 0.0) {
                            updateMapWithLocation(viewModel.latitude.value, longitude)
                        }
                    }
                }
                
                launch {
                    viewModel.location.collectLatest { location ->
                        location?.let {
                            // Location loaded successfully for edit mode
                            // No schedule handling needed anymore
                        }
                    }
                }
            }
        }
    }
    
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        // Enable map settings
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
        }
        
        // Set default location to Ha Noi
        val defaultLocation = LatLng(20.1964661, 106.3225575)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))
        
        // Enable user location if permissions are granted
        try {
            map.isMyLocationEnabled = true
        } catch (e: SecurityException) {
            // Handle permission not granted
        }
        
        // Set up map click listener
        map.setOnMapClickListener { latLng ->
            updateMapWithLocation(latLng.latitude, latLng.longitude)
            viewModel.updateLocationOnMap(latLng.latitude, latLng.longitude)
        }
        
        // If we have location data, update the map
        if (viewModel.latitude.value != 0.0 && viewModel.longitude.value != 0.0) {
            updateMapWithLocation(viewModel.latitude.value, viewModel.longitude.value)
        }
    }
    
    private fun updateMapWithLocation(latitude: Double, longitude: Double) {
        googleMap?.let { map ->
            map.clear()
            
            val position = LatLng(latitude, longitude)
            
            // Add marker
            map.addMarker(MarkerOptions().position(position).title(binding.nameEditText.text.toString()))
            
            // Add circle for radius
            addCircleToMap(position)
            
            // Move camera
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
        }
    }
    
    private fun updateMapCircle() {
        val lat = viewModel.latitude.value
        val lng = viewModel.longitude.value
        
        if (lat != 0.0 && lng != 0.0) {
            googleMap?.let { map ->
                map.clear()
                
                val position = LatLng(lat, lng)
                
                // Add marker
                map.addMarker(MarkerOptions()
                                .position(position)
                                .title(binding.nameEditText.text.toString())
                            )
                
                // Add circle for radius
                addCircleToMap(position)
            }
        }
    }
    
    private fun addCircleToMap(position: LatLng) {
        googleMap?.addCircle(
            CircleOptions()
                .center(position)
                .radius(viewModel.radius.value.toDouble())
                .strokeWidth(2f)
                .strokeColor(requireContext().getColor(R.color.primaryColor))
                .fillColor(requireContext().getColor(R.color.primaryColorTransparent))
        )
    }
    

    
    override fun onDestroyView() {
        super.onDestroyView()
        searchTextWatcher?.let { binding.searchEditText.removeTextChangedListener(it) }
        _binding = null
    }

} 