package com.example.childlocate.ui.parent.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.childlocate.R
import com.example.childlocate.databinding.FragmentHomeBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

private object MapConstants {
    const val DEFAULT_ZOOM = 15f
    const val MARKER_SIZE = 100
    const val CORNER_RADIUS = 50f
}

class HomeFragment : Fragment(), OnMapReadyCallback {
    private lateinit var binding: FragmentHomeBinding
    private val viewModel: HomeViewModel by lazy {
        ViewModelProvider(this)[HomeViewModel::class.java]
    }
    private val audioRealtimeViewModel: AudioStreamViewModel by lazy {
        ViewModelProvider(this)[AudioStreamViewModel::class.java]
    }

    private lateinit var childSpinnerAdapter: ChildSpinnerAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var googleMap: GoogleMap
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<NestedScrollView>

    private var focusedChildId: String? = null
    private var userMarker: Marker? = null
    private val childMarkers = mutableMapOf<String, Marker>()
    private var userLatitude: Double? = null
    private var userLongitude: Double? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBottomSheet()
        setupObservers()
        setupButtons()
        setupChildrenSpinner()
        setupMap()
        setupAudioStream()

    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet).apply {
            state = BottomSheetBehavior.STATE_COLLAPSED
        }

        binding.bottomSheet.background = MaterialShapeDrawable().apply {
            shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setTopLeftCorner(CornerFamily.ROUNDED, MapConstants.CORNER_RADIUS)
                .setTopRightCorner(CornerFamily.ROUNDED, MapConstants.CORNER_RADIUS)
                .build()
        }

        setupBottomSheetCallback()
    }

    private fun setupBottomSheetCallback() {
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Optional: Handle slide animations
            }
        })
    }

    private fun setupObservers() {
        //load familyId
        viewModel.familyId.observe(viewLifecycleOwner) { familyId ->
            familyId?.let {
                setupFamilyChat(it)
            }
        }
        //load các trẻ trong gia đình
        viewModel.children.observe(viewLifecycleOwner) { children ->
            childSpinnerAdapter.apply {
                clear()
                addAll(children)
                notifyDataSetChanged()
                restoreSelectedChild()
            }
        }

        // Observe các vị trí trẻ đang chia sẻ vị trí
        viewModel.childrenLocations.observe(viewLifecycleOwner) { locationsMap ->
            updateChildrenMarkersOnMap(locationsMap)
            
            // Update button state nếu trẻ được chọn thay đổi trạng thái vị trí
            val focusedChild = viewModel.focusedChild.value
            if (focusedChild != null && locationsMap.containsKey(focusedChild.childId)) {
                val currentState = viewModel.focusedChildState.value
                updateLocationButtonState(currentState)
                updateSafeLocationStatus(locationsMap[focusedChild.childId])
            }
        }

        // Observe vào trẻ đang được chọn
        viewModel.focusedChild.observe(viewLifecycleOwner) { focusedChild ->
            focusedChild?.let {
                focusedChildId = it.childId
                // Move camera to focused child if location available
                val location = viewModel.childrenLocations.value?.get(it.childId)
                location?.let { locationData ->
                    val latLng = LatLng(locationData.latitude, locationData.longitude)
                    if (::googleMap.isInitialized) {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, MapConstants.DEFAULT_ZOOM))
                    }
                }
                // Update safe location status for new focused child
                updateSafeLocationStatus(location)
            }
        }
        //quan sát trạng thái trẻ để cập nhật button
        lifecycleScope.launch {
            viewModel.focusedChildState.collect { state ->
                updateLocationButtonState(state)

                val focusedChild = viewModel.focusedChild.value
                if (focusedChild != null) {
                    val locationData = viewModel.childrenLocations.value?.get(focusedChild.childId)
                    updateSafeLocationStatus(locationData)
                }
            }
        }
    }


    private fun updateLocationButtonState(state: ChildTrackingState) {
        binding.btnFindDirection.apply {
            val focusedChild = viewModel.focusedChild.value
            val childName = focusedChild?.childName ?: "Child"
            
            // Get current location data for focused child
            focusedChild?.let { child ->
                viewModel.childrenLocations.value?.get(child.childId)
            }
            
            when {
                state.isLoading -> {
                    text = "Đang tải..."
                    isEnabled = false
                    contentDescription = "Request in progress"
                }
                state.isTracking -> {
                    text = "Dừng theo dõi $childName"
                    isEnabled = true
                    contentDescription = "Currently tracking $childName, tap to stop"
                }
                state.error != null -> {
                    text = "Thử lại"
                    isEnabled = true
                    contentDescription = "Error occurred, tap to retry"
                    Snackbar.make(binding.root, state.error, Snackbar.LENGTH_LONG).show()
                }
                else -> {
                    text = "Bắt đầu theo dõi $childName"
                    isEnabled = true
                    contentDescription = "Start tracking $childName's location"
                }
            }
        }
    }

    private fun updateChildrenMarkersOnMap(locationsMap: Map<String, ChildLocationData>) {
        if (!::googleMap.isInitialized) return

        // xóa trẻ không còn tracking
        val currentChildIds = locationsMap.keys
        val markersToRemove = childMarkers.keys - currentChildIds
        markersToRemove.forEach { childId ->
            childMarkers[childId]?.remove()
            childMarkers.remove(childId)
        }

        // Thêm trẻ tracking
        locationsMap.forEach { (childId, locationData) ->
            val latLng = LatLng(locationData.latitude, locationData.longitude)
            val isFocused = childId == focusedChildId
            
            // Remove existing marker if any
            childMarkers[childId]?.remove()
            
            // Tạo marker dựa trên avatar
            if (locationData.avatarUrl != null) {
                loadCustomMarker(locationData, latLng, isFocused) { marker ->
                    childMarkers[childId] = marker
                }
            } else {
                val marker = addDefaultMarker(locationData, latLng, isFocused)
                childMarkers[childId] = marker
            }
        }
    }

    private fun loadCustomMarker(
        locationData: ChildLocationData, 
        location: LatLng, 
        isFocused: Boolean,
        onMarkerReady: (Marker) -> Unit
    ) {
        Glide.with(this)
            .asBitmap()
            .load(locationData.avatarUrl)
            .circleCrop()
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val markerBitmap = createMarkerBitmap(
                        resource,
                        isFocused
                    )
                    val marker = addCustomMarker(locationData, location, markerBitmap)
                    onMarkerReady(marker)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // Handle placeholder if needed
                }
            })
    }

    private fun createMarkerBitmap(
        avatarBitmap: Bitmap,
        isFocused: Boolean
    ): Bitmap {
        val size = if (isFocused) MapConstants.MARKER_SIZE else (MapConstants.MARKER_SIZE * 0.8).toInt()
        val result = Bitmap.createBitmap(size, size + 20, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        val scaledAvatar = Bitmap.createScaledBitmap(avatarBitmap, size, size, false)
        canvas.drawBitmap(scaledAvatar, 0f, 0f, null)

        // Add focus indicator
        if (isFocused) {
            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f
                color = Color.BLUE
                isAntiAlias = true
            }
            canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 3f, paint)
        }
        
        return result
    }

    private fun addCustomMarker(locationData: ChildLocationData, location: LatLng, bitmap: Bitmap): Marker {
        val markerOptions = MarkerOptions()
            .position(location)
            .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
        
        // Set title based on safe location status
        val title = if (locationData.isInSafeZone && locationData.safeLocationName != null) {
            "${locationData.childName} - Đang ở: ${locationData.safeLocationName}"
        } else {
            locationData.childName
        }
        markerOptions.title(title)
        
        // Set snippet with address or coordinates
        val baseSnippet = locationData.address?.getAddressLine(0) 
            ?: "Lat: ${String.format("%.4f", locationData.latitude)}, Lon: ${String.format("%.4f", locationData.longitude)}"
        
        val snippet = if (locationData.isInSafeZone) {
            "Vùng an toàn • $baseSnippet"
        } else {
            "Vị trí lạ • $baseSnippet"
        }
        
        // Add battery info if available
        locationData.batteryLevel?.let { battery ->
            markerOptions.snippet("$snippet • Pin: $battery%")
        } ?: markerOptions.snippet(snippet)
        
        return googleMap.addMarker(markerOptions)!!
    }

    private fun addDefaultMarker(locationData: ChildLocationData, location: LatLng, isFocused: Boolean): Marker {
        val markerOptions = MarkerOptions()
            .position(location)
        
        // Set title based on safe location status
        val title = if (locationData.isInSafeZone && locationData.safeLocationName != null) {
            "${locationData.childName} - Đang ở: ${locationData.safeLocationName}"
        } else {
            locationData.childName
        }
        markerOptions.title(title)
        
        // Set snippet with address or coordinates
        val baseSnippet = locationData.address?.getAddressLine(0) 
            ?: "Lat: ${String.format("%.4f", locationData.latitude)}, Lon: ${String.format("%.4f", locationData.longitude)}"
        
        val snippet = if (locationData.isInSafeZone) {
            "Vùng an toàn • $baseSnippet"
        } else {
            "Vị trí lạ • $baseSnippet"
        }
        
        // Add battery info if available
        locationData.batteryLevel?.let { battery ->
            markerOptions.snippet("$snippet • Pin: $battery%")
        } ?: markerOptions.snippet(snippet)
        
        // Use different colors based on safe zone status and focus
        val markerColor = when {
            locationData.isInSafeZone && isFocused -> BitmapDescriptorFactory.HUE_GREEN
            locationData.isInSafeZone -> BitmapDescriptorFactory.HUE_CYAN
            isFocused -> BitmapDescriptorFactory.HUE_BLUE
            else -> BitmapDescriptorFactory.HUE_RED
        }
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(markerColor))
        
        return googleMap.addMarker(markerOptions)!!
    }

    private fun restoreSelectedChild() {
        viewModel.focusedChild.value?.let { focusedChild ->
            val position = childSpinnerAdapter.getPosition(focusedChild)
            if (position != -1) {
                binding.childNameSpinner.setSelection(position)
            }
        }
    }

    private fun setupFamilyChat(familyId: String) {
        binding.fabChat.setOnClickListener {
            val parentId = viewModel.parentId.value.toString()
            findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToDetailChatFragment(parentId, familyId)
            )
        }
    }

    private fun setupButtons() {
        with(binding) {
            btnLocation.setOnClickListener { navigateToLocationsFragment() }
            btnSetting.setOnClickListener { navigateToTaskFragment() }
            btnHistory.setOnClickListener { navigateToWebFilterFragment() }
            btnSeeDetail.setOnClickListener { navigateToUsageDetailFragment() }
            btnCallChild.setOnClickListener { navigateToHistoryFragment() }
            btnFindDirection.setOnClickListener { handleLocationRequestToggle() }
            btnListenSound.setOnClickListener { handleAudioToggle() }
        }
    }
    private fun navigateToHistoryFragment(){
        val parentId = viewModel.parentId.value.toString()
        focusedChildId?.let { childId ->
            val action = HomeFragmentDirections.actionHomeFragmentToHistoryFragment(parentId, childId)
            findNavController().navigate(action)
        }
    }

    private fun navigateToLocationsFragment() {
        val parentId = viewModel.parentId.value.toString()
        focusedChildId?.let { childId ->
            val action = HomeFragmentDirections.actionHomeFragmentToLocationsFragment(parentId, childId)
            findNavController().navigate(action)
        }
    }

    private fun navigateToTaskFragment() {
        val parentId = viewModel.parentId.value.toString()
        focusedChildId?.let { childId ->
            val action = HomeFragmentDirections.actionHomeFragmentToTaskFragment(parentId, childId)
            findNavController().navigate(action)
        }
    }

    private fun navigateToWebFilterFragment() {
        focusedChildId?.let { childId ->
            val action = HomeFragmentDirections.actionHomeFragmentToWebFilterFragment(childId)
            findNavController().navigate(action)
        }
    }

    private fun navigateToUsageDetailFragment() {
        focusedChildId?.let { childId ->
            val action = HomeFragmentDirections.actionHomeFragmentToUsageDetailFragment(childId)
            findNavController().navigate(action)
        }
    }

    private fun handleLocationRequestToggle() {
        val focusedChild = viewModel.focusedChild.value ?: return
        val currentState = viewModel.focusedChildState.value
        
        if (currentState.isTracking) {
            viewModel.sendStopLocationRequest(focusedChild.childId)
        } else {
            viewModel.sendLocationRequest(focusedChild.childId)
        }
    }


    private fun handleAudioToggle() {
        when (audioRealtimeViewModel.streamingState.value) {
            is StreamingState.Idle -> {
                // Kiểm tra lần đầu sử dụng -> hiển thị dialog
                if (isFirstTimeUsingAudio()) {
                    showAudioFeatureDialog()
                } else {
                    startAudioListening()
                }
            }
            is StreamingState.Listening -> {
                focusedChildId?.let { audioRealtimeViewModel.stopRecording(it) }
            }
            is StreamingState.Connecting -> {
                // Cho phép cancel request khi đang connecting
                audioRealtimeViewModel.cancelConnection()
                showMessage("Đã hủy kết nối")
            }
            is StreamingState.Error -> {
                // Retry logic
                startAudioListening()
            }
            is StreamingState.Other -> {
                // Handle other states if needed
                showMessage("Đang được lắng nghe từ phụ huynh khác")
            }
        }
    }

    private fun isFirstTimeUsingAudio(): Boolean {
        val prefs = requireContext().getSharedPreferences("audio_prefs", Context.MODE_PRIVATE)
        //return !prefs.getBoolean("audio_guide_shown", false)
        return true
    }

    private fun markAudioGuideAsShown() {
        val prefs = requireContext().getSharedPreferences("audio_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("audio_guide_shown", true).apply()
    }

    private fun showAudioFeatureDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_audio_feature_guide, null)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)
        val btnContinue = dialogView.findViewById<android.widget.Button>(R.id.btnContinue)
        val cbDontShowAgain = dialogView.findViewById<android.widget.CheckBox>(R.id.cbDontShowAgain)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnContinue.setOnClickListener {
            if (cbDontShowAgain.isChecked) {
                markAudioGuideAsShown()
            }
            dialog.dismiss()
            startAudioListening()
        }

        dialog.show()
    }

    private fun startAudioListening() {
        focusedChildId?.let { audioRealtimeViewModel.requestRecording(it) }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun setupChildrenSpinner() {
        childSpinnerAdapter = ChildSpinnerAdapter(requireContext())
        binding.childNameSpinner.apply {
            adapter = childSpinnerAdapter
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                    childSpinnerAdapter.getItem(position)?.let { child ->
                        viewModel.selectChild(child)
                        audioRealtimeViewModel.selectChild(child.childId)
                        focusedChildId = child.childId
                        Log.d("HomeFragment", "Focused child: $focusedChildId")
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        }
    }

    private fun setupMap() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapView) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction().replace(R.id.mapView, it).commit()
            }
        mapFragment.getMapAsync(this)
        getCurrentLocation()
    }

    private fun setupAudioStream() {
        lifecycleScope.launch {
            audioRealtimeViewModel.streamingState.collect { state ->
                updateStreamingUI(state)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateStreamingUI(state: StreamingState) {
        binding.btnListenSound.apply {
            when (state) {
                is StreamingState.Idle -> {
                    text = "Nghe âm thanh"
                    isEnabled = true
                    contentDescription = "Bắt đầu nghe âm thanh từ con em"
                }
                is StreamingState.Connecting -> {
                    text = "Đang kết nối..."
                    isEnabled = true // Cho phép cancel
                    contentDescription = "Đang kết nối để nghe âm thanh, nhấn để hủy"
                }
                is StreamingState.Listening -> {
                    text = "Dừng nghe"
                    isEnabled = true
                    contentDescription = "Đang nghe âm thanh, nhấn để dừng"
                }
                is StreamingState.Error -> {
                    text = "Thử lại"
                    isEnabled = true
                    contentDescription = "Có lỗi xảy ra, nhấn để thử lại"
                    Log.d("AUDIO", state.message)
                    // Hiển thị error message cho user
                    showMessage("Lỗi: ${state.message}")
                }
                is StreamingState.Other -> {
                    text = "Đang được lắng nghe tự phụ huynh khác"
                    isEnabled = false
                    contentDescription = "Đang xử lý yêu cầu khác, vui lòng đợi"
                }
            }
        }
    }

    private fun getCurrentLocation() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
            return
        }
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                userLatitude = it.latitude
                userLongitude = it.longitude
                Log.d("Maps", "User Location: $userLatitude, $userLongitude")
                updateUserLocation(LatLng(userLatitude!!, userLongitude!!))
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onMapReady(mMap: GoogleMap) {
        googleMap = mMap
        applyMapStyle()
        updateUserLocation(LatLng(userLatitude ?: 0.0, userLongitude ?: 0.0))
        
        // Set up marker click listener
        googleMap.setOnMarkerClickListener { marker ->
            if (marker == userMarker) {
                marker.showInfoWindow()
                return@setOnMarkerClickListener true
            }
            
            // Find the child data for this marker
            val childData = viewModel.childrenLocations.value?.values?.find { locationData ->
                marker.position.latitude == locationData.latitude && 
                marker.position.longitude == locationData.longitude
            }
            
            childData?.let { data ->
                // Update title based on safe location status
                val title = if (data.isInSafeZone && data.safeLocationName != null) {
                    "${data.childName} - Đang ở: ${data.safeLocationName}"
                } else {
                    data.childName
                }
                marker.title = title

                // Update snippet with safe zone info
                val baseAddress = data.address?.getAddressLine(0) 
                    ?: "Lat: ${String.format("%.4f", data.latitude)}, Lon: ${String.format("%.4f", data.longitude)}"
                
                val snippet = if (data.isInSafeZone) {
                    " Vùng an toàn • $baseAddress"
                } else {
                    " Vị trí lạ • $baseAddress"
                }
                
                val batteryInfo = data.batteryLevel?.let { " • Pin: ${it}%" } ?: ""
                marker.snippet = "$snippet$batteryInfo"
                
                marker.showInfoWindow()
            }
            
            true // Return true to indicate we've handled the click
        }
    }

    private fun applyMapStyle() {
        try {
            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style)
            )
            if (!success) {
                Log.e("MapFragment", "Failed to apply map style.")
            }
        } catch (e: Resources.NotFoundException) {
            Log.e("MapFragment", "Map style resource not found.", e)
        }
    }

    private fun updateUserLocation(userLatLng: LatLng) {
        if (!::googleMap.isInitialized) return

        userMarker?.remove()
        userMarker = googleMap.addMarker(
            MarkerOptions()
                .position(userLatLng)
                .title("Vị trí của bạn")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, MapConstants.DEFAULT_ZOOM))
    }

    private fun updateSafeLocationStatus(locationData: ChildLocationData?) {
        if (locationData == null) {
            binding.safeLocationStatus.visibility = View.GONE
            return
        }
        
        if (locationData.isInSafeZone && locationData.safeLocationName != null) {
            binding.safeLocationStatus.apply {
                visibility = View.VISIBLE
                text = " ${locationData.childName} đang ở: ${locationData.safeLocationName}"
                setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
                
                // Set icon based on location type
                locationData.safeLocationIcon?.let { iconRes ->
                    setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
                } ?: setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_home_24, 0, 0, 0)
            }
        } else if (viewModel.focusedChildState.value.isTracking) {
            binding.safeLocationStatus.apply {
                visibility = View.VISIBLE
                text = "${locationData.childName} đang ở vị trí lạ"
                setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_add_location_24, 0, 0, 0)
            }
        } else {
            binding.safeLocationStatus.visibility = View.GONE
        }
    }
}
