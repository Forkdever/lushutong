package com.example.lushutong

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.llw.newmapdemo.R
import com.example.lushutong.LoginStatusManager  // 确保导入登录状态管理类
import com.example.lushutong.ProfileSideDialogFragment  // 导入侧边栏Fragment
import com.example.lushutong.LoginActivity  // 导入登录Activity

class CommunityActivity : AppCompatActivity(), ProfileSideDialogFragment.OnProfileInteractionListener {
    private lateinit var rvPosts: RecyclerView
    private lateinit var postAdapter: PostAdapter

    // 新增：登录结果回调
    private lateinit var loginResultLauncher: ActivityResultLauncher<Intent>
    // 新增：社区头像ImageView（全局变量）
    private var ivAvatar: ImageView? = null

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

        // 新增：初始化登录状态管理
        LoginStatusManager.init(this)

        // 新增：注册登录结果回调（和主页一致）
        loginResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "登录成功！", Toast.LENGTH_SHORT).show()
                // 登录成功：更新头像
                updateAvatarByLoginStatus()
            }
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
            // 发布帖子逻辑：先检查登录状态
            if (LoginStatusManager.isLoggedIn()) {
                // 已登录，执行发布逻辑
                Toast.makeText(this, "进入发布帖子页面", Toast.LENGTH_SHORT).show()
            } else {
                // 未登录，跳转到登录页
                val intent = Intent(this, LoginActivity::class.java)
                loginResultLauncher.launch(intent)
            }
        }

        // 新增：初始化社区头像ImageView
        ivAvatar = findViewById(R.id.imageView_user_in_Community)
        // 新增：初始加载头像（根据登录状态）
        updateAvatarByLoginStatus()

        // 新增：社区头像点击事件（和主页逻辑一致）
        ivAvatar?.setOnClickListener {
            if (LoginStatusManager.isLoggedIn()) {
                // 已登录，打开侧边栏
                try {
                    val sideDialog = ProfileSideDialogFragment.newInstance()
                    sideDialog.show(supportFragmentManager, "ProfileSideDialog")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                // 未登录，跳转到登录界面
                val intent = Intent(this, LoginActivity::class.java)
                loginResultLauncher.launch(intent)
            }
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

    // 新增：根据登录状态更新头像（和主页完全一致）
    private fun updateAvatarByLoginStatus() {
        ivAvatar?.let { avatar ->
            if (LoginStatusManager.isLoggedIn()) {
                // 已登录：显示bg.png
                avatar.setImageResource(R.drawable.bg)
            } else {
                // 未登录：显示downloaded_image.png
                avatar.setImageResource(R.drawable.downloaded_image)
            }
        }
    }

    // 新增：实现退出登录回调（和主页一致）
    override fun onLogout() {
        Toast.makeText(this, "用户已退出登录", Toast.LENGTH_SHORT).show()
        // 退出登录后更新头像
        updateAvatarByLoginStatus()
    }
}