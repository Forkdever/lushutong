package com.example.plan;

/**
 * 行程活动类，需保证字段可被序列化框架访问
 */
public class Activity {
    private String time;
    private String location_name; // 字段名与服务器保持一致

    // 1. 保留带参构造函数（供反射创建实例）
    public Activity(String time, String location_name) {
        this.time = time;
        this.location_name = location_name;
    }

    // 2. 添加无参构造函数（可选，部分序列化框架需要）
    public Activity() {}

    // 3. 为私有字段添加 public getter 方法（关键！序列化框架需要）
    public String getTime() {
        return time;
    }

    public String getLocation_name() {
        return location_name;
    }

    // 4. 可选：添加 setter 方法（便于后续修改字段）
    public void setTime(String time) {
        this.time = time;
    }

    public void setLocation_name(String location_name) {
        this.location_name = location_name;
    }
}