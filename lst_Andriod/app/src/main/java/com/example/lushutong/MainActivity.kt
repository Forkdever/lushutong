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
import com.llw.newmapdemo.R
class MainActivity : AppCompatActivity(), ProfileSideDialogFragment.OnProfileInteractionListener {
    // 登录结果回调
    private lateinit var loginResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 初始化登录状态管理
        LoginStatusManager.init(this)

        // 注册登录结果回调
        loginResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "登录成功！", Toast.LENGTH_SHORT).show()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 社区按钮点击
        findViewById<Button>(R.id.button_Community_in_Main)?.setOnClickListener {
            val intent = Intent(this, CommunityActivity::class.java)
            startActivity(intent)
        }

        // 创建行程按钮点击
        findViewById<Button>(R.id.button_CreateItinerary)?.setOnClickListener {
            val intent = Intent(this, TravelActivity::class.java)
            startActivity(intent)
        }

        // 头像点击逻辑
        val ivAvatar: ImageView? = findViewById(R.id.imageView_user_in_Main)
        ivAvatar?.setOnClickListener {

            // 检查登录状态
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

        // 一起加入按钮点击
        val btnJoinTogether: Button? = findViewById(R.id.button_JoinTogether)
        btnJoinTogether?.setOnClickListener {
            try {
                val collaborationDialog = CollaborationCodeFragment.newInstance()
                collaborationDialog.show(supportFragmentManager, "CollaborationCodeBottomSheet")
            } catch (e: Exception) {
                Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    // 实现退出登录回调
    override fun onLogout() {
        Toast.makeText(this, "用户已退出登录", Toast.LENGTH_SHORT).show()
    }
}