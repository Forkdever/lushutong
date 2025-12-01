package com.example.lushutong

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.llw.newmapdemo.R
import android.content.Intent
// 左侧个人信息弹窗 Fragment
class ProfileSideDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 隐藏弹窗默认标题栏
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        // 加载左侧弹窗布局
        val view = inflater.inflate(R.layout.fragment_profile_side, container, false)

        // 设置点击事件
        setupClickListeners(view)

        return view
    }

    private fun setupClickListeners(view: View) {
        // 我的旅行计划
        view.findViewById<View>(R.id.ll_my_trips).setOnClickListener {
            // 1. 关闭当前弹窗
            dismiss()
            // 2. 启动新Activity
            val intent = Intent(requireContext(), MyTripsActivity::class.java)
            startActivity(intent)
        }

        // 已发布的旅行日志
        view.findViewById<View>(R.id.ll_published_logs).setOnClickListener {
            Toast.makeText(requireContext(), "已发布的旅行日志", Toast.LENGTH_SHORT).show()
        }

        // 未发布的旅行日志
        view.findViewById<View>(R.id.ll_unpublished_logs).setOnClickListener {
            Toast.makeText(requireContext(), "未发布的旅行日志", Toast.LENGTH_SHORT).show()
        }

        // 退出登录
        view.findViewById<TextView>(R.id.tv_logout).setOnClickListener {
            // 清除登录状态（调用LoginStatusManager中定义的logout方法）
            LoginStatusManager.logout()
            Toast.makeText(requireContext(), "已退出登录", Toast.LENGTH_SHORT).show()
            dismiss()

            // 通知Activity更新状态
            (activity as? OnProfileInteractionListener)?.onLogout()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.apply {
            window?.setGravity(Gravity.START)

            // 兼容低版本的屏幕宽度获取
            val displayMetrics = requireContext().resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val dialogWidth = (screenWidth * 0.7).toInt()

            window?.setLayout(dialogWidth, WindowManager.LayoutParams.MATCH_PARENT)
            window?.setDimAmount(0.5f)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    // 外部快速创建实例
    companion object {
        fun newInstance(): ProfileSideDialogFragment {
            return ProfileSideDialogFragment()
        }
    }

    // 定义交互接口，用于和Activity通信
    interface OnProfileInteractionListener {
        fun onLogout()
    }
}