package com.example.lushutong

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.llw.newmapdemo.R
import com.example.plan.TravelPlan
import com.example.plan.TravelPlanManager
import com.example.plan.TravelPlanUploader


class CollaborationCodeFragment : BottomSheetDialogFragment() {

    private lateinit var ivBack: ImageView
    private lateinit var etCode: EditText
    private lateinit var btnConfirm: Button
    private lateinit var llContent: LinearLayout


    // 假设Uploader是全局或通过接口获取，这里需根据实际情况初始化

    private val uploader: TravelPlanUploader by lazy {
        TravelPlanUploader(requireContext()) // 传入 Fragment 绑定的 Context
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 弹窗背景透明（避免默认黑色边框）
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return inflater.inflate(R.layout.fragment_collaboration_code, container, false)
    }

    override fun onStart() {
        super.onStart()
        val window: Window? = dialog?.window
        if (window != null) {
            // 1. 底部弹窗，宽度全屏，高度=屏幕1/2（保持不变）
            window.setGravity(android.view.Gravity.BOTTOM)
            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val dialogHeight = screenHeight / 2

            val layoutParams = window.attributes
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.height = dialogHeight
            layoutParams.dimAmount = 0.6f
            window.attributes = layoutParams
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            initViews(view)
            initEvents()
            // 核心：强制弹窗默认完全展开（无需上拉）
            forceExpandBottomSheet()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "初始化失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 强制 BottomSheet 弹窗完全展开
    private fun forceExpandBottomSheet() {
        // 获取弹窗的根视图（即 fragment_collaboration_code.xml 的根 LinearLayout）
        val rootView = view ?: return
        // 获取 BottomSheet 行为对象
        val behavior = BottomSheetBehavior.from(rootView.parent as View)
        // 1. 设置默认状态为「完全展开」
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        // 2. 可选：禁用拖拽调整（避免用户手动折叠，保持始终展开）
        behavior.isDraggable = false
        // 3. 可选：禁用滑动关闭（如果需要）
        behavior.isHideable = false
    }

    // 初始化控件（不变）
    private fun initViews(view: View) {
        ivBack = view.findViewById(R.id.iv_back)
        etCode = view.findViewById(R.id.et_collaboration_code)
        btnConfirm = view.findViewById(R.id.btn_confirm)
        llContent = view.findViewById(R.id.ll_content)
    }

    // 初始化交互事件（不变）
    private fun initEvents() {
        // 1. 外部点击关闭弹窗
        dialog?.apply {
            setCanceledOnTouchOutside(true)
            setCancelable(true)
        }

        // 2. 返回按钮关闭
        ivBack.setOnClickListener {
            dismiss()
        }

        // 输入框去除自动格式化（PlanId无需加"-"，若PlanId有固定格式可保留）
        etCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // 移除原有的"-"格式化逻辑，保留输入原样
            }
        })

        // 确认按钮：校验PlanId有效性
        btnConfirm.setOnClickListener {
            val inputPlanId = etCode.text.toString().trim()
            if (inputPlanId.isEmpty()) {
                Toast.makeText(context, "请输入PlanId", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 调用接口校验PlanId是否有效
            validatePlanId(inputPlanId)
        }
    }

    /**
     * 校验PlanId有效性：调用fetchTravelPlanByPlanId接口
     */
    private fun validatePlanId(planId: String) {
        // 显示加载状态（可选，优化体验）
        btnConfirm.isEnabled = false
        btnConfirm.text = "验证中..."

        uploader.fetchTravelPlanByPlanId(planId, object : TravelPlanUploader.FetchCallback {
            override fun onSuccess(plan: TravelPlan) {
                // PlanId有效：关闭弹窗并回调成功（可通过接口传递plan数据）
                Toast.makeText(context, "PlanId有效：${plan.title}", Toast.LENGTH_SHORT).show()
                // 传递planId到CreateTripActivity
                val intent = Intent(requireContext(), CreateTripActivity::class.java).apply {
                    putExtra("TARGET_PLAN_ID", plan.planId) // 存储目标planId
                }
                startActivity(intent)
                dismiss()
                btnConfirm.isEnabled = true
                btnConfirm.text = "确认"

            }

            override fun onFailure(errorMsg: String) {
                // PlanId无效：提示错误
                Toast.makeText(context, "PlanId无效：$errorMsg", Toast.LENGTH_SHORT).show()
                btnConfirm.isEnabled = true
                btnConfirm.text = "确认"
            }

        })
    }


    /*
    // 3. 输入框自动格式化（4位+"-"）
        etCode.addTextChangedListener(object : TextWatcher {
            private var isEditing = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isEditing) return
                isEditing = true
                val text = s.toString().replace("-", "")
                if (text.length >= 4) {
                    val formatted = text.substring(0, 4) + "-" + text.substring(4)
                    etCode.setText(formatted)
                    etCode.setSelection(formatted.length)
                }
                isEditing = false
            }
        })

        // 4. 确定按钮验证
        btnConfirm.setOnClickListener {
            val code = etCode.text.toString().trim()
            val pattern = Regex("^[A-Z]{4}-\\d{4}$")
            if (pattern.matches(code)) {
                Toast.makeText(context, "协作码验证成功：$code", Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                Toast.makeText(context, "请输入正确格式（例：ABCD-1234）", Toast.LENGTH_SHORT).show()
            }
        }
    }*/

    companion object {
        fun newInstance(): CollaborationCodeFragment {
            return CollaborationCodeFragment()
        }
    }
}