package com.example.plan;

import java.util.List;
import com.google.gson.annotations.SerializedName;
/**
 * 每日行程类，保证序列化框架可访问字段
 */
public class Day {
    @SerializedName("day_number")
    private int dayNumber;

    @SerializedName("activities") // 确保activities字段也匹配（可选，若服务端字段名一致可省略）
    private List<Activity> activities;

    // 1. 保留带参构造函数（供反射/手动创建实例）
    public Day(int dayNumber, List<Activity> activities) {
        this.dayNumber = dayNumber;
        this.activities = activities;
    }

    // 2. 添加无参构造函数（关键：部分序列化框架反序列化时需要）
    public Day() {}

    // 3. 为私有字段添加 public getter 方法（序列化框架必须通过 getter 访问私有字段）
    public int getDayNumber() {
        return dayNumber;
    }

    public List<Activity> getActivities() {
        return activities;
    }

    // 4. 可选：添加 setter 方法（便于后续修改字段值）
    public void setDayNumber(int dayNumber) {
        this.dayNumber = dayNumber;
    }

    public void setActivities(List<Activity> activities) {
        this.activities = activities;
    }
}