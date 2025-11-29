package com.example.lushutong

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.login_test.LoginController
import com.llw.newmapdemo.R

class LoginActivity : AppCompatActivity() {
    private lateinit var loginController: LoginController // 声明控制器

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 初始化登录状态管理（保留原有逻辑）
        LoginStatusManager.init(this)

        // 初始化LoginController（核心：传入Activity上下文）
        loginController = LoginController(this)
        // 调用控制器初始化方法（绑定UI+设置监听+启动自动刷新）
        loginController.setup()

        // 保留返回按钮逻辑（点击直接返回上一页）
        findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.tv_forgot_password).setOnClickListener {
            Toast.makeText(this, "忘记密码功能", Toast.LENGTH_SHORT).show()
        }
    }

    // 重写onDestroy，释放控制器资源（避免内存泄漏）
    override fun onDestroy() {
        super.onDestroy()
        loginController.onDestroy()
    }
}