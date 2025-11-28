package com.llw.newmapdemo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.geocoder.GeocodeAddress;
import com.amap.api.services.geocoder.GeocodeQuery;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeAddress;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.amap.api.services.route.BusPath;
import com.amap.api.services.route.BusRouteResult;
import com.amap.api.services.route.DrivePath;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.RideRouteResult;
import com.amap.api.services.route.RouteSearch;
import com.amap.api.services.route.WalkPath;
import com.amap.api.services.route.WalkRouteResult;
import com.llw.newmapdemo.databinding.ActivityMainBinding;
import com.llw.newmapdemo.overlay.DrivingRouteOverlay;
import com.llw.newmapdemo.overlay.RouteOverlay;
import com.llw.newmapdemo.overlay.WalkRouteOverlay;

import com.llw.newmapdemo.databinding.ActivityMapDemoBinding;

import java.util.ArrayList;
import java.util.List;

public class MapDemoActivity extends AppCompatActivity implements AMapLocationListener, LocationSource,
        PoiSearch.OnPoiSearchListener, AMap.OnMapClickListener, AMap.OnMapLongClickListener,
        GeocodeSearch.OnGeocodeSearchListener, AMap.OnMarkerClickListener, AMap.OnMarkerDragListener, AMap.InfoWindowAdapter, AMap.OnInfoWindowClickListener {
    // 接口定义
    // 用于展示给 UI 的每一段路信息
    public static class RouteSegmentInfo {
        public int index;           // 第几段 0,1,2...
        public String fromName;     // 起点名（可选）
        public String toName;       // 终点名（可选）
        public String driveTime;    // 驾车时间
        public String transitTime;  // 公交时间
        public String walkTime;     // 步行时间
        public String driveDistance;// 驾车距离
    }

    public interface OnRouteInfoUpdateListener {
        void onRouteInfoUpdated(List<RouteSegmentInfo> routeList);
    }


    //poi接口
    public interface OnPoiSearchResultListener {
        void onPoiResult(List<PoiItem> poiItems);
    }

    private OnPoiSearchResultListener poiResultListener;

    public void setOnPoiSearchResultListener(OnPoiSearchResultListener listener) {
        this.poiResultListener = listener;
    }

    private OnRouteInfoUpdateListener routeInfoListener;

    public void setOnRouteInfoUpdateListener(OnRouteInfoUpdateListener listener) {
        this.routeInfoListener = listener;
    }
    private static final String TAG = "MainActivity";
    private ActivityMapDemoBinding binding;
    // 请求权限意图
    private ActivityResultLauncher<String> requestPermission;
    // 声明AMapLocationClient类对象
    public AMapLocationClient mLocationClient = null;
    // 声明AMapLocationClientOption对象
    public AMapLocationClientOption mLocationOption = null;
    // 声明地图控制器
    private AMap aMap = null;
    // 声明地图定位监听
    private LocationSource.OnLocationChangedListener mListener = null;
    //POI查询对象
    private PoiSearch.Query query;
    //POI搜索对象
    private PoiSearch poiSearch;
    //城市码
    private String cityCode = null;
    //地理编码搜索
    private GeocodeSearch geocodeSearch;
    //解析成功标识码
    private static final int PARSE_SUCCESS_CODE = 1000;
    //城市
    private String city;
    //标点列表
    private final List<Marker> markerList = new ArrayList<>();

    // 文字标签用的 marker（只显示方框 + 文本）
    private final List<Marker> labelMarkerList = new ArrayList<>();


    // 新增：存储三种交通方式信息的"盒子"
    // 每一段路的所有信息
    class RouteInfo {
        public String driveTime = "无";      // 驾车时间
        public String transitTime = "无";    // 公交时间
        public String walkTime = "无";       // 步行时间
        public String driveDistance = "0公里"; // 驾车距离

        // 这一段路的起点、终点（用于回调匹配）
        public LatLonPoint startPoint;
        public LatLonPoint endPoint;

        // 三种方式是否“查完了”（成功或失败都算）
        public boolean walkDone = false;
        public boolean driveDone = false;
        public boolean transitDone = false;
    }

    // 三种方式的查询阶段标记
    private boolean walkPhaseStarted = false;
    private boolean drivePhaseStarted = false;
    private boolean transitPhaseStarted = false;

    // 存储所有路段的信息（和景点顺序对应）
    private List<RouteInfo> routeInfoList = new ArrayList<>();
    // 存储景点相关数据
    private List<LatLonPoint> scenicPoints = new ArrayList<>(); // 景点坐标（按添加顺序）
    private List<RouteOverlay> routeOverlays = new ArrayList<>(); // 路径覆盖物（用于删除）
    private RouteSearch routeSearch; // 高德路径规划核心类
    private static final int TRAVEL_MODE = 0; // 0=步行（默认），1=骑行，2=驾车，3=公交


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
            // 权限申请结果
            showMsg(result ? "已获取到权限" : "权限申请失败");
        });
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMapDemoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        // 初始化定位
        initLocation();
        // 绑定生命周期 onCreate
        binding.mapView.onCreate(savedInstanceState);
        // 初始化地图
        initMap();
        // 初始化搜索
        initSearch();
        // 初始化控件
        initView();
    }

    /**
     * 初始化搜索
     */
    private void initSearch() {
        // 构造 GeocodeSearch 对象
        try {
            geocodeSearch = new GeocodeSearch(this);
            // 设置监听
            geocodeSearch.setOnGeocodeSearchListener(this);
        } catch (AMapException e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化控件
     */
    private void initView() {
        // Poi搜索按钮点击事件
        binding.fabPoi.setOnClickListener(v -> {
            String keyword = binding.etAddress.getText().toString().trim(); // 复用地址输入框
            if (keyword.isEmpty()) {
                showMsg("请输入景点关键词（如：故宫、颐和园）");
                return;
            }
            // 发起POI搜索（关键词、不限类型、城市码）
            startPoiSearch(keyword);
            /*
            //构造query对象
            query = new PoiSearch.Query("购物", "", cityCode);
            // 设置每页最多返回多少条poiItem
            query.setPageSize(10);
            //设置查询页码
            query.setPageNum(1);
            //构造 PoiSearch 对象
            try {
                poiSearch = new PoiSearch(this, query);
                //设置搜索回调监听
                poiSearch.setOnPoiSearchListener(this);
                //发起搜索附近POI异步请求
                poiSearch.searchPOIAsyn();
            } catch (AMapException e) {
                throw new RuntimeException(e);
            }*/
        });

        // fabClearMarker的点击事件（新）
        binding.fabClearMarker.setOnClickListener(v -> {
            // 清空标记列表和地图标记（原有逻辑）
            for (Marker marker : markerList) {
                marker.remove();
            }
            markerList.clear();

            // 清空景点列表和路径
            scenicPoints.clear();
            clearAllRoutes();
            // 新增：清空文字 label marker
            for (Marker label : labelMarkerList) {
                label.remove();
            }
            labelMarkerList.clear();

            showMsg("已清空所有标记和路径");
            binding.fabClearMarker.setVisibility(View.INVISIBLE); // 隐藏清空按钮（原有逻辑）
        });
        // 清除标点按钮点击事件（原）
        binding.fabClearMarker.setOnClickListener(v -> clearAllMarker());
        // 路线按钮点击事件
        binding.fabRoute.setOnClickListener(v -> startActivity(new Intent(this, RouteActivity.class)));
        // 添加天气查询按钮的点击事件（核心代码）
        binding.fabWeather.setOnClickListener(v -> openWeatherQuery()); // 绑定按钮到跳转方法
        // 键盘按键监听
        binding.etAddress.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
                //获取输入框的值
                String address = binding.etAddress.getText().toString().trim();
                if (address.isEmpty()) {
                    showMsg("请输入地址");
                } else {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    //隐藏软键盘
                    imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);

                    // name表示地址，第二个参数表示查询城市，中文或者中文全拼，citycode、adcode
                    GeocodeQuery query = new GeocodeQuery(address, city);
                    geocodeSearch.getFromLocationNameAsyn(query);
                }
                return true;
            }
            return false;
        });
    }

    // 新增：发起POI搜索的方法
    private void startPoiSearch(String keyword) {
        try {
            //如果城市码为空，使用城市名
            String searchArea = TextUtils.isEmpty(cityCode) ? city : cityCode;
            if (TextUtils.isEmpty(searchArea)) {
                showMsg("无法获取当前城市信息，请稍后再试");
                return;
            }
            // 构造搜索查询：关键词、类型（""=不限）、城市码
            query = new PoiSearch.Query(keyword, "", cityCode);
            query.setPageSize(10); // 每页10条结果
            query.setPageNum(1); // 第1页
            poiSearch = new PoiSearch(this, query);
            poiSearch.setOnPoiSearchListener(this); // 设置结果回调
            poiSearch.searchPOIAsyn(); // 异步搜索
        } catch (AMapException e) {
            e.printStackTrace();
            showMsg("搜索失败：" + e.getMessage());
        }
    }
    /**
     * 初始化地图
     */
    private void initMap() {
        if (aMap == null) {
            aMap = binding.mapView.getMap();
            // 创建定位蓝点的样式
            MyLocationStyle myLocationStyle = new MyLocationStyle();
            // 自定义定位蓝点图标
            myLocationStyle.myLocationIcon(BitmapDescriptorFactory.fromResource(R.drawable.gps_point));
            // 自定义精度范围的圆形边框颜色  都为0则透明
            myLocationStyle.strokeColor(Color.argb(0, 0, 0, 0));
            // 自定义精度范围的圆形边框宽度  0 无宽度
            myLocationStyle.strokeWidth(0);
            // 设置圆形的填充颜色  都为0则透明
            myLocationStyle.radiusFillColor(Color.argb(0, 0, 0, 0));
            // 设置定位蓝点的样式
            aMap.setMyLocationStyle(myLocationStyle);
            // 设置定位监听
            aMap.setLocationSource(this);
            // 设置为true表示启动显示定位蓝点，false表示隐藏定位蓝点并不进行定位，默认是false。
            aMap.setMyLocationEnabled(true);
            //设置最小缩放等级为12 ，缩放级别范围为[3, 20]
            aMap.setMinZoomLevel(12);
            // 开启室内地图
            aMap.showIndoorMap(true);
            // 设置地图点击事件
            //aMap.setOnMapClickListener(this);
            // 设置地图长按事件
            aMap.setOnMapLongClickListener(this);
            // 设置地图标点点击事件
            aMap.setOnMarkerClickListener(this);
            // 设置地图标点拖拽事件
            aMap.setOnMarkerDragListener(this);
            // 设置InfoWindowAdapter监听
            aMap.setInfoWindowAdapter(this);
            // 设置InfoWindow点击事件
            aMap.setOnInfoWindowClickListener(this);
            // 地图控件设置
            UiSettings uiSettings = aMap.getUiSettings();
            // 隐藏缩放按钮
            uiSettings.setZoomControlsEnabled(false);
            // 显示比例尺，默认不显示
            uiSettings.setScaleControlsEnabled(true);
        }
        // 新增：初始化路径规划
        try {
            routeSearch = new RouteSearch(this);
            // 后续设置路径规划监听器等逻辑...
        } catch (AMapException e) {
            e.printStackTrace();
            // 异常处理：提示用户“路径规划初始化失败，请检查高德密钥和网络”
            Toast.makeText(this, "路径规划功能异常：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        // 设置路径规划结果回调（必须实现这个接口）
        routeSearch.setRouteSearchListener(new RouteSearch.OnRouteSearchListener() {
            // 步行路径结果回调（核心，默认先实现步行）
            @Override
            public void onWalkRouteSearched(WalkRouteResult result, int code) {
                handleWalkRouteResult(result, code);
            }

            // 其他出行方式回调（暂时留空，后续可扩展）
            @Override
            public void onDriveRouteSearched(DriveRouteResult result, int code) {
                handleDriveRouteResult(result, code); // 我们稍后实现这个方法
            }
            @Override
            public void onRideRouteSearched(RideRouteResult result, int code) {}
            @Override
            public void onBusRouteSearched(BusRouteResult result, int code) {
                handleTransitRouteResult(result, code); // 我们稍后实现这个方法
            }
        });

        // 新增：地图点击监听（用于点击添加景点）
        aMap.setOnMapClickListener(latLng -> {
            //addScenicByClick(latLng); // 点击地图添加景点（后续实现）
        });

        // 新增：标记点击监听（用于删除景点）
        aMap.setOnMarkerClickListener(marker -> {
            // 点击时重新显示信息窗
            marker.showInfoWindow();
            deleteScenicByClick(marker); // 点击标记删除景点（后续实现）
            return true; // 拦截默认点击事件
        });
    }

    // POI 列表适配器
    private static class PoiAdapter extends BaseAdapter {
        private final Context context;
        private final List<PoiItem> poiList;

        // 构造方法：传入上下文和POI结果列表
        public PoiAdapter(Context context, List<PoiItem> poiList) {
            this.context = context;
            this.poiList = poiList;
        }

        @Override
        public int getCount() {
            return poiList.size(); // 结果数量
        }

        @Override
        public Object getItem(int position) {
            return poiList.get(position); // 获取单个结果
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            // 复用布局，优化性能
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_poi, parent, false);
                holder = new ViewHolder();
                holder.tvTitle = convertView.findViewById(R.id.tv_poi_title);
                holder.tvAddress = convertView.findViewById(R.id.tv_poi_address);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            // 绑定数据到布局
            PoiItem poi = poiList.get(position);
            holder.tvTitle.setText(poi.getTitle()); // 景点名称
            holder.tvAddress.setText(poi.getSnippet()); // 景点地址
            return convertView;
        }

        // 视图持有者（避免重复findViewById）
        static class ViewHolder {
            TextView tvTitle;
            TextView tvAddress;
        }
    }

    /**
     * 初始化定位
     */
    private void initLocation() {
        try {
            //初始化定位
            mLocationClient = new AMapLocationClient(getApplicationContext());
            //设置定位回调监听
            mLocationClient.setLocationListener(this);
            //初始化AMapLocationClientOption对象
            mLocationOption = new AMapLocationClientOption();
            //设置定位模式为AMapLocationMode.Hight_Accuracy，高精度模式。
            mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            //获取最近3s内精度最高的一次定位结果
            mLocationOption.setOnceLocationLatest(true);
            //设置是否返回地址信息（默认返回地址信息）
            mLocationOption.setNeedAddress(true);
            //设置定位超时时间，单位是毫秒
            mLocationOption.setHttpTimeOut(6000);
            //给定位客户端对象设置定位参数
            mLocationClient.setLocationOption(mLocationOption);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 开始定位
     */
    private void startLocation() {
        if (mLocationClient != null) mLocationClient.startLocation();
    }

    /**
     * 停止定位
     */
    private void stopLocation() {
        if (mLocationClient != null) mLocationClient.stopLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 绑定生命周期 onResume
        binding.mapView.onResume();
        // 检查是否已经获取到定位权限
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // 获取到权限
            startLocation();
        } else {
            // 请求定位权限
            requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 绑定生命周期 onPause
        binding.mapView.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // 绑定生命周期 onSaveInstanceState
        binding.mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 绑定生命周期 onDestroy
        binding.mapView.onDestroy();
    }

    private void showMsg(CharSequence llw) {
        Toast.makeText(this, llw, Toast.LENGTH_SHORT).show();
    }

    /**
     * 定位回调结果
     */
    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        if (aMapLocation == null) {
            showMsg("定位失败，aMapLocation 为空");
            return;
        }
        // 获取定位结果
        if (aMapLocation.getErrorCode() == 0) {
            // 定位成功
            showMsg("定位成功");
//            aMapLocation.getLocationType();//获取当前定位结果来源，如网络定位结果，详见定位类型表
//            aMapLocation.getLatitude();//获取纬度
//            aMapLocation.getLongitude();//获取经度
//            aMapLocation.getAccuracy();//获取精度信息
//            aMapLocation.getAddress();//详细地址，如果option中设置isNeedAddress为false，则没有此结果，网络定位结果中会有地址信息，GPS定位不返回地址信息。
//            aMapLocation.getCountry();//国家信息
//            aMapLocation.getProvince();//省信息
//            aMapLocation.getCity();//城市信息
            String result = aMapLocation.getDistrict();//城区信息
//            aMapLocation.getStreet();//街道信息
//            aMapLocation.getStreetNum();//街道门牌号信息
//            aMapLocation.getCityCode();//城市编码
//            aMapLocation.getAdCode();//地区编码
//            aMapLocation.getAoiName();//获取当前定位点的AOI信息
//            aMapLocation.getBuildingId();//获取当前室内定位的建筑物Id
//            aMapLocation.getFloor();//获取当前室内定位的楼层
//            aMapLocation.getGpsAccuracyStatus();//获取GPS的当前状态

            // 停止定位
            stopLocation();
            // 显示地图定位结果
            if (mListener != null) {
                mListener.onLocationChanged(aMapLocation);
            }
            // 显示浮动按钮
            binding.fabPoi.show();
            // 城市编码赋值
            //cityCode = aMapLocation.getCityCode();
            // 修正：正确获取城市码和城市
            cityCode = aMapLocation.getAdCode(); // 使用地区编码更可靠
            city = aMapLocation.getCity();
            if (city == null || city.isEmpty()) {
                city = aMapLocation.getProvince(); // 若城市为空则使用省份
            }
            // 添加调试日志
            Log.d(TAG, "定位成功 - 城市: " + city + ", 城市码: " + cityCode +
                    ", 区域: " + aMapLocation.getDistrict() +
                    ", 省份: " + aMapLocation.getProvince());
            // 城市
            city = aMapLocation.getCity();
        } else {
            // 定位失败
            showMsg("定位失败，错误：" + aMapLocation.getErrorInfo());
            Log.e(TAG, "location Error, ErrCode:"
                    + aMapLocation.getErrorCode() + ", errInfo:"
                    + aMapLocation.getErrorInfo());
        }
    }

    /**
     * 激活定位
     *
     * @param onLocationChangedListener
     */
    @Override
    public void activate(OnLocationChangedListener onLocationChangedListener) {
        if (mListener == null) {
            mListener = onLocationChangedListener;
        }
        startLocation();
    }

    /**
     * 禁用
     */
    @Override
    public void deactivate() {
        mListener = null;
        if (mLocationClient != null) {
            mLocationClient.stopLocation();
            mLocationClient.onDestroy();
        }
        mLocationClient = null;
    }

    /**
     * POI搜索返回
     *
     * @param poiResult POI所有数据
     * @param resultCode
     */
    @Override
    public void onPoiSearched(PoiResult poiResult, int resultCode) {
        Log.d(TAG, "POI搜索结果：resultCode=" + resultCode + ", 结果数量=" + (poiResult != null ? poiResult.getPois().size() : 0));
        if (resultCode != AMapException.CODE_AMAP_SUCCESS) {
            showMsg("搜索失败，错误码：" + resultCode);
            return;
        }

        ArrayList<PoiItem> poiItems = poiResult.getPois();
        if (poiItems == null || poiItems.isEmpty()) {
            showMsg("未找到相关景点，请换个关键词");
            return;
        }

        // ============== UI 有监听，就交给 UI 展示
        if (poiResultListener != null) {
            poiResultListener.onPoiResult(poiItems);
            return;
        }

        // 1. 创建弹窗，展示POI搜索结果列表
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_poi_list, null);
        if (dialogView == null) {
            showMsg("列表布局加载失败");
            return;
        }
        ListView lvPoi = dialogView.findViewById(R.id.lv_poi_result);

        // 2. 设置适配器，绑定数据
        PoiAdapter adapter = new PoiAdapter(this, poiItems);
        lvPoi.setAdapter(adapter);

        // 3. 显示弹窗
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择景点")
                .setView(dialogView)
                .setNegativeButton("取消", null) // 取消按钮
                .create();
        dialog.show();

        // 4. 用户点击列表项（选择景点）
        lvPoi.setOnItemClickListener((parent, view, position, id) -> {
            PoiItem selectedPoi = poiItems.get(position); // 用户选中的景点
            addScenicByPoi(selectedPoi); // 添加选中的景点到地图（后续实现）
            dialog.dismiss(); // 关闭弹窗
        });
    }

    // poi接口2：处理用户选择的POI（添加为景点）
    public void addScenicByPoi(PoiItem poi) {
        // 获取景点坐标（LatLonPoint转LatLng，高德地图需要）
        LatLonPoint poiPoint = poi.getLatLonPoint();
        LatLng poiLatLng = new LatLng(poiPoint.getLatitude(), poiPoint.getLongitude());

        // 1. 添加景点坐标到列表（用于路径规划）
        scenicPoints.add(poiPoint);

        // 2. 在地图上添加景点标记
        MarkerOptions markerOptions = new MarkerOptions()
                .position(poiLatLng)
                .icon(BitmapDescriptorFactory.fromResource(R.mipmap.landmark)) // 已有图标
                .title(poi.getTitle()) // 景点名称
                .snippet("点击删除该景点"); // 提示文字
        Marker marker = aMap.addMarker(markerOptions);
        markerList.add(marker); // 加入已有标记列表（方便清空）
        marker.showInfoWindow(); // 显示信息窗
        // 3. 移动地图视角到新添加的景点
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(poiLatLng, 15));

        // 4. 新增景点后，更新路径
        updateRoute();

        showMsg("已添加景点：" + poi.getTitle());
        // 调整地图大小
        adjustMapViewToShowAll();
    }

    // 新增：点击地图添加景点
    private void addScenicByClick(LatLng latLng) {
        // 1. 转换坐标类型（LatLng转LatLonPoint，用于路径规划）
        LatLonPoint scenicPoint = new LatLonPoint(latLng.latitude, latLng.longitude);

        // 2. 添加坐标到景点列表
        scenicPoints.add(scenicPoint);

        // 3. 添加地图标记（复用图标，标记名称按顺序命名）
        String scenicName = "景点" + scenicPoints.size();
        MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.fromResource(R.mipmap.landmark))
                .title(scenicName)
                .snippet("点击删除该景点");
        Marker marker = aMap.addMarker(markerOptions);
        markerList.add(marker);
        marker.showInfoWindow();

        // 4. 移动地图视角
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));

        // 5. 更新路径
        updateRoute();

        showMsg("已添加" + scenicName);
        // 调整地图大小
        adjustMapViewToShowAll();
    }

    // 改进：处理步行路径结果

    private void handleWalkRouteResult(WalkRouteResult result, int code) {
        if (result == null) {
            Log.e(TAG, "步行路径结果为空");
            return;
        }

        int routeIndex = findRouteIndexByPoints(result.getStartPos(), result.getTargetPos());
        if (routeIndex == -1 || routeIndex >= routeInfoList.size()) {
            Log.e(TAG, "步行路径索引无效：" + routeIndex);
            return;
        }

        RouteInfo info = routeInfoList.get(routeIndex);

        if (code == AMapException.CODE_AMAP_SUCCESS
                && result.getPaths() != null
                && !result.getPaths().isEmpty()) {

            WalkPath walkPath = result.getPaths().get(0);
            int timeMin = (int) walkPath.getDuration() / 60;
            info.walkTime = "约" + timeMin + "分钟";

            // 绘制步行路径
            WalkRouteOverlay routeOverlay = new WalkRouteOverlay(
                    this, aMap, walkPath, result.getStartPos(), result.getTargetPos());
            routeOverlay.setNodeIconVisibility(false);
            routeOverlay.addToMap();
            routeOverlays.add(routeOverlay);
        } else {
            info.walkTime = "无";  // 失败或查不到
            Log.w(TAG, "步行路径规划失败，错误码：" + code);
        }

        // 无论成功失败，都标记为完成
        info.walkDone = true;
        checkAllInfoReady();
    }


    // 改进：通过坐标键来查找路段索引 单段路径规划
    // 通过起点终点坐标，在 routeInfoList 中找到对应的路段索引
    private int findRouteIndexByPoints(LatLonPoint start, LatLonPoint end) {
        if (start == null || end == null) return -1;

        for (int i = 0; i < routeInfoList.size(); i++) {
            RouteInfo info = routeInfoList.get(i);
            if (isSamePoint(info.startPoint, start) && isSamePoint(info.endPoint, end)) {
                return i;
            }
        }

        Log.w(TAG, "未找到路段索引，起点: " + start + ", 终点: " + end);
        return -1;
    }


    // 改进的坐标比较方法，支持自定义容差

    private boolean isSamePoint(LatLonPoint a, LatLonPoint b, double tolerance) {
        if (a == null || b == null) return false;
        return Math.abs(a.getLatitude() - b.getLatitude())  < tolerance
                && Math.abs(a.getLongitude() - b.getLongitude()) < tolerance;
    }


    // 重载原有方法，保持兼容
    private boolean isSamePoint(LatLonPoint a, LatLonPoint b) {
        return isSamePoint(a, b, 0.0005);
    }

    // 检查是否所有路段的信息都已获取
    // 替换现有的 checkAllInfoReady 方法
    private void checkAllInfoReady() {
        if (routeInfoList.isEmpty()) {
            Log.d(TAG, "路段信息列表为空");
            return;
        }

        boolean allWalkDone = true;
        boolean allDriveDone = true;
        boolean allTransitDone = true;

        for (RouteInfo info : routeInfoList) {
            if (!info.walkDone)    allWalkDone = false;
            if (!info.driveDone)   allDriveDone = false;
            if (!info.transitDone) allTransitDone = false;
        }

        // 1. 步行全部完成后，启动驾车阶段
        if (allWalkDone && !drivePhaseStarted) {
            Log.d(TAG, "步行阶段完成，开始驾车阶段");
            startDriveForAllSegments();
            return;
        }

        // 2. 驾车也全部完成后，启动公交阶段
        if (allWalkDone && allDriveDone && !transitPhaseStarted) {
            Log.d(TAG, "驾车阶段完成，开始公交阶段");
            startTransitForAllSegments();
            return;
        }

        // 3. 三种方式都完成了，通知 UI
        if (allWalkDone && allDriveDone && allTransitDone) {
            Log.d(TAG, "所有路段三种方式都已完成，共 " + routeInfoList.size() + " 段");
            notifyUIUpdate();
        }
    }


    private void notifyUIUpdate() {
        if (routeInfoListener == null) {
            Log.w(TAG, "UI监听器未设置，无法发送数据");
            return;
        }

        List<RouteSegmentInfo> resultList = new ArrayList<>();

        for (int i = 0; i < routeInfoList.size(); i++) {
            RouteInfo info = routeInfoList.get(i);

            RouteSegmentInfo seg = new RouteSegmentInfo();
            seg.index         = i;
            seg.driveTime     = info.driveTime;
            seg.transitTime   = info.transitTime;
            seg.walkTime      = info.walkTime;
            seg.driveDistance = info.driveDistance;
            // 如果你能拿到起终点名字，可以顺手塞进去
            resultList.add(seg);
        }

        routeInfoListener.onRouteInfoUpdated(resultList);
    }

    /*
    // 通知UI更新数据（把RouteInfo转成字符串列表）
    private void notifyUIUpdate() {
        List<String> resultList = new ArrayList<>();

        for (int i = 0; i < routeInfoList.size(); i++) {
            RouteInfo info = routeInfoList.get(i);

            // 构建格式化的交通信息
            String infoStr = String.format("驾车%s，地铁%s，步行%s，距离%s",
                    info.driveTime, info.transitTime, info.walkTime, info.driveDistance);

            resultList.add(infoStr);
            Log.d(TAG, "路段" + (i+1) + "交通信息：" + infoStr);
        }

        // 通知UI更新
        if (routeInfoListener != null) {
            routeInfoListener.onRouteInfoUpdated(resultList);
            Log.d(TAG, "已发送" + resultList.size() + "个路段的交通信息给UI");
        } else {
            Log.w(TAG, "UI监听器未设置，无法发送数据");
            // 临时显示结果用于调试
            for (String info : resultList) {
                Log.d(TAG, "交通信息：" + info);
            }
        }
    }*/

    // 判断两个坐标点是否相同（允许微小误差，因为浮点数可能有精度问题）
    //private boolean isSamePoint(LatLonPoint a, LatLonPoint b) {
    //    if (a == null || b == null) return false;
        // 纬度和经度相差小于0.0001视为同一个点
    //    return Math.abs(a.getLatitude() - b.getLatitude()) < 0.0001
    //            && Math.abs(a.getLongitude() - b.getLongitude()) < 0.0001;
    //}

    // 添加超时处理
    // 修改 checkAndHandleTimeout 方法
    private void checkAndHandleTimeout() {
        try {
            new android.os.Handler(getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    boolean allReady = true;
                    for (RouteInfo info : routeInfoList) {
                        if ("无".equals(info.driveTime) || "无".equals(info.transitTime) || "无".equals(info.walkTime)) {
                            allReady = false;
                            break;
                        }
                    }

                    if (!allReady) {
                        Log.w(TAG, "路径规划超时，强制更新可用信息");
                        // 为未完成的查询设置默认值
                        for (int i = 0; i < routeInfoList.size(); i++) {
                            RouteInfo info = routeInfoList.get(i);
                            if ("无".equals(info.driveTime)) {
                                info.driveTime = "计算超时";
                                info.driveDistance = "未知";
                            }
                            if ("无".equals(info.transitTime)) {
                                info.transitTime = "计算超时";
                            }
                            if ("无".equals(info.walkTime)) {
                                info.walkTime = "计算超时";
                            }
                        }
                        notifyUIUpdate();
                    }
                }
            }, 15000); // 增加到15秒超时
        } catch (Exception e) {
            Log.e(TAG, "超时处理异常: " + e.getMessage());
            // 即使超时处理失败，也要确保UI能更新
            notifyUIUpdate();
        }
    }

    // 新增：计算两点之间的距离（直线距离，用于TSP计算）
    private double calculateDistance(LatLonPoint a, LatLonPoint b) {
        double lat1 = a.getLatitude();
        double lon1 = a.getLongitude();
        double lat2 = b.getLatitude();
        double lon2 = b.getLongitude();

        // 地球半径(千米)
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double aHaversine = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(aHaversine), Math.sqrt(1-aHaversine));
    }

    // 新增：TSP贪心算法获取最优路径顺序
    private List<Integer> getOptimalRouteOrder(List<LatLonPoint> points) {
        if (points.size() <= 1) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < points.size(); i++) {
                order.add(i);
            }
            return order;
        }

        List<Integer> order = new ArrayList<>();
        boolean[] visited = new boolean[points.size()];

        // 从第一个点开始
        order.add(0);
        visited[0] = true;

        // 贪心选择下一个最近的点
        for (int i = 1; i < points.size(); i++) {
            int lastIndex = order.get(order.size() - 1);
            double minDistance = Double.MAX_VALUE;
            int nextIndex = -1;

            for (int j = 0; j < points.size(); j++) {
                if (!visited[j]) {
                    double distance = calculateDistance(points.get(lastIndex), points.get(j));
                    if (distance < minDistance) {
                        minDistance = distance;
                        nextIndex = j;
                    }
                }
            }

            if (nextIndex != -1) {
                order.add(nextIndex);
                visited[nextIndex] = true;
            }
        }

        return order;
    }

    // 修改 handleDriveRouteResult 方法中的索引查找：
    private void handleDriveRouteResult(DriveRouteResult result, int code) {
        Log.d(TAG, "驾车路径结果回调 - 代码: " + code + ", 结果: " + (result != null ? "非空" : "空"));

        if (result == null) {
            Log.e(TAG, "驾车路径结果为空");
            // 找不到具体路段，只能把所有还没完成的路段设置默认值
            for (RouteInfo info : routeInfoList) {
                if (!info.driveDone) {
                    info.driveTime = "无";
                    info.driveDistance = "0公里";
                    info.driveDone = true;
                }
            }
            checkAllInfoReady();
            return;
        }

        int routeIndex = findRouteIndexByPoints(result.getStartPos(), result.getTargetPos());
        if (routeIndex == -1 || routeIndex >= routeInfoList.size()) {
            Log.e(TAG, "驾车路径索引无效：" + routeIndex);
            return;
        }

        RouteInfo info = routeInfoList.get(routeIndex);

        if (code == AMapException.CODE_AMAP_SUCCESS
                && result.getPaths() != null
                && !result.getPaths().isEmpty()) {

            DrivePath drivePath = result.getPaths().get(0);
            int timeMin = (int) drivePath.getDuration() / 60;
            double distanceKm = drivePath.getDistance() / 1000.0;

            info.driveTime = "约" + timeMin + "分钟";
            info.driveDistance = String.format("%.1f公里", distanceKm);

            // 绘制驾车路径
            DrivingRouteOverlay routeOverlay = new DrivingRouteOverlay(
                    this, aMap, drivePath,
                    result.getStartPos(), result.getTargetPos(), null);
            routeOverlay.setNodeIconVisibility(false);
            routeOverlay.addToMap();
            routeOverlays.add(routeOverlay);
        } else {
            info.driveTime = "无";
            info.driveDistance = "0公里";
            Log.w(TAG, "驾车路径规划失败，错误码：" + code);
        }

        info.driveDone = true;
        checkAllInfoReady();
    }

    // 新增：为所有路段设置默认的驾车信息
    private void setDefaultDriveInfoForAllRoutes() {
        for (RouteInfo info : routeInfoList) {
            if ("无".equals(info.driveTime)) {
                info.driveTime = "无";
                info.driveDistance = "0公里";
            }
        }
        checkAllInfoReady();
    }

    // 修改 handleTransitRouteResult 方法中的索引查找：
    private void handleTransitRouteResult(BusRouteResult result, int code) {
        Log.d(TAG, "公交路径结果回调 - 代码: " + code + ", 结果: " + (result != null ? "非空" : "空"));

        if (result == null) {
            Log.e(TAG, "公交路径结果为空，错误码: " + code);
            // 同样，把所有未完成的公交信息标记为“无”
            for (RouteInfo info : routeInfoList) {
                if (!info.transitDone) {
                    info.transitTime = "无";
                    info.transitDone = true;
                }
            }
            checkAllInfoReady();
            return;
        }

        int routeIndex = findRouteIndexByPoints(result.getStartPos(), result.getTargetPos());
        if (routeIndex == -1 || routeIndex >= routeInfoList.size()) {
            Log.e(TAG, "公交路径索引无效：" + routeIndex);
            return;
        }

        RouteInfo info = routeInfoList.get(routeIndex);

        if (code == AMapException.CODE_AMAP_SUCCESS
                && result.getPaths() != null
                && !result.getPaths().isEmpty()) {

            BusPath busPath = result.getPaths().get(0);
            int timeMin = (int) busPath.getDuration() / 60;
            info.transitTime = "约" + timeMin + "分钟";

            // 如需画公交线路，这里可以加 BusRouteOverlay（你原来是否画看你需求）
        } else {
            info.transitTime = "无";
            Log.w(TAG, "公交路径规划失败，错误码：" + code);
        }

        info.transitDone = true;
        checkAllInfoReady();
    }

    // 新增：为所有路段设置默认的公交信息
    private void setDefaultTransitInfoForAllRoutes() {
        for (RouteInfo info : routeInfoList) {
            if ("无".equals(info.transitTime)) {
                info.transitTime = "无";
            }
        }
        checkAllInfoReady();
    }
    // 新增：更新路径（核心方法）

    // 核心：根据景点更新所有路段，并按顺序启动三种方式的查询
    private void updateRoute() {
        Log.d(TAG, "开始更新路径，景点数量：" + scenicPoints.size()
                + ", 最优顺序：" + getOptimalRouteOrder(scenicPoints));

        // 1. 清除地图上旧的路线
        clearAllRoutes();
        routeInfoList.clear();

        // 重置阶段标记
        walkPhaseStarted = false;
        drivePhaseStarted = false;
        transitPhaseStarted = false;

        // 2. 至少 2 个景点才需要规划
        if (scenicPoints.size() < 2) {
            showMsg("当前景点数：" + scenicPoints.size() + "，至少添加2个景点生成路径");
            return;
        }

        // 3. 计算最优顺序（你原来的方法）
        List<Integer> optimalOrder = getOptimalRouteOrder(scenicPoints);

        // 根据路径顺序更新文字方框 label
        updateLabelMarkersWithRouteOrder(optimalOrder);

        // 4. 根据最优顺序生成每一段的 RouteInfo
        for (int i = 0; i < optimalOrder.size() - 1; i++) {
            int startIndex = optimalOrder.get(i);
            int endIndex = optimalOrder.get(i + 1);

            LatLonPoint startPoint = scenicPoints.get(startIndex);
            LatLonPoint endPoint   = scenicPoints.get(endIndex);

            RouteInfo info = new RouteInfo();
            info.startPoint = startPoint;
            info.endPoint   = endPoint;
            routeInfoList.add(info);
        }
        // 5. 先启动“步行”查询阶段
        startWalkForAllSegments();
        // 调整视角
        adjustMapViewToShowAll();

    }

    // === 步行阶段 ===
    private void startWalkForAllSegments() {
        if (walkPhaseStarted) return;
        walkPhaseStarted = true;

        for (int i = 0; i < routeInfoList.size(); i++) {
            RouteInfo info = routeInfoList.get(i);
            RouteSearch.FromAndTo fromAndTo =
                    new RouteSearch.FromAndTo(info.startPoint, info.endPoint);
            RouteSearch.WalkRouteQuery walkQuery =
                    new RouteSearch.WalkRouteQuery(fromAndTo, RouteSearch.WalkDefault);
            routeSearch.calculateWalkRouteAsyn(walkQuery);
        }
    }

    // === 驾车阶段 ===
    private void startDriveForAllSegments() {
        if (drivePhaseStarted) return;
        drivePhaseStarted = true;

        for (int i = 0; i < routeInfoList.size(); i++) {
            RouteInfo info = routeInfoList.get(i);
            RouteSearch.FromAndTo fromAndTo =
                    new RouteSearch.FromAndTo(info.startPoint, info.endPoint);
            RouteSearch.DriveRouteQuery driveQuery = new RouteSearch.DriveRouteQuery(
                    fromAndTo, RouteSearch.DRIVING_SINGLE_DEFAULT, null, null, "");
            routeSearch.calculateDriveRouteAsyn(driveQuery);
        }
    }

    // === 公交阶段 ===
    private void startTransitForAllSegments() {
        if (transitPhaseStarted) return;
        transitPhaseStarted = true;

        for (int i = 0; i < routeInfoList.size(); i++) {
            RouteInfo info = routeInfoList.get(i);
            RouteSearch.FromAndTo fromAndTo =
                    new RouteSearch.FromAndTo(info.startPoint, info.endPoint);

            if (TextUtils.isEmpty(cityCode)) {
                // cityCode 为空，用城市名
                if (!TextUtils.isEmpty(city)) {
                    RouteSearch.BusRouteQuery busQuery = new RouteSearch.BusRouteQuery(
                            fromAndTo, RouteSearch.BUS_DEFAULT, city, 0);
                    routeSearch.calculateBusRouteAsyn(busQuery);
                } else {
                    // 城市信息都没有，直接标记为查不到
                    info.transitTime = "无";
                    info.transitDone = true;
                }
            } else {
                // 用 cityCode
                RouteSearch.BusRouteQuery busQuery = new RouteSearch.BusRouteQuery(
                        fromAndTo, RouteSearch.BUS_DEFAULT, cityCode, 0);
                routeSearch.calculateBusRouteAsyn(busQuery);
            }
        }
    }


    // 新增：清除所有已绘制的路径
    private void clearAllRoutes() {
        for (RouteOverlay overlay : routeOverlays) {
            overlay.removeFromMap(); // 从地图上移除路径
        }
        routeOverlays.clear(); // 清空路径列表
       // routeQueryMap.clear();
    }

    // 新增：调整地图视角以显示所有景点和路径
    private void adjustMapViewToShowAll() {
        if (scenicPoints.isEmpty()) return;

        // 收集所有景点坐标（转换为LatLng）
        List<LatLng> allPoints = new ArrayList<>();
        for (LatLonPoint point : scenicPoints) {
            allPoints.add(new LatLng(point.getLatitude(), point.getLongitude()));
        }

        // 计算所有点的经纬度边界
        double minLat = allPoints.get(0).latitude;
        double maxLat = allPoints.get(0).latitude;
        double minLng = allPoints.get(0).longitude;
        double maxLng = allPoints.get(0).longitude;

        for (LatLng latLng : allPoints) {
            minLat = Math.min(minLat, latLng.latitude);
            maxLat = Math.max(maxLat, latLng.latitude);
            minLng = Math.min(minLng, latLng.longitude);
            maxLng = Math.max(maxLng, latLng.longitude);
        }

        // 添加边界缓冲（避免点贴边）
        double latPadding = (maxLat - minLat) * 0.1; // 10%缓冲
        double lngPadding = (maxLng - minLng) * 0.1;
        minLat -= latPadding;
        maxLat += latPadding;
        minLng -= lngPadding;
        maxLng += lngPadding;

        // 创建边界对象
        LatLng southwest = new LatLng(minLat, minLng);
        LatLng northeast = new LatLng(maxLat, maxLng);
        LatLngBounds bounds = new LatLngBounds(southwest, northeast);

        // 调整地图视角（带动画效果）
        aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100)); // 100像素边距
    }
    // 新增：点击标记删除景点
    private void deleteScenicByClick(Marker marker) {
        // 1. 检查该标记是否是景点标记（避免删除其他标记）
        if (!markerList.contains(marker)) {
            return;
        }

        // 2. 找到该标记对应的景点坐标索引
        int deleteIndex = -1;
        LatLng markerLatLng = marker.getPosition();
        for (int i = 0; i < scenicPoints.size(); i++) {
            LatLonPoint point = scenicPoints.get(i);
            // 坐标匹配（允许微小误差）
            if (Math.abs(point.getLatitude() - markerLatLng.latitude) < 0.0001 &&
                    Math.abs(point.getLongitude() - markerLatLng.longitude) < 0.0001) {
                deleteIndex = i;
                break;
            }
        }

        // 3. 从列表和地图上删除
        if (deleteIndex != -1) {
            scenicPoints.remove(deleteIndex); // 删除坐标
        }
        marker.remove(); // 从地图上删除标记
        markerList.remove(marker); // 从标记列表删除

        // 4. 删除后更新路径
        updateRoute();

        showMsg("已删除景点：" + marker.getTitle());
    }
    /**
     public void onPoiSearched(PoiResult poiResult, int i) {
     //解析result获取POI信息

     //获取POI组数列表
     ArrayList<PoiItem> poiItems = poiResult.getPois();
     for (PoiItem poiItem : poiItems) {
     Log.d("MainActivity", " Title：" + poiItem.getTitle() + " Snippet：" + poiItem.getSnippet());
     }
     }*/

    /**
     * POI中的项目搜索返回
     *
     * @param poiItem 获取POI item
     * @param i
     */
    @Override
    public void onPoiItemSearched(PoiItem poiItem, int i) {

    }

    /**
     * 通过经纬度获取地址
     *
     * @param latLng
     */
    private void latLonToAddress(LatLng latLng) {
        //位置点  通过经纬度进行构建
        LatLonPoint latLonPoint = new LatLonPoint(latLng.latitude, latLng.longitude);
        //逆编码查询  第一个参数表示一个Latlng，第二参数表示范围多少米，第三个参数表示是火系坐标系还是GPS原生坐标系
        RegeocodeQuery query = new RegeocodeQuery(latLonPoint, 20, GeocodeSearch.AMAP);
        //异步获取地址信息
        geocodeSearch.getFromLocationAsyn(query);
    }

    /**
     * 添加地图标点
     *
     * @param latLng
     */
    private void addMarker(LatLng latLng) {
        //显示浮动按钮
        binding.fabClearMarker.show();
        //添加标点
        Marker marker = aMap.addMarker(new MarkerOptions()
                .draggable(true)
                .position(latLng)
                .title("标题")
                .snippet("详细内容"));
        // 显示InfoWindow
        marker.showInfoWindow();
        //设置标点的绘制动画效果
//        Animation animation = new RotateAnimation(marker.getRotateAngle(), marker.getRotateAngle() + 180, 0, 0, 0);
//        long duration = 1000L;
//        animation.setDuration(duration);
//        animation.setInterpolator(new LinearInterpolator());
//        marker.setAnimation(animation);
//        marker.startAnimation();

        markerList.add(marker);
    }

    /**
     * 清空地图Marker
     */
    public void clearAllMarker() {
        if (markerList != null && !markerList.isEmpty()) {
            for (Marker markerItem : markerList) {
                markerItem.remove();
            }
        }
        binding.fabClearMarker.hide();
    }

    /**
     * 改变地图中心位置
     * @param latLng 位置
     */
    private void updateMapCenter(LatLng latLng) {
        // CameraPosition 第一个参数： 目标位置的屏幕中心点经纬度坐标。
        // CameraPosition 第二个参数： 目标可视区域的缩放级别
        // CameraPosition 第三个参数： 目标可视区域的倾斜度，以角度为单位。
        // CameraPosition 第四个参数： 可视区域指向的方向，以角度为单位，从正北向顺时针方向计算，从0度到360度
        CameraPosition cameraPosition = new CameraPosition(latLng, 16, 30, 0);
        //位置变更
        CameraUpdate cameraUpdate = CameraUpdateFactory.newCameraPosition(cameraPosition);
        //改变位置（使用动画）
        aMap.animateCamera(cameraUpdate);
    }

    /**
     * 打开天气查询界面
     */
    private void openWeatherQuery() {
        Intent intent = new Intent(this, WeatherQueryActivity.class);
        startActivity(intent);
    }

    @Override
    public void onMapClick(LatLng latLng) {
        // 通过经纬度获取地址
        latLonToAddress(latLng);
        // 点击地图时添加标点
        //addMarker(latLng);
        // 改变地图中心点
        //updateMapCenter(latLng);
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        // 通过经纬度获取地址
        latLonToAddress(latLng);
    }

    /**
     * 坐标转地址
     *
     * @param regeocodeResult
     * @param rCode
     */
    @Override
    public void onRegeocodeSearched(RegeocodeResult regeocodeResult, int rCode) {
        //解析result获取地址描述信息
        if (rCode == PARSE_SUCCESS_CODE) {
            RegeocodeAddress regeocodeAddress = regeocodeResult.getRegeocodeAddress();
            //显示解析后的地址
            showMsg("地址：" + regeocodeAddress.getFormatAddress());
        } else {
            showMsg("获取地址失败");
        }
    }

    /**
     * 地址转坐标
     *
     * @param geocodeResult
     * @param rCode
     */
    @Override
    public void onGeocodeSearched(GeocodeResult geocodeResult, int rCode) {
        if (rCode != PARSE_SUCCESS_CODE) {
            showMsg("获取坐标失败");
            return;
        }
        List<GeocodeAddress> geocodeAddressList = geocodeResult.getGeocodeAddressList();
        if (geocodeAddressList != null && !geocodeAddressList.isEmpty()) {
            LatLonPoint latLonPoint = geocodeAddressList.get(0).getLatLonPoint();
            //显示解析后的坐标
            showMsg("坐标：" + latLonPoint.getLongitude() + "，" + latLonPoint.getLatitude());
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        if (!marker.isInfoWindowShown()) { // 显示
            marker.showInfoWindow();
        } else { // 隐藏
            //deleteScenicByClick(marker);
            marker.hideInfoWindow();
        }
        return true;
    }

    @Override
    public void onMarkerDragStart(Marker marker) {
        Log.d(TAG, "开始拖拽");
    }

    @Override
    public void onMarkerDrag(Marker marker) {
        Log.d(TAG, "拖拽中...");
    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
        showMsg("拖拽完成");
    }

    /**
     * 修改内容
     *
     * @param marker
     * @return
     */
    @Override
    public View getInfoContents(Marker marker) {
        View infoContent = getLayoutInflater().inflate(
                R.layout.custom_info_contents, null);
        render(marker, infoContent);
        return infoContent;
    }

    /**
     * 修改背景
     *
     * @param marker
     */
    @Override
    public View getInfoWindow(Marker marker) {
        View infoWindow = getLayoutInflater().inflate(
                R.layout.custom_info_window, null);
        render(marker, infoWindow);
        return infoWindow;
    }
    private BitmapDescriptor getScenicMarkerIcon(String text) {
        View view = LayoutInflater.from(this).inflate(R.layout.view_scenic_marker, null);
        TextView tv = view.findViewById(R.id.tv_marker_label);
        tv.setText(text);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        view.measure(widthSpec, heightSpec);
        int w = view.getMeasuredWidth();
        int h = view.getMeasuredHeight();
        view.layout(0, 0, w, h);

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }
    // 根据最优路径顺序，为每个景点添加一个悬浮的文字方框 Marker
    private void updateLabelMarkersWithRouteOrder(List<Integer> optimalOrder) {
        if (optimalOrder == null || optimalOrder.isEmpty()) return;
        if (markerList == null || markerList.isEmpty()) return;

        // 1. 先清空旧的文字 label marker
        for (Marker labelMarker : labelMarkerList) {
            labelMarker.remove();
        }
        labelMarkerList.clear();

        // 2. 按路径顺序给每个景点生成一个 label marker
        for (int i = 0; i < optimalOrder.size(); i++) {
            int scenicIndex = optimalOrder.get(i);
            if (scenicIndex < 0 || scenicIndex >= markerList.size()) continue;

            Marker baseMarker = markerList.get(scenicIndex);
            LatLng pos = baseMarker.getPosition();

            String name = baseMarker.getTitle();
            if (name == null) name = "景点";

            // 文本格式：1. 景点名
            String label = (i + 1) + ". " + name;

            BitmapDescriptor icon = getScenicMarkerIcon(label);

            // 在同一位置再放一个“文字方框” marker，anchor 往上提一点，让方框在地标上方
            MarkerOptions labelOptions = new MarkerOptions()
                    .position(pos)
                    .icon(icon)
                    .anchor(0.5f, 1.6f)   // Y > 1 表示往上提
                    .zIndex(10);           // 比底座 marker 高一点

            Marker labelMarker = aMap.addMarker(labelOptions);
            labelMarkerList.add(labelMarker);
        }
    }



    /**
     * 渲染
     *
     * @param marker
     * @param view
     */
    private void render(Marker marker, View view) {
        //修改InfoWindow标题内容样式
        String title = marker.getTitle();
        TextView titleUi = ((TextView) view.findViewById(R.id.title));
        if (title != null) {
            // 使用更美观的颜色和样式
            SpannableString titleText = new SpannableString(title);
            titleText.setSpan(new ForegroundColorSpan(Color.parseColor("#4285F4")), 0, // 蓝色
                    titleText.length(), 0);
            titleUi.setTextSize(14); // 稍大一点的字号
            titleUi.setTypeface(null, Typeface.BOLD); // 粗体
            titleUi.setText(titleText);
        } else {
            titleUi.setText("");
        }

        // 修改InfoWindow片段内容样式 - 美化
        String snippet = marker.getSnippet();
        TextView snippetUi = ((TextView) view.findViewById(R.id.snippet));
        if (snippet != null) {
            SpannableString snippetText = new SpannableString(snippet);
            snippetText.setSpan(new ForegroundColorSpan(Color.parseColor("#666666")), 0, // 深灰色
                    snippetText.length(), 0);
            snippetUi.setTextSize(12);
            snippetUi.setText(snippetText);
        } else {
            snippetUi.setText("");
        }

        // 可选：为信息窗口添加背景色
        view.setBackgroundColor(Color.argb(230, 255, 255, 255)); // 半透明白色背景
    }

    /**
     * InfoWindow点击事件
     *
     * @param marker
     */
    @Override
    public void onInfoWindowClick(Marker marker) {
        showMsg("弹窗内容：标题：" + marker.getTitle() + "\n内容：" + marker.getSnippet());
    }

//=================================接口======================================
    //与景点列表的接口-->
    /**
     * 接收数据库返回的地点字符串列表，转换为坐标并规划路径
     * @param locationNames 地点名称列表（如：["故宫", "颐和园"]）
     */
    public void setScenicPointsFromStrings(List<String> locationNames) {
        if (locationNames == null || locationNames.isEmpty()) {
            showMsg("地点列表为空");
            return;
        }

        // 1. 清空现有数据（避免冲突）
        clearAllMarker(); // 清空地图标记
        scenicPoints.clear(); // 清空景点坐标列表
        clearAllRoutes(); // 清空现有路径

        // 2. 用于记录成功解析的坐标（避免异步回调顺序问题）
        List<LatLonPoint> tempPoints = new ArrayList<>();
        // 用于记录解析失败的地点
        List<String> failedLocations = new ArrayList<>();
        // 计数器：跟踪地理编码完成的数量
        int[] completedCount = {0};
        int total = locationNames.size();

        // 3. 遍历地点列表，逐个解析为坐标（地理编码是异步操作）
        for (String name : locationNames) {
            if (TextUtils.isEmpty(name)) {
                failedLocations.add(name);
                completedCount[0]++;
                continue;
            }

            // 发起地理编码：将地点名称转换为经纬度
            GeocodeQuery query = new GeocodeQuery(name, city); // city为当前城市（已有变量）
            geocodeSearch.getFromLocationNameAsyn(query);

            // 利用匿名内部类+外部变量捕获当前地点，处理编码结果
            final String currentLocation = name;
            geocodeSearch.setOnGeocodeSearchListener(new GeocodeSearch.OnGeocodeSearchListener() {
                @Override
                public void onGeocodeSearched(GeocodeResult result, int code) {
                    completedCount[0]++;
                    if (code == PARSE_SUCCESS_CODE && result != null && !result.getGeocodeAddressList().isEmpty()) {
                        // 解析成功：获取第一个匹配的坐标
                        GeocodeAddress address = result.getGeocodeAddressList().get(0);
                        LatLonPoint point = address.getLatLonPoint();
                        tempPoints.add(point);

                        // 在地图上添加标记
                        LatLng latLng = new LatLng(point.getLatitude(), point.getLongitude());
                        MarkerOptions markerOptions = new MarkerOptions()
                                .position(latLng)
                                .icon(BitmapDescriptorFactory.fromResource(R.mipmap.landmark))
                                .title(currentLocation)
                                .snippet("点击删除该景点");
                        Marker marker = aMap.addMarker(markerOptions);
                        markerList.add(marker);
                        marker.showInfoWindow();

                    } else {
                        // 解析失败
                        failedLocations.add(currentLocation);
                    }

                    // 所有地点解析完成后，统一处理
                    if (completedCount[0] == total) {
                        // 将成功解析的坐标添加到景点列表
                        scenicPoints.addAll(tempPoints);
                        // 提示解析结果
                        if (!failedLocations.isEmpty()) {
                            showMsg("以下地点解析失败：" + TextUtils.join("、", failedLocations));
                        }
                        // 规划路径
                        updateRoute();
                        // 调整地图视角显示所有景点
                        adjustMapViewToShowAll();
                    }
                }

                @Override
                public void onRegeocodeSearched(RegeocodeResult result, int code) {
                    // 不需要处理逆地理编码（地址转坐标用的是onGeocodeSearched）
                }
            });
        }
    }

}




