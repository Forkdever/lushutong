package com.llw.newmapdemo;

/**
 * 业务层天气数据模型
 * 用于在App内部传递和显示天气信息
 */
public class WeatherData {
    private String city;            // 城市名称
    private String date;            // 查询日期
    private String weather;         // 天气现象
    private String temperature;     // 温度
    private String wind;            // 风力风向
    private String humidity;        // 湿度
    private String reportTime;      // 数据发布时间
    private String dressAdvice;     // 穿衣建议

    // 构造方法
    public WeatherData() {}

    // Getter和Setter方法
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getWeather() { return weather; }
    public void setWeather(String weather) { this.weather = weather; }

    public String getTemperature() { return temperature; }
    public void setTemperature(String temperature) { this.temperature = temperature; }

    public String getWind() { return wind; }
    public void setWind(String wind) { this.wind = wind; }

    public String getHumidity() { return humidity; }
    public void setHumidity(String humidity) { this.humidity = humidity; }

    public String getReportTime() { return reportTime; }
    public void setReportTime(String reportTime) { this.reportTime = reportTime; }

    public String getDressAdvice() { return dressAdvice; }
    public void setDressAdvice(String dressAdvice) { this.dressAdvice = dressAdvice; }

    @Override
    public String toString() {
        return "WeatherData{" +
                "city='" + city + '\'' +
                ", date='" + date + '\'' +
                ", weather='" + weather + '\'' +
                ", temperature='" + temperature + '\'' +
                ", wind='" + wind + '\'' +
                ", humidity='" + humidity + '\'' +
                ", reportTime='" + reportTime + '\'' +
                ", dressAdvice='" + dressAdvice + '\'' +
                '}';
    }
}