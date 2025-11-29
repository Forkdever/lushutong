package com.example.lushutong

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.maps.MapView
import com.amap.api.services.core.PoiItem
import com.llw.newmapdemo.R
import com.example.lushutong.SearchPlaceActivity
import com.llw.newmapdemo.TravelMapController
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.*

// 待安排地点数据类
data class PendingPlace(
    val name: String,
    val address: String,
    val rating: String,
    val tag1: String,
    val tag2: String,
    var id: String = UUID.randomUUID().toString() // 增加唯一ID用于识别
)

// 每日行程数据类
data class DayItinerary(
    val dayNumber: Int,
    val tabId: Int,
    val places: MutableList<PendingPlace> = mutableListOf()
)

// 待安排地点适配器
class PendingPlaceAdapter(
    private val placeList: MutableList<PendingPlace>,
    private val currentDay: Int,
    private val dragListener: (View) -> Unit,
    private val onOrderChangedListener: () -> Unit,
    private val onDeleteListener: (PendingPlace) -> Unit
) : RecyclerView.Adapter<PendingPlaceAdapter.PendingPlaceViewHolder>() {

    inner class PendingPlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_place_name)
        val tvAddress: TextView = itemView.findViewById(R.id.tv_place_address)
        val tvRating: TextView = itemView.findViewById(R.id.tv_place_rating)
        val tvTag1: TextView = itemView.findViewById(R.id.tv_tag1)
        val tvTag2: TextView = itemView.findViewById(R.id.tv_tag2)
        val ivDrag: ImageView = itemView.findViewById(R.id.iv_drag)
        val ivDelete: ImageView = itemView.findViewById(R.id.iv_delete)
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

        // 删除按钮点击事件
        holder.ivDelete.setOnClickListener {
            onDeleteListener(place)
            placeList.removeAt(position)
            notifyItemRemoved(position)
            onOrderChangedListener()
        }
    }

    override fun getItemCount(): Int = placeList.size

    // 交换列表项（拖动排序）
    fun onItemMove(fromPosition: Int, toPosition: Int) {
        placeList.swap(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        onOrderChangedListener()
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
    private lateinit var rvPlaces: RecyclerView
    private var dayCount = 1

    // 滑动相关变量
    private var startY = 0f
    private val SWIPE_THRESHOLD = 100 // 滑动阈值

    // 数据相关
    private val dayItineraries = mutableListOf<DayItinerary>()
    private var currentDayIndex = 0
    private val pendingTagId = View.generateViewId() // 待安排标签ID

    // 地图相关
    private lateinit var mapView: MapView
    private lateinit var mapController: TravelMapController
    private lateinit var searchPlaceLauncher: ActivityResultLauncher<Intent>

    // 适配器和拖动相关
    private lateinit var placeAdapter: PendingPlaceAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    // 上传相关
    private lateinit var planUploader: TravelPlanUploader
    private var currentPlanId: String = "TP${System.currentTimeMillis()}"
    private val currentUserId = 10001 // 实际应从登录系统获取
    private val sdf = SimpleDateFormat("yyyyyyyy-MM-dd", Locale.getDefault())

    // 防抖处理
    private val updateHandler = Handler(Looper.getMainLooper())
    private val DEBOUNCE_DELAY = 500L

    // DP转PX
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_trip)
        initViews()
        mapController = TravelMapController(this, mapView)
        mapController.onCreate(savedInstanceState)
        initUploader()
        initDayItineraries()
        initPlacesRecyclerView()
        initEvents()
        initTextWatchers()
        setupSwipeToChangeDay()
    }

    // 初始化每日行程数据
    private fun initDayItineraries() {
        // 添加第一天
        val day1TabId = R.id.tab_day_1
        dayItineraries.add(DayItinerary(1, day1TabId))

        // 添加待安排
        dayItineraries.add(DayItinerary(0, pendingTagId))
    }

    // 初始化上传工具
    private fun initUploader() {
        planUploader = TravelPlanUploader(this)
    }

    private fun initViews() {
        etTripName = findViewById(R.id.et_trip_name)
        etTripDate = findViewById(R.id.et_trip_date)
        hsTabs = findViewById(R.id.hs_tabs)
        scrollableTabsContainer = findViewById(R.id.scrollable_tabs_container)
        itineraryContainer = findViewById(R.id.itinerary_container)
        itineraryPendingHint = findViewById(R.id.itinerary_pending_hint)
        rvPlaces = findViewById(R.id.rv_pending_places)
        mapView = findViewById(R.id.mapView)
        selectTab(R.id.tab_overview)
    }

    // 初始化地点RecyclerView（支持跨天拖动）
    private fun initPlacesRecyclerView() {
        setupAdapter()

        // ItemTouchHelper配置（支持上下左右拖动）
        val touchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                placeAdapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                return makeMovementFlags(dragFlags, 0)
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    // 处理跨天拖动检测
                    handleCrossDayDrag(viewHolder.adapterPosition)
                }
            }
        }

        itemTouchHelper = ItemTouchHelper(touchHelperCallback)
        itemTouchHelper.attachToRecyclerView(rvPlaces)
    }

    // 设置适配器
    private fun setupAdapter() {
        val currentItinerary = dayItineraries[currentDayIndex]
        placeAdapter = PendingPlaceAdapter(
            currentItinerary.places,
            currentItinerary.dayNumber,
            dragListener = { view ->
                itemTouchHelper.startDrag(rvPlaces.getChildViewHolder(view.parent as View))
            },
            onOrderChangedListener = {
                updateOverview()
                updateTravelPlanDebounced()
            },
            onDeleteListener = { place ->
                mapController.removeScenic(place.name)
                updateOverview()
                updateTravelPlanDebounced()
            }
        )

        rvPlaces.layoutManager = LinearLayoutManager(this)
        rvPlaces.adapter = placeAdapter
    }

    // 处理跨天拖动
    private fun handleCrossDayDrag(position: Int) {
        val currentItinerary = dayItineraries[currentDayIndex]
        if (position < 0 || position >= currentItinerary.places.size) return

        val place = currentItinerary.places[position]

        // 检测是否拖到边缘，这里简化处理，实际可以根据位置判断
        // 左滑尝试移动到前一天
        if (currentDayIndex > 0) {
            val prevDayIndex = currentDayIndex - 1
            movePlaceToDay(place, currentDayIndex, prevDayIndex)
        }
        // 右滑尝试移动到后一天
        else if (currentDayIndex < dayItineraries.size - 1) {
            val nextDayIndex = currentDayIndex + 1
            movePlaceToDay(place, currentDayIndex, nextDayIndex)
        }
    }

    // 将地点从一天移动到另一天
    private fun movePlaceToDay(place: PendingPlace, fromDayIndex: Int, toDayIndex: Int) {
        if (fromDayIndex < 0 || fromDayIndex >= dayItineraries.size ||
            toDayIndex < 0 || toDayIndex >= dayItineraries.size ||
            fromDayIndex == toDayIndex) {
            return
        }

        // 从原天数移除
        dayItineraries[fromDayIndex].places.remove(place)
        // 添加到目标天数
        dayItineraries[toDayIndex].places.add(place)

        // 更新UI
        if (fromDayIndex == currentDayIndex) {
            placeAdapter.notifyDataSetChanged()
        }

        updateOverview()
        updateTravelPlanDebounced()

        // 显示提示
        Toast.makeText(
            this,
            "已将${place.name}移动到${if (dayItineraries[toDayIndex].dayNumber == 0) "待安排" else "第${dayItineraries[toDayIndex].dayNumber}天"}",
            Toast.LENGTH_SHORT
        ).show()
    }

    // 初始化文本变化监听
    private fun initTextWatchers() {
        // 行程名称变化监听
        etTripName.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                updateTravelPlanDebounced()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 日期变化监听（选择后触发）
        etTripDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    etTripDate.setText("$year-${month + 1}-$day")
                    updateTravelPlanDebounced()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    // 设置滑动切换天数
    private fun setupSwipeToChangeDay() {
        val scrollView = findViewById<ScrollView>(R.id.itinerary_container).parent as ScrollView

        scrollView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    false
                }
                MotionEvent.ACTION_UP -> {
                    val endY = event.y
                    val deltaY = endY - startY

                    // 上滑切换到下一天
                    if (deltaY < -SWIPE_THRESHOLD && currentDayIndex < dayItineraries.size - 1) {
                        currentDayIndex++
                        switchToDay(currentDayIndex)
                        true
                    }
                    // 下滑切换到上一天
                    else if (deltaY > SWIPE_THRESHOLD && currentDayIndex > 0) {
                        currentDayIndex--
                        switchToDay(currentDayIndex)
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    // 切换到指定天数
    private fun switchToDay(index: Int) {
        if (index < 0 || index >= dayItineraries.size) return

        currentDayIndex = index
        val day = dayItineraries[index]
        selectTab(day.tabId)
        setupAdapter()

        // 滚动到顶部
        val scrollView = findViewById<ScrollView>(R.id.itinerary_container).parent as ScrollView
        scrollView.scrollTo(0, 0)
    }

    private fun initEvents() {
        // 返回按钮
        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }

        // 保存按钮（手动触发上传）
        findViewById<ImageView>(R.id.iv_save).setOnClickListener {
            updateTravelPlan()
            Toast.makeText(this, "行程已手动保存", Toast.LENGTH_SHORT).show()
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
        searchPlaceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data ?: return@registerForActivityResult
                val selectedPois: ArrayList<PoiItem>? =
                    data.getParcelableArrayListExtra("selected_pois")

                if (!selectedPois.isNullOrEmpty()) {
                    selectedPois.forEach { poi ->
                        mapController.addScenicFromPoi(poi)
                        // 添加到待安排
                        dayItineraries.last().places.add(
                            PendingPlace(
                                name = poi.title ?: "",
                                address = poi.snippet ?: "",
                                rating = "",
                                tag1 = poi.typeDes ?: "",
                                tag2 = poi.cityName ?: ""
                            )
                        )
                    }
                    if (currentDayIndex == dayItineraries.size - 1) {
                        placeAdapter.notifyDataSetChanged()
                    }
                    updateOverview()
                    selectTab(pendingTagId)
                    Toast.makeText(this, "已添加${selectedPois.size}个地点", Toast.LENGTH_SHORT).show()
                    updateTravelPlanDebounced()
                }
            }
        }

        findViewById<ImageView>(R.id.iv_add_item).setOnClickListener {
            val intent = Intent(this, SearchPlaceActivity::class.java)
            searchPlaceLauncher.launch(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // 标签点击事件
        setupInitialTabClicks()
    }

    // 构建当前行程数据
    private fun buildCurrentTravelPlan(): TravelPlan? {
        return try {
            val tripName = etTripName.text.toString().ifEmpty { "未命名行程" }
            val startDateStr = etTripDate.text.toString()
            val startDate = if (startDateStr.isNotEmpty()) sdf.parse(startDateStr) else Date()
            val endDate = Date(startDate.time + dayCount * 24 * 60 * 60 * 1000L)

            // 构建天数列表
            val days = mutableListOf<Day>()
            dayItineraries.forEach { dayItinerary ->
                val activities = dayItinerary.places.map { place ->
                    Activity(
                        time = "",
                        location_name = place.name
                    )
                }
                days.add(Day(dayItinerary.dayNumber, activities))
            }

            // 版本历史
            val versionHistory = listOf(
                VersionHistory(
                    editorId = currentUserId,
                    editTime = Date(),
                    changeLog = "更新行程安排"
                )
            )

            TravelPlan(
                planId = currentPlanId,
                title = tripName,
                creatorId = currentUserId,
                collaborators = null,
                status = "draft",
                content = Content(
                    destination = "未设置",
                    startDate = startDate,
                    endDate = endDate,
                    days = days,
                    transport = null,
                    notes = null
                ),
                versionHistory = versionHistory,
                tags = null,
                createdAt = Date(),
                updatedAt = Date()
            )
        } catch (e: Exception) {
            Log.e("BuildPlanError", "构建行程失败", e)
            null
        }
    }

    // 防抖上传行程
    private fun updateTravelPlanDebounced() {
        updateHandler.removeCallbacksAndMessages(null)
        updateHandler.postDelayed({
            updateTravelPlan()
        }, DEBOUNCE_DELAY)
    }

    // 实际上传行程
    private fun updateTravelPlan() {
        val plan = buildCurrentTravelPlan() ?: return

        planUploader.updateTravelPlanByPlanId(currentPlanId, plan, object : TravelPlanUploader.UploadCallback {
            override fun onSuccess(savedPlan: TravelPlan) {
                currentPlanId = savedPlan.planId
                Log.d("PlanUpdate", "行程更新成功: ${savedPlan.planId}")
            }

            override fun onFailure(errorMsg: String) {
                Log.e("PlanUpdate", "行程更新失败: $errorMsg")
            }
        })
    }

    // 更新总览显示
    private fun updateOverview() {
        // 清除现有总览内容
        val overviewViews = mutableListOf<View>()
        for (i in 0 until itineraryContainer.childCount) {
            val child = itineraryContainer.getChildAt(i)
            if (child.tag != null && child.tag.toString().startsWith("overview_")) {
                overviewViews.add(child)
            }
        }
        overviewViews.forEach { itineraryContainer.removeView(it) }

        // 添加每日行程到总览
        dayItineraries.forEach { dayItinerary ->
            if (dayItinerary.dayNumber == 0) return@forEach // 跳过待安排

            if (dayItinerary.places.isNotEmpty()) {
                val overviewLayout = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dpToPx(8) }
                    orientation = LinearLayout.VERTICAL
                    background = ContextCompat.getDrawable(this@CreateTripActivity, R.drawable.bg_gray)
                    setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                    tag = "overview_day_${dayItinerary.dayNumber}"
                }

                overviewLayout.addView(TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    text = "第${dayItinerary.dayNumber}天安排："
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })

                dayItinerary.places.forEachIndexed { index, place ->
                    overviewLayout.addView(TextView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dpToPx(4) }
                        text = "${index + 1}. ${place.name}（${place.tag1}/${place.tag2}）"
                        textSize = 14f
                        setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black))
                    })
                }

                itineraryContainer.addView(overviewLayout)
            }
        }

        // 添加待安排到总览
        val pendingItinerary = dayItineraries.last()
        if (pendingItinerary.places.isNotEmpty()) {
            val overviewLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dpToPx(8) }
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@CreateTripActivity, R.drawable.bg_gray)
                setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                tag = "overview_pending"
            }

            overviewLayout.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                text = "待安排地点："
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            pendingItinerary.places.forEachIndexed { index, place ->
                overviewLayout.addView(TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dpToPx(4) }
                    text = "${index + 1}. ${place.name}（${place.tag1}/${place.tag2}）"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black))
                })
            }

            itineraryContainer.addView(overviewLayout)
        }
    }

    // 添加新天数
    private fun addNewDay() {
        dayCount++
        val newDayTabId = View.generateViewId()

        // 创建新标签
        val dayTab = TextView(this).apply {
            id = newDayTabId
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(36)
            ).apply { marginStart = dpToPx(8) }
            text = "第${dayCount}天"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black))
            setBackgroundResource(R.drawable.bg_tab_unselected)
            setPadding(dpToPx(16), 0, dpToPx(16), 0)
            gravity = Gravity.CENTER
            isClickable = true
        }

        // 添加到标签容器（待待安排前）
        scrollableTabsContainer.addView(dayTab, scrollableTabsContainer.childCount - 1)
        dayTab.setOnClickListener { selectTab(dayTab.id) }

        // 添加到数据列表（待安排前）
        dayItineraries.add(dayItineraries.size - 1, DayItinerary(dayCount, newDayTabId))

        // 创建行程项视图
        val itineraryItem = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(8) }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@CreateTripActivity, R.drawable.bg_gray)
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            tag = dayTab.id.toString()
        }

        itineraryItem.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = "第${dayCount}天: "
            setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        itineraryItem.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = "待安排行程"
            setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.text_gray))
            textSize = 14f
        })

        itineraryContainer.addView(itineraryItem)
        selectTab(dayTab.id)
        scrollToTab(dayTab)
        Toast.makeText(this, "已添加第${dayCount}天", Toast.LENGTH_SHORT).show()
        updateTravelPlanDebounced()
    }

    // 标签切换逻辑
    private fun selectTab(selectedTabId: Int) {
        // 重置所有标签样式
        findViewById<TextView>(R.id.tab_overview).apply {
            setBackgroundResource(R.drawable.bg_tab_unselected)
            setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black))
        }

        for (i in 0 until scrollableTabsContainer.childCount) {
            (scrollableTabsContainer.getChildAt(i) as? TextView)?.apply {
                setBackgroundResource(R.drawable.bg_tab_unselected)
                setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black))
            }
        }

        // 设置选中标签样式
        findViewById<TextView>(selectedTabId)?.apply {
            setBackgroundResource(R.drawable.bg_tab_selected)
            setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.white))
        }

        // 更新当前天数索引
        currentDayIndex = dayItineraries.indexOfFirst { it.tabId == selectedTabId }

        // 根据选中的标签显示不同内容
        when (selectedTabId) {
            R.id.tab_overview -> {
                showAllItineraries()
                itineraryPendingHint.visibility = View.GONE
                rvPlaces.visibility = View.GONE
            }
            pendingTagId -> {
                hideAllItineraries()
                if (dayItineraries.last().places.isEmpty()) {
                    itineraryPendingHint.visibility = View.VISIBLE
                    rvPlaces.visibility = View.GONE
                } else {
                    itineraryPendingHint.visibility = View.GONE
                    rvPlaces.visibility = View.VISIBLE
                    setupAdapter()
                }
            }
            else -> {
                hideAllItineraries()
                val currentItinerary = dayItineraries.find { it.tabId == selectedTabId }
                if (currentItinerary?.places?.isEmpty() == true) {
                    itineraryPendingHint.visibility = View.VISIBLE
                    rvPlaces.visibility = View.GONE
                } else {
                    itineraryPendingHint.visibility = View.GONE
                    rvPlaces.visibility = View.VISIBLE
                    setupAdapter()
                }
            }
        }
    }

    private fun showAllItineraries() {
        for (i in 0 until itineraryContainer.childCount) {
            val child = itineraryContainer.getChildAt(i)
            if (child.id != R.id.itinerary_pending_hint && child.id != R.id.rv_pending_places) {
                child.visibility = View.VISIBLE
            }
        }
    }

    private fun hideAllItineraries() {
        for (i in 0 until itineraryContainer.childCount) {
            val child = itineraryContainer.getChildAt(i)
            if (child.id != R.id.itinerary_pending_hint && child.id != R.id.rv_pending_places) {
                child.visibility = View.GONE
            }
        }
    }

    private fun showTargetItinerary(targetTabId: String) {
        for (i in 0 until itineraryContainer.childCount) {
            val child = itineraryContainer.getChildAt(i)
            if (child.id == R.id.itinerary_pending_hint || child.id == R.id.rv_pending_places) continue
            child.visibility = if (child.tag == targetTabId) View.VISIBLE else View.GONE
        }
    }

    private fun scrollToTab(tab: View) {
        hsTabs.scrollTo(tab.left - dpToPx(16), 0)
    }

    private fun setupInitialTabClicks() {
        findViewById<TextView>(R.id.tab_overview).setOnClickListener { selectTab(R.id.tab_overview) }
        findViewById<TextView>(R.id.tab_day_1).setOnClickListener { selectTab(R.id.tab_day_1) }
        findViewById<TextView>(R.id.tab_pending).setOnClickListener { selectTab(pendingTagId) }
    }

    // 地图生命周期管理
    override fun onResume() {
        super.onResume()
        if (::mapController.isInitialized) mapController.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (::mapController.isInitialized) mapController.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mapController.isInitialized) mapController.onDestroy()
        updateHandler.removeCallbacksAndMessages(null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::mapController.isInitialized) mapController.onSaveInstanceState(outState)
    }
}