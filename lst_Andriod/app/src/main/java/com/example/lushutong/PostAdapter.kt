package com.example.lushutong
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.llw.newmapdemo.R
class PostAdapter(private val postList: List<Post>) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.iv_post_avatar)
        val tvUsername: TextView = itemView.findViewById(R.id.tv_post_username)
        val tvTime: TextView = itemView.findViewById(R.id.tv_post_time)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_post_title)
        val tvContent: TextView = itemView.findViewById(R.id.tv_post_content)
        val tvLike: TextView = itemView.findViewById(R.id.tv_post_like)
        val tvComment: TextView = itemView.findViewById(R.id.tv_post_comment)
        val tvCollect: TextView = itemView.findViewById(R.id.tv_post_collect)
        val tvShare: TextView = itemView.findViewById(R.id.tv_post_share)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = postList[position]
        holder.tvUsername.text = post.username
        holder.tvTime.text = post.publishTime
        holder.tvTitle.text = post.title
        holder.tvContent.text = post.content
        holder.tvLike.text = formatNumber(post.likeCount)
        holder.tvComment.text = formatNumber(post.commentCount)
        holder.tvCollect.text = formatNumber(post.collectCount)
        holder.tvShare.text = formatNumber(post.shareCount)

        // 点击跳转详情页
        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, PostDetailActivity::class.java)
            intent.putExtra("post_data", post)
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = postList.size

    private fun formatNumber(num: Int): String {
        return when {
            num >= 1000 -> "${num / 1000}k"
            else -> num.toString()
        }
    }
}