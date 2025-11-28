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
import com.llw.newmapdemo.R

import com.amap.api.maps.MapView
import com.amap.api.services.core.PoiItem
import com.llw.newmapdemo.TravelMapController
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



class SearchPlaceActivity : AppCompatActivity(),
    TravelMapController.OnPoiSearchResultListener{
    private lateinit var placeList: MutableList<PlaceItem>
    private lateinit var adapter: PlaceAdapter

    // 新增：
    private lateinit var mapView: MapView
    private lateinit var mapController: TravelMapController
    private val poiList: MutableList<PoiItem> = mutableListOf()   // 保存真实的 POI 数据


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_place)

        // 1. 初始化控件
        val etSearch = findViewById<EditText>(R.id.et_search)
        val ivSearch = findViewById<ImageView>(R.id.iv_search)
        val rvPlaces = findViewById<RecyclerView>(R.id.rv_places)
        val btnAdd = findViewById<Button>(R.id.btn_add)
        val ivBack = findViewById<ImageView>(R.id.iv_back)

        // 新增：MapView & Controller
        mapView = findViewById(R.id.mapView)
        mapController = TravelMapController(this, mapView)
        mapController.setOnPoiSearchResultListener(this)   // 注册搜索结果回调
        mapController.onCreate(savedInstanceState)         // 把生命周期传进去

        // 暂时不再使用“测试数据”，这里先给一个空列表
        placeList = mutableListOf()

        adapter = PlaceAdapter(placeList) { position ->
            placeList[position].isSelected = !placeList[position].isSelected
            adapter.notifyItemChanged(position)
        }
        rvPlaces.layoutManager = LinearLayoutManager(this)
        rvPlaces.adapter = adapter

        // 搜索按钮
        ivSearch.setOnClickListener {
            val keyword = etSearch.text.toString().trim()
            if (keyword.isEmpty()) {
                Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show()
            } else {
                // 使用 TravelMapController 做 POI 搜索（城市先传 null，用当前定位城市）
                mapController.searchPoi(keyword, null)
            }
        }

        // 添加按钮
        btnAdd.setOnClickListener {
            val selectedIndexList = placeList
                .mapIndexedNotNull { index, item -> if (item.isSelected) index else null }

            if (selectedIndexList.isEmpty()) {
                Toast.makeText(this, "请选择地点", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 1. 把选中的 POI 加到地图上（作为景点）
            selectedIndexList.forEach { idx ->
                val poi = poiList[idx]
                mapController.addScenicFromPoi(poi)
            }

            // 2. 同时把选中的 POI 回传给上一个页面（用 PoiItem，里面自带经纬度）
            val selectedPois = ArrayList<PoiItem>()
            selectedIndexList.forEach { idx ->
                selectedPois.add(poiList[idx])
            }

            Intent().apply {
                putParcelableArrayListExtra("selected_pois", selectedPois)
                setResult(RESULT_OK, this)
            }

            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // 返回按钮
        ivBack.setOnClickListener { finish() }
    }

    override fun onPoiResult(poiItems: List<PoiItem>) {
        // 这个回调在 TravelMapController.onPoiSearched 里调用
        // 切回主线程更新 UI
        runOnUiThread {
            if (poiItems.isEmpty()) {
                Toast.makeText(this, "未找到相关地点", Toast.LENGTH_SHORT).show()
                placeList.clear()
                poiList.clear()
                adapter.notifyDataSetChanged()
                return@runOnUiThread
            }

            // 保存真实 POI 数据
            poiList.clear()
            poiList.addAll(poiItems)

            // 把 POI 转成列表展示用的 PlaceItem
            placeList.clear()
            poiItems.forEachIndexed { index, poi ->
                placeList.add(
                    PlaceItem(
                        id = index,
                        name = poi.title ?: "未知地点",
                        address = poi.snippet ?: "",
                        rating = "",                      // 这里你可以根据 poi 的别的字段填充评分
                        tag1 = poi.typeDes ?: "",         // POI 类型描述
                        tag2 = poi.cityName ?: "",        // 城市名等
                        isSelected = false
                    )
                )
            }

            adapter.notifyDataSetChanged()
        }
    }
    override fun onResume() {
        super.onResume()
        mapController.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapController.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapController.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapController.onSaveInstanceState(outState)
    }



}