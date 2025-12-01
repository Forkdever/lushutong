package com.example.lushutong

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.maps.MapView
import com.amap.api.services.core.PoiItem
import com.example.plan.CollaborationManager
import com.example.plan.HttpCollaborationManager
import com.example.plan.TravelPlanManager
import com.example.plan.TravelPlanUploader
import com.llw.newmapdemo.R
import com.llw.newmapdemo.TravelMapController
import com.llw.newmapdemo.WeatherController
import com.llw.newmapdemo.WeatherData
import io.noties.markwon.Markwon
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.example.plan.TravelPlan
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.Window
import android.content.Context
import androidx.core.content.ContextCompat

// 待安排地点数据类
data class PendingPlace(
    val name: String,
    val address: String,
    val rating: String,
    val tag1: String,
    val tag2: String,
    var id: String = UUID.randomUUID().toString(), // 唯一ID，用来对应缓存

    // 这两个一开始可以是 null，后面通过高德 API 查到以后再填进去
    var latitude: Double? = null,
    var longitude: Double? = null,

    // 可选，高德 POI 的 id（如果有的话）
    var poiId: String? = null
)


// 每日行程数据类
data class DayItinerary(
    val dayNumber: Int,
    val tabId: Int,
    val places: MutableList<PendingPlace> = mutableListOf(),
    var weatherData: WeatherData? = null, // 当天天气数据
    var weatherLoading: Boolean = false,  // 天气加载状态
    var weatherError: String? = null      // 天气错误信息
)

// 待安排地点适配器
class PendingPlaceAdapter(
    private val placeList: MutableList<PendingPlace>,
    private val currentDay: Int,
    private val currentDayIndex: Int, // 添加当前天索引
    private val dragListener: (View) -> Unit,
    private val onOrderChangedListener: () -> Unit,
    private val onDeleteListener: (PendingPlace) -> Unit,
    private val onMoveToDayListener: (PendingPlace, Int) -> Unit,
    private val getDayNames: () -> List<String>


) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // 在 PendingPlaceAdapter 内部
    private var routeSegments: List<TravelMapController.RouteSegmentInfo> = emptyList()

    /**
     * UI 调用：更新当前天的每一段路的交通信息
     */
    fun updateRouteSegments(newList: List<TravelMapController.RouteSegmentInfo>) {
        routeSegments = newList
        try {
            notifyDataSetChanged()
        } catch (e: Exception) {
            Log.e("PendingPlaceAdapter", "更新交通信息失败", e)
        }
    }


    companion object {
        const val VIEW_TYPE_PLACE = 0
        const val VIEW_TYPE_TRAFFIC = 1
    }

    inner class PendingPlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_place_name)
        val tvAddress: TextView = itemView.findViewById(R.id.tv_place_address)
        val tvRating: TextView = itemView.findViewById(R.id.tv_place_rating)
        val tvTag1: TextView = itemView.findViewById(R.id.tv_tag1)
        val tvTag2: TextView = itemView.findViewById(R.id.tv_tag2)
        val ivDrag: ImageView = itemView.findViewById(R.id.iv_drag)
        val ivDelete: ImageView = itemView.findViewById(R.id.iv_delete)
    }

    inner class PlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_place_name)
        val tvAddress: TextView = itemView.findViewById(R.id.tv_place_address)
        val tvRating: TextView = itemView.findViewById(R.id.tv_place_rating)
        val tvTag1: TextView = itemView.findViewById(R.id.tv_tag1)
        val tvTag2: TextView = itemView.findViewById(R.id.tv_tag2)
        val ivDrag: ImageView = itemView.findViewById(R.id.iv_drag)
        val ivDelete: ImageView = itemView.findViewById(R.id.iv_delete)
    }

    inner class TrafficInfoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTransit: TextView = itemView.findViewById(R.id.tv_transit_time)
        val tvDriving: TextView = itemView.findViewById(R.id.tv_driving_time)
        val tvDistance: TextView = itemView.findViewById(R.id.tv_distance)
    }

    // 现在有 n 个景点，就要显示 n + (n-1) = 2n-1 个 item
    override fun getItemCount(): Int {
        return if (placeList.isEmpty()) 0 else placeList.size * 2 - 1
    }

    override fun getItemViewType(position: Int): Int {
        // 偶数位置：0,2,4,... 是景点；奇数位置是中间的“交通信息”
        return if (position % 2 == 0) VIEW_TYPE_PLACE else VIEW_TYPE_TRAFFIC
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_PLACE) {
            val view = inflater.inflate(R.layout.item_pending_place, parent, false)
            PlaceViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_traffic_info, parent, false)
            TrafficInfoViewHolder(view)
        }
    }


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_PLACE) {
            // ---- 绑定景点 item ----
            val placeHolder = holder as PlaceViewHolder
            val placeIndex = position / 2          // 0,2,4... -> 0,1,2...
            if (placeIndex !in placeList.indices) return

            val place = placeList[placeIndex]
            placeHolder.tvName.text = place.name
            placeHolder.tvAddress.text = place.address
            placeHolder.tvRating.text = place.rating
            placeHolder.tvTag1.text = place.tag1
            placeHolder.tvTag2.text = place.tag2

            // 拖动
            placeHolder.ivDrag.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    dragListener(placeHolder.ivDrag)
                }
                false
            }

            // 删除 / 移动的菜单（这里把 adapterPosition 传过去）
            placeHolder.ivDelete.setOnClickListener {

                showActionMenu(placeHolder.itemView, place)
            }

        } else {
            // ---- 绑定中间的“交通信息” item ----
            val trafficHolder = holder as TrafficInfoViewHolder

            // 当前 traffic 在 position=1 对应 place[0] -> place[1]
            val fromIndex = position / 2
            val toIndex = fromIndex + 1
            if (fromIndex !in placeList.indices || toIndex !in placeList.indices) return

            val fromPlace = placeList[fromIndex]
            val toPlace = placeList[toIndex]


            // 段索引：第 fromIndex 段，对应景点 fromIndex -> fromIndex+1
            val segmentIndex = fromIndex
            val seg = routeSegments.getOrNull(segmentIndex)

            if (seg != null) {
                // 有真实数据
                trafficHolder.tvTransit.text =
                    if (!seg.transitTime.isEmpty()) seg.transitTime else "公共交通（无数据）"
                trafficHolder.tvDriving.text =
                    if (!seg.driveTime.isEmpty()) seg.driveTime else "驾车（无数据）"
                trafficHolder.tvDistance.text =
                    if (!seg.driveDistance.isEmpty()) seg.driveDistance else "距离（无数据）"
            } else {
                // 暂时还没有规划结果（正在算 / 没有调用到回调）
                trafficHolder.tvTransit.text = "规划中"
                trafficHolder.tvDriving.text = "规划中"
                trafficHolder.tvDistance.text = "规划中"
            }

        }
    }


    // 显示操作菜单弹窗（修复：移除重复的列表项删除）

    // 在 PendingPlaceAdapter 内部
    private fun showActionMenu(anchor: View, place: PendingPlace) {
        try {
            val ctx = anchor.context

            // 1. 先拿到“可以移动到的天数列表”，例如：
            // ["移动到待安排", "移动到第1天", "移动到第2天", ...]
            val dayNames = getDayNames.invoke()  // 你原来的 getDayNames 参数

            // 2. 最后再加一个“删除”
            val options = dayNames + "删除"

            AlertDialog.Builder(ctx)
                .setTitle("操作")
                .setItems(options.toTypedArray()) { _, which ->
                    // which == 点击的菜单索引

                    if (which < dayNames.size) {
                        // 点击的是“移动到某一天”
                        if (which == currentDayIndex) {
                            Toast.makeText(ctx, "已在当前天数中", Toast.LENGTH_SHORT).show()
                            return@setItems
                        }

                        // 触发外部回调：place + 目标天（排序索引）
                        onMoveToDayListener(place, which)
                    } else {
                        // 点击的是“删除”
                        // 注意：这里不能直接用 ViewHolder 的 adapterPosition，
                        // 只用 placeList 里真正的索引，避免越界
                        val idx = placeList.indexOfFirst { it.id == place.id }
                        if (idx != -1) {
                            placeList.removeAt(idx)
                            // 通知列表刷新（简单起见全量刷新）
                            notifyDataSetChanged()
                            // 告诉外部有删除操作
                            onDeleteListener?.invoke(place)
                        }
                    }
                }
                .show()
        } catch (e: Exception) {
            Log.e("ShowActionMenuError", "显示操作菜单失败", e)
        }
    }


    //override fun getItemCount(): Int = placeList.size

    // 交换列表项（拖动排序）
    fun onItemMove(fromPosition: Int, toPosition: Int) {
        try {
            // 这里传进来的 from/to 保证是“景点行”的 position（偶数）
            val fromIndex = fromPosition / 2
            val toIndex = toPosition / 2
            if (fromIndex in placeList.indices && toIndex in placeList.indices && fromIndex != toIndex) {
                Collections.swap(placeList, fromIndex, toIndex)
                // 整体结构变了，最简单的方式还是全量刷新
                notifyDataSetChanged()
                onOrderChangedListener()
            }
        } catch (e: Exception) {
            Log.e("ItemMoveError", "交换列表项失败", e)
        }
    }


    // 精确移除指定位置的项（供外部调用）
    fun removeItem(position: Int) {
        try {
            if (position >= 0 && position < placeList.size) {
                placeList.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, placeList.size - position)
            }
        } catch (e: Exception) {
            Log.e("RemoveItemError", "移除项失败", e)
        }
    }

    // 移除指定的place对象（修复：通过ID查找）
    fun removePlace(place: PendingPlace) {
        try {
            val position = placeList.indexOfFirst { it.id == place.id }
            if (position != -1) {
                removeItem(position)
                onOrderChangedListener() // 触发顺序变化回调
            }
        } catch (e: Exception) {
            Log.e("RemovePlaceError", "移除place失败", e)
        }
    }

    // 触发顺序变化回调（供外部调用）
    fun notifyOrderChanged() {
        onOrderChangedListener()
    }

    // 刷新数据（供外部调用）
    fun refreshData() {
        try {
            notifyDataSetChanged()
        } catch (e: Exception) {
            Log.e("RefreshDataError", "刷新数据失败", e)
        }
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
    private lateinit var markdownContent: String
    // === 修正这里：移除重复声明，只保留一个 ===
    private var collaborationManager: com.example.plan.HttpCollaborationManager? = null
    private var isCollaborationMode = false
    private var dayCount = 1
    // 数据相关
    private val dayItineraries = mutableListOf<DayItinerary>()
    private var currentDayIndex = 0
    private var pendingTagId = R.id.tab_pending // 使用XML中定义的ID
    private var currentSelectedTabId = R.id.tab_overview // 记录当前选中的标签

    // 地图相关
    private var mapView: MapView? = null
    private var mapController: TravelMapController? = null

    private lateinit var searchPlaceLauncher: ActivityResultLauncher<Intent>

    // 适配器和拖动相关
    private var placeAdapter: PendingPlaceAdapter? = null
    private var itemTouchHelper: ItemTouchHelper? = null

    // 上传相关
    private var planUploader: TravelPlanUploader? = null
    private var currentPlanId: String = "TP${System.currentTimeMillis()}"
    private val currentUserId = 10001 // 实际应从登录系统获取
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // 防抖处理
    private val updateHandler = Handler(Looper.getMainLooper())
    private val DEBOUNCE_DELAY = 500L
    private val overviewUpdateHandler = Handler(Looper.getMainLooper())
    private val OVERVIEW_UPDATE_DELAY = 200L

    // 天气相关
    private lateinit var weatherController: WeatherController
    private var tripCity: String = "北京" // 默认城市，可从行程地点自动提取

    // 标记是否正在更新总览
    private var isUpdatingOverview = false

    // DP转PX
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = true
        }
        setContentView(R.layout.activity_create_trip)

        // 2. 接收从TravelActivity传递的Markdown数据
        markdownContent = intent.getStringExtra("TRAVEL_PLAN_MARKDOWN") ?: """
# 默认旅行计划
> 暂无生成的计划内容
        """.trimIndent()
        // 可选：接收目的地等其他数据
        tripCity = intent.getStringExtra("TRIP_DESTINATION") ?: "北京"
        val departureDate = intent.getStringExtra("TRIP_DEPARTURE_DATE") ?: ""
        try {
            initViews()
            initUploader()
            initDayItineraries()
            weatherController = WeatherController(this)
            val (contentBeforeAt, dayAttractionsMap) = parseMarkdownContent()
            markdownContent = contentBeforeAt // 弹窗只显示@之前的内容
            populateItinerariesFromAttractions(dayAttractionsMap)
            updateDayTabs() // 重建天数标签
            updateItineraryDisplay() // 更新行程显示
            if (departureDate.isNotEmpty()) {
                etTripDate.setText(departureDate)
            }
            if (currentSelectedTabId == R.id.tab_overview) {
                updateOverview() // 强制刷新总览
            }
            initPlacesRecyclerView()
            initEvents()
            initTextWatchers()

            val targetPlanId = intent.getStringExtra("TARGET_PLAN_ID")
            if (targetPlanId.isNullOrEmpty()) {
                initNewTripPlan()
            } else {
                loadExistingTripPlan(targetPlanId)
            }

            mapView?.let {
                mapController = TravelMapController(this, it)
                mapController?.onCreate(savedInstanceState)
                mapController?.setOnRouteInfoUpdateListener(
                    object : TravelMapController.OnRouteInfoUpdateListener {
                        override fun onRouteInfoUpdated(list: MutableList<TravelMapController.RouteSegmentInfo>?) {
                            runOnUiThread {
                                val segments = list ?: emptyList()
                                placeAdapter?.updateRouteSegments(segments)
                            }
                        }
                    }
                )
            }
            // onCreate末尾
            if (currentSelectedTabId == R.id.tab_overview) {
                Handler(Looper.getMainLooper()).postDelayed({
                    updateOverview()
                }, 300) // 延迟确保UI渲染完成
            }

        } catch (e: Exception) {
            Log.e("onCreateError", "初始化失败", e)
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 初始化协作管理器
    private fun initCollaboration() {
        try {
            collaborationManager = HttpCollaborationManager.getInstance()
        } catch (e: Exception) {
            Log.e("CollaborationInit", "初始化协作管理器失败", e)
        }
    }

    // 修改开始监听方法
    private fun startCollaborationListening(planId: String) {
        try {
            collaborationManager?.startListening(planId, object : HttpCollaborationManager.CollaborationListener {
                override fun onPlanUpdated(updatedPlan: TravelPlan) {
                    runOnUiThread {
                        handleCollaborationUpdate(updatedPlan)
                    }
                }

                override fun onError(errorMessage: String) {
                    runOnUiThread {
                        Log.e("Collaboration", "协作监听错误: $errorMessage")
                        //Toast.makeText(this@CreateTripActivity, "协作同步失败", Toast.LENGTH_SHORT).show()
                    }
                }
            })
            Log.d("Collaboration", "开始HTTP协作监听，planId: $planId")
        } catch (e: Exception) {
            Log.e("Collaboration", "启动协作监听失败", e)
        }
    }

    // 修改保存方法，通知其他用户
    private fun updateTravelPlan() {
        try {
            val plan = buildCurrentTravelPlan() ?: return
            val planManager = TravelPlanManager.getInstance(applicationContext)

            planManager.updateTravelPlan(
                currentPlanId,
                plan,
                object : TravelPlanUploader.UploadCallback {
                    override fun onSuccess(savedPlan: TravelPlan) {
                        currentPlanId = savedPlan.planId
                        Log.d("PlanUpdate", "行程更新成功: ${savedPlan.planId}")

                        // 通知其他用户有更新
                        if (isCollaborationMode) {
                            collaborationManager?.notifyUpdate(currentPlanId)
                        }
                    }

                    override fun onFailure(errorMsg: String) {
                        Log.e("PlanUpdate", "行程更新失败: $errorMsg")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("UpdatePlanError", "更新行程失败", e)
        }
    }

    // 处理协作更新
    private fun handleCollaborationUpdate(updatedPlan: TravelPlan) {
        try {
            Log.d("Collaboration", "收到协作更新: ${updatedPlan.title}")

            // 避免自己触发更新的循环
            if (isUpdatingOverview) return

            // 更新界面数据
            updatedPlan.content?.days?.let { remoteDays ->
                // 清空现有数据但保留当前结构
                dayItineraries.clear()

                // 将远程数据转换为本地数据结构
                remoteDays.forEachIndexed { index, day ->
                    val dayItinerary = DayItinerary(
                        dayNumber = day.dayNumber,
                        tabId = if (day.dayNumber == 0) pendingTagId else View.generateViewId(),
                        places = mutableListOf()
                    )

                    // 转换activities为PendingPlace
                    day.activities?.forEach { activity ->
                        dayItinerary.places.add(
                            PendingPlace(
                                name = activity.location_name ?: "未命名地点",
                                address = activity.location_name ?: "",
                                rating = "",
                                tag1 = activity.time ?: "",
                                tag2 = tripCity,
                                latitude = null,
                                longitude = null,
                                poiId = null
                            )
                        )
                    }

                    dayItineraries.add(dayItinerary)
                }

                // 确保有待安排区域
                if (dayItineraries.none { it.dayNumber == 0 }) {
                    dayItineraries.add(DayItinerary(0, pendingTagId))
                }

                // 更新UI
                updateDayTabs()
                updateItineraryDisplay()

                // 如果当前在总览页面，更新总览
                if (currentSelectedTabId == R.id.tab_overview) {
                    overviewUpdateHandler.removeCallbacksAndMessages(null)
                    overviewUpdateHandler.postDelayed({
                        updateOverview()
                    }, OVERVIEW_UPDATE_DELAY)
                }

                // 同步地图路线
                syncRouteWithCurrentUI()

                Toast.makeText(this, "行程已同步更新", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("Collaboration", "处理协作更新失败", e)
        }
    }



    // 新增：加载已有旅行方案
    // 修正加载已有旅行方案的方法
    private fun loadExistingTripPlan(planId: String) {
        planUploader?.fetchTravelPlanByPlanId(planId, object : TravelPlanUploader.FetchCallback {
            override fun onSuccess(plan: TravelPlan) {
                runOnUiThread {
                    try {
                        // 1. 更新本地方案基础信息（调用Java类Getter）
                        currentPlanId = plan.getPlanId() ?: "TP${System.currentTimeMillis()}"
                        etTripName.setText(plan.getTitle() ?: "未命名行程")

                        // 2. 处理日期（从Content获取，调用getStartDate()）
                        val startDate = plan.getContent()?.getStartDate()
                        val startDateStr = startDate?.let { sdf.format(it) } ?: sdf.format(Date())
                        etTripDate.setText(startDateStr)

                        // 3. 获取目的地城市（调用Content的getDestination()）
                        tripCity = plan.getContent()?.getDestination() ?: "未知城市"

                        // 4. 重置本地行程数据（保留“待安排”区域，本地核心结构）
                        dayItineraries.clear()
                        dayItineraries.add(DayItinerary(0, pendingTagId)) // 固定添加待安排

                        // 5. 解析远程每日行程（严格调用Day类的Getter）
                        plan.getContent()?.getDays()?.let { remoteDays ->
                            Log.d("LoadPlan", "远程Days列表size：${remoteDays.size}") // 打印天数

                            // 直接遍历remoteDays，用索引生成dayNumber（替代排序）
                            remoteDays.forEachIndexed { index, remoteDay ->
                                // 用索引+1生成dayNumber（第0个元素→第1天，第1个→第2天...）
                                val localDayNumber = index + 1
                                Log.d("LoadPlan", "当前索引：$index → 生成的dayNumber：$localDayNumber")
                                Log.d("LoadPlan", "当前Day的activities数量：${remoteDay.getActivities()?.size ?: "0"}")

                                // 创建本地DayItinerary（使用生成的dayNumber）
                                val localDay = DayItinerary(
                                    dayNumber = localDayNumber, // 核心：用索引生成的有效dayNumber
                                    tabId = View.generateViewId(),
                                    places = mutableListOf(),
                                    weatherLoading = false,
                                    weatherError = null
                                )

                                // 解析Activity（数据库有time和location_name，正常获取）
                                remoteDay.getActivities()?.forEach { remoteActivity ->
                                    val placeName = remoteActivity.getLocation_name() ?: "未命名地点"
                                    val placeTime = remoteActivity.getTime() ?: "全天"

                                    localDay.places.add(
                                        PendingPlace(
                                            name = placeName,
                                            address = placeName, // 用地点名填充地址（匹配本地格式）
                                            rating = "4.7",      // 本地默认评分
                                            tag1 = placeTime,    // 用远程time作为tag1（时段）
                                            tag2 = tripCity,     // 用目的地作为tag2（城市）
                                            id = UUID.randomUUID().toString(), // 本地唯一ID
                                            latitude = null,
                                            longitude = null,
                                            poiId = null
                                        )
                                    )
                                }

                                // 将本地Day添加到行程列表
                                dayItineraries.add(localDay)
                            }

                            // 更新本地天数计数和默认选中项
                            dayCount = dayItineraries.size // 直接用列表大小（已过滤待安排）
                            currentDayIndex = 0 // 默认选中第一天

                            // 触发本地UI更新（关键）
                            updateDayTabs()          // 生成天数标签
                            updateItineraryDisplay() // 更新行程列表
                            setupAdapter()           // 刷新RecyclerView

                            // 补充天气和地图逻辑
                            if (currentSelectedTabId == R.id.tab_overview) {
                                updateOverview()
                            }
                            dayItineraries.forEach {
                                queryDayWeather(it) // 为每个天数查天气
                            }
                            syncRouteWithCurrentUI() // 同步地图路线
                            isCollaborationMode = true
                            ivRefresh.visibility = View.VISIBLE

                            Toast.makeText(this@CreateTripActivity, "已加载协作方案：${plan.getTitle()}", Toast.LENGTH_SHORT).show()

                        }
                    } catch (e: Exception) {
                        Log.e("LoadPlanError", "处理行程数据失败", e)
                        Toast.makeText(this@CreateTripActivity, "处理行程数据失败：${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(errorMsg: String) {
                runOnUiThread {
                    Toast.makeText(this@CreateTripActivity, "加载失败：$errorMsg", Toast.LENGTH_SHORT).show()
                    initNewTripPlan() // 加载失败则创建新方案
                }
            }
        })
    }

    /** 更新日期标签页（显示所有天数） */
    /** 更新日期标签页（显示所有天数） */
    private fun updateDayTabs() {
        try {
            // 清空可滚动标签容器（保留总览标签）
            scrollableTabsContainer.removeAllViews()

            // 按天数排序（排除待安排和总览）
            val sortedDays = dayItineraries.filter { it.dayNumber > 0 }.sortedBy { it.dayNumber }

            // 添加天数标签
            sortedDays.forEach { dayItinerary ->
                // 使用 TextView 创建天数标签
                val dayTab = TextView(this).apply {
                    id = dayItinerary.tabId
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dpToPx(36)
                    ).apply {
                        marginStart = dpToPx(8)
                    }
                    text = "第${dayItinerary.dayNumber}天"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black))
                    setBackgroundResource(R.drawable.bg_tab_unselected)
                    setPadding(dpToPx(16), 0, dpToPx(16), 0)
                    gravity = Gravity.CENTER
                    isClickable = true
                }

                dayTab.setOnClickListener {
                    val dayIndex = dayItineraries.indexOfFirst { it.tabId == dayItinerary.tabId }
                    if (dayIndex != -1) switchToDay(dayIndex)
                }

                scrollableTabsContainer.addView(dayTab)
            }

            // 添加待安排标签（如果不存在）
            val pendingItinerary = getPendingItinerary()
            val existingPendingTab = scrollableTabsContainer.findViewById<TextView>(pendingTagId)
            if (pendingItinerary != null && existingPendingTab == null) {
                val pendingTab = TextView(this).apply {
                    id = pendingTagId
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dpToPx(36)
                    ).apply {
                        marginStart = dpToPx(8)
                    }
                    text = "待安排"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black))
                    setBackgroundResource(R.drawable.bg_tab_unselected)
                    setPadding(dpToPx(16), 0, dpToPx(16), 0)
                    gravity = Gravity.CENTER
                    isClickable = true
                }

                pendingTab.setOnClickListener {
                    val pendingIndex = dayItineraries.indexOfFirst { it.dayNumber == 0 }
                    if (pendingIndex != -1) switchToDay(pendingIndex)
                }

                scrollableTabsContainer.addView(pendingTab)
            }

            // 滚动到当前选中的标签
            scrollToCurrentTab()

        } catch (e: Exception) {
            Log.e("UpdateDayTabsError", "更新标签页失败", e)
        }
    }

    /** 滚动到当前选中的标签 */
    private fun scrollToCurrentTab() {
        try {
            val currentItinerary = getItineraryByIndex(currentDayIndex) ?: return
            val currentTab = scrollableTabsContainer.findViewById<View>(currentItinerary.tabId)
            currentTab?.let {
                hsTabs.post {
                    hsTabs.scrollTo(it.left - dpToPx(16), 0)
                }
            }
        } catch (e: Exception) {
            Log.e("ScrollToTabError", "滚动到标签失败", e)
        }
    }


    /** 更新当前天数的行程内容显示 */
    /** 更新当前天数的行程内容显示 */
    private fun updateItineraryDisplay() {
        try {
            val currentDay = dayItineraries.getOrNull(currentDayIndex) ?: return

            if (currentDay.places.isNotEmpty()) {
                itineraryPendingHint.visibility = View.GONE
                rvPlaces.visibility = View.VISIBLE
                setupAdapter() // 刷新适配器绑定最新数据
            } else {
                itineraryPendingHint.visibility = View.VISIBLE
                rvPlaces.visibility = View.GONE
            }

            // 确保RecyclerView在容器中（防止被意外移除）
            if (rvPlaces.parent != itineraryContainer) {
                itineraryContainer.addView(rvPlaces)
            }

        } catch (e: Exception) {
            Log.e("UpdateItineraryError", "更新行程显示失败", e)
        }
    }

    // 抽取原逻辑：初始化新方案
    private fun initNewTripPlan() {
        // ========== 创建行程（参数来自界面） ==========
        val planManager = TravelPlanManager.getInstance(applicationContext)

        // 1. 动态生成行程ID
        val dynamicPlanId = "TP${System.currentTimeMillis()}"
        currentPlanId = dynamicPlanId

        // 2. 从输入框获取行程名称
        val tripName = etTripName.text.toString().takeIf { it.isNotEmpty() } ?: "未命名行程"

        // 3. 提取目的地
        val destination = getDestinationFromItinerary() ?: "北京"

        // 4. 获取日期信息
        val startDateStr = etTripDate.text.toString().takeIf { it.isNotEmpty() } ?: sdf.format(Date())
        val startDate = try {
            sdf.parse(startDateStr)
        } catch (e: Exception) {
            Date()
        }
        val endDate = Calendar.getInstance().apply {
            time = startDate
            add(Calendar.DAY_OF_YEAR, dayCount - 1)
        }.time
        val endDateStr = sdf.format(endDate)

        // 5. 确保dayItineraries中有初始天数数据（关键修复）
        if (dayItineraries.isEmpty() || dayItineraries.none { it.dayNumber == 1 }) {
            dayItineraries.clear()
            dayItineraries.add(DayItinerary(1, R.id.tab_day_1))  // 添加第一天
            dayItineraries.add(DayItinerary(0, pendingTagId))    // 添加待安排
            dayCount = 1
        }


        // 7. 构建初始行程数据
        val initialPlan = buildCurrentTravelPlan()
        val initialDays = initialPlan?.content?.days

        // 创建行程
        planManager.createTravelPlan(
            dynamicPlanId,
            tripName,
            currentUserId,
            destination,
            startDateStr,
            endDateStr,
            initialDays,
            object : TravelPlanUploader.UploadCallback {
                override fun onSuccess(plan: com.example.plan.TravelPlan) {
                    runOnUiThread {
                        currentPlanId = plan.planId
                        Toast.makeText(
                            this@CreateTripActivity,
                            "创建成功: ${plan.title ?: tripName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    updateTravelPlan()
                }

                override fun onFailure(errorMsg: String) {
                    runOnUiThread {
                        Toast.makeText(
                            this@CreateTripActivity,
                            "创建失败：$errorMsg",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    // 获取待安排行程（工具方法）
    private fun getPendingItinerary(): DayItinerary? {
        return dayItineraries.find { it.dayNumber == 0 }
    }

    // 获取指定索引的行程
    private fun getItineraryByIndex(index: Int): DayItinerary? {
        return if (index >= 0 && index < dayItineraries.size) {
            dayItineraries[index]
        } else {
            null
        }
    }

    /**
     * 把“当前 Tab 显示的这一天景点顺序”同步给地图做路径规划
     * 这里只负责把“按顺序的名字列表”交给 TravelMapController，
     * 具体的经纬度解析 + 画点 + 路线规划都在 TravelMapController 里做。
     */
    private fun syncRouteWithCurrentUI() {
        try {
            val controller = mapController ?: return
            val currentItinerary = getItineraryByIndex(currentDayIndex) ?: return

            val places = currentItinerary.places
            if (places.isEmpty()) {
                // 当前页面没有景点，直接清空地图上的景点和路线
                controller.clearAllScenic()
                return
            }

            // 按当前 UI 顺序提取景点名称列表
            val nameList = places.map { it.name }

            // 交给 TravelMapController：内部会用高德 POI 解析经纬度并按顺序规划路线
            controller.updateRouteByPlaceNames(nameList)

        } catch (e: Exception) {
            Log.e("SyncRouteError", "同步路径到地图失败", e)
        }
    }




    // 获取指定排序索引的行程对象
    private fun getItineraryBySortedIndex(sortedIndex: Int): DayItinerary? {
        return try {
            val sortedDays = dayItineraries.sortedBy { it.dayNumber }
            if (sortedIndex >= 0 && sortedIndex < sortedDays.size) {
                sortedDays[sortedIndex]
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("GetItineraryError", "获取行程失败", e)
            null
        }
    }
    private lateinit var ivRefresh: ImageView
    private var isRefreshing = false  // 防止重复刷新
    // 初始化每日行程数据
    private fun initDayItineraries() {
        try {
            // 确保待安排项存在（后续会被填充逻辑覆盖）
            if (dayItineraries.none { it.dayNumber == 0 }) {
                dayItineraries.add(DayItinerary(0, pendingTagId))
            }
        } catch (e: Exception) {
            Log.e("InitDayError", "初始化行程数据失败", e)
        }
    }

    // 初始化上传工具
    private fun initUploader() {
        try {
            planUploader = TravelPlanUploader(this)
        } catch (e: Exception) {
            Log.e("InitUploaderError", "初始化上传工具失败", e)
        }
    }

    private fun initViews() {
        try {
            etTripName = findViewById(R.id.et_trip_name)
            etTripDate = findViewById(R.id.et_trip_date)
            hsTabs = findViewById(R.id.hs_tabs)
            scrollableTabsContainer = findViewById(R.id.scrollable_tabs_container)
            itineraryContainer = findViewById(R.id.itinerary_container)
            itineraryPendingHint = findViewById(R.id.itinerary_pending_hint)
            rvPlaces = findViewById(R.id.rv_pending_places)
            mapView = findViewById(R.id.mapView)
            ivRefresh = findViewById(R.id.iv_refresh)
            // 使用XML中定义的待安排标签ID
            pendingTagId = R.id.tab_pending

            selectTab(R.id.tab_overview)
        } catch (e: Exception) {
            Log.e("InitViewsError", "初始化控件失败", e)
            throw e
        }
    }

    // 初始化地点RecyclerView（只保留上下拖动排序）
    private fun initPlacesRecyclerView() {
        try {
            setupAdapter()

            // ItemTouchHelper配置（只支持上下拖动）
            val touchHelperCallback = object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val adapter = rvPlaces.adapter as? PendingPlaceAdapter ?: return false

                    val fromPos = viewHolder.adapterPosition
                    val toPos = target.adapterPosition

                    // 只有“景点行”可以互相交换
                    if (adapter.getItemViewType(fromPos) != PendingPlaceAdapter.VIEW_TYPE_PLACE ||
                        adapter.getItemViewType(toPos) != PendingPlaceAdapter.VIEW_TYPE_PLACE) {
                        return false
                    }

                    adapter.onItemMove(fromPos, toPos)
                    return true
                }

                override fun getMovementFlags(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ): Int {
                    val adapter = rvPlaces.adapter as? PendingPlaceAdapter
                    val pos = viewHolder.adapterPosition
                    val viewType = adapter?.getItemViewType(pos)

                    // 交通信息行禁止拖动
                    if (viewType == PendingPlaceAdapter.VIEW_TYPE_TRAFFIC) {
                        return makeMovementFlags(0, 0)
                    }

                    val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                    return makeMovementFlags(dragFlags, 0)
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            }

            itemTouchHelper = ItemTouchHelper(touchHelperCallback)
            itemTouchHelper?.attachToRecyclerView(rvPlaces)
        } catch (e: Exception) {
            Log.e("InitRecyclerViewError", "初始化RecyclerView失败", e)
        }
    }

    // 获取天数名称列表（用于弹窗显示）
    private fun getDayNamesList(): List<String> {
        val dayNames = mutableListOf<String>()
        try {
            // 按天数排序后生成名称列表
            val sortedDays = dayItineraries.sortedBy { it.dayNumber }
            sortedDays.forEachIndexed { index, dayItinerary ->
                if (dayItinerary.dayNumber == 0) {
                    dayNames.add("移动到待安排")
                } else {
                    dayNames.add("移动到第${dayItinerary.dayNumber}天")
                }
            }
        } catch (e: Exception) {
            Log.e("GetDayNamesError", "获取天数名称失败", e)
        }
        return dayNames
    }

    // 获取当前天在排序后的列表中的索引
    private fun getCurrentDayIndexInSortedList(): Int {
        try {
            val sortedDays = dayItineraries.sortedBy { it.dayNumber }
            val currentItinerary = getItineraryByIndex(currentDayIndex)
            return if (currentItinerary != null) {
                sortedDays.indexOfFirst { it.tabId == currentItinerary.tabId }
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e("GetDayIndexError", "获取天数索引失败", e)
            return 0
        }
    }

    // 设置适配器
    private fun setupAdapter() {
        try {
            val currentItinerary = getItineraryByIndex(currentDayIndex) ?: return

            val currentSortedIndex = getCurrentDayIndexInSortedList()

            placeAdapter = PendingPlaceAdapter(
                currentItinerary.places, // 绑定当前天数的places
                currentItinerary.dayNumber,
                currentSortedIndex,
                dragListener = { view ->
                    val viewHolder = rvPlaces.findContainingViewHolder(view)
                    viewHolder?.let { itemTouchHelper?.startDrag(it) }
                },
                onOrderChangedListener = {
                    syncRouteWithCurrentUI()
                    if (currentSelectedTabId == R.id.tab_overview) {
                        overviewUpdateHandler.postDelayed({ updateOverview() }, OVERVIEW_UPDATE_DELAY)
                    }
                    updateTravelPlanDebounced()
                },
                onDeleteListener = { place ->
                    syncRouteWithCurrentUI()
                    if (currentSelectedTabId == R.id.tab_overview) {
                        overviewUpdateHandler.postDelayed({ updateOverview() }, OVERVIEW_UPDATE_DELAY)
                    }
                    updateTravelPlanDebounced()
                },
                onMoveToDayListener = { place, targetSortedIndex ->
                    Handler(Looper.getMainLooper()).post {
                        movePlaceToDayBySortedIndex(place, targetSortedIndex)
                    }
                },
                getDayNames = { getDayNamesList() }
            )

            rvPlaces.layoutManager = LinearLayoutManager(this)
            rvPlaces.adapter = placeAdapter
        } catch (e: Exception) {
            Log.e("SetupAdapterError", "设置适配器失败", e)
        }
    }

    // 通过排序索引移动地点（修复：正确处理索引转换）

    // 通过“排序后的索引”移动地点
    private fun movePlaceToDayBySortedIndex(place: PendingPlace, targetSortedIndex: Int) {
        try {
            // 目标行程（按 dayNumber 排序后的）
            val targetItinerary = getItineraryBySortedIndex(targetSortedIndex)
            // 当前所在的行程（按原始索引）
            val fromItinerary = getItineraryByIndex(currentDayIndex)

            if (targetItinerary == null || fromItinerary == null ||
                targetItinerary.tabId == fromItinerary.tabId
            ) {
                // 自己移动到自己当天，或者目标无效，直接返回
                return
            }

            // 目标行程在原始列表中的索引
            val targetIndex = dayItineraries.indexOfFirst { it.tabId == targetItinerary.tabId }
            if (targetIndex == -1) return

            // 真正移动数据
            movePlaceToDay(place, currentDayIndex, targetIndex)

        } catch (e: Exception) {
            Log.e("MovePlaceByIndexError", "移动地点失败", e)
        }
    }


    // 将地点从一天移动到另一天（修复：统一处理移除和刷新）
    private fun movePlaceToDay(place: PendingPlace, fromDayIndex: Int, toDayIndex: Int) {
        if (fromDayIndex == toDayIndex) return

        val fromItinerary = getItineraryByIndex(fromDayIndex)
        val toItinerary = getItineraryByIndex(toDayIndex)

        if (fromItinerary == null || toItinerary == null) return

        synchronized(this) {
            try {
                val fromIndex = fromItinerary.places.indexOfFirst { it.id == place.id }
                if (fromIndex == -1) return

                // 先从原来的那一天移除
                val removed = fromItinerary.places.removeAt(fromIndex)
                // 再加到目标那一天最后
                toItinerary.places.add(removed)

                // 刷新列表 UI
                runOnUiThread {
                    placeAdapter?.notifyDataSetChanged()
                }

                // 只有当前显示的这一天受影响时，才需要重新规划路线
                if (fromDayIndex == currentDayIndex || toDayIndex == currentDayIndex) {
                    placeAdapter?.notifyOrderChanged()
                    syncRouteWithCurrentUI()
                }

                updateTravelPlanDebounced()

                runOnUiThread {
                    Toast.makeText(
                        this,
                        "已将${place.name}移动到${if (toItinerary.dayNumber == 0) "待安排" else "第${toItinerary.dayNumber}天"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("MovePlaceError", "移动地点失败", e)
                runOnUiThread {
                    Toast.makeText(this, "移动失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    // 初始化文本变化监听
    private fun initTextWatchers() {
        try {
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
                try {
                    val calendar = Calendar.getInstance()
                    DatePickerDialog(
                        this,
                        { _, year, month, day ->
                            val newDate = String.format("%04d-%02d-%02d", year, month + 1, day)
                            etTripDate.setText(newDate)

                            // 日期变化，重新查询所有天数的天气
                            dayItineraries.filter { it.dayNumber > 0 }.forEach {
                                queryDayWeather(it)
                            }

                            // 只有在总览标签下才更新总览
                            if (currentSelectedTabId == R.id.tab_overview) {
                                overviewUpdateHandler.removeCallbacksAndMessages(null)
                                overviewUpdateHandler.postDelayed({
                                    updateOverview()
                                }, OVERVIEW_UPDATE_DELAY)
                            }

                            updateTravelPlanDebounced()
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    ).show()
                } catch (e: Exception) {
                    Log.e("DatePickerError", "日期选择失败", e)
                }
            }
        } catch (e: Exception) {
            Log.e("InitTextWatcherError", "初始化文本监听失败", e)
        }
    }

    // 切换到指定天数
    private fun switchToDay(index: Int) {
        try {
            if (index < 0 || index >= dayItineraries.size) return

            currentDayIndex = index
            val day = dayItineraries[index]
            selectTab(day.tabId)
            setupAdapter()

            // 滚动到顶部
            val scrollView = findViewById<ScrollView>(R.id.itinerary_scroll_view)
            scrollView?.scrollTo(0, 0)
        } catch (e: Exception) {
            Log.e("SwitchDayError", "切换天数失败", e)
        }
    }

    private fun initEvents() {
        try {
            // 返回按钮
            findViewById<ImageView>(R.id.iv_back)?.setOnClickListener { finish() }
            findViewById<ImageView>(R.id.iv_show_markdown)?.setOnClickListener {
                showMarkdownDialog()
            }
            // 保存按钮（手动触发上传）

            // 添加天数
            findViewById<ImageView>(R.id.iv_add_day)?.setOnClickListener {
                if (it.isClickable) {
                    it.isClickable = false
                    addNewDay()
                    it.postDelayed({ it.isClickable = true }, 500)
                }
            }
            ivRefresh.setOnClickListener {
                if (isCollaborationMode && currentPlanId.isNotEmpty() && !isRefreshing) {
                    refreshCollaborationPlan()
                } else if (!isCollaborationMode) {
                    Toast.makeText(this, "非协作模式无需刷新", Toast.LENGTH_SHORT).show()
                }
            }
            // 添加行程项（跳转到搜索页面）
            searchPlaceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                try {
                    if (result.resultCode == RESULT_OK) {
                        val data = result.data ?: return@registerForActivityResult
                        val selectedPois: ArrayList<PoiItem>? =
                            data.getParcelableArrayListExtra("selected_pois")

                        if (!selectedPois.isNullOrEmpty()) {
                            selectedPois.forEach { poi ->
                                mapController?.addScenicFromPoi(poi)
                                // 添加到待安排
                                val pendingItinerary = getPendingItinerary()
                                pendingItinerary?.places?.add(
                                    PendingPlace(
                                        name = poi.title ?: "",
                                        address = poi.snippet ?: "",
                                        rating = "",
                                        tag1 = poi.typeDes ?: "",
                                        tag2 = poi.cityName ?: "",
                                        latitude = poi.latLonPoint?.latitude,
                                        longitude = poi.latLonPoint?.longitude,
                                        poiId = poi.poiId
                                    )
                                )


                                // 提取城市
                                if (poi.cityName?.isNotEmpty() == true) {
                                    tripCity = poi.cityName!!
                                    // 更新所有天气（城市变化）
                                    dayItineraries.filter { it.dayNumber > 0 }.forEach {
                                        queryDayWeather(it)
                                    }
                                }
                            }
                            if (currentDayIndex == dayItineraries.indexOfFirst { it.dayNumber == 0 }) {
                                placeAdapter?.refreshData() // 刷新数据
                            }
                            // ⭐ 新增：添加完景点后，同步一次当前页面的路线
                            syncRouteWithCurrentUI()
                            // 只有在总览标签下才更新总览
                            if (currentSelectedTabId == R.id.tab_overview) {
                                overviewUpdateHandler.removeCallbacksAndMessages(null)
                                overviewUpdateHandler.postDelayed({
                                    updateOverview()
                                }, OVERVIEW_UPDATE_DELAY)
                            }
                            selectTab(pendingTagId)
                            Toast.makeText(this, "已添加${selectedPois.size}个地点", Toast.LENGTH_SHORT).show()
                            updateTravelPlanDebounced()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SearchResultError", "处理搜索结果失败", e)
                }
            }

            findViewById<ImageView>(R.id.iv_add_item)?.setOnClickListener {
                try {
                    val intent = Intent(this, SearchPlaceActivity::class.java)
                    searchPlaceLauncher.launch(intent)
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                } catch (e: Exception) {
                    Log.e("LaunchSearchError", "启动搜索失败", e)
                    Toast.makeText(this, "启动搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            // 标签点击事件
            setupInitialTabClicks()
        } catch (e: Exception) {
            Log.e("InitEventsError", "初始化事件失败", e)
        }
    }
    private fun refreshCollaborationPlan() {
        isRefreshing = true
        ivRefresh.isClickable = false
        ivRefresh.setImageResource(android.R.drawable.progress_indeterminate_horizontal)  // 显示加载状态

        // 1. 停止旧的协作监听
        collaborationManager?.stopListening()

        // 2. 重新加载行程数据（复用输入协作码的加载逻辑）
        loadExistingTripPlan(currentPlanId)

        // 3. 加载完成后恢复状态并重启监听
        Handler(Looper.getMainLooper()).postDelayed({
            // 重启协作监听
            startCollaborationListening(currentPlanId)

            // 恢复按钮状态
            isRefreshing = false
            ivRefresh.isClickable = true
            ivRefresh.setImageResource(android.R.drawable.ic_popup_sync)

            Toast.makeText(this, "行程已刷新", Toast.LENGTH_SHORT).show()
        }, 1000)  // 延迟避免UI闪烁
    }
    // 构建当前行程数据
    private fun buildCurrentTravelPlan(): com.example.plan.TravelPlan? {
        try {
            // 1. 提取基础数据并打印
            val tripName = etTripName.text.toString().takeIf { it.isNotEmpty() } ?: "未命名行程"
            val startDateStr = etTripDate.text.toString()
            val startDate = try {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(startDateStr)
            } catch (e: Exception) {
                Log.w("BuildPlan", "日期解析失败，使用当前日期：${e.message}")
                Date()
            }
            val endDate = Calendar.getInstance().apply {
                time = startDate
                add(Calendar.DAY_OF_YEAR, dayCount - 1)
            }.time
            val destination = getDestinationFromItinerary() ?: "北京"

            Log.d("BuildPlan", "===== 基础数据 =====")
            Log.d("BuildPlan", "行程名称：$tripName")
            Log.d("BuildPlan", "目的地：$destination")
            Log.d("BuildPlan", "天数：$dayCount，开始日期：$startDateStr，结束日期：${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(endDate)}")
            Log.d("BuildPlan", "dayItineraries总数：${dayItineraries.size}，内容：${dayItineraries.map { "dayNumber=${it.dayNumber}, places=${it.places.size}" }}")

            // 2. 获取 Java 类的 Class 对象并打印
            val activityClass = Class.forName("com.example.plan.Activity")
            val dayClass = Class.forName("com.example.plan.Day")
            val contentClass = Class.forName("com.example.plan.Content")
            val travelPlanClass = Class.forName("com.example.plan.TravelPlan")
            Log.d("BuildPlan", "===== 反射类加载 =====")
            Log.d("BuildPlan", "Activity类：${activityClass.name}")
            Log.d("BuildPlan", "Day类：${dayClass.name}")
            Log.d("BuildPlan", "Content类：${contentClass.name}")
            Log.d("BuildPlan", "TravelPlan类：${travelPlanClass.name}")

            // 只处理有效天数（dayNumber>0），过滤待安排
            val validDays = dayItineraries.filter { it.dayNumber > 0 }
            Log.d("BuildPlan", "===== 有效行程筛选 =====")
            Log.d("BuildPlan", "有效行程数量（dayNumberq>0）：${validDays.size}，内容：${validDays.map { "dayNumber=${it.dayNumber}, places=${it.places.size}" }}")

            val dayList = java.util.ArrayList<Any>(validDays.size)

            validDays.forEachIndexed { dayIdx, dayItinerary ->
                Log.d("BuildPlan", "===== 处理第${dayIdx}个有效行程（dayNumber=${dayItinerary.dayNumber}） =====")
                Log.d("BuildPlan", "该行程景点数量：${dayItinerary.places.size}，景点列表：${dayItinerary.places.map { it.name }}")

                val activities = java.util.ArrayList<Any>(dayItinerary.places.size)

                dayItinerary.places.forEachIndexed { placeIdx, place ->
                    val time = when (placeIdx) { 0 -> "上午"; 1 -> "下午"; else -> "晚上" }
                    Log.d("BuildPlan", "处理景点$placeIdx：名称=${place.name}，时间=$time")

                    // 反射创建 Activity 实例
                    val activityConstructor = activityClass.getDeclaredConstructor(String::class.java, String::class.java)
                    activityConstructor.isAccessible = true
                    val activityInstance = activityConstructor.newInstance(time, place.name)
                    activities.add(activityInstance)
                    Log.d("BuildPlan", "成功创建Activity实例：time=$time, location_name=${place.name}")
                }

                Log.d("BuildPlan", "第${dayIdx}个行程生成的Activity数量：${activities.size}")

                // 反射创建 Day 实例
                val dayConstructor = dayClass.getDeclaredConstructor(Int::class.java, java.util.List::class.java)
                dayConstructor.isAccessible = true
                val dayInstance = dayConstructor.newInstance(dayItinerary.dayNumber, activities)
                dayList.add(dayInstance)
                Log.d("BuildPlan", "成功创建Day实例：dayNumber=${dayItinerary.dayNumber}，添加到dayList后总数=${dayList.size}")
            }
            val transportClass = Class.forName("com.example.plan.Transport") // 确保包名正确
                // 找到Transport的构造函数：参数为int和String
            val transportConstructor = transportClass.getDeclaredConstructor(
                Int::class.java,    // 对应time参数（int类型）
                String::class.java  // 对应currency参数（String类型）
            )
            transportConstructor.isAccessible = true
            // 传入具体参数值创建实例（比如time=60, currency="CNY"，可根据需求调整）
            val transportInstance = transportConstructor.newInstance(60, "CNY")
            Log.d("BuildPlan", "===== dayList最终结果 =====")
            Log.d("BuildPlan", "dayList总数量：${dayList.size}")
            if (dayList.isEmpty()) {
                Log.e("BuildPlan", "警告：dayList为空！没有任何有效行程数据被添加")
            }

            // 构建 Content 实例
            Log.d("BuildPlan", "===== 构建Content实例 =====")
            val contentConstructor = contentClass.getDeclaredConstructor(
                String::class.java,          // destination
                Date::class.java,            // startDate
                Date::class.java,            // endDate
                java.util.List::class.java,  // days（List<Day>）
                transportClass,              // transport（Transport类型）
                String::class.java           // notes
            )
            contentConstructor.isAccessible = true
            val contentInstance = contentConstructor.newInstance(
                destination,
                startDate,
                endDate,
                dayList,        // 第四个参数：days
                transportInstance, // 第五个参数：Transport实例（已正确创建）
                "自动生成的行程计划" // 第六个参数：notes
            )
            Log.d("BuildPlan", "成功创建Content实例")

            // 反射获取Content的days字段并打印
            val contentDaysMethod = contentClass.getMethod("getDays")
            val contentDays = contentDaysMethod.invoke(contentInstance) as List<*>
            Log.d("BuildPlan", "Content中的days数量：${contentDays.size}")
            if (contentDays.isNotEmpty()) {
                contentDays.forEachIndexed { idx, day ->
                    val dayNumberMethod = dayClass.getMethod("getDayNumber")
                    val dayActivitiesMethod = dayClass.getMethod("getActivities")
                    val dayNumber = dayNumberMethod.invoke(day) as Int
                    val dayActivities = dayActivitiesMethod.invoke(day) as List<*>
                    Log.d("BuildPlan", "Content中第${idx}个Day：dayNumber=$dayNumber，Activities数量=${dayActivities.size}")
                }
            }
            // 获取Collaborator和VersionHistory类（用于反射确认类型，传空列表时可省略，但建议获取）
            val collaboratorClass = Class.forName("com.example.plan.Collaborator")
            val versionHistoryClass = Class.forName("com.example.plan.VersionHistory")

// 构造空列表（反射中泛型擦除，List<Collaborator>用List类型即可）
            val emptyCollaborators = java.util.ArrayList<Any>() // 或 Collections.emptyList()
            val emptyVersionHistory = java.util.ArrayList<Any>()
            val emptyTags = java.util.ArrayList<String>()

            // 构建 TravelPlan 实例
            Log.d("BuildPlan", "===== 构建TravelPlan实例 =====")
            val planConstructor = travelPlanClass.getDeclaredConstructor(
                String::class.java,                  // 1. planId
                String::class.java,                  // 2. title
                Int::class.java,                     // 3. creatorId
                java.util.List::class.java,          // 4. collaborators (List<Collaborator>)
                String::class.java,                  // 5. status
                contentClass,                        // 6. content
                java.util.List::class.java,          // 7. versionHistory (List<VersionHistory>)
                java.util.List::class.java,          // 8. tags (List<String>)
                Date::class.java,                    // 9. createdAt
                Date::class.java                     // 10. updatedAt
            )
            planConstructor.isAccessible = true

// 创建TravelPlan实例（按顺序传入参数）
            val planInstance = planConstructor.newInstance(
                currentPlanId,                       // 1. planId
                tripName,                            // 2. title
                currentUserId,                       // 3. creatorId
                emptyCollaborators,                  // 4. collaborators（空列表）
                "draft",                             // 5. status
                contentInstance,                     // 6. content
                emptyVersionHistory,                 // 7. versionHistory（空列表）
                emptyTags,                           // 8. tags（空列表）
                Date(),                              // 9. createdAt
                Date()                               // 10. updatedAt
            ) as com.example.plan.TravelPlan
            Log.d("BuildPlan", "成功创建TravelPlan实例：planId=${planInstance.planId}")

            // 最终校验并打印JSON
            val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
            val planJson = gson.toJson(planInstance)
            Log.d("BuildPlanFinal", "最终生成的TravelPlan JSON：\n$planJson")

            return planInstance
        } catch (e: Exception) {
            Log.e("BuildPlanError", "构建行程失败：${e.message}", e)
            return null
        }
    }

    private fun getDestinationFromItinerary(): String? {
        return dayItineraries.firstOrNull { it.dayNumber == 1 }?.places?.firstOrNull()?.tag2
            ?: getPendingItinerary()?.places?.firstOrNull()?.tag2
    }


    // 防抖上传行程
    private fun updateTravelPlanDebounced() {
        try {
            updateHandler.removeCallbacksAndMessages(null)
            updateHandler.postDelayed({
                updateTravelPlan()
            }, DEBOUNCE_DELAY)
        } catch (e: Exception) {
            Log.e("DebounceError", "防抖处理失败", e)
        }
    }

    /*// 实际上传行程
    private fun updateTravelPlan() {
        try {
            val plan = buildCurrentTravelPlan() ?: return
            // 改用你封装好的 TravelPlanManager（而不是直接用 planUploader）
            val planManager = TravelPlanManager.getInstance(applicationContext)
            planManager.updateTravelPlan(
                currentPlanId, // 计划ID
                plan,          // 更新后的计划对象
                object : TravelPlanUploader.UploadCallback {
                    override fun onSuccess(savedPlan: TravelPlan) {
                        currentPlanId = savedPlan.planId
                        Log.d("PlanUpdate", "行程更新成功: ${savedPlan.planId}")
                    }

                    override fun onFailure(errorMsg: String) {
                        Log.e("PlanUpdate", "行程更新失败: $errorMsg")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("UpdatePlanError", "更新行程失败", e)
        }
    }*/

    // 计算第n天的日期（基于出发日期）
    private fun calculateDayDate(dayOffset: Int): String {
        return try {
            val startDateStr = etTripDate.text.toString()
            val startDate = if (startDateStr.isNotEmpty()) {
                sdf.parse(startDateStr)
            } else {
                Date()
            }
            val calendar = Calendar.getInstance()
            calendar.time = startDate
            calendar.add(Calendar.DAY_OF_YEAR, dayOffset - 1) // dayNumber从1开始
            sdf.format(calendar.time)
        } catch (e: Exception) {
            sdf.format(Date())
        }
    }

    // 查询指定天数的天气
    private fun queryDayWeather(dayItinerary: DayItinerary) {
        if (dayItinerary.dayNumber == 0) return // 待安排不查询天气

        val targetDate = calculateDayDate(dayItinerary.dayNumber)
        dayItinerary.weatherLoading = true
        dayItinerary.weatherError = null

        // 更新UI
        if (currentSelectedTabId == dayItinerary.tabId || currentSelectedTabId == R.id.tab_overview) {
            updateWeatherDisplay(dayItinerary)
        }

        // 查询天气
        weatherController.query(tripCity, targetDate, object : WeatherController.OnWeatherResultListener {
            override fun onLoading(show: Boolean) {
                dayItinerary.weatherLoading = show
                updateWeatherDisplay(dayItinerary)
            }

            override fun onSuccess(data: WeatherData) {
                dayItinerary.weatherData = data
                dayItinerary.weatherLoading = false
                updateWeatherDisplay(dayItinerary)
            }

            override fun onError(msg: String) {
                dayItinerary.weatherError = msg
                dayItinerary.weatherLoading = false
                updateWeatherDisplay(dayItinerary)
            }
        })
    }

    // 更新天气显示
    private fun updateWeatherDisplay(dayItinerary: DayItinerary) {
        // 只有在总览标签下才更新总览
        if (currentSelectedTabId == R.id.tab_overview) {
            overviewUpdateHandler.removeCallbacksAndMessages(null)
            overviewUpdateHandler.postDelayed({
                try {
                    runOnUiThread {
                        updateOverview()
                    }
                } catch (e: Exception) {
                    Log.e("WeatherUpdateError", "更新天气显示失败", e)
                }
            }, OVERVIEW_UPDATE_DELAY)
        } else if (currentSelectedTabId == dayItinerary.tabId) {
            // 具体天数页面刷新
            runOnUiThread {
                val weatherView = itineraryContainer.findViewWithTag<View>("weather_${dayItinerary.dayNumber}")
                weatherView?.let { updateWeatherView(it, dayItinerary) }
            }
        }
    }

    // 更新单个天气视图
    private fun updateWeatherView(weatherView: View, dayItinerary: DayItinerary) {
        val ivIcon = weatherView.findViewById<ImageView>(R.id.iv_weather_icon)
        val tvDate = weatherView.findViewById<TextView>(R.id.tv_weather_date)
        val tvStatus = weatherView.findViewById<TextView>(R.id.tv_weather_status)
        val tvTemp = weatherView.findViewById<TextView>(R.id.tv_weather_temp)
        val tvWind = weatherView.findViewById<TextView>(R.id.tv_weather_wind)
        val progress = weatherView.findViewById<ProgressBar>(R.id.progress_weather)
        val tvError = weatherView.findViewById<TextView>(R.id.tv_weather_error)

        // 确保所有视图都正确初始化
        if (ivIcon == null || tvDate == null || tvStatus == null ||
            tvTemp == null || tvWind == null || progress == null || tvError == null) {
            Log.e("WeatherViewError", "天气视图组件初始化失败")
            return
        }

        if (dayItinerary.weatherLoading) {
            ivIcon.visibility = View.GONE
            tvDate.visibility = View.GONE
            tvStatus.visibility = View.GONE
            tvTemp.visibility = View.GONE
            tvWind.visibility = View.GONE
            tvError.visibility = View.GONE
            progress.visibility = View.VISIBLE
        } else if (dayItinerary.weatherError != null) {
            ivIcon.visibility = View.GONE
            tvDate.visibility = View.GONE
            tvStatus.visibility = View.GONE
            tvTemp.visibility = View.GONE
            tvWind.visibility = View.GONE
            progress.visibility = View.GONE
            tvError.visibility = View.VISIBLE
            tvError.text = dayItinerary.weatherError
        } else if (dayItinerary.weatherData != null) {
            val weather = dayItinerary.weatherData!!
            ivIcon.visibility = View.VISIBLE
            tvDate.visibility = View.VISIBLE
            tvStatus.visibility = View.VISIBLE
            tvTemp.visibility = View.VISIBLE
            tvWind.visibility = View.VISIBLE
            progress.visibility = View.GONE
            tvError.visibility = View.GONE

            tvDate.text = weather.date
            tvStatus.text = weather.weather
            tvTemp.text = weather.temperature
            tvWind.text = " | ${weather.wind}"

            // 根据天气状况设置图标
            when {
                weather.weather.contains("晴") -> ivIcon.setImageResource(android.R.drawable.ic_menu_myplaces)
                weather.weather.contains("雨") -> ivIcon.setImageResource(android.R.drawable.ic_menu_send)
                weather.weather.contains("云") -> ivIcon.setImageResource(android.R.drawable.ic_menu_add)
                weather.weather.contains("阴") -> ivIcon.setImageResource(android.R.drawable.ic_menu_call)
                else -> ivIcon.setImageResource(android.R.drawable.ic_menu_myplaces)
            }
        }
    }

    // 更新总览显示（优化版本）
    private fun updateOverview() {
        // 防止并发更新
        if (isUpdatingOverview) return

        // 确保只在总览标签下执行
        if (currentSelectedTabId != R.id.tab_overview) return

        // 确保在主线程执行
        if (!Looper.myLooper()?.isCurrentThread()!!) {
            runOnUiThread { updateOverview() }
            return
        }

        try {
            isUpdatingOverview = true

            // ========== 关键：先清空总览容器 ==========
            itineraryContainer.removeAllViews()

            // 检查容器是否可用
            if (itineraryContainer == null || itineraryContainer.windowToken == null) {
                isUpdatingOverview = false
                return
            }

            // 按天数排序（1,2,3...天，待安排最后）
            val sortedDays = dayItineraries.sortedBy { it.dayNumber }

            // 添加每日行程到总览
            sortedDays.forEach { dayItinerary ->
                if (dayItinerary.dayNumber == 0) return@forEach // 跳过待安排，最后单独处理

                try {
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

                    // 添加天数标题行（包含天气按钮）
                    val titleLayout = LinearLayout(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                    }

                    titleLayout.addView(TextView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                        text = "第${dayItinerary.dayNumber}天安排："
                        textSize = 14f
                        setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black))
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    })

                    // 刷新天气按钮
                    titleLayout.addView(ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            dpToPx(24),
                            dpToPx(24)
                        ).apply { marginStart = dpToPx(8) }
                        setImageResource(android.R.drawable.ic_input_add)
                        setColorFilter(ContextCompat.getColor(this@CreateTripActivity, R.color.black))
                        setOnClickListener {
                            queryDayWeather(dayItinerary) // 手动刷新天气
                        }
                    })

                    overviewLayout.addView(titleLayout)

                    // 添加天气显示板块
                    val weatherView = LayoutInflater.from(this)
                        .inflate(R.layout.item_weather, overviewLayout, false)
                    weatherView.tag = "overview_weather_${dayItinerary.dayNumber}"
                    overviewLayout.addView(weatherView)
                    updateWeatherView(weatherView, dayItinerary)

                    // 如果还没查询过天气，自动查询
                    if (dayItinerary.weatherData == null && !dayItinerary.weatherLoading) {
                        queryDayWeather(dayItinerary)
                    }

                    // 行程内容
                    if (dayItinerary.places.isNotEmpty()) {
                        // 有行程安排的情况
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
                    } else {
                        // 没有行程安排的情况
                        overviewLayout.addView(TextView(this).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = dpToPx(4) }
                            text = "待安排行程"
                            textSize = 14f
                            setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.text_gray))
                        })
                    }

                    // 添加到容器
                    itineraryContainer.addView(overviewLayout)

                } catch (e: Exception) {
                    Log.e("AddDayOverviewError", "添加第${dayItinerary.dayNumber}天总览失败", e)
                }
            }

            // 最后添加待安排区域（确保始终在最下方）
            val pendingItinerary = getPendingItinerary()
            pendingItinerary?.let {
                try {
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

                    if (it.places.isNotEmpty()) {
                        it.places.forEachIndexed { index, place ->
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
                    } else {
                        overviewLayout.addView(TextView(this).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = dpToPx(4) }
                            text = "暂无待安排地点"
                            textSize = 14f
                            setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.text_gray))
                        })
                    }

                    itineraryContainer.addView(overviewLayout)
                } catch (e: Exception) {
                    Log.e("AddPendingOverviewError", "添加待安排总览失败", e)
                }
            }

        } catch (e: Exception) {
            Log.e("UpdateOverviewError", "更新总览失败", e)
        } finally {
            isUpdatingOverview = false
        }
    }

    // 添加新天数（修复初始行程不为空的问题）
    private fun addNewDay() {
        try {
            dayCount++
            val newDayTabId = View.generateViewId()

            // 创建新标签
            val dayTab = TextView(this).apply {
                id = newDayTabId
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(36)
                ).apply {
                    marginStart = dpToPx(8)
                }
                text = "第${dayCount}天"
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@CreateTripActivity, R.color.black))
                setBackgroundResource(R.drawable.bg_tab_unselected)
                setPadding(dpToPx(16), 0, dpToPx(16), 0)
                gravity = Gravity.CENTER
                isClickable = true
            }

            // 添加到标签容器（待安排标签前）
            val pendingTabIndex = scrollableTabsContainer.indexOfChild(findViewById(pendingTagId))
            if (pendingTabIndex != -1) {
                scrollableTabsContainer.addView(dayTab, pendingTabIndex)
            } else {
                scrollableTabsContainer.addView(dayTab)
            }

            dayTab.setOnClickListener {
                val dayIndex = dayItineraries.indexOfFirst { it.tabId == newDayTabId }
                if (dayIndex != -1) switchToDay(dayIndex)
            }

            // 添加到数据列表（待安排前）
            val pendingIndex = dayItineraries.indexOfFirst { it.dayNumber == 0 }
            val newDayItinerary = DayItinerary(dayCount, newDayTabId)
            if (pendingIndex != -1) {
                // 新添加的天数默认空行程
                dayItineraries.add(pendingIndex, newDayItinerary)
            } else {
                dayItineraries.add(newDayItinerary)
            }

            selectTab(newDayTabId)
            scrollToTab(dayTab)
            Toast.makeText(this, "已添加第${dayCount}天", Toast.LENGTH_SHORT).show()

            // 查询新天数的天气
            queryDayWeather(newDayItinerary)

            // 只有在总览标签下才更新总览
            if (currentSelectedTabId == R.id.tab_overview) {
                overviewUpdateHandler.removeCallbacksAndMessages(null)
                overviewUpdateHandler.postDelayed({
                    updateOverview()
                }, OVERVIEW_UPDATE_DELAY)
            }

            updateTravelPlanDebounced()
        } catch (e: Exception) {
            Log.e("AddNewDayError", "添加新天数失败", e)
            Toast.makeText(this, "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 标签切换逻辑
    private fun selectTab(selectedTabId: Int) {
        try {
            currentSelectedTabId = selectedTabId

            // 重置所有标签样式
            findViewById<TextView>(R.id.tab_overview)?.apply {
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
            if (currentDayIndex == -1) currentDayIndex = 0

            // ========== 关键修复：先清空容器 ==========
            itineraryContainer.removeAllViews()

            // 根据选中的标签显示内容
            when (selectedTabId) {
                R.id.tab_overview -> {
                    itineraryPendingHint.visibility = View.GONE
                    rvPlaces.visibility = View.GONE
                    overviewUpdateHandler.postDelayed({ updateOverview() }, 100)
                }

                else -> {
                    // 具体天数/待安排：统一用RecyclerView显示
                    val currentItinerary = getItineraryByIndex(currentDayIndex)
                    if (currentItinerary?.dayNumber ?: 0 > 0) {
                        // 添加天气板块（保留）
                        val weatherView = LayoutInflater.from(this)
                            .inflate(R.layout.item_weather, itineraryContainer, false)
                        weatherView.tag = "weather_${currentItinerary?.dayNumber}"
                        weatherView.layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dpToPx(8) }
                        itineraryContainer.addView(weatherView)
                        currentItinerary?.let { updateWeatherView(weatherView, it) }
                        if (currentItinerary?.weatherData == null && currentItinerary?.weatherLoading == false) {
                            currentItinerary?.let { queryDayWeather(it) }
                        }
                    }
                    // 添加RecyclerView到容器（确保结构正确）
                    itineraryContainer.addView(rvPlaces)
                    // 显示RecyclerView并刷新适配器
                    updateItineraryDisplay()
                    syncRouteWithCurrentUI() // 同步地图路线
                }
            }
        } catch (e: Exception) {
            Log.e("SelectTabError", "切换标签失败", e)
        }
    }

    private fun scrollToTab(tab: View) {
        try {
            hsTabs.scrollTo(tab.left - dpToPx(16), 0)
        } catch (e: Exception) {
            Log.e("ScrollToTabError", "滚动标签失败", e)
        }
    }

    private fun setupInitialTabClicks() {
        try {
            findViewById<TextView>(R.id.tab_overview)?.setOnClickListener { selectTab(R.id.tab_overview) }
            findViewById<TextView>(R.id.tab_day_1)?.setOnClickListener { selectTab(R.id.tab_day_1) }
            findViewById<TextView>(R.id.tab_pending)?.setOnClickListener { selectTab(pendingTagId) }
        } catch (e: Exception) {
            Log.e("SetupTabsError", "设置标签点击事件失败", e)
        }
    }

    // 地图生命周期管理
    override fun onResume() {
        super.onResume()
        try {
            mapController?.onResume()

            // 恢复协作监听
            if (isCollaborationMode && !currentPlanId.isNullOrEmpty()) {
                startCollaborationListening(currentPlanId)
            }
        } catch (e: Exception) {
            Log.e("MapResumeError", "地图恢复失败", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            mapController?.onPause()
        } catch (e: Exception) {
            Log.e("MapPauseError", "地图暂停失败", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mapController?.onDestroy()
            updateHandler.removeCallbacksAndMessages(null)
            overviewUpdateHandler.removeCallbacksAndMessages(null)

            // 清理协作资源
            collaborationManager?.stopListening()
            collaborationManager?.cleanup()
        } catch (e: Exception) {
            Log.e("MapDestroyError", "地图销毁失败", e)
        }
    }
    /*override fun onResume() {
        super.onResume()
        try {
            mapController?.onResume()
        } catch (e: Exception) {
            Log.e("MapResumeError", "地图恢复失败", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            mapController?.onPause()
        } catch (e: Exception) {
            Log.e("MapPauseError", "地图暂停失败", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mapController?.onDestroy()
            updateHandler.removeCallbacksAndMessages(null)
            overviewUpdateHandler.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            Log.e("MapDestroyError", "地图销毁失败", e)
        }
    }*/

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        try {
            mapController?.onSaveInstanceState(outState)
        } catch (e: Exception) {
            Log.e("MapSaveError", "保存地图状态失败", e)
        }
    }

    // 上传工具类
    private fun showMarkdownDialog() {
        try {
            val dialog = AlertDialog.Builder(this).create()
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_markdown, null)
            val tvMarkdown = dialogView.findViewById<TextView>(R.id.tv_markdown)
            val btnClose = dialogView.findViewById<Button>(R.id.btn_close_dialog)

            // 初始化Markwon
            val markwon = Markwon.create(this)

            // 为TextView启用滚动（否则长内容无法滑动）
            tvMarkdown.movementMethod = ScrollingMovementMethod()

            // 渲染Markdown到TextView
            markwon.setMarkdown(tvMarkdown, markdownContent)

            // 关闭按钮事件
            btnClose.setOnClickListener {
                dialog.dismiss()
            }

            // 设置弹窗
            dialog.setView(dialogView)
            dialog.setCanceledOnTouchOutside(true)
            dialog.show()

            // 调整弹窗大小
            val window = dialog.window
            window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(),
                (resources.displayMetrics.heightPixels * 0.8).toInt())
            window?.setGravity(Gravity.CENTER)
        } catch (e: Exception) {
            Log.e("MarkdownDialogError", "显示弹窗失败", e)
            Toast.makeText(this, "显示旅行计划失败", Toast.LENGTH_SHORT).show()
        }
    }
    private fun parseMarkdownContent(): Pair<String, Map<Int, List<String>>> {
        val lines = markdownContent.split("\n")
        var contentBeforeAt = markdownContent // 默认显示全部（无@行时）
        val dayAttractionsMap = mutableMapOf<Int, List<String>>()

        // 查找@开头的行
        val atLineIndex = lines.indexOfFirst { it.trimStart().startsWith("@") }
        if (atLineIndex != -1) {
            // 提取@之前的正文内容
            contentBeforeAt = lines.take(atLineIndex).joinToString("\n")
            // 提取@行的景点内容（去掉@和前后空格）
            val atLineContent = lines[atLineIndex].trimStart().substringAfter("@").trim()
            // 解析每天景点映射
            dayAttractionsMap.putAll(parseDayAttractions(atLineContent))
        }

        return Pair(contentBeforeAt, dayAttractionsMap)
    }

    // 解析@行中的「天数:景点」格式，返回<天数, 景点列表>
    private fun parseDayAttractions(atLineContent: String): Map<Int, List<String>> {
        val dayMap = mutableMapOf<Int, List<String>>()
        if (atLineContent.isEmpty()) return dayMap

        // 中文数字转阿拉伯数字的映射
        val chineseNumMap = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4,
            "五" to 5, "六" to 6, "七" to 7, "八" to 8, "九" to 9
        )

        // 按分号分割每天条目（处理空格）
        val dayEntries = atLineContent.split(";").map { it.trim() }.filter { it.isNotEmpty() }

        for (entry in dayEntries) {
            // 按冒号分割天数和景点（只分割一次）
            val (dayStr, attractionsStr) = entry.split(":", limit = 2).map { it.trim() }
            if (dayStr.isBlank() || attractionsStr.isBlank()) continue

            // 提取中文天数（如「第一天」→「一」）
            val chineseNum = dayStr.replace("第", "").replace("天", "").trim()
            // 转换为阿拉伯数字
            val dayNumber = chineseNumMap[chineseNum] ?: continue

            // 按顿号分割景点列表
            val attractions = attractionsStr.split("、").map { it.trim() }.filter { it.isNotEmpty() }
            if (attractions.isNotEmpty()) {
                dayMap[dayNumber] = attractions
            }
        }
        return dayMap
    }
    private fun populateItinerariesFromAttractions(dayAttractionsMap: Map<Int, List<String>>) {
        // 清空现有行程（保留待安排项）
        dayItineraries.clear()
        dayItineraries.add(DayItinerary(0, pendingTagId)) // 待安排

        if (dayAttractionsMap.isEmpty()) {
            // 默认第一天使用XML中的固定ID
            dayItineraries.add(DayItinerary(1, R.id.tab_day_1))
            dayCount = 1
            return
        }

        // 按天数排序并创建行程
        val sortedDays = dayAttractionsMap.keys.sorted()
        dayCount = sortedDays.last()

        for (dayNumber in sortedDays) {
            // 第一天使用固定ID，后续动态生成
            val tabId = if (dayNumber == 1) R.id.tab_day_1 else View.generateViewId()
            val dayItinerary = DayItinerary(dayNumber, tabId)
            val attractions = dayAttractionsMap[dayNumber] ?: emptyList()

            // 填充景点数据
            attractions.forEachIndexed { idx, attraction ->
                val timeTag = when (idx % 3) { 0 -> "上午"; 1 -> "下午"; else -> "晚上" }
                val address = getAttractionAddress(attraction)
                dayItinerary.places.add(
                    PendingPlace(
                        name = attraction,
                        address = address,
                        rating = "4.7",
                        tag1 = timeTag,
                        tag2 = "三亚",
                        id = UUID.randomUUID().toString()
                    )
                )
            }
            dayItineraries.add(dayItinerary)
        }
    }
    private fun getAttractionAddress(attraction: String): String {
        return when (attraction) {
            "三亚湾" -> "海南省三亚市三亚湾路"
            "第一市场" -> "海南省三亚市天涯区新建街155号"
            "解放路步行街" -> "海南省三亚市天涯区解放路"
            "蜈支洲岛" -> "海南省三亚市海棠区蜈支洲岛"
            "海棠湾免税城" -> "海南省三亚市海棠区海棠湾镇"
            "呀诺达热带雨林" -> "海南省三亚市保亭县三道镇"
            "鹿回头风景区" -> "海南省三亚市吉阳区鹿岭路"
            else -> "海南省三亚市$attraction"
        }
    }
}