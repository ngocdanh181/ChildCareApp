package com.example.childlocate.ui.parent.detailchat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.childlocate.R
import com.example.childlocate.data.model.ChatMessage
import com.example.childlocate.data.model.MessageStatus
import com.example.childlocate.data.model.MessageType
import com.example.childlocate.databinding.ItemChatMessageReceivedOneBinding
import com.example.childlocate.databinding.ItemChatMessageSentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val currentUserId: String,
    var memberNames: Map<String, String>,
    var memberAvatars: Map<String, String>,
    private val onImageClick: (String) -> Unit,
    private val onAudioPlay: (String, String) -> Unit,
    private val onAudioPause: (String) -> Unit,
    private val onAudioSeek: (String, Int) -> Unit,
    private val onAudioComplete: (String) -> Unit
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2

        // Sử dụng pattern mới để định dạng ngày tháng
        private val DATE_FORMATTER = SimpleDateFormat("hh:mm a", Locale.getDefault())
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val binding = ItemChatMessageSentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            SentMessageViewHolder(binding)
            }
            else -> {
                val binding = ItemChatMessageReceivedOneBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            ReceivedMessageViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        val senderName = memberNames[message.senderId] ?: "Unknown"
        val senderAvatar = memberAvatars[message.senderId].orEmpty()

        when (holder) {
            is SentMessageViewHolder -> holder.bind(message, senderName)
            is ReceivedMessageViewHolder -> holder.bind(message, senderName, senderAvatar)
        }
    }

    inner class SentMessageViewHolder(private val binding: ItemChatMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage, senderName: String) {
            binding.senderNameTextView.text = senderName

            // Ẩn tất cả view trước
                binding.messageTextView.visibility = View.GONE
                binding.messageImageView.visibility = View.GONE
            binding.audioMessageLayout.visibility = View.GONE

            // Hiển thị view phù hợp dựa vào loại tin nhắn
            when (message.type) {
                MessageType.TEXT -> {
                    binding.messageTextView.apply {
                        visibility = View.VISIBLE
                        text = message.content
                    }
                }
                MessageType.IMAGE -> {
                    binding.messageImageView.apply {
                        visibility = View.VISIBLE
                        Glide.with(context)
                            .load(message.content)
                            .placeholder(R.drawable.baseline_image_24)
                            .error(R.drawable.ic_error)
                            .into(this)

                        setOnClickListener { onImageClick(message.content) }
                    }
                }
                MessageType.AUDIO -> {
                    binding.audioMessageLayout.visibility = View.VISIBLE
                    setupAudioMessage(message)
                }
            }

            binding.timestampTextView.text = formatTimestamp(message.timestamp)

            // Cập nhật trạng thái tin nhắn
            updateMessageStatus(message)
        }

        private fun updateMessageStatus(message: ChatMessage) {
            val statusIcon = when (message.status) {
                MessageStatus.SENT -> R.drawable.ic_message_sent
                MessageStatus.DELIVERED -> R.drawable.ic_message_delivered
                MessageStatus.READ -> R.drawable.ic_message_read
            }
            binding.messageStatusImageView.setImageResource(statusIcon)
            binding.sendingProgressBar.visibility =
                if (message.status == MessageStatus.SENT) View.VISIBLE else View.GONE
        }

        private fun setupAudioMessage(message: ChatMessage) {
            binding.playPauseButton.setOnClickListener {
                // Kiểm tra nếu đang play thì pause, ngược lại thì play
                if (binding.playPauseButton.tag == "playing") {
                    onAudioPause(message.id)
                    binding.playPauseButton.tag = "paused"
                } else {
                    onAudioPlay(message.id, message.content)
                    binding.playPauseButton.tag = "playing"
                }
            }

            binding.audioProgressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        onAudioSeek(message.id, progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        fun updatePlaybackState(isPlaying: Boolean, progress: Int, duration: String) {
            binding.playPauseButton.apply {
                setImageResource(
                    if (isPlaying) R.drawable.ic_stop else R.drawable.ic_play
                )
                tag = if (isPlaying) "playing" else "paused"
            }
            binding.audioProgressBar.progress = progress
            binding.durationTextView.text = duration
        }
    }

    inner class ReceivedMessageViewHolder(private val binding: ItemChatMessageReceivedOneBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage, senderName: String, senderAvatar: String) {
            binding.receivedNameTextView.text = senderName

            // Load avatar
            Glide.with(binding.root.context)
                .load(senderAvatar.takeIf { it.isNotEmpty() })
                .placeholder(R.drawable.baseline_person_2_24)
                .error(R.drawable.baseline_person_2_24)
                .into(binding.avatarImageView)

            // Ẩn tất cả view trước
                binding.messageTextView.visibility = View.GONE
                binding.messageImageView.visibility = View.GONE
                binding.audioMessageLayout.visibility = View.GONE

            // Hiển thị view phù hợp dựa vào loại tin nhắn
            when (message.type) {
                MessageType.TEXT -> {
                    binding.messageTextView.apply {
                        visibility = View.VISIBLE
                        text = message.content
                    }
                }
                MessageType.IMAGE -> {
                    binding.messageImageView.apply {
                        visibility = View.VISIBLE
                        Glide.with(context)
                            .load(message.content)
                            .placeholder(R.drawable.baseline_image_24)
                            .error(R.drawable.ic_error)
                            .into(this)

                        setOnClickListener { onImageClick(message.content) }
                    }
                }
                MessageType.AUDIO -> {
                    binding.audioMessageLayout.visibility = View.VISIBLE
                    setupAudioMessage(message)
                }
            }

            binding.timestampTextView.text = formatTimestamp(message.timestamp)
        }

        private fun setupAudioMessage(message: ChatMessage) {
            binding.playPauseButton.setOnClickListener {
                // Kiểm tra nếu đang play thì pause, ngược lại thì play
                if (binding.playPauseButton.tag == "playing") {
                    onAudioPause(message.id)
                    binding.playPauseButton.tag = "paused"
                } else {
                    onAudioPlay(message.id, message.content)
                    binding.playPauseButton.tag = "playing"
                }
            }

            binding.audioProgressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        onAudioSeek(message.id, progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        fun updatePlaybackState(isPlaying: Boolean, progress: Int, duration: String) {
            binding.playPauseButton.apply {
                setImageResource(
                    if (isPlaying) R.drawable.ic_stop else R.drawable.ic_play
                )
                tag = if (isPlaying) "playing" else "paused"
            }
            binding.audioProgressBar.progress = progress
            binding.durationTextView.text = duration
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return DATE_FORMATTER.format(Date(timestamp))
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}