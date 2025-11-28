package com.example.lushutong

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CommentAdapter(private val commentList: List<Comment>) :
    RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivCommentAvatar: ImageView = itemView.findViewById(R.id.iv_comment_avatar)
        val tvCommentUsername: TextView = itemView.findViewById(R.id.tv_comment_username)
        val tvCommentTime: TextView = itemView.findViewById(R.id.tv_comment_time)
        val tvCommentContent: TextView = itemView.findViewById(R.id.tv_comment_content)
        val tvCommentLike: TextView = itemView.findViewById(R.id.tv_comment_like)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = commentList[position]
        holder.tvCommentUsername.text = comment.username
        holder.tvCommentTime.text = comment.time
        holder.tvCommentContent.text = comment.content
        holder.tvCommentLike.text = comment.likeCount.toString()
    }

    override fun getItemCount(): Int = commentList.size
}