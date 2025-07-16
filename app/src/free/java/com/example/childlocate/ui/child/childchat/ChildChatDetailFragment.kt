package com.example.childlocate.ui.child.childchat

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.childlocate.databinding.FragmentChildChatDetailBinding
import com.example.childlocate.ui.parent.detailchat.ChatAdapter
import com.example.childlocate.ui.parent.detailchat.ImageFullscreenDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit


class ChildChatDetailFragment : Fragment() {

    companion object {
        private const val ARG_FAMILY_ID = "family_id"
        private const val ARG_CHILD_ID = "child_id"

        fun newInstance(familyId: String?, childId: String?) = ChildChatDetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_FAMILY_ID, familyId)
                putString(ARG_CHILD_ID, childId)
            }
        }
    }

    private var _binding: FragmentChildChatDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChildChatDetailViewModel by viewModels()
    private var senderId: String ? = null
    private var receiverId: String ? = null

    private lateinit var chatAdapter: ChatAdapter
    private var imageCapture: ImageCapture? = null
    private var isAtBottom = true
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraControl: CameraControl? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isFrontCamera = true
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var camera: Camera? = null

    private var recordingTimer: CountDownTimer? = null
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var currentPlayingMessageId: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResult(permissions)
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            viewModel.setLoading(true)
            viewModel.uploadMessage(it)
        }
    }

    private var audioUpdateJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChildChatDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.let {
            receiverId = it.getString(ARG_FAMILY_ID) ?: ""
            senderId = it.getString(ARG_CHILD_ID) ?: ""
        }
        setupUI()
        setupObservers()

    }

    private fun setupUI() {
        setupToolbar()
        initChatRecyclerView()
        setupClickListeners()

        Log.d("ChildChatDetailFragment", "Sender ID: $senderId, Receiver ID: $receiverId")

        receiverId?.let { senderId?.let { it1 -> viewModel.setChatParticipants(it1, it) } }
    }

    private fun setupToolbar() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun initChatRecyclerView() {
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(context)
        chatAdapter = ChatAdapter(
            currentUserId = senderId!!,
            memberNames = emptyMap(),
            memberAvatars = emptyMap(),
            onImageClick = { imageUrl -> showImageFullscreen(imageUrl) },
            onAudioPlay = { messageId, audioUrl -> handleAudioPlay(messageId, audioUrl) },
            onAudioPause = { messageId -> handleAudioPause(messageId) },
            onAudioSeek = { messageId, progress -> handleAudioSeek(messageId, progress) },
            onAudioComplete = { messageId -> handleAudioComplete(messageId) }
        )
        binding.chatRecyclerView.adapter = chatAdapter

        binding.chatRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                isAtBottom = layoutManager.findLastCompletelyVisibleItemPosition() == chatAdapter.itemCount - 1
            }
        })
    }

    private fun setupClickListeners() {
        binding.apply {
            imageMessage.setOnClickListener { openGallery() }
            sendButton.setOnClickListener { sendMessage() }
            childCall.setOnClickListener { initiatePhoneCall() }
            cameraButton.setOnClickListener { startCamera() }
            closeCameraButton.setOnClickListener { hideCamera() }
            cameraCaptureButton.setOnClickListener { takePhoto() }
            switchCameraButton.setOnClickListener { switchCamera() }
            audioButton.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startRecording()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        stopRecording()
                        true
                    }
                    else -> false
                }
            }
            cancelRecordingButton.setOnClickListener {
                cancelRecording()
            }
        }
    }

    private fun setupObservers() {
        /*viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.memberNames.collect { memberNames ->
                    viewModel.memberAvatars.collect { memberAvatars ->
                        chatAdapter = ChatAdapter(
                            currentUserId = args.senderId,
                            memberNames = memberNames,
                            memberAvatars = memberAvatars,
                            onImageClick = { imageUrl -> showImageFullscreen(imageUrl) },
                            onAudioPlay = { messageId, audioUrl -> handleAudioPlay(messageId, audioUrl) },
                            onAudioPause = { messageId -> handleAudioPause(messageId) },
                            onAudioSeek = { messageId, progress -> handleAudioSeek(messageId, progress) },
                            onAudioComplete = { messageId -> handleAudioComplete(messageId) }
                        )
                        binding.chatRecyclerView.adapter = chatAdapter
                    }
                }
            }
        }*/
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.memberNames.collect { names ->
                        chatAdapter.memberNames = names
                        chatAdapter.notifyDataSetChanged() // hoặc chỉ update nếu bạn dùng DiffUtil cho tên
                    }
                }
                launch {
                    viewModel.memberAvatars.collect { avatars ->
                        chatAdapter.memberAvatars = avatars
                        chatAdapter.notifyDataSetChanged()
                    }
                }
            }
        }



        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { messages ->
                    chatAdapter.submitList(messages)
                    if (isAtBottom) scrollToBottom()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { isLoading ->
                    binding.isLoading = isLoading
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.familyName.collect { value ->
                    binding.chatTitle.text = value
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recordingError.collect { error ->
                    error?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        // Reset lỗi sau khi hiển thị nếu muốn
                        viewModel.resetError()
                    }
                }
            }
        }

    }

    private fun openGallery() {
        when {
            isPermissionGranted(Manifest.permission.READ_MEDIA_IMAGES) -> {
                getContent.launch("image/*")
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    showPermissionRationaleDialog(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
                }
            }
        }
    }

    private fun startCamera() {
        when {
            isPermissionGranted(Manifest.permission.CAMERA) -> {
                launchCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionRationaleDialog(Manifest.permission.CAMERA)
            }
            else -> {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            }
        }
    }

    private fun launchCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider = binding.viewFinder.surfaceProvider
                    }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()

                cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                cameraControl = camera?.cameraControl
                // Observe zoom state
                camera?.cameraInfo?.zoomState?.observe(viewLifecycleOwner) { state ->
                    // Optional: Update UI based on zoom capabilities
                    val currentZoomRatio = state.zoomRatio
                    val maxZoom = state.maxZoomRatio
                    val minZoom = state.minZoomRatio

                    // Update SeekBar progress nếu cần
                    binding.cameraZoomSeekBar.progress =
                        ((currentZoomRatio - minZoom) / (maxZoom - minZoom) * 100).toInt()
                }
                setupPinchToZoom()
                showCamera()
            } catch (e: Exception) {
                showError("Không thể khởi tạo camera: ${e.localizedMessage}")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun setupPinchToZoom() {
        scaleGestureDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // Sử dụng camera instance đã lưu
                val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                val maxZoom = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 10f
                val minZoom = camera?.cameraInfo?.zoomState?.value?.minZoomRatio ?: 1f

                val delta = detector.scaleFactor
                val newZoomRatio = currentZoomRatio * delta

                // Giới hạn zoom ratio trong khoảng hợp lý
                val limitedZoomRatio = newZoomRatio.coerceIn(minZoom, maxZoom)

                cameraControl?.setZoomRatio(limitedZoomRatio)
                return true
            }
        })

        // Tách riêng xử lý zoom và focus
        binding.viewFinder.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!scaleGestureDetector.isInProgress) {
                        val factory = binding.viewFinder.meteringPointFactory
                        val point = factory.createPoint(event.x, event.y)
                        Log.d("DetailCharFragment", event.x.toString())

                        val focusAction = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()

                        cameraControl?.startFocusAndMetering(focusAction)
                        showFocusIndicator(event.x, event.y)
                        return@setOnTouchListener true
                    }
                }
            }
            scaleGestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun showFocusIndicator(x: Float, y: Float) {
        binding.focusRing.apply {
            visibility = View.VISIBLE

            // Tính toán vị trí tương đối trong PreviewView
            val params = layoutParams as ConstraintLayout.LayoutParams
            params.leftMargin = (x - width / 2).toInt()
            params.topMargin = (y - height / 2).toInt()
            layoutParams = params

            // Animation
            alpha = 1.0f
            scaleX = 1.5f
            scaleY = 1.5f

            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    visibility = View.GONE
                    alpha = 1.0f
                }
                .start()
        }
    }

    private fun switchCamera() {
        isFrontCamera = !isFrontCamera
        // Xác định CameraSelector dựa trên trạng thái
        cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Reset zoom về mặc định (1x)
        cameraControl?.setZoomRatio(1f)
        launchCamera()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // Create temporary file
        val photoFile = createTempImageFile()

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    viewModel.setLoading(true)
                    viewModel.uploadMessage(savedUri)
                    hideCamera()
                }

                override fun onError(exc: ImageCaptureException) {
                    showError("Lỗi chụp ảnh: ${exc.message}")
                }
            }
        )
    }

    private fun createTempImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().cacheDir
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    private fun showCamera() {
        binding.apply {
            cameraContainer.visibility = View.VISIBLE
            chatRecyclerView.visibility = View.GONE
            bottomContainer.visibility = View.GONE
        }
    }

    private fun hideCamera() {
        binding.apply {
            cameraContainer.visibility = View.GONE
            chatRecyclerView.visibility = View.VISIBLE
            bottomContainer.visibility = View.VISIBLE
        }
        cameraProvider?.unbindAll()
    }

    private fun sendMessage() {
        val messageText = binding.messageEditText.text.toString().trim()
        if (messageText.isNotEmpty()) {
            viewModel.sendMessage(messageText)
            binding.messageEditText.text.clear()
            hideKeyboard()
            scrollToBottom()
        }
    }

    private fun scrollToBottom() {
        binding.chatRecyclerView.post {
            // Kiểm tra xem adapter có tồn tại và có items không
            if (chatAdapter.itemCount > 0) {
                binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
            }
        }
    }

    private fun initiatePhoneCall() {
        viewModel.getPhoneNumber()?.let { phoneNumber ->
            val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            try {
                startActivity(callIntent)
            } catch (e: ActivityNotFoundException) {
                showError("Không thể thực hiện cuộc gọi")
            }
        } ?: showError("Không tìm thấy số điện thoại")
    }

    private fun showPermissionRationaleDialog(permission: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Cần cấp quyền")
            .setMessage(getPermissionRationaleMessage(permission))
            .setPositiveButton("Cấp quyền") { _, _ ->
                requestPermissionLauncher.launch(arrayOf(permission))
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun getPermissionRationaleMessage(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA -> "Ứng dụng cần quyền truy cập camera để chụp ảnh"
            Manifest.permission.READ_MEDIA_IMAGES -> "Ứng dụng cần quyền truy cập thư viện ảnh để gửi hình ảnh"
            else -> "Ứng dụng cần quyền này để hoạt động"
        }
    }

    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val granted = permissions.values.all { it }
        if (!granted) {
            showError("Vui lòng cấp quyền để sử dụng tính năng này")
        }
    }

    private fun isPermissionGranted(permission: String) =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.messageEditText.windowToken, 0)
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rebind camera use cases on orientation change
        if (binding.cameraContainer.visibility == View.VISIBLE) {
            imageCapture = null
            launchCamera()
        }
    }

    private fun startRecording() {
        if (checkRecordPermission()) {
            viewModel.startRecording()
            showRecordingUI()
            startRecordingTimer()
        } else {
            requestRecordPermission()
        }
    }

    private fun stopRecording() {
        viewModel.stopRecording()
        hideRecordingUI()
        stopRecordingTimer()
    }

    private fun cancelRecording() {
        viewModel.cancelRecording()
        hideRecordingUI()
        stopRecordingTimer()
    }

    private fun showRecordingUI() {
        binding.recordingContainer.visibility = View.VISIBLE
    }

    private fun hideRecordingUI() {
        binding.recordingContainer.visibility = View.GONE
    }

    private fun startRecordingTimer() {
        recordingTimer?.cancel()
        recordingTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val duration = viewModel.getRecordingDuration()
                binding.recordingTimeTextView.text = formatDuration(duration)
            }

            override fun onFinish() {
                // Not used
            }
        }.start()
    }

    private fun stopRecordingTimer() {
        recordingTimer?.cancel()
        recordingTimer = null
    }

    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun checkRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRecordPermission() {
        requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
    }

    private fun showImageFullscreen(imageUrl: String) {
        ImageFullscreenDialog(requireContext(), imageUrl).show()
    }

    private fun handleAudioPlay(messageId: String, audioUrl: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (currentPlayingMessageId == messageId) {
                // Toggle play/pause
                mediaPlayer?.apply {
                    if (isPlaying) {
                        pause()
                        updateAudioPlaybackState(messageId, false, currentPosition * 100 / duration, formatDuration(currentPosition.toLong()))
                    } else {
                        start()
                        updateAudioPlaybackState(messageId, true, currentPosition * 100 / duration, formatDuration(currentPosition.toLong()))
                        startAudioProgressUpdate(messageId)
                    }
                }
            } else {
                // Start new playback
                mediaPlayer?.release()
                mediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(audioUrl)
                    prepare()
                    setOnCompletionListener {
                        viewLifecycleOwner.lifecycleScope.launch {
                            updateAudioPlaybackState(messageId, false, 0, formatDuration(0))
                            currentPlayingMessageId = null
                            stopAudioProgressUpdate()
                        }
                    }
                    start()
                }
                currentPlayingMessageId = messageId
                updateAudioPlaybackState(messageId, true, 0, formatDuration(0))
                startAudioProgressUpdate(messageId)
            }
        }
    }

    private fun handleAudioPause(messageId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            mediaPlayer?.pause()
            updateAudioPlaybackState(
                messageId,
                false,
                mediaPlayer?.currentPosition?.times(100)?.div(mediaPlayer?.duration ?: 1) ?: 0,
                formatDuration(mediaPlayer?.currentPosition?.toLong() ?: 0)
            )
            stopAudioProgressUpdate()
        }
    }

    private fun handleAudioSeek(messageId: String, progress: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            mediaPlayer?.let { player ->
                val seekPosition = (progress * player.duration) / 100
                player.seekTo(seekPosition)
                updateAudioPlaybackState(
                    messageId,
                    player.isPlaying,
                    progress,
                    formatDuration(seekPosition.toLong())
                )
            }
        }
    }

    private fun handleAudioComplete(messageId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            updateAudioPlaybackState(messageId, false, 0, formatDuration(0))
            currentPlayingMessageId = null
            stopAudioProgressUpdate()
        }
    }

    private fun startAudioProgressUpdate(messageId: String) {
        stopAudioProgressUpdate()
        audioUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive && currentPlayingMessageId == messageId) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val progress = (player.currentPosition * 100 / player.duration)
                        val duration = formatDuration(player.currentPosition.toLong())
                        updateAudioPlaybackState(messageId, true, progress, duration)
                    }
                }
                delay(100) // Update every 100ms
            }
        }
    }

    private fun stopAudioProgressUpdate() {
        audioUpdateJob?.cancel()
        audioUpdateJob = null
    }

    private fun updateAudioPlaybackState(messageId: String, isPlaying: Boolean, progress: Int, duration: String) {
        val position = chatAdapter.currentList.indexOfFirst { it.id == messageId }
        if (position != -1) {
            val holder = binding.chatRecyclerView.findViewHolderForAdapterPosition(position)
            when (holder) {
                is ChatAdapter.SentMessageViewHolder -> holder.updatePlaybackState(isPlaying, progress, duration)
                is ChatAdapter.ReceivedMessageViewHolder -> holder.updatePlaybackState(isPlaying, progress, duration)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraProvider?.unbindAll()
        stopRecordingTimer()
        stopAudioProgressUpdate()
        mediaPlayer?.release()
        mediaPlayer = null
        currentPlayingMessageId = null
        _binding = null
    }
}