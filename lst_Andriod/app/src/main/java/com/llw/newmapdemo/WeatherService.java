package com.llw.newmapdemo;


import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 天气服务类 - 负责与高德天气API通信
 */
public class WeatherService {

    // 替换为你的高德Web服务API Key
    private static final String WEATHER_API_KEY = "f8c274cdf8de00facb620ae2a43a2c84";

    private Context context;
    private Gson gson;
    private OkHttpClient httpClient;

    // 回调接口，用于异步返回数据
    public interface WeatherCallback {
        void onSuccess(WeatherData weatherData);
        void onFailure(String errorMessage);
    }

    public WeatherService(Context context) {
        this.context = context;
        this.gson = new Gson();
        this.httpClient = new OkHttpClient();
    }

    /**
     * 查询指定城市和日期的天气
     * @param cityName 城市名称（如："北京市"）
     * @param date 查询日期（格式："yyyy-MM-dd"），如果为null则查询实时天气
     * @param callback 结果回调
     */
    public void queryWeather(String cityName, String date, WeatherCallback callback) {
        new Thread(() -> {
            try {
                WeatherData weatherData;
                Log.d("WeatherQuery", "开始查询天气：城市=" + cityName + "，日期=" + date);

                if (date == null || date.isEmpty() || isToday(date)) {
                    Log.d("WeatherQuery", "查询实时天气");
                    weatherData = getLiveWeather(cityName);
                } else {
                    Log.d("WeatherQuery", "查询预报天气");
                    weatherData = getForecastWeather(cityName, date);
                }

                if (weatherData != null) {
                    String dressAdvice = generateDressAdvice(weatherData);
                    weatherData.setDressAdvice(dressAdvice);
                    callback.onSuccess(weatherData);
                    Log.d("WeatherQuery", "天气查询成功");
                } else {
                    Log.e("WeatherQuery", "getLiveWeather/getForecastWeather返回null");
                    callback.onFailure("获取天气数据失败：接口返回空");
                }

            } catch (Exception e) {
                Log.e("WeatherQuery", "天气查询异常", e); // 打印完整异常栈
                callback.onFailure("天气查询异常: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 获取实时天气数据
     */
    private WeatherData getLiveWeather(String cityName) throws IOException {
        // 构建API请求URL
        String url = "https://restapi.amap.com/v3/weather/weatherInfo?" +
                "key=" + WEATHER_API_KEY +
                "&city=" + URLEncoder.encode(cityName, "UTF-8") +
                "&extensions=all";  // base=实时天气，all=预报天气

        // 发送HTTP GET请求
        Request request = new Request.Builder().url(url).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String jsonResponse = response.body().string();

                // 使用Gson解析JSON
                WeatherResponse weatherResponse = gson.fromJson(jsonResponse, WeatherResponse.class);

                // 检查API响应状态
                if ("1".equals(weatherResponse.status) &&
                        weatherResponse.lives != null &&
                        !weatherResponse.lives.isEmpty()) {

                    LiveWeather live = weatherResponse.lives.get(0);
                    return convertLiveToWeatherData(live);
                }
            }
        }
        return null;
    }

    /**
     * 获取预报天气数据
     */
    private WeatherData getForecastWeather(String cityName, String targetDate) throws IOException {
        // 构建API请求URL
        String url = "https://restapi.amap.com/v3/weather/weatherInfo?" +
                "key=" + WEATHER_API_KEY +
                "&city=" + URLEncoder.encode(cityName, "UTF-8") +
                "&extensions=all";  // 获取预报天气

        // 发送HTTP GET请求
        Request request = new Request.Builder().url(url).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String jsonResponse = response.body().string();
                // 打印完整响应
                Log.d("WeatherService", "实时天气API返回: " + jsonResponse);                 // 使用Gson解析JSON
                //
                WeatherResponse weatherResponse = gson.fromJson(jsonResponse, WeatherResponse.class);

                // 检查API响应状态
                if ("1".equals(weatherResponse.status) &&
                        weatherResponse.forecasts != null &&
                        !weatherResponse.forecasts.isEmpty()) {

                    ForecastWeather forecast = weatherResponse.forecasts.get(0);

                    // 在预报数据中查找指定日期的天气
                    if (forecast.casts != null) {
                        for (DailyForecast daily : forecast.casts) {
                            if (targetDate.equals(daily.date)) {
                                return convertForecastToWeatherData(daily, forecast.city);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 将实时天气数据转换为业务数据模型
     */
    private WeatherData convertLiveToWeatherData(LiveWeather live) {
        WeatherData data = new WeatherData();
        data.setCity(live.city);
        data.setDate(getCurrentDate()); // 实时天气使用当前日期
        data.setWeather(live.weather);
        data.setTemperature(live.temperature + "℃");
        data.setWind(live.windDirection + " " + live.windPower + "级");
        data.setHumidity(live.humidity + "%");
        data.setReportTime(live.reportTime);
        return data;
    }

    /**
     * 将预报天气数据转换为业务数据模型
     */
    private WeatherData convertForecastToWeatherData(DailyForecast forecast, String city) {
        WeatherData data = new WeatherData();
        data.setCity(city);
        data.setDate(forecast.date);
        data.setWeather(forecast.dayWeather); // 使用白天天气
        data.setTemperature(forecast.nightTemp + "℃~" + forecast.dayTemp + "℃");
        data.setWind(forecast.dayWind + " " + forecast.dayPower + "级");
        data.setHumidity(""); // 预报接口不提供湿度
        data.setReportTime(""); // 在预报数据中不单独提供
        return data;
    }

    /**
     * 生成穿衣建议（基于温度和天气现象）
     */
    private String generateDressAdvice(WeatherData data) {
        try {
            // 提取温度数值（处理温度范围如"15℃~25℃"）
            String tempStr = data.getTemperature().replace("℃", "").split("~")[0];
            int temperature = Integer.parseInt(tempStr.trim());

            String weather = data.getWeather();
            String baseAdvice = getBaseDressAdvice(temperature);
            return adjustAdviceByWeather(baseAdvice, weather);

        } catch (Exception e) {
            return "根据天气情况选择合适的衣物";
        }
    }

    /**
     * 基于温度的穿衣建议
     */
    private String getBaseDressAdvice(int temperature) {
        if (temperature >= 28) {
            return "天气炎热，建议穿短袖、短裤、裙子等夏季衣物";
        } else if (temperature >= 23) {
            return "温度舒适，建议穿T恤、薄外套、长裤等春秋过渡装";
        } else if (temperature >= 18) {
            return "稍有凉意，建议穿长袖、针织衫、薄毛衣等";
        } else if (temperature >= 10) {
            return "天气较凉，建议穿毛衣、厚外套、风衣等";
        } else if (temperature >= 0) {
            return "天气寒冷，建议穿羽绒服、棉衣、厚毛衣，注意保暖";
        } else {
            return "天气严寒，建议穿厚羽绒服、保暖内衣、帽子围巾等全套防寒装备";
        }
    }

    /**
     * 根据天气现象调整建议
     */
    private String adjustAdviceByWeather(String baseAdvice, String weather) {
        StringBuilder advice = new StringBuilder(baseAdvice);

        if (weather.contains("雨")) {
            advice.append("，有降雨请携带雨具");
        }
        if (weather.contains("雪")) {
            advice.append("，下雪路滑注意防滑");
        }
        if (weather.contains("风")) {
            advice.append("，风力较大建议添加防风外套");
        }
        if (weather.contains("晴")) {
            advice.append("，天气晴朗适宜户外活动");
        }

        return advice.toString();
    }

    /**
     * 工具方法：判断是否为今天
     */
    private boolean isToday(String date) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        return today.equals(date);
    }

    /**
     * 工具方法：获取当前日期字符串
     */
    private String getCurrentDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
}
