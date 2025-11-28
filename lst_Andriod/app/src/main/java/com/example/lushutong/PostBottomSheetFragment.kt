package com.example.lushutong // 必须和你的包名一致，不能改

// 导入必要的依赖类（缺少会爆红，鼠标悬停爆红处按 Alt+Enter 自动导入）
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.llw.newmapdemo.R
// 类继承 BottomSheetDialogFragment（核心，实现部分覆盖弹窗）
class PostBottomSheetFragment : BottomSheetDialogFragment() {

    // 第一步：加载弹窗布局（关联之前创建的 fragment_post_bottom_sheet.xml）
    override fun onCreateView(
        inflater: LayoutInflater, // 布局加载器，用于加载 XML 布局
        container: ViewGroup?, // 父容器，固定传 null 即可
        savedInstanceState: Bundle? // 保存状态，无需处理
    ): View? {
        // 关键：加载弹窗布局（参数1=布局文件，参数2=container，参数3=false）
        return inflater.inflate(R.layout.fragment_post_bottom_sheet, container, false)
    }

    // 第二步：布局加载完成后，初始化控件（输入框、发布按钮）和点击事件
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 找到布局中的控件（通过 view.findViewById，因为 Fragment 没有 findViewById 方法）
        val etPostContent: EditText = view.findViewById(R.id.et_post_content) // 发帖输入框
        val btnPublish: Button = view.findViewById(R.id.btn_publish) // 发布按钮

        // 2. 给发布按钮设置点击事件
        btnPublish.setOnClickListener {
            // 获取输入框内容（trim() 去除前后空格）
            val postContent = etPostContent.text.toString().trim()

            // 3. 简单校验：输入为空则提示，不为空则模拟发布
            if (postContent.isEmpty()) {
                // 弹出提示：请输入内容（context 是 Fragment 的上下文，不用改）
                Toast.makeText(context, "请输入发帖内容~", Toast.LENGTH_SHORT).show()
            } else {
                // 模拟发布成功（后续可替换为上传服务器的逻辑）
                Toast.makeText(context, "发布成功！内容：$postContent", Toast.LENGTH_SHORT).show()
                dismiss() // 发布后关闭弹窗（返回上一页）
            }
        }

        // 第三步：配置弹窗样式（可选，控制高度、背景透明度）
        dialog?.apply { // dialog 是 BottomSheetDialogFragment 自带的弹窗对象
            // 设置弹窗宽度=全屏，高度=400dp（可修改数值调整覆盖范围）
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, dp2px(400))
            // 设置背景半透明（0-1 之间，0=完全透明，1=完全不透明，0.5 最适中）
            window?.setDimAmount(0.5f)
        }
    }

    // 工具方法：dp 转 px（适配不同屏幕，避免弹窗高度在不同手机上显示不一致）
    private fun dp2px(dp: Int): Int {
        // 获取屏幕密度，乘以 dp 数值，四舍五入转为 px
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }

    //  companion object：方便外部（比如 MainActivity）快速创建 Fragment 实例
    companion object {
        // 外部调用 PostBottomSheetFragment.newInstance() 即可创建实例
        fun newInstance(): PostBottomSheetFragment {
            return PostBottomSheetFragment()
        }
    }
}