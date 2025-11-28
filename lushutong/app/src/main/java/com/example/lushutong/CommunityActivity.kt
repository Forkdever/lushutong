package com.example.lushutong

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class CommunityActivity : AppCompatActivity() {
    private lateinit var rvPosts: RecyclerView
    private lateinit var postAdapter: PostAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_community)

        // 系统栏适配
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 初始化帖子列表
        initPostList()

        // 底部主页按钮跳转
        findViewById<Button>(R.id.button_Main_in_Community).setOnClickListener {
            val intent = Intent(this@CommunityActivity, MainActivity::class.java)
            startActivity(intent)
        }

        // 悬浮按钮点击事件
        val fabPost: FloatingActionButton = findViewById(R.id.fab_post)
        fabPost.setOnClickListener {
            // 发布帖子逻辑
        }

        // 用户头像点击事件
        val ivAvatar: ImageView = findViewById(R.id.imageView_user_in_Community)
        ivAvatar.setOnClickListener {
            // 个人中心逻辑
        }
    }

    private fun initPostList() {
        rvPosts = findViewById(R.id.rv_posts)
        rvPosts.layoutManager = LinearLayoutManager(this@CommunityActivity)
        val mockPostList = getMockPosts()
        postAdapter = PostAdapter(mockPostList)
        rvPosts.adapter = postAdapter
    }

    private fun getMockPosts(): List<Post> {
        return listOf(
            Post(
                id = 1,
                userId = 1001,
                username = "旅行达人小张",
                userAvatar = "avatar1",
                publishTime = "3小时前",
                location = "日本东京",
                title = "京都3日文化之旅",
                content = "刚刚结束京都之旅，分享我的3日文化深度游！第一天：清水寺感受千年古刹魅力，漫步二年坂三年坂，品尝抹茶甜点；第二天：伏见稻荷大社探索千本鸟居，岚山竹林小径散步，体验和服租赁拍照；第三天：金阁寺欣赏金碧辉煌，锦市场品尝地道小吃，晚上祇园欣赏传统艺妓表演。",
                imageUrls = listOf("url1", "url2", "url3", "url4"),
                likeCount = 1200,
                commentCount = 328,
                collectCount = 500,
                shareCount = 156
            ),
            Post(
                id = 2,
                userId = 1002,
                username = "背包客小李",
                userAvatar = "avatar2",
                publishTime = "1天前",
                location = "青海西宁",
                title = "青海湖环湖骑行全攻略",
                content = "青海湖环湖骑行真的太治愈了！全程约360公里，建议分4天完成：Day1西宁→塔尔寺→青海湖；Day2青海湖→茶卡盐湖；Day3茶卡盐湖→黑马河；Day4黑马河→西宁。沿途风景绝美，一定要注意防晒和高反！",
                imageUrls = listOf("url5", "url6", "url7"),
                likeCount = 856,
                commentCount = 215,
                collectCount = 420,
                shareCount = 98
            ),
            Post(
                id = 3,
                userId = 1003,
                username = "美食探店王",
                userAvatar = "avatar3",
                publishTime = "2天前",
                location = "四川成都",
                title = "成都必吃的10家地道小吃店",
                content = "作为一个在成都待了5年的吃货，整理出这份私藏小吃清单：1. 陈婆婆珍藏抄手；2. 洞子口张老二凉粉；3. 小名堂担担甜水面；4. 叶婆婆钵钵鸡；5. 严太婆锅盔；6. 甘记肥肠粉；7. 王记特色锅盔；8. 贺记蛋烘糕；9. 盘飧市卤味；10. 廖老妈蹄花。",
                imageUrls = listOf("url8", "url9"),
                likeCount = 2100,
                commentCount = 560,
                collectCount = 1800,
                shareCount = 320
            )
        )
    }
}