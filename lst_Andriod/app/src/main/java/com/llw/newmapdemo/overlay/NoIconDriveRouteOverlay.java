package com.llw.newmapdemo.overlay;

import android.content.Context;
import com.amap.api.maps.AMap;
import com.amap.api.maps.model.Marker;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.route.DrivePath;
import java.util.List;

public class NoIconDriveRouteOverlay extends DrivingRouteOverlay {

    // 补充 throughPointList 参数，传递给父类
    public NoIconDriveRouteOverlay(Context context, AMap amap, DrivePath path,
                                   LatLonPoint start, LatLonPoint end, List<LatLonPoint> throughPointList) {
        super(context, amap, path, start, end, throughPointList); // 匹配父类的5参数构造方法
    }

    @Override
    public void addToMap() {
        super.addToMap();
        removeAllRouteMarkers();
    }

    private void removeAllRouteMarkers() {
        List<Marker> allMarkers = mAMap.getMapScreenMarkers();
        if (allMarkers == null) return;

        for (Marker marker : allMarkers) {
            if (marker.getTitle() != null && marker.getTitle().contains("路径")) {
                marker.remove();
            }
        }
    }
}