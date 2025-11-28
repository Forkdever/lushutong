package com.example.lushutong

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    private lateinit var etPhone: EditText
    private lateinit var etVerifyCode: EditText
    private lateinit var btnGetCode: Button
    private lateinit var btnLogin: Button
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 初始化登录状态管理
        LoginStatusManager.init(this)

        // 初始化控件
        initViews()

        // 设置监听
        setListeners()
    }

    private fun initViews() {
        etPhone = findViewById(R.id.et_phone)
        etVerifyCode = findViewById(R.id.et_verify_code)
        btnLogin = findViewById(R.id.btn_login)
        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_forgot_password).setOnClickListener {
            Toast.makeText(this, "忘记密码功能", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setListeners() {
        // 输入监听（控制登录按钮状态）
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                checkLoginButtonStatus()
            }
        }

        etPhone.addTextChangedListener(textWatcher)
        etVerifyCode.addTextChangedListener(textWatcher)


        // 登录按钮点击
        btnLogin.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            val code = etVerifyCode.text.toString().trim()

            if (code != "123456") { // 模拟验证码验证
                Toast.makeText(this, "验证码错误", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 保存登录状态
            LoginStatusManager.saveLoginStatus(phone, "用户$phone")
            Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()

            // 返回上一页并刷新状态
            setResult(RESULT_OK)
            finish()
        }
    }

    // 检查登录按钮状态
    private fun checkLoginButtonStatus() {
        val phone = etPhone.text.toString().trim()
        val code = etVerifyCode.text.toString().trim()
        val isEnabled = phone.length == 11 && code.isNotEmpty()

        btnLogin.isEnabled = isEnabled
        // 修复：直接设置颜色而不是引用drawable
        btnLogin.setBackgroundColor(if (isEnabled) {
            resources.getColor(android.R.color.holo_blue_light, theme)
        } else {
            resources.getColor(android.R.color.darker_gray, theme)
        })
    }


    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}