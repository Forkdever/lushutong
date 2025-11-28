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
            Toast.makeText(requireContext(), "我的旅行计划", Toast.LENGTH_SHORT).show()
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
            // 清除登录状态
            LoginStatusManager.clearLoginStatus()
            Toast.makeText(requireContext(), "已退出登录", Toast.LENGTH_SHORT).show()
            dismiss()

            // 通知Activity更新状态
            (activity as? OnProfileInteractionListener)?.onLogout()
        }
    }

    override fun onStart() {
        super.onStart()
        // 关键：配置弹窗样式（位置、宽度、背景透明度）
        dialog?.apply {
            // 1. 设置弹窗位置：左侧对齐
            window?.setGravity(Gravity.START)
            // 2. 设置弹窗宽度=屏幕宽度的70%，高度=全屏
            val windowManager = requireActivity().windowManager
            val screenWidth = windowManager.currentWindowMetrics.bounds.width()
            val dialogWidth = (screenWidth * 0.7).toInt() // 70% 屏幕宽度（可调整比例）
            window?.setLayout(dialogWidth, WindowManager.LayoutParams.MATCH_PARENT)
            // 3. 设置背景半透明（右侧可看到主界面，点击右侧关闭）
            window?.setDimAmount(0.5f) // 0-1 之间，0=完全透明，1=完全不透明
            // 4. 去掉弹窗默认边框和阴影（可选，更美观）
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