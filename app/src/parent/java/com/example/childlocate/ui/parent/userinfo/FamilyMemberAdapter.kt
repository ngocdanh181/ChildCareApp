package com.example.childlocate.ui.parent.userinfo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.childlocate.R
import com.example.childlocate.data.model.FamilyMember

class FamilyMemberAdapter(
    private val onEditClick: (FamilyMember) -> Unit,
    private val onDeleteClick: (FamilyMember) -> Unit,
    private val onAvatarClick: (FamilyMember) -> Unit
) : ListAdapter<FamilyMember, FamilyMemberAdapter.FamilyMemberViewHolder>(FamilyMemberDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FamilyMemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_family_member, parent, false)
        return FamilyMemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: FamilyMemberViewHolder, position: Int) {
        val member = getItem(position)
        holder.bind(member)
    }

    inner class FamilyMemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarImageView: ImageView = itemView.findViewById(R.id.member_avatar)
        private val nameTextView: TextView = itemView.findViewById(R.id.member_name)
        private val roleTextView: TextView = itemView.findViewById(R.id.member_role)
        private val phoneTextView: TextView = itemView.findViewById(R.id.member_phone)
        private val editButton: ImageView = itemView.findViewById(R.id.edit_member_button)
        private val deleteButton: ImageView = itemView.findViewById(R.id.delete_member_button)

        fun bind(member: FamilyMember) {
            nameTextView.text = member.name
            
            // Hiển thị vai trò bằng tiếng Việt
            val roleText = when (member.role) {
                "parent_primary" -> "Phụ huynh chính"
                "parent_secondary" -> "Phụ huynh phụ"
                "child" -> "Trẻ em"
                else -> member.role
            }
            roleTextView.text = roleText
            
            // Hiển thị số điện thoại nếu có
            if (member.phone.isNotEmpty()) {
                phoneTextView.visibility = View.VISIBLE
                phoneTextView.text = member.phone
            } else {
                phoneTextView.visibility = View.GONE
            }

            // Load avatar nếu có
            if (member.avatarUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(member.avatarUrl)
                    .placeholder(R.drawable.baseline_person_2_24)
                    .into(avatarImageView)
            } else {
                avatarImageView.setImageResource(R.drawable.baseline_person_2_24)
            }

            // Ẩn nút xóa nếu là phụ huynh chính
            if (member.role == "parent_primary") {
                deleteButton.visibility = View.GONE
            } else {
                deleteButton.visibility = View.VISIBLE
            }

            // Xử lý sự kiện click
            editButton.setOnClickListener {
                onEditClick(member)
            }

            deleteButton.setOnClickListener {
                onDeleteClick(member)
            }
            
            // Thêm sự kiện click vào avatar
            avatarImageView.setOnClickListener {
                onAvatarClick(member)
            }
        }
    }

    class FamilyMemberDiffCallback : DiffUtil.ItemCallback<FamilyMember>() {
        override fun areItemsTheSame(oldItem: FamilyMember, newItem: FamilyMember): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FamilyMember, newItem: FamilyMember): Boolean {
            return oldItem == newItem
        }
    }
} 