package com.example.childlocate.ui.parent.detailchat

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.childlocate.databinding.DialogImageFullscreenBinding
import java.io.File
import java.io.FileOutputStream

class ImageFullscreenDialog(context: Context, private val imageUrl: String) : Dialog(context) {

    private lateinit var binding: DialogImageFullscreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogImageFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set dialog to fullscreen
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        setupImage()
        setupButtons()
    }

    private fun setupImage() {
        binding.loadingProgressBar.visibility = View.VISIBLE

        Glide.with(context)
            .asBitmap()
            .load(imageUrl)
            .listener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap>?,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.loadingProgressBar.visibility = View.GONE
                    Toast.makeText(context, "Không thể tải ảnh", Toast.LENGTH_SHORT).show()
                    return false
                }

                override fun onResourceReady(
                    resource: Bitmap?,
                    model: Any?,
                    target: Target<Bitmap>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.loadingProgressBar.visibility = View.GONE
                    return false
                }
            })
            .into(binding.fullscreenImageView)
    }

    private fun setupButtons() {
        binding.downloadButton.setOnClickListener {
            downloadImage()
        }

        binding.shareButton.setOnClickListener {
            shareImage()
        }
    }

    private fun downloadImage() {
        try {
            Glide.with(context)
                .asBitmap()
                .load(imageUrl)
                .listener(object : RequestListener<Bitmap> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Bitmap>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        Toast.makeText(context, "Không thể tải ảnh", Toast.LENGTH_SHORT).show()
                        return false
                    }

                    override fun onResourceReady(
                        resource: Bitmap?,
                        model: Any?,
                        target: Target<Bitmap>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        resource?.let { saveImage(it) }
                        return false
                    }
                })
                .submit()
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi khi tải ảnh", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImage(bitmap: Bitmap) {
        try {
            val imagesDir = File(context.getExternalFilesDir(null), "ChildLocate/Images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }

            val imageFile = File(imagesDir, "image_${System.currentTimeMillis()}.jpg")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }

            Toast.makeText(context, "Đã lưu ảnh", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi khi lưu ảnh", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage() {
        try {
            Glide.with(context)
                .asBitmap()
                .load(imageUrl)
                .listener(object : RequestListener<Bitmap> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Bitmap>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        Toast.makeText(context, "Không thể chia sẻ ảnh", Toast.LENGTH_SHORT).show()
                        return false
                    }

                    override fun onResourceReady(
                        resource: Bitmap?,
                        model: Any?,
                        target: Target<Bitmap>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        resource?.let { shareImageFile(it) }
                        return false
                    }
                })
                .submit()
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi khi chia sẻ ảnh", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImageFile(bitmap: Bitmap) {
        try {
            val imagesDir = File(context.cacheDir, "shared_images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }

            val imageFile = File(imagesDir, "shared_image_${System.currentTimeMillis()}.jpg")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ ảnh"))
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi khi chia sẻ ảnh", Toast.LENGTH_SHORT).show()
        }
    }
}