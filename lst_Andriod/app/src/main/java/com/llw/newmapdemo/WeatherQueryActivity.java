package com.llw.newmapdemo;



import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;


/**
 * 天气查询Activity - 用户界面和交互逻辑
 */
public class WeatherQueryActivity extends AppCompatActivity {

    // UI组件
    private EditText etCity, etDate;
    private Button btnQuery;
    private LinearLayout llResult;
    private ProgressBar progressBar;

    // 显示结果的TextView
    private TextView tvLocation, tvWeather, tvTemperature, tvWind, tvReportTime, tvDressAdvice;

    // 天气服务
    private WeatherService weatherService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather_query);

        // 初始化UI组件
        initViews();

        // 初始化天气服务
        weatherService = new WeatherService(this);

        // 设置按钮点击事件
        setupClickListeners();
    }

    /**
     * 初始化所有UI组件
     */
    private void initViews() {
        // 输入组件
        etCity = findViewById(R.id.et_city);
        etDate = findViewById(R.id.et_date);
        btnQuery = findViewById(R.id.btn_query);

        // 结果组件
        llResult = findViewById(R.id.ll_result);
        progressBar = findViewById(R.id.progress_bar);

        // 结果显示TextView
        tvLocation = findViewById(R.id.tv_location);
        tvWeather = findViewById(R.id.tv_weather);
        tvTemperature = findViewById(R.id.tv_temperature);
        tvWind = findViewById(R.id.tv_wind);
        tvReportTime = findViewById(R.id.tv_report_time);
        tvDressAdvice = findViewById(R.id.tv_dress_advice);
    }

    /**
     * 设置点击事件监听器
     */
    private void setupClickListeners() {
        btnQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 当用户点击查询按钮时执行
                onQueryButtonClicked();
            }
        });
    }

    /**
     * 查询按钮点击处理
     */
    private void onQueryButtonClicked() {
        // 获取用户输入
        String city = etCity.getText().toString().trim();
        String date = etDate.getText().toString().trim();

        // 验证输入
        if (city.isEmpty()) {
            Toast.makeText(this, "请输入城市名称", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示加载状态
        showLoading(true);

        // 隐藏之前的结果
        llResult.setVisibility(View.GONE);

        // 调用天气服务查询
        weatherService.queryWeather(city, date, new WeatherService.WeatherCallback() {
            @Override
            public void onSuccess(WeatherData weatherData) {
                // 在主线程更新UI（网络请求在子线程，必须切回主线程更新UI）
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showLoading(false);
                        displayWeatherResult(weatherData);
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showLoading(false);
                        Toast.makeText(WeatherQueryActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    /**
     * 显示天气查询结果
     */
    private void displayWeatherResult(WeatherData weatherData) {
        // 更新UI显示天气信息
        tvLocation.setText("地点：" + weatherData.getCity());
        tvWeather.setText("天气：" + weatherData.getWeather());
        tvTemperature.setText("温度：" + weatherData.getTemperature());
        tvWind.setText("风力：" + weatherData.getWind());

        // 处理发布时间显示
        String reportTime = weatherData.getReportTime();
        if (reportTime != null && !reportTime.isEmpty()) {
            tvReportTime.setText("发布时间：" + reportTime);
            tvReportTime.setVisibility(View.VISIBLE);
        } else {
            tvReportTime.setVisibility(View.GONE);
        }

        // 显示穿衣建议
        tvDressAdvice.setText(weatherData.getDressAdvice());

        // 显示结果区域
        llResult.setVisibility(View.VISIBLE);

        // 显示成功提示
        Toast.makeText(this, "查询成功", Toast.LENGTH_SHORT).show();
    }

    /**
     * 显示或隐藏加载状态
     */
    private void showLoading(boolean show) {
        if (show) {
            progressBar.setVisibility(View.VISIBLE);
            btnQuery.setEnabled(false);
            btnQuery.setText("查询中...");
        } else {
            progressBar.setVisibility(View.GONE);
            btnQuery.setEnabled(true);
            btnQuery.setText("查询天气");
        }
    }
}