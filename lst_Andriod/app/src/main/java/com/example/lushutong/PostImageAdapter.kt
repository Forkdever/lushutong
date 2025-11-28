package com.example.lushutong

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import com.llw.newmapdemo.R
class PostImageAdapter(
    context: Context,
    private val imageUrls: List<String>
) : ArrayAdapter<String>(context, 0, imageUrls) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        // 确保布局文件名正确（item_post_image）
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_post_image, parent, false)

        // 确保控件ID正确（iv_post_image）
        val imageView = view.findViewById<ImageView>(R.id.iv_post_image)
        // 这里用系统占位图，实际项目中可替换为图片加载库（如Glide）
        imageView.setImageResource(android.R.drawable.ic_menu_gallery)

        return view
    }
}