package com.example.lushutong

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

// 待安排地点数据类
data class PendingPlace(
    val name: String,
    val address: String,
    val rating: String,
    val tag1: String,
    val tag2: String
)

// 待安排地点适配器
class PendingPlaceAdapter(
    private val placeList: MutableList<PendingPlace>,
    private val dragListener: (View) -> Unit
) : RecyclerView.Adapter<PendingPlaceAdapter.PendingPlaceViewHolder>() {

    inner class PendingPlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_place_name)
        val tvAddress: TextView = itemView.findViewById(R.id.tv_place_address)
        val tvRating: TextView = itemView.findViewById(R.id.tv_place_rating)
        val tvTag1: TextView = itemView.findViewById(R.id.tv_tag1)
        val tvTag2: TextView = itemView.findViewById(R.id.tv_tag2)
        val ivDrag: ImageView = itemView.findViewById(R.id.iv_drag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PendingPlaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_place, parent, false)
        return PendingPlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PendingPlaceViewHolder, position: Int) {
        val place = placeList[position]
        holder.tvName.text = place.name
        holder.tvAddress.text = place.address
        holder.tvRating.text = place.rating
        holder.tvTag1.text = place.tag1
        holder.tvTag2.text = place.tag2

        // 拖动图标触摸事件
        holder.ivDrag.setOnTouchListener { _, _ ->
            dragListener(holder.ivDrag)
            false
        }
    }

    override fun getItemCount(): Int = placeList.size

    // 交换列表项（拖动排序）
    fun onItemMove(fromPosition: Int, toPosition: Int) {
        placeList.swap(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }

    // 扩展函数：交换列表元素
    private fun <T> MutableList<T>.swap(index1: Int, index2: Int) {
        val temp = this[index1]
        this[index1] = this[index2]
        this[index2] = temp
    }
}

class CreateTripActivity : AppCompatActivity() {
    // 控件声明
    private lateinit var etTripName: EditText
    private lateinit var etTripDate: EditText
    private lateinit var hsTabs: HorizontalScrollView
    private lateinit var scrollableTabsContainer: LinearLayout
    private lateinit var itineraryContainer: LinearLayout
    private lateinit var itineraryPendingHint: LinearLayout
    private lateinit var rvPendingPlaces: RecyclerView // 正确声明
    private var dayCount = 1

    // 待安排地点数据源
    private val pendingPlaceList = mutableListOf<PendingPlace>()
    private lateinit var pendingAdapter: PendingPlaceAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    // DP转PX
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_trip)
        initViews()
        initPendingRecyclerView()
        initEvents()
    }

    private fun initViews() {
        etTripName = findViewById(R.id.et_trip_name)
        etTripDate = findViewById(R.id.et_trip_date)
        hsTabs = findViewById(R.id.hs_tabs)
        scrollableTabsContainer = findViewById(R.id.scrollable_tabs_container)
        itineraryContainer = findViewById(R.id.itinerary_container)
        itineraryPendingHint = findViewById(R.id.itinerary_pending_hint)
        rvPendingPlaces = findViewById(R.id.rv_pending_places) // 正确初始化

        // 初始选中总览
        selectTab(R.id.tab_overview)
    }

    // 初始化待安排RecyclerView（拖动排序）
    private fun initPendingRecyclerView() {
        pendingAdapter = PendingPlaceAdapter(pendingPlaceList) { view ->
            itemTouchHelper.startDrag(rvPendingPlaces.getChildViewHolder(view.parent as View))
        }

        rvPendingPlaces.layoutManager = LinearLayoutManager(this)
        rvPendingPlaces.adapter = pendingAdapter

        // ItemTouchHelper配置
        val touchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                pendingAdapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
                updateOverviewPending() // 同步总览
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        }

        itemTouchHelper = ItemTouchHelper(touchHelperCallback)
        itemTouchHelper.attachToRecyclerView(rvPendingPlaces)
    }

    private fun initEvents() {
        // 返回按钮
        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }

        // 日期选择
        etTripDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    etTripDate.setText("$year-${month + 1}-$day")
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // 添加天数
        findViewById<ImageView>(R.id.iv_add_day).setOnClickListener {
            if (it.isClickable) {
                it.isClickable = false
                addNewDay()
                it.postDelayed({ it.isClickable = true }, 500)
            }
        }

        // 添加行程项（跳转到搜索页面）
        findViewById<ImageView>(R.id.iv_add_item).setOnClickListener {
            startActivityForResult(
                Intent(this, SearchPlaceActivity::class.java),
                1001
            )
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // 标签点击事件
        setupInitialTabClicks()
    }

    // 接收搜索页面返回的地点
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val selectedPlaces = data?.getSerializableExtra("selected_places") as? List<PlaceItem>
            selectedPlaces?.forEach { place ->
                pendingPlaceList.add(
                    PendingPlace(
                        name = place.name,
                        address = place.address,
                        rating = place.rating,
                        tag1 = place.tag1,
                        tag2 = place.tag2
                    )
                )
            }
            pendingAdapter.notifyDataSetChanged()
            updateOverviewPending()
            selectTab(R.id.tab_pending)
            Toast.makeText(this, "已添加${pendingPlaceList.size}个地点", Toast.LENGTH_SHORT).show()
        }
    }

    // 更新总览中的待安排显示
    private fun updateOverviewPending() {
        // 移除原有待安排汇总项
        val overviewPendingId = "overview_pending"
        for (i in 0 until itineraryContainer.childCount) {
            val child = itineraryContainer.getChildAt(i)
            if (child.tag == overviewPendingId) {
                itineraryContainer.removeView(child)
                break
            }
        }

        // 添加新的汇总项
        if (pendingPlaceList.isNotEmpty()) {
            val overviewLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dpToPx(8) }
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@CreateTripActivity, R.drawable.bg_gray) // 正确引用
                setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                tag = overviewPendingId
            }

            // 标题
            overviewLayout.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                text = "待安排地点："
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black)) // 修复除法错误
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            // 地点列表
            pendingPlaceList.forEachIndexed { index, place ->
                overviewLayout.addView(TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dpToPx(4) }
                    text = "${index + 1}. ${place.name}（${place.tag1}/${place.tag2}）"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black)) // 修复除法错误
                })
            }

            itineraryContainer.addView(overviewLayout)
        }
    }

    // 添加新天数
    private fun addNewDay() {
        dayCount++

        // 创建天数标签
        val dayTab = TextView(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(36)
            ).apply { marginStart = dpToPx(8) }
            text = "第${dayCount}天"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black)) // 修复除法错误
            setBackgroundResource(R.drawable.bg_tab_unselected)
            setPadding(dpToPx(16), 0, dpToPx(16), 0)
            gravity = Gravity.CENTER
            isClickable = true
        }

        // 插入到待安排标签前
        scrollableTabsContainer.addView(dayTab, scrollableTabsContainer.childCount - 1)

        // 标签点击事件
        dayTab.setOnClickListener { selectTab(dayTab.id) }

        // 创建行程项
        val itineraryItem = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(8) }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@CreateTripActivity, R.drawable.bg_gray) // 正确引用
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            tag = dayTab.id.toString()
        }

        itineraryItem.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = "第${dayCount}天: "
            setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black)) // 修复除法错误
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        itineraryItem.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = "待安排行程"
            setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.text_gray)) // 修复除法错误
            textSize = 14f
        })

        itineraryContainer.addView(itineraryItem)
        selectTab(dayTab.id)
        scrollToTab(dayTab)
        Toast.makeText(this, "已添加第${dayCount}天", Toast.LENGTH_SHORT).show()
    }

    // 标签切换逻辑
    private fun selectTab(selectedTabId: Int) {
        // 重置所有标签样式
        findViewById<TextView>(R.id.tab_overview).apply {
            setBackgroundResource(R.drawable.bg_tab_unselected)
            setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black)) // 修复除法错误
        }

        for (i in 0 until scrollableTabsContainer.childCount) {
            (scrollableTabsContainer.getChildAt(i) as? TextView)?.apply {
                setBackgroundResource(R.drawable.bg_tab_unselected)
                setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black)) // 修复除法错误
            }
        }

        // 设置选中标签样式
        findViewById<TextView>(selectedTabId).apply {
            setBackgroundResource(R.drawable.bg_tab_selected)
            setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.white)) // 修复除法错误
        }

        // 内容显示控制
        when (selectedTabId) {
            R.id.tab_overview -> {
                showAllItineraries()
                itineraryPendingHint.visibility = View.GONE
                rvPendingPlaces.visibility = View.GONE
            }
            R.id.tab_pending -> {
                hideAllItineraries()
                if (pendingPlaceList.isEmpty()) {
                    itineraryPendingHint.visibility = View.VISIBLE
                    rvPendingPlaces.visibility = View.GONE
                } else {
                    itineraryPendingHint.visibility = View.GONE
                    rvPendingPlaces.visibility = View.VISIBLE
                }
            }
            else -> {
                showTargetItinerary(selectedTabId.toString())
                itineraryPendingHint.visibility = View.GONE
                rvPendingPlaces.visibility = View.GONE
            }
        }
    }

    // 显示所有行程
    private fun showAllItineraries() {
        for (i in 0 until itineraryContainer.childCount) {
            val child = itineraryContainer.getChildAt(i)
            if (child.id != R.id.itinerary_pending_hint && child.id != R.id.rv_pending_places) {
                child.visibility = View.VISIBLE
            }
        }
    }

    // 隐藏所有行程
    private fun hideAllItineraries() {
        for (i in 0 until itineraryContainer.childCount) {
            val child = itineraryContainer.getChildAt(i)
            if (child.id != R.id.itinerary_pending_hint && child.id != R.id.rv_pending_places) {
                child.visibility = View.GONE
            }
        }
    }

    // 显示指定天数行程
    private fun showTargetItinerary(targetTabId: String) {
        for (i in 0 until itineraryContainer.childCount) {
            val child = itineraryContainer.getChildAt(i)
            if (child.id == R.id.itinerary_pending_hint || child.id == R.id.rv_pending_places) continue
            child.visibility = if (child.tag == targetTabId) View.VISIBLE else View.GONE
        }
    }

    // 滚动到指定标签
    private fun scrollToTab(tab: View) {
        hsTabs.scrollTo(tab.left - dpToPx(16), 0)
    }

    // 初始化标签点击事件
    private fun setupInitialTabClicks() {
        findViewById<TextView>(R.id.tab_overview).setOnClickListener { selectTab(R.id.tab_overview) }
        findViewById<TextView>(R.id.tab_day_1).setOnClickListener { selectTab(R.id.tab_day_1) }
        findViewById<TextView>(R.id.tab_pending).setOnClickListener { selectTab(R.id.tab_pending) }
    }
}