package com.llw.newmapdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapView;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.amap.api.services.route.BusPath;
import com.amap.api.services.route.BusRouteResult;
import com.amap.api.services.route.DrivePath;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.RouteSearch;
import com.amap.api.services.route.WalkPath;
import com.amap.api.services.route.WalkRouteResult;
import com.llw.newmapdemo.overlay.DrivingRouteOverlay;

// 这是你项目里已有的覆盖物类（包名自己按工程实际修改）
import com.llw.newmapdemo.overlay.RouteOverlay;
import com.llw.newmapdemo.overlay.WalkRouteOverlay;
import com.llw.newmapdemo.overlay.DrivingRouteOverlay;


import java.util.ArrayList;
import java.util.List;

/**
 * 负责：
 *  - 初始化高德地图 & 定位
 *  - 维护景点列表、路径规划
 *  - 对外提供：交通信息回调、POI 搜索结果回调
 *
 * 用法（给 UI 同学）：
 *  1. 在布局里放一个 MapView
 *  2. 在 Fragment/Activity 里 new TravelMapController(context, mapView)
 *  3. 把 Fragment/Activity 的生命周期转发给本类的 onCreate/onResume/onPause/onDestroy
 *  4. 调用：
 *      - searchPoi(keyword, city) 发起景点搜索
 *      - addScenicFromPoi(poiItem) 把某个搜索结果加为景点
 *      - clearAllScenic() 清空所有景点
 *  5. 通过：
 *      - setOnRouteInfoUpdateListener(...) 监听每段路的交通信息
 *      - setOnPoiSearchResultListener(...) 监听 POI 搜索结果
 */
public class TravelMapController implements
        LocationSource,
        AMapLocationListener,
        PoiSearch.OnPoiSearchListener,
        RouteSearch.OnRouteSearchListener,
        AMap.OnMarkerClickListener {

    private static final String TAG = "TravelMapController";

    private final Context context;
    private final MapView mapView;
    private AMap aMap;

    // 定位相关
    private OnLocationChangedListener mLocationChangedListener;
    private AMapLocationClient locationClient;
    private AMapLocationClientOption locationOption;

    // 路径规划相关
    private RouteSearch routeSearch;

    /** 景点的坐标列表：和 scenicMarkerList 一一对应 */
    private final List<LatLonPoint> scenicPoints = new ArrayList<>();
    /** 景点的“底座” Marker */
    private final List<Marker> scenicMarkerList = new ArrayList<>();
    /** 悬浮在上方的“文字方框” Marker */
    private final List<Marker> labelMarkerList = new ArrayList<>();
    /** 每一段路的信息（内部用） */
    private final List<RouteInfo> routeInfoList = new ArrayList<>();
    /** 地图上所有路径的覆盖物（步行 + 驾车） */
    private final List<RouteOverlay> routeOverlays = new ArrayList<>();


    // 查询阶段标记（步行 → 驾车 → 公交）
    private boolean walkPhaseStarted = false;
    private boolean drivePhaseStarted = false;
    private boolean transitPhaseStarted = false;

    // ⭐ 新增：是否已经把镜头移动到“当前位置”
    private boolean hasMovedToLocationOnce = false;

    // 城市信息（公交路线要用）
    private String city;      // 比如 "北京"
    private String cityCode;  // 高德城市编码，可选

    // ====== 对外回调接口 ======

    /** 对 UI 暴露的每一段路交通信息 */
    public static class RouteSegmentInfo {
        public int index;           // 第几段 (0,1,2...)
        public String fromName;     // 起点名（可选）
        public String toName;       // 终点名（可选）
        public String driveTime;    // 驾车时间
        public String transitTime;  // 公交时间
        public String walkTime;     // 步行时间
        public String driveDistance;// 驾车距离
    }

    /** 路径规划完成后回调给 UI 的接口 */
    public interface OnRouteInfoUpdateListener {
        void onRouteInfoUpdated(List<RouteSegmentInfo> list);
    }

    /** POI 搜索结果回调给 UI 的接口 */
    public interface OnPoiSearchResultListener {
        void onPoiResult(List<PoiItem> poiItems);
    }

    private OnRouteInfoUpdateListener routeInfoListener;
    private OnPoiSearchResultListener poiResultListener;

    public void setOnRouteInfoUpdateListener(OnRouteInfoUpdateListener l) {
        this.routeInfoListener = l;
    }

    public void setOnPoiSearchResultListener(OnPoiSearchResultListener l) {
        this.poiResultListener = l;
    }

    // ====== 构造 & 生命周期 ======

    public TravelMapController(Context context, MapView mapView) {
        this.context = context.getApplicationContext();
        this.mapView = mapView;
    }

    /** 必须在宿主 Activity/Fragment 的 onCreate 里调用 */
    public void onCreate(Bundle savedInstanceState) {
        mapView.onCreate(savedInstanceState);
        initMap();
        initLocation();
        initRouteSearch();
    }

    public void onResume() {
        mapView.onResume();
        if (locationClient != null) {
            locationClient.startLocation();
        }
    }

    public void onPause() {
        mapView.onPause();
        if (locationClient != null) {
            locationClient.stopLocation();
        }
    }

    public void onDestroy() {
        mapView.onDestroy();
        if (locationClient != null) {
            locationClient.onDestroy();
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        mapView.onSaveInstanceState(outState);
    }

    // ====== Public API 给 UI 用 ======

    /**
     * UI 同学调用：搜索某城市下的 POI（景点等）
     * @param keyword  关键字，如 “故宫”
     * @param cityName 城市名，如 “北京”，可为 null（使用当前 city）
     */
    public void searchPoi(String keyword, String cityName) {
        if (TextUtils.isEmpty(keyword)) {
            toast("请输入搜索关键词");
            return;
        }
        String searchCity = !TextUtils.isEmpty(cityName) ? cityName : this.city;
        if (TextUtils.isEmpty(searchCity)) {
            toast("城市信息为空，无法搜索 POI");
            return;
        }

        PoiSearch.Query query = new PoiSearch.Query(keyword, "", searchCity);
        query.setPageSize(20);
        query.setPageNum(0);

        PoiSearch poiSearch;
        try {
            poiSearch = new PoiSearch(context, query);
        } catch (AMapException e) {
            e.printStackTrace();
            toast("初始化 POI 搜索失败");
            return;
        }
        poiSearch.setOnPoiSearchListener(this);
        poiSearch.searchPOIAsyn();
    }

    /**
     * UI 同学调用：把某个 POI 搜索结果添加为“景点”
     */
    public void addScenicFromPoi(PoiItem poiItem) {
        if (poiItem == null || poiItem.getLatLonPoint() == null) {
            toast("无效的 POI 数据");
            return;
        }
        LatLonPoint point = poiItem.getLatLonPoint();
        scenicPoints.add(point);

        LatLng latLng = new LatLng(point.getLatitude(), point.getLongitude());

        MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.fromResource(R.mipmap.landmark))
                .title(poiItem.getTitle())
                .snippet("点击删除该景点");

        Marker marker = aMap.addMarker(markerOptions);
        scenicMarkerList.add(marker);

        updateRoute();
    }

    /** UI 同学调用：清空所有景点及路径 */
    public void clearAllScenic() {
        for (Marker m : scenicMarkerList) {
            m.remove();
        }
        scenicMarkerList.clear();

        for (Marker m : labelMarkerList) {
            m.remove();
        }
        labelMarkerList.clear();

        scenicPoints.clear();
        clearAllRoutes();
    }

    // ================= 内部实现 =================

    private void initMap() {
        if (aMap == null) {
            aMap = mapView.getMap();
        }
        UiSettings uiSettings = aMap.getUiSettings();
        uiSettings.setZoomControlsEnabled(false); // 例：关闭默认缩放按钮，按需改

        aMap.setOnMarkerClickListener(this);
        aMap.setLocationSource(this);
        aMap.setMyLocationEnabled(true);
    }

    private void initLocation() {
        try {
            locationClient = new AMapLocationClient(context);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        locationOption = new AMapLocationClientOption();
        locationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        locationOption.setOnceLocation(false);
        locationOption.setInterval(10_000);

        locationClient.setLocationOption(locationOption);
        locationClient.setLocationListener(this);
    }

    private void initRouteSearch() {
        try {
            routeSearch = new RouteSearch(context);
        } catch (AMapException e) {
            throw new RuntimeException(e);
        }
        routeSearch.setRouteSearchListener(this);
    }

    // ----- LocationSource 接口 -----

    @Override
    public void activate(OnLocationChangedListener listener) {
        this.mLocationChangedListener = listener;
        if (locationClient != null) {
            locationClient.startLocation();
        }
    }

    @Override
    public void deactivate() {
        mLocationChangedListener = null;
        if (locationClient != null) {
            locationClient.stopLocation();
        }
    }

    // ----- 定位回调 -----
    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        if (aMapLocation == null) return;
        if (aMapLocation.getErrorCode() == 0) {
            if (mLocationChangedListener != null) {
                mLocationChangedListener.onLocationChanged(aMapLocation);
            }
            // 记录城市信息（公交规划用）
            this.city = aMapLocation.getCity();
            this.cityCode = aMapLocation.getCityCode();

            // ⭐ 只在“还没有任何景点”时才把镜头移到当前位置，并且只移动一次
            if (!hasMovedToLocationOnce && scenicPoints.isEmpty()) {
                LatLng latLng = new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude());
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f));
                hasMovedToLocationOnce = true;
            }
        } else {
            Log.e(TAG, "定位失败：" + aMapLocation.getErrorCode() + ", " + aMapLocation.getErrorInfo());
        }
    }


    // ----- Marker 点击（删除景点） -----

    @Override
    public boolean onMarkerClick(Marker marker) {
        // 只删除“景点底座 marker”
        int index = scenicMarkerList.indexOf(marker);
        if (index >= 0) {
            // 同步删除 scenicPoints & scenicMarkerList
            scenicPoints.remove(index);
            scenicMarkerList.remove(index);
            marker.remove();
            // 重算路径
            updateRoute();
            return true; // 拦截默认行为
        }
        return false;
    }

    // ----- POI 搜索回调 -----

    @Override
    public void onPoiSearched(PoiResult poiResult, int resultCode) {
        if (resultCode != AMapException.CODE_AMAP_SUCCESS) {
            toast("搜索失败，错误码：" + resultCode);
            return;
        }
        ArrayList<PoiItem> poiItems = poiResult != null ? poiResult.getPois() : null;
        if (poiItems == null || poiItems.isEmpty()) {
            toast("未找到相关地点");
            return;
        }

        if (poiResultListener != null) {
            poiResultListener.onPoiResult(poiItems);
        } else {
            // 没有 UI 监听时，可以简单吐一个数量
            toast("找到 " + poiItems.size() + " 个结果（未设置 OnPoiSearchResultListener）");
        }
    }

    @Override
    public void onPoiItemSearched(PoiItem poiItem, int i) {
        // 不使用单条搜索
    }

    // ========= 路径规划 =========

    /** 内部用的每段路信息 */
    private static class RouteInfo {
        String driveTime = "无";
        String transitTime = "无";
        String walkTime = "无";
        String driveDistance = "0公里";

        LatLonPoint startPoint;
        LatLonPoint endPoint;

        boolean walkDone = false;
        boolean driveDone = false;
        boolean transitDone = false;
    }

    /** 重新按照 scenicPoints 进行路径规划 */
    private void updateRoute() {
        clearAllRoutes();
        routeInfoList.clear();

        walkPhaseStarted = false;
        drivePhaseStarted = false;
        transitPhaseStarted = false;

        if (scenicPoints.size() < 2) {
            // 景点不足 2 个，不需要规划
            notifyUIUpdate(); // 让 UI 清空一下
            return;
        }

        List<Integer> optimalOrder = getOptimalRouteOrder(scenicPoints);

        // 生成每段 RouteInfo
        for (int i = 0; i < optimalOrder.size() - 1; i++) {
            int startIndex = optimalOrder.get(i);
            int endIndex = optimalOrder.get(i + 1);
            RouteInfo info = new RouteInfo();
            info.startPoint = scenicPoints.get(startIndex);
            info.endPoint = scenicPoints.get(endIndex);
            routeInfoList.add(info);
        }

        // 根据最优顺序，在每个景点上方画“文字方框”
        updateLabelMarkersWithRouteOrder(optimalOrder);

        // 先启动步行阶段
        startWalkForAllSegments();
        // ⭐ 调整视角：把所有景点都包含在屏幕内
        adjustCameraToAllScenic();


    }

    /** 调整视角：把所有景点都包含在屏幕内 */
    private void adjustCameraToAllScenic() {
        if (scenicPoints.isEmpty()) {
            return;
        }

        LatLngBounds.Builder builder = LatLngBounds.builder();
        for (LatLonPoint p : scenicPoints) {
            builder.include(new LatLng(p.getLatitude(), p.getLongitude()));
        }

        // 第二个参数是边距（像素），可以根据需要调大/调小
        LatLngBounds bounds = builder.build();
        aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
    }


    private void clearAllRoutes() {
        // 这里简单做：清除地图上除 marker 外的所有覆盖物
        // 如果你项目里有专门的 RouteOverlay 列表，可以在这里 removeFromMap()
        //aMap.clear();
        // 清空后需要把 marker 重新加回去
        /*
        for (int i = 0; i < scenicPoints.size(); i++) {
            LatLonPoint p = scenicPoints.get(i);
            LatLng latLng = new LatLng(p.getLatitude(), p.getLongitude());
            MarkerOptions opt = new MarkerOptions()
                    .position(latLng)
                    .icon(BitmapDescriptorFactory.fromResource(R.mipmap.landmark))
                    .title(scenicMarkerList.size() > i ? scenicMarkerList.get(i).getTitle() : "景点");
            Marker m = aMap.addMarker(opt);
            scenicMarkerList.set(i, m);
        }*/
        // label marker 会在 updateRoute 里重建
        for (RouteOverlay overlay : routeOverlays) {
            overlay.removeFromMap();
        }
        routeOverlays.clear();
    }

    // TODO: 这里的“最优路径顺序”可以用你原来 MainActivity 的实现替换
    private List<Integer> getOptimalRouteOrder(List<LatLonPoint> points) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            order.add(i);
        }
        // 这里只是按添加顺序，实际项目中请用你自己的 TSP/贪心算法
        return order;
    }

    // === 三个阶段：步行 → 驾车 → 公交 ===

    private void startWalkForAllSegments() {
        if (walkPhaseStarted) return;
        walkPhaseStarted = true;

        for (RouteInfo info : routeInfoList) {
            RouteSearch.FromAndTo ft = new RouteSearch.FromAndTo(info.startPoint, info.endPoint);
            RouteSearch.WalkRouteQuery q =
                    new RouteSearch.WalkRouteQuery(ft, RouteSearch.WalkDefault);
            routeSearch.calculateWalkRouteAsyn(q);
        }
    }

    private void startDriveForAllSegments() {
        if (drivePhaseStarted) return;
        drivePhaseStarted = true;

        for (RouteInfo info : routeInfoList) {
            RouteSearch.FromAndTo ft = new RouteSearch.FromAndTo(info.startPoint, info.endPoint);
            RouteSearch.DriveRouteQuery q = new RouteSearch.DriveRouteQuery(
                    ft, RouteSearch.DRIVING_SINGLE_DEFAULT, null, null, "");
            routeSearch.calculateDriveRouteAsyn(q);
        }
    }

    private void startTransitForAllSegments() {
        if (transitPhaseStarted) return;
        transitPhaseStarted = true;

        for (RouteInfo info : routeInfoList) {
            RouteSearch.FromAndTo ft = new RouteSearch.FromAndTo(info.startPoint, info.endPoint);
            if (TextUtils.isEmpty(city) && TextUtils.isEmpty(cityCode)) {
                info.transitTime = "无";
                info.transitDone = true;
                continue;
            }
            String c = !TextUtils.isEmpty(cityCode) ? cityCode : city;
            RouteSearch.BusRouteQuery q = new RouteSearch.BusRouteQuery(
                    ft, RouteSearch.BUS_DEFAULT, c, 0);
            routeSearch.calculateBusRouteAsyn(q);
        }
    }

    // --- RouteSearch 回调 ---

    @Override
    public void onWalkRouteSearched(WalkRouteResult result, int code) {
        handleWalkRouteResult(result, code);
    }

    @Override
    public void onDriveRouteSearched(DriveRouteResult result, int code) {
        handleDriveRouteResult(result, code);
    }

    @Override
    public void onBusRouteSearched(BusRouteResult result, int code) {
        handleTransitRouteResult(result, code);
    }

    @Override
    public void onRideRouteSearched(com.amap.api.services.route.RideRouteResult rideRouteResult, int i) {
        // 未使用
    }

    // --- 各种 handleXXX 与你 MainActivity 里的逻辑类似 ---

    private void handleWalkRouteResult(WalkRouteResult result, int code) {
        if (result == null) return;

        int idx = findRouteIndexByPoints(result.getStartPos(), result.getTargetPos());
        if (idx < 0 || idx >= routeInfoList.size()) return;

        RouteInfo info = routeInfoList.get(idx);

        if (code == AMapException.CODE_AMAP_SUCCESS
                && result.getPaths() != null
                && !result.getPaths().isEmpty()) {

            WalkPath path = result.getPaths().get(0);
            int timeMin = (int) (path.getDuration() / 60);
            info.walkTime = "约" + timeMin + "分钟";

            // 如果你想画步行路线，可以在这里使用自定义 WalkRouteOverlay
            // WalkRouteOverlay overlay = new WalkRouteOverlay(...);
            // overlay.addToMap();

        } else {
            info.walkTime = "无";
        }
        info.walkDone = true;
        checkAllInfoReady();
    }

    private void handleDriveRouteResult(DriveRouteResult result, int code) {
        if (result == null) return;

        int idx = findRouteIndexByPoints(result.getStartPos(), result.getTargetPos());
        if (idx < 0 || idx >= routeInfoList.size()) return;

        RouteInfo info = routeInfoList.get(idx);

        if (code == AMapException.CODE_AMAP_SUCCESS
                && result.getPaths() != null
                && !result.getPaths().isEmpty()) {

            DrivePath path = result.getPaths().get(0);
            int timeMin = (int) (path.getDuration() / 60);
            double km = path.getDistance() / 1000.0;

            info.driveTime = "约" + timeMin + "分钟";
            info.driveDistance = String.format("%.1f公里", km);

            // 绘制驾车路径
            DrivingRouteOverlay routeOverlay = new DrivingRouteOverlay(
                    context,
                    aMap,
                    path,
                    result.getStartPos(),
                    result.getTargetPos(),
                    null);
            routeOverlay.setNodeIconVisibility(false);
            routeOverlay.addToMap();
            routeOverlays.add(routeOverlay);
        } else {
            info.driveTime = "无";
            info.driveDistance = "0公里";
        }
        info.driveDone = true;
        checkAllInfoReady();
    }

    private void handleTransitRouteResult(BusRouteResult result, int code) {
        if (result == null) return;

        int idx = findRouteIndexByPoints(result.getStartPos(), result.getTargetPos());
        if (idx < 0 || idx >= routeInfoList.size()) return;

        RouteInfo info = routeInfoList.get(idx);

        if (code == AMapException.CODE_AMAP_SUCCESS
                && result.getPaths() != null
                && !result.getPaths().isEmpty()) {

            BusPath path = result.getPaths().get(0);
            int timeMin = (int) (path.getDuration() / 60);
            info.transitTime = "约" + timeMin + "分钟";

        } else {
            info.transitTime = "无";
        }
        info.transitDone = true;
        checkAllInfoReady();
    }

    // --- 根据起终点坐标找到是第几段路 ---

    private int findRouteIndexByPoints(LatLonPoint start, LatLonPoint end) {
        if (start == null || end == null) return -1;
        for (int i = 0; i < routeInfoList.size(); i++) {
            RouteInfo info = routeInfoList.get(i);
            if (isSamePoint(info.startPoint, start) && isSamePoint(info.endPoint, end)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isSamePoint(LatLonPoint a, LatLonPoint b) {
        if (a == null || b == null) return false;
        double tol = 0.0005; // 大约几十米
        return Math.abs(a.getLatitude() - b.getLatitude()) < tol
                && Math.abs(a.getLongitude() - b.getLongitude()) < tol;
    }

    // --- 阶段控制 & 最终回调 UI ---

    private void checkAllInfoReady() {
        boolean allWalkDone = true;
        boolean allDriveDone = true;
        boolean allTransitDone = true;

        for (RouteInfo info : routeInfoList) {
            if (!info.walkDone) allWalkDone = false;
            if (!info.driveDone) allDriveDone = false;
            if (!info.transitDone) allTransitDone = false;
        }

        if (allWalkDone && !drivePhaseStarted) {
            startDriveForAllSegments();
            return;
        }

        if (allWalkDone && allDriveDone && !transitPhaseStarted) {
            startTransitForAllSegments();
            return;
        }

        if (allWalkDone && allDriveDone && allTransitDone) {
            notifyUIUpdate();
        }
    }

    private void notifyUIUpdate() {
        if (routeInfoListener == null) return;

        List<RouteSegmentInfo> list = new ArrayList<>();
        for (int i = 0; i < routeInfoList.size(); i++) {
            RouteInfo info = routeInfoList.get(i);
            RouteSegmentInfo seg = new RouteSegmentInfo();
            seg.index = i;
            seg.driveTime = info.driveTime;
            seg.walkTime = info.walkTime;
            seg.transitTime = info.transitTime;
            seg.driveDistance = info.driveDistance;
            list.add(seg);
        }
        routeInfoListener.onRouteInfoUpdated(list);
    }

    // ======= 文字方框 marker 相关 =======

    /** 把 view_scenic_marker.xml 渲染成 BitmapDescriptor */
    private BitmapDescriptor getScenicMarkerIcon(String text) {
        View view = LayoutInflater.from(context).inflate(R.layout.view_scenic_marker, null);
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

    /** 根据最优路径顺序，在每个景点上方画一个“白色半透明方框 + 蓝字编号” */
    private void updateLabelMarkersWithRouteOrder(List<Integer> optimalOrder) {
        // 先清除旧的 label marker
        for (Marker m : labelMarkerList) {
            m.remove();
        }
        labelMarkerList.clear();

        if (optimalOrder == null || optimalOrder.isEmpty()) return;
        if (scenicMarkerList.isEmpty()) return;

        for (int i = 0; i < optimalOrder.size(); i++) {
            int scenicIndex = optimalOrder.get(i);
            if (scenicIndex < 0 || scenicIndex >= scenicMarkerList.size()) continue;

            Marker baseMarker = scenicMarkerList.get(scenicIndex);
            LatLng pos = baseMarker.getPosition();

            String name = baseMarker.getTitle();
            if (name == null) name = "景点";

            String label = (i + 1) + ". " + name;
            BitmapDescriptor icon = getScenicMarkerIcon(label);

            MarkerOptions opt = new MarkerOptions()
                    .position(pos)
                    .icon(icon)
                    .anchor(0.5f, 1.6f) // 上移
                    .zIndex(10);

            Marker labelMarker = aMap.addMarker(opt);
            labelMarkerList.add(labelMarker);
        }
    }

    // ======== 工具方法 ========

    private void toast(String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }
}

