package com.example.lushutong

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager

class PostDetailActivity : AppCompatActivity() {
    private var isLiked = false
    private var isCollected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_detail)

        // 获取帖子数据
        val post = intent.getSerializableExtra("post_data") as Post

        // 绑定帖子数据
        findViewById<TextView>(R.id.tv_detail_username).text = post.username
        findViewById<TextView>(R.id.tv_detail_time_location).text = "${post.publishTime} · ${post.location}"
        findViewById<TextView>(R.id.tv_detail_title).text = post.title
        findViewById<TextView>(R.id.tv_detail_content).text = post.content
        findViewById<TextView>(R.id.tv_detail_like).text = formatNumber(post.likeCount)
        findViewById<TextView>(R.id.tv_detail_comment).text = formatNumber(post.commentCount)
        findViewById<TextView>(R.id.tv_detail_share).text = formatNumber(post.shareCount)

        // 加载图片网格
        val gridView = findViewById<GridView>(R.id.gv_detail_images)
        gridView.adapter = PostImageAdapter(this, post.imageUrls)

        // 初始化评论列表
        initCommentList()

        // 返回按钮
        findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            finish()
        }

        // 底部互动栏点击事件
        // 点赞
        findViewById<View>(R.id.ll_like).setOnClickListener {
            isLiked = !isLiked
            val ivLike = findViewById<ImageView>(R.id.iv_like)
            ivLike.setColorFilter(if (isLiked) Color.RED else Color.GRAY)
            Toast.makeText(this, if (isLiked) "已点赞" else "取消点赞", Toast.LENGTH_SHORT).show()
        }

        // 评论
        findViewById<View>(R.id.ll_comment).setOnClickListener {
            Toast.makeText(this, "打开评论输入框", Toast.LENGTH_SHORT).show()
        }

        // 收藏
        findViewById<View>(R.id.ll_collect).setOnClickListener {
            isCollected = !isCollected
            val ivCollect = findViewById<ImageView>(R.id.iv_collect)
            ivCollect.setColorFilter(if (isCollected) Color.parseColor("#FF9800") else Color.GRAY)
            Toast.makeText(this, if (isCollected) "已收藏" else "取消收藏", Toast.LENGTH_SHORT).show()
        }

        // 分享
        findViewById<View>(R.id.ll_share).setOnClickListener {
            Toast.makeText(this, "分享到微信朋友圈", Toast.LENGTH_SHORT).show()
        }
    }

    // 初始化评论列表
    private fun initCommentList() {
        val rvComments = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_comments)
        rvComments.layoutManager = LinearLayoutManager(this)
        rvComments.adapter = CommentAdapter(getMockComments())
    }

    // 模拟评论数据
    private fun getMockComments(): List<Comment> {
        return listOf(
            Comment(
                userId = 2001,
                username = "用户123",
                userAvatar = "avatar2001",
                content = "太实用了！下个月也计划去京都，请问町屋民宿具体怎么预订？",
                time = "1小时前",
                likeCount = 28
            ),
            Comment(
                userId = 2002,
                username = "旅行爱好者",
                userAvatar = "avatar2002",
                content = "岚山竹林一定要早上去，人少拍照好看！我上次下午去全是人从众",
                time = "2小时前",
                likeCount = 15
            ),
            Comment(
                userId = 2003,
                username = "吃货一枚",
                userAvatar = "avatar2003",
                content = "锦市场的章鱼小丸子和抹茶冰淇淋强烈推荐！",
                time = "3小时前",
                likeCount = 36
            )
        )
    }

    // 数字格式化
    private fun formatNumber(num: Int): String {
        return when {
            num >= 1000 -> "${num / 1000}k"
            else -> num.toString()
        }
    }
}