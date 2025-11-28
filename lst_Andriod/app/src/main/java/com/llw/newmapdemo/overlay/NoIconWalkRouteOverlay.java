package com.llw.newmapdemo.overlay; // 确保包名和你的项目一致

import android.content.Context;
import com.amap.api.maps.AMap;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.route.WalkPath;

// 继承高德的步行路径覆盖物
public class NoIconWalkRouteOverlay extends WalkRouteOverlay {

    // 构造方法，直接复用父类的构造
    public NoIconWalkRouteOverlay(Context context, AMap amap, WalkPath path, LatLonPoint start, LatLonPoint end) {
        super(context, amap, path, start, end);
    }

    // 重写添加起点图标的方法：空实现（不添加任何图标）

    protected void addStartMarker() {
        // 这里什么都不写，就不会显示起点图标
    }

    // 重写添加终点图标的方法：空实现
    protected void addEndMarker() {
        // 这里什么都不写，就不会显示终点图标
    }

    // 重写添加路径节点图标的方法：空实现（节点就是路径中的转弯点等标记）

    protected void addNodeMarker(int index, LatLonPoint latLonPoint) {
        // 这里什么都不写，就不会显示路径中的节点图标
    }
}