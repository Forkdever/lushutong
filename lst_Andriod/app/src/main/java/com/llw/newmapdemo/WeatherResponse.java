package com.llw.newmapdemo;


import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 高德天气API响应数据模型
 * 对应JSON结构：https://lbs.amap.com/api/webservice/guide/api/weatherinfo
 */
public class WeatherResponse {

    @SerializedName("status")
    public String status;           // 响应状态 "1"=成功 "0"=失败

    @SerializedName("count")
    public String count;            // 返回结果总数目

    @SerializedName("info")
    public String info;             // 返回状态说明

    @SerializedName("infocode")
    public String infoCode;         // 返回状态说明编码

    @SerializedName("lives")
    public List<LiveWeather> lives; // 实时天气信息列表

    @SerializedName("forecasts")
    public List<ForecastWeather> forecasts; // 预报天气信息列表

    // 无参构造方法（Gson需要）
    public WeatherResponse() {}
}

/**
 * 实时天气数据模型
 */
class LiveWeather {
    @SerializedName("province")
    public String province;         // 省份名

    @SerializedName("city")
    public String city;             // 城市名

    @SerializedName("adcode")
    public String adcode;           // 区域编码

    @SerializedName("weather")
    public String weather;          // 天气现象（阴、晴、雨等）

    @SerializedName("temperature")
    public String temperature;      // 实时温度

    @SerializedName("winddirection")
    public String windDirection;    // 风向

    @SerializedName("windpower")
    public String windPower;        // 风力

    @SerializedName("humidity")
    public String humidity;         // 空气湿度

    @SerializedName("reporttime")
    public String reportTime;       // 数据发布的时间

    public LiveWeather() {}
}

/**
 * 预报天气数据模型
 */
class ForecastWeather {
    @SerializedName("city")
    public String city;             // 城市名称

    @SerializedName("adcode")
    public String adcode;           // 城市编码

    @SerializedName("province")
    public String province;         // 省份名称

    @SerializedName("reporttime")
    public String reportTime;       // 预报发布时间

    @SerializedName("casts")
    public List<DailyForecast> casts; // 天气预报数据

    public ForecastWeather() {}
}

/**
 * 每日天气预报数据
 */
class DailyForecast {
    @SerializedName("date")
    public String date;             // 日期

    @SerializedName("week")
    public String week;             // 星期几

    @SerializedName("dayweather")
    public String dayWeather;       // 白天天气现象

    @SerializedName("nightweather")
    public String nightWeather;     // 晚上天气现象

    @SerializedName("daytemp")
    public String dayTemp;          // 白天温度

    @SerializedName("nighttemp")
    public String nightTemp;        // 晚上温度

    @SerializedName("daywind")
    public String dayWind;          // 白天风向

    @SerializedName("nightwind")
    public String nightWind;        // 晚上风向

    @SerializedName("daypower")
    public String dayPower;         // 白天风力

    @SerializedName("nightpower")
    public String nightPower;       // 晚上风力

    public DailyForecast() {}
}