package com.example.lushutong

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.Serializable

// 地点数据类
data class PlaceItem(
    val id: Int,
    val name: String,
    val address: String,
    val rating: String,
    val tag1: String,
    val tag2: String,
    var isSelected: Boolean = false
) : Serializable

// 地点列表适配器
class PlaceAdapter(
    private val items: MutableList<PlaceItem>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<PlaceAdapter.PlaceViewHolder>() {

    inner class PlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_place_name)
        val tvAddress: TextView = itemView.findViewById(R.id.tv_place_address)
        val tvRating: TextView = itemView.findViewById(R.id.tv_place_rating)
        val tvTag1: TextView = itemView.findViewById(R.id.tv_tag1)
        val tvTag2: TextView = itemView.findViewById(R.id.tv_tag2)
        val ivCheck: ImageView = itemView.findViewById(R.id.iv_check)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_place, parent, false)
        return PlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        holder.tvAddress.text = item.address
        holder.tvRating.text = item.rating
        holder.tvTag1.text = item.tag1
        holder.tvTag2.text = item.tag2
        holder.ivCheck.visibility = if (item.isSelected) View.VISIBLE else View.GONE

        // 点击选中
        holder.itemView.setOnClickListener { onItemClick(position) }
    }

    override fun getItemCount(): Int = items.size

    // 获取选中的地点
    fun getSelectedPlaces(): List<PlaceItem> = items.filter { it.isSelected }
}

class SearchPlaceActivity : AppCompatActivity() {
    private lateinit var placeList: MutableList<PlaceItem>
    private lateinit var adapter: PlaceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_place)

        // 初始化控件
        val etSearch = findViewById<EditText>(R.id.et_search)
        val ivSearch = findViewById<ImageView>(R.id.iv_search)
        val rvPlaces = findViewById<RecyclerView>(R.id.rv_places)
        val btnAdd = findViewById<Button>(R.id.btn_add)
        val ivBack = findViewById<ImageView>(R.id.iv_back)

        // 测试数据
        placeList = mutableListOf(
            PlaceItem(1, "故宫博物院", "北京市东城区景山前街4号", "★★★★★ 4.9", "历史古迹", "5A景区"),
            PlaceItem(2, "上海迪士尼乐园", "上海市浦东新区川沙新镇", "★★★★☆ 4.7", "主题乐园", "亲子"),
            PlaceItem(3, "杭州西湖", "杭州市西湖区西湖街道", "★★★★★ 4.8", "自然景观", "免费"),
            PlaceItem(4, "成都大熊猫基地", "成都市成华区熊猫大道1375号", "★★★★☆ 4.7", "动物园", "亲子"),
            PlaceItem(5, "西安兵马俑", "西安市临潼区秦陵北路", "★★★★★ 4.8", "历史古迹", "5A景区")
        )

        // 设置适配器
        adapter = PlaceAdapter(placeList) { position ->
            placeList[position].isSelected = !placeList[position].isSelected
            adapter.notifyItemChanged(position)
        }
        rvPlaces.layoutManager = LinearLayoutManager(this)
        rvPlaces.adapter = adapter

        // 搜索按钮
        ivSearch.setOnClickListener {
            val keyword = etSearch.text.toString().trim()
            Toast.makeText(this, "搜索：$keyword", Toast.LENGTH_SHORT).show()
        }

        // 添加按钮
        btnAdd.setOnClickListener {
            val selected = adapter.getSelectedPlaces()
            if (selected.isEmpty()) {
                Toast.makeText(this, "请选择地点", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Intent().apply {
                putExtra("selected_places", ArrayList(selected))
                setResult(RESULT_OK, this)
            }
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // 返回按钮
        ivBack.setOnClickListener { finish() }
    }
}