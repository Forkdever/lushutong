package com.example.plan;

import android.content.Context;
import android.util.Log;

import java.util.List;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.text.ParseException;

/**
 * 旅行计划管理工具类，封装所有网络请求操作
 */
public class TravelPlanManager {
    private static volatile TravelPlanManager instance;
    private final TravelPlanUploader uploader;

    // 私有构造函数，初始化上传器
    private TravelPlanManager(Context context) {
        // 使用应用上下文避免内存泄漏
        this.uploader = new TravelPlanUploader(context.getApplicationContext());
    }

    // 单例模式获取实例
    public static TravelPlanManager getInstance(Context context) {
        if (instance == null) {
            synchronized (TravelPlanManager.class) {
                if (instance == null) {
                    instance = new TravelPlanManager(context);
                }
            }
        }
        return instance;
    }

    //region 旅行计划操作方法

    /**
     * 创建新的旅行计划
     * @param planId 计划ID
     * @param title 计划标题
     * @param creatorId 创建者ID
     * @param destination 目的地
     * @param startDate 开始日期(格式: yyyy-MM-dd)
     * @param endDate 结束日期(格式: yyyy-MM-dd)
     * @param callback 回调接口
     */
    public void createTravelPlan(String planId, String title, int creatorId,
                                 String destination, String startDate, String endDate,List<Day> days,
                                 TravelPlanUploader.UploadCallback callback) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

            // 构建行程内容
            Content content = new Content(
                    destination,
                    sdf.parse(startDate),
                    sdf.parse(endDate),
                    days, // 可根据需要添加每日行程
                    null, // 可根据需要添加交通信息
                    null
            );

            // 构建旅行计划对象
            TravelPlan plan = new TravelPlan(
                    planId,
                    title,
                    creatorId,
                    null, // 可添加协作者
                    "draft", // 初始状态为草稿
                    content,
                    null, // 可添加版本历史
                    null, // 可添加标签
                    new Date(),
                    new Date()
            );

            uploader.uploadTravelPlan(plan, callback);
        } catch (ParseException e) {
            if (callback != null) {
                callback.onFailure("日期格式错误，请使用yyyy-MM-dd");
            }
        }
    }

    /**
     * 根据planId获取旅行计划
     */
    public void getTravelPlanByPlanId(String planId, TravelPlanUploader.FetchCallback callback) {
        uploader.fetchTravelPlanByPlanId(planId, callback);
    }

    /**
     * 根据创建者ID获取所有旅行计划
     */
    public void getTravelPlansByCreatorId(int creatorId, TravelPlanUploader.FetchListCallback callback) {
        uploader.getTravelPlansByCreatorId(creatorId, callback);
    }

    /**
     * 更新旅行计划
     * @param planId 计划ID
     * @param updatedPlan 更新后的计划对象
     * @param callback 回调接口
     */
    public void updateTravelPlan(String planId, TravelPlan updatedPlan, TravelPlanUploader.UploadCallback callback) {
        Log.d("TravelPlanManager", "更新行程计划: " + planId);
        uploader.updateTravelPlanByPlanId(planId, updatedPlan, callback);
    }

    //endregion
}