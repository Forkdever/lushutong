package com.example.lushutong

import android.app.Activity
import android.app.DatePickerDialog
import io.noties.markwon.Markwon
import android.text.method.ScrollingMovementMethod
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.maps.MapView
import com.amap.api.services.core.PoiItem
import com.llw.newmapdemo.R
import com.llw.newmapdemo.TravelMapController
import com.llw.newmapdemo.WeatherController
import com.llw.newmapdemo.WeatherData
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.*
import java.util.Collections

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
        // 安全检查：确保position有效
        if (position < 0 || position >= placeList.size) {
            return
        }

        val place = placeList[position]
        holder.tvName.text = place.name
        holder.tvAddress.text = place.address
        holder.tvRating.text = place.rating
        holder.tvTag1.text = place.tag1
        holder.tvTag2.text = place.tag2

        // 拖动图标触摸事件
        holder.ivDrag.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                dragListener(holder.ivDrag)
            }
            false
        }

        // 删除按钮点击事件 - 修改为弹出操作菜单
        holder.ivDelete.setOnClickListener {
            showActionMenu(holder.itemView.context, place, holder.adapterPosition)
        }
    }

    // 显示操作菜单弹窗（修复：移除重复的列表项删除）
    private fun showActionMenu(context: android.content.Context, place: PendingPlace, position: Int) {
        try {
            val dayNames = getDayNames()
            if (dayNames.isEmpty()) {
                Toast.makeText(context, "没有可移动的天数", Toast.LENGTH_SHORT).show()
                return
            }

            val options = mutableListOf<String>()
            options.addAll(dayNames)
            options.add("删除")

            val builder = AlertDialog.Builder(context)
            builder.setTitle("操作选项")
            builder.setItems(options.toTypedArray()) { _, which ->
                // 使用主线程处理操作
                Handler(Looper.getMainLooper()).post {
                    try {
                        if (which < dayNames.size) {
                            // 检查是否是移动到同一天
                            if (which != currentDayIndex) {
                                // 仅通知移动事件，移除操作交给movePlaceToDay统一处理
                                onMoveToDayListener(place, which)
                            } else {
                                Toast.makeText(context, "已在当前天数中", Toast.LENGTH_SHORT).show()
                            }
                        } else if (which == dayNames.size) {
                            // 删除操作
                            if (position >= 0 && position < placeList.size) {
                                onDeleteListener(place)
                                placeList.removeAt(position)
                                notifyItemRemoved(position)
                                notifyItemRangeChanged(position, placeList.size - position)
                                onOrderChangedListener()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ActionMenuError", "处理操作失败", e)
                        Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            builder.show()
        } catch (e: Exception) {
            Log.e("ActionMenuError", "显示菜单失败", e)
            Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount(): Int = placeList.size

    // 交换列表项（拖动排序）
    fun onItemMove(fromPosition: Int, toPosition: Int) {
        try {
            if (fromPosition in placeList.indices && toPosition in placeList.indices && fromPosition != toPosition) {
                // 使用Collections.swap确保线程安全
                Collections.swap(placeList, fromPosition, toPosition)
                notifyItemMoved(fromPosition, toPosition)
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
    private var dayCount = 1
    private val markdownContent = """
# 北京4日深度游旅行计划（11月20日 - 11月23日）

> **季节提示**：11月的北京已进入初冬，气温约为3℃-12℃，早晚偏冷，建议携带保暖外套、帽子和手套。此时游客相对较少，可更从容地游览知名景点，且能感受到北京的冬日氛围。

---

## 第一天（11月20日）：抵达与古都初体验
- **上午（抵达后）**
  - **地点**：酒店办理入住（推荐王府井或东直门附近，交通便利）
  - **活动**：稍作休息，调整状态
- **中午**
  - **地点**：王府井大街
  - **活动**：品尝北京地道小吃（如炸酱面、糖葫芦、烤鸭）
- **下午**
  - **地点**：天安门广场、人民大会堂外景
  - **活动**：感受庄严的首都中心
- **傍晚**
  - **地点**：故宫博物院（建议提前网上预约门票）
  - **活动**：参观太和殿、中和殿、保和殿，欣赏宫廷建筑与历史文物
- **晚上**
  - **地点**：东来顺或全聚德
  - **活动**：北京烤鸭晚餐

---

## 第二天（11月21日）：皇家园林与胡同文化
- **上午**
  - **地点**：颐和园
  - **活动**：游览长廊、昆明湖畔、佛香阁，初冬的颐和园湖面宁静，风景别有韵味
- **中午**
  - **地点**：颐和园附近餐馆
  - **活动**：享用简餐
- **下午**
  - **地点**：恭王府
  - **活动**：探索清代王府的建筑与文化
- **傍晚**
  - **地点**：南锣鼓巷
  - **活动**：漫步胡同，探访特色咖啡店、文创商铺
- **晚上**
  - **地点**：什刹海酒吧街
  - **活动**：夜景与酒吧小酌，感受北京的夜生活

---

## 第三天（11月22日）：长城壮景之旅
- **全天**
  - **地点**：八达岭长城（乘坐高铁或旅游巴士）
  - **活动**：
    - **上午**：攀登长城，拍摄初冬的长城风光
    - **中午**：景区内用餐或自带简餐
    - **下午**：继续游览长城周边景点或参观长城博物馆
    - **傍晚**：返回市区
- **晚上**
  - **地点**：簋街
  - **活动**：特色川菜、烤串、小龙虾等美食

---

## 第四天（11月23日）：人文与购物收尾
- **上午**
  - **地点**：中国国家博物馆
  - **活动**：深入了解中华文明发展脉络
- **中午**
  - **地点**：前门大街
  - **活动**：品尝老北京炸酱面或驴打滚
- **下午**
  - **地点**：三里屯或SKP
  - **活动**：购物与休闲
- **傍晚**
  - **活动**：返回酒店取行李，前往机场或火车站，结束愉快行程

---

### 额外建议
- **交通**：推荐使用地铁出行，经济且快速；长城可选择高铁至八达岭站更方便。
- **着装**：初冬注意保暖，可带围巾和手套。
- **门票**：故宫、颐和园、恭王府等景点需提前网上预约。
- **美食**：除了烤鸭，可尝试豆汁、焦圈、爆肚等地道北京味。

---
""".trimIndent()
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
        setContentView(R.layout.activity_create_trip)

        try {
            initViews()
            initUploader()
            initDayItineraries()
            // 初始化天气控制器
            weatherController = WeatherController(this)
            // 添加测试景点数据
            addTestPlaces()
            initPlacesRecyclerView()
            initEvents()
            initTextWatchers()

            // 设置默认行程名称和日期
            etTripName.setText("北京五日游")
            etTripDate.setText(sdf.format(Date()))

            // 延迟初始化地图，避免布局未完成导致的问题
            mapView?.let {
                mapController = TravelMapController(this, it)
                mapController?.onCreate(savedInstanceState)
            }
        } catch (e: Exception) {
            Log.e("onCreateError", "初始化失败", e)
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 添加测试景点数据
    private fun addTestPlaces() {
        try {
            // 第一天的景点
            val day1Itinerary = dayItineraries.find { it.dayNumber == 1 }
            day1Itinerary?.places?.addAll(listOf(
                PendingPlace(
                    name = "天安门广场",
                    address = "北京市东城区长安街",
                    rating = "4.8",
                    tag1 = "历史古迹",
                    tag2 = "北京"
                ),
                PendingPlace(
                    name = "故宫博物院",
                    address = "北京市东城区景山前街4号",
                    rating = "4.9",
                    tag1 = "世界文化遗产",
                    tag2 = "北京"
                ),
                PendingPlace(
                    name = "景山公园",
                    address = "北京市东城区景山前街44号",
                    rating = "4.7",
                    tag1 = "公园",
                    tag2 = "北京"
                )
            ))

            // 待安排的景点
            val pendingItinerary = getPendingItinerary()
            pendingItinerary?.places?.addAll(listOf(
                PendingPlace(
                    name = "八达岭长城",
                    address = "北京市延庆区G6京藏高速58号出口",
                    rating = "4.7",
                    tag1 = "世界文化遗产",
                    tag2 = "北京"
                ),
                PendingPlace(
                    name = "颐和园",
                    address = "北京市海淀区新建宫门路19号",
                    rating = "4.8",
                    tag1 = "皇家园林",
                    tag2 = "北京"
                ),
                PendingPlace(
                    name = "天坛公园",
                    address = "北京市东城区天坛路甲1号",
                    rating = "4.7",
                    tag1 = "世界文化遗产",
                    tag2 = "北京"
                ),
                PendingPlace(
                    name = "南锣鼓巷",
                    address = "北京市东城区南锣鼓巷",
                    rating = "4.6",
                    tag1 = "美食街",
                    tag2 = "北京"
                ),
                PendingPlace(
                    name = "什刹海",
                    address = "北京市西城区什刹海街道",
                    rating = "4.7",
                    tag1 = "景区",
                    tag2 = "北京"
                ),
                PendingPlace(
                    name = "圆明园",
                    address = "北京市海淀区清华西路28号",
                    rating = "4.6",
                    tag1 = "历史古迹",
                    tag2 = "北京"
                )
            ))

            // 延迟更新总览，避免初始化时频繁调用
            if (currentSelectedTabId == R.id.tab_overview) {
                overviewUpdateHandler.postDelayed({
                    updateOverview()
                }, 500)
            }
        } catch (e: Exception) {
            Log.e("AddTestPlacesError", "添加测试数据失败", e)
        }
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

    // 初始化每日行程数据
    private fun initDayItineraries() {
        try {
            // 添加第一天
            val day1TabId = R.id.tab_day_1
            dayItineraries.add(DayItinerary(1, day1TabId))

            // 添加待安排
            dayItineraries.add(DayItinerary(0, pendingTagId))
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
                    // 安全检查
                    if (viewHolder.adapterPosition == RecyclerView.NO_POSITION ||
                        target.adapterPosition == RecyclerView.NO_POSITION) {
                        return false
                    }

                    placeAdapter?.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

                override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                    val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                    return makeMovementFlags(dragFlags, 0)
                }
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

            // 获取当前天在排序后的列表中的索引
            val currentSortedIndex = getCurrentDayIndexInSortedList()

            placeAdapter = PendingPlaceAdapter(
                currentItinerary.places,
                currentItinerary.dayNumber,
                currentSortedIndex, // 传递当前天在排序列表中的索引
                dragListener = { view ->
                    try {
                        val viewHolder = rvPlaces.findContainingViewHolder(view)
                        viewHolder?.let {
                            itemTouchHelper?.startDrag(it)
                        }
                    } catch (e: Exception) {
                        Log.e("DragError", "开始拖动失败", e)
                    }
                },
                onOrderChangedListener = {
                    // 只有在总览标签下才更新总览
                    if (currentSelectedTabId == R.id.tab_overview) {
                        overviewUpdateHandler.removeCallbacksAndMessages(null)
                        overviewUpdateHandler.postDelayed({
                            updateOverview()
                        }, OVERVIEW_UPDATE_DELAY)
                    }
                    updateTravelPlanDebounced()
                },
                onDeleteListener = { place ->
                    try {
                        //mapController?.removeScenic(place.name)
                        // 只有在总览标签下才更新总览
                        if (currentSelectedTabId == R.id.tab_overview) {
                            overviewUpdateHandler.removeCallbacksAndMessages(null)
                            overviewUpdateHandler.postDelayed({
                                updateOverview()
                            }, OVERVIEW_UPDATE_DELAY)
                        }
                        updateTravelPlanDebounced()
                    } catch (e: Exception) {
                        Log.e("DeleteError", "删除失败", e)
                    }
                },
                onMoveToDayListener = { place, targetSortedIndex ->
                    // 使用主线程处理移动操作
                    Handler(Looper.getMainLooper()).post {
                        try {
                            movePlaceToDayBySortedIndex(place, targetSortedIndex)
                        } catch (e: Exception) {
                            Log.e("MoveToDayError", "处理移动失败", e)
                        }
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
    private fun movePlaceToDayBySortedIndex(place: PendingPlace, targetSortedIndex: Int) {
        try {
            // 获取排序后的目标行程
            val targetItinerary = getItineraryBySortedIndex(targetSortedIndex)
            val fromItinerary = getItineraryByIndex(currentDayIndex)

            if (targetItinerary == null || fromItinerary == null || targetItinerary.tabId == fromItinerary.tabId) {
                return // 如果是同一天或目标无效，直接返回
            }

            // 获取目标行程在原始列表中的索引
            val targetIndex = dayItineraries.indexOfFirst { it.tabId == targetItinerary.tabId }
            if (targetIndex == -1) return

            // 执行移动操作
            movePlaceToDay(place, currentDayIndex, targetIndex)

        } catch (e: Exception) {
            Log.e("MovePlaceByIndexError", "移动地点失败", e)
        }
    }

    // 将地点从一天移动到另一天（修复：统一处理移除和刷新）
    private fun movePlaceToDay(place: PendingPlace, fromDayIndex: Int, toDayIndex: Int) {
        // 使用同步块确保线程安全
        synchronized(dayItineraries) {
            try {
                // 安全检查
                val fromItinerary = getItineraryByIndex(fromDayIndex)
                val toItinerary = getItineraryByIndex(toDayIndex)

                if (fromItinerary == null || toItinerary == null || fromDayIndex == toDayIndex) {
                    return // 如果是同一天或索引无效，直接返回
                }

                // 找到地点在原列表中的位置（通过ID查找，避免equals问题）
                val placePosition = fromItinerary.places.indexOfFirst { it.id == place.id }
                if (placePosition == -1) {
                    Log.w("MovePlaceWarning", "地点不存在于原列表中: ${place.name}")
                    return
                }

                // 先添加到目标列表，再从原列表移除（避免数据丢失）
                val placeToMove = fromItinerary.places[placePosition]
                toItinerary.places.add(placeToMove)

                // 更新UI - 如果当前显示的是原天数，精确刷新适配器
                if (fromDayIndex == currentDayIndex && placeAdapter != null) {
                    // 使用主线程更新UI
                    runOnUiThread {
                        placeAdapter?.removePlace(placeToMove) // 使用新的removePlace方法
                    }
                }

                // 如果目标是待安排且当前显示的是待安排，刷新适配器
                if (toItinerary.dayNumber == 0 && currentDayIndex == toDayIndex) {
                    runOnUiThread {
                        placeAdapter?.refreshData()
                    }
                }

                // 只有在总览标签下才更新总览
                if (currentSelectedTabId == R.id.tab_overview) {
                    overviewUpdateHandler.removeCallbacksAndMessages(null)
                    overviewUpdateHandler.postDelayed({
                        updateOverview()
                    }, OVERVIEW_UPDATE_DELAY)
                }

                // 通知顺序变化
                if (fromDayIndex == currentDayIndex || toDayIndex == currentDayIndex) {
                    placeAdapter?.notifyOrderChanged()
                }

                updateTravelPlanDebounced()

                // 显示提示
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
            findViewById<ImageView>(R.id.iv_save)?.setOnClickListener {
                updateTravelPlan()
                Toast.makeText(this, "行程已手动保存", Toast.LENGTH_SHORT).show()
            }

            // 添加天数
            findViewById<ImageView>(R.id.iv_add_day)?.setOnClickListener {
                if (it.isClickable) {
                    it.isClickable = false
                    addNewDay()
                    it.postDelayed({ it.isClickable = true }, 500)
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
                                        tag2 = poi.cityName ?: ""
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

    // 构建当前行程数据
    private fun buildCurrentTravelPlan(): TravelPlan? {
        return try {
            val tripName = etTripName.text.toString().ifEmpty { "未命名行程" }
            val startDateStr = etTripDate.text.toString()
            val startDate = if (startDateStr.isNotEmpty()) sdf.parse(startDateStr) else Date()
            val endDate = Date(startDate.time + dayCount * 24 * 60 * 60 * 1000L)

            // 构建天数列表（按天数排序）
            val sortedDays = dayItineraries.sortedBy { it.dayNumber }
            val days = mutableListOf<Day>()
            sortedDays.forEach { dayItinerary ->
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
                    destination = tripCity,
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
        try {
            updateHandler.removeCallbacksAndMessages(null)
            updateHandler.postDelayed({
                updateTravelPlan()
            }, DEBOUNCE_DELAY)
        } catch (e: Exception) {
            Log.e("DebounceError", "防抖处理失败", e)
        }
    }

    // 实际上传行程
    private fun updateTravelPlan() {
        try {
            val plan = buildCurrentTravelPlan() ?: return

            planUploader?.updateTravelPlanByPlanId(currentPlanId, plan, object : TravelPlanUploader.UploadCallback {
                override fun onSuccess(savedPlan: TravelPlan) {
                    currentPlanId = savedPlan.planId
                    Log.d("PlanUpdate", "行程更新成功: ${savedPlan.planId}")
                }

                override fun onFailure(errorMsg: String) {
                    Log.e("PlanUpdate", "行程更新失败: $errorMsg")
                }
            })
        } catch (e: Exception) {
            Log.e("UpdatePlanError", "更新行程失败", e)
        }
    }

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

            // 检查容器是否可用
            if (itineraryContainer == null || itineraryContainer.windowToken == null) {
                isUpdatingOverview = false
                return
            }

            // 清除现有总览内容（优化移除逻辑）
            val overviewViews = mutableListOf<View>()
            for (i in 0 until itineraryContainer.childCount) {
                val child = itineraryContainer.getChildAt(i)
                if (child != null && child.tag != null && child.tag.toString().startsWith("overview_")) {
                    overviewViews.add(child)
                }
            }

            // 批量移除视图
            overviewViews.forEach { view ->
                try {
                    if (view.parent == itineraryContainer) {
                        itineraryContainer.removeView(view)
                    }
                } catch (e: Exception) {
                    Log.w("RemoveViewWarning", "移除视图失败", e)
                }
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
            // 记录当前选中的标签
            currentSelectedTabId = selectedTabId

            // 清除所有内容（总览和天气）
            val viewsToRemove = mutableListOf<View>()
            for (i in 0 until itineraryContainer.childCount) {
                val child = itineraryContainer.getChildAt(i)
                if (child != null && (
                            (child.tag != null && child.tag.toString().startsWith("overview_")) ||
                                    (child.tag != null && child.tag.toString().startsWith("weather_"))
                            )) {
                    viewsToRemove.add(child)
                }
            }

            // 批量移除视图
            viewsToRemove.forEach {
                try {
                    if (it.parent == itineraryContainer) {
                        itineraryContainer.removeView(it)
                    }
                } catch (e: Exception) {
                    Log.w("RemoveViewWarning", "移除视图失败", e)
                }
            }

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

            // 根据选中的标签显示不同内容
            when (selectedTabId) {
                R.id.tab_overview -> {
                    // 显示总览
                    itineraryPendingHint.visibility = View.GONE
                    rvPlaces.visibility = View.GONE

                    // 延迟更新总览，避免UI卡顿
                    overviewUpdateHandler.removeCallbacksAndMessages(null)
                    overviewUpdateHandler.postDelayed({
                        updateOverview()
                    }, 100)
                }
                else -> {
                    // 具体天数或待安排标签
                    val currentItinerary = getItineraryByIndex(currentDayIndex)

                    // 具体天数显示天气
                    if (currentItinerary?.dayNumber ?: 0 > 0) {
                        // 添加天气显示板块
                        val weatherView = LayoutInflater.from(this)
                            .inflate(R.layout.item_weather, itineraryContainer, false)
                        weatherView.tag = "weather_${currentItinerary?.dayNumber}"
                        weatherView.layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dpToPx(8) }
                        itineraryContainer.addView(weatherView)

                        // 更新天气显示
                        currentItinerary?.let { updateWeatherView(weatherView, it) }

                        // 如果还没查询过天气，自动查询
                        if (currentItinerary?.weatherData == null && currentItinerary?.weatherLoading == false) {
                            currentItinerary?.let { queryDayWeather(it) }
                        }
                    }

                    if (currentItinerary?.places.isNullOrEmpty()) {
                        itineraryPendingHint.visibility = View.VISIBLE
                        rvPlaces.visibility = View.GONE
                    } else {
                        itineraryPendingHint.visibility = View.GONE
                        rvPlaces.visibility = View.VISIBLE
                        setupAdapter()
                    }
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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        try {
            mapController?.onSaveInstanceState(outState)
        } catch (e: Exception) {
            Log.e("MapSaveError", "保存地图状态失败", e)
        }
    }

    // 数据类补充
    data class TravelPlan(
        val planId: String,
        val title: String,
        val creatorId: Int,
        val collaborators: List<Int>?,
        val status: String,
        val content: Content,
        val versionHistory: List<VersionHistory>,
        val tags: List<String>?,
        val createdAt: Date,
        val updatedAt: Date
    )

    data class Content(
        val destination: String,
        val startDate: Date,
        val endDate: Date,
        val days: List<Day>,
        val transport: String?,
        val notes: String?
    )

    data class Day(
        val dayNumber: Int,
        val activities: List<Activity>
    )

    data class Activity(
        val time: String,
        val location_name: String
    )

    data class VersionHistory(
        val editorId: Int,
        val editTime: Date,
        val changeLog: String
    )

    // 上传工具类
    class TravelPlanUploader(context: android.content.Context) {
        interface UploadCallback {
            fun onSuccess(savedPlan: TravelPlan)
            fun onFailure(errorMsg: String)
        }

        fun updateTravelPlanByPlanId(planId: String, plan: TravelPlan, callback: UploadCallback) {
            // 模拟上传
            Handler(Looper.getMainLooper()).postDelayed({
                callback.onSuccess(plan)
            }, 1000)
        }
    }
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
}