package com.example.lushutong

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import java.util.Calendar
import com.android.volley.DefaultRetryPolicy
import com.llw.newmapdemo.R
class TravelActivity : AppCompatActivity() {
    // 声明控件（出发日期、回程日期）
    private lateinit var etDeparture: EditText
    private lateinit var etDestination: EditText
    private lateinit var etDepartureDate: EditText
    private lateinit var etReturnDate: EditText
    private lateinit var etOtherDemand: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnBack: Button

    private val tagButtons = mutableListOf<Button>()
    private val selectedTags = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_travel)
        bindViews()
        initEvents()
    }

    private fun bindViews() {
        etDeparture = findViewById(R.id.et_departure)
        etDestination = findViewById(R.id.et_destination)
        etDepartureDate = findViewById(R.id.et_departure_date)
        etReturnDate = findViewById(R.id.et_return_date)
        etOtherDemand = findViewById(R.id.et_other_demand)
        btnSubmit = findViewById(R.id.btn_submit)
        btnBack = findViewById(R.id.btn_back)

        tagButtons.add(findViewById(R.id.btn_tag1))
        tagButtons.add(findViewById(R.id.btn_tag2))
        tagButtons.add(findViewById(R.id.btn_tag3))
        tagButtons.add(findViewById(R.id.btn_tag4))
        tagButtons.add(findViewById(R.id.btn_tag5))
        tagButtons.add(findViewById(R.id.btn_tag6))
        tagButtons.add(findViewById(R.id.btn_tag7))
        tagButtons.add(findViewById(R.id.btn_tag8))
        tagButtons.add(findViewById(R.id.btn_tag9))
    }

    private fun initEvents() {
        // 出发日期点击：弹出日历
        etDepartureDate.setOnClickListener {
            showDatePickerDialog { selectedDate ->
                etDepartureDate.setText(selectedDate)
            }
        }

        // 回程日期点击：弹出日历
        etReturnDate.setOnClickListener {
            showDatePickerDialog { selectedDate ->
                etReturnDate.setText(selectedDate)
            }
        }

        // 标签按钮逻辑
        tagButtons.forEach { button ->
            button.setOnClickListener {
                val tagText = button.text.toString()
                if (selectedTags.contains(tagText)) {
                    selectedTags.remove(tagText)
                    button.setBackgroundResource(R.drawable.bg_tag)
                    button.setTextColor(0xFF475569.toInt())
                } else {
                    selectedTags.add(tagText)
                    button.setBackgroundResource(R.drawable.bg_submit)
                    button.setTextColor(resources.getColor(R.color.white, theme))
                }
            }
        }

        // 提交按钮逻辑
        btnSubmit.setOnClickListener {
            btnSubmit.isEnabled = false
            val departure = etDeparture.text.toString().trim()
            val destination = etDestination.text.toString().trim()
            val departureDate = etDepartureDate.text.toString().trim()
            val returnDate = etReturnDate.text.toString().trim()
            val otherDemand = etOtherDemand.text.toString().trim()

            // 基本验证（可选）
            // if (departure.isEmpty() || destination.isEmpty() || departureDate.isEmpty() || returnDate.isEmpty()) {
            //     Toast.makeText(this@TravelActivity, "出发地、目的地、出发日期、回程日期不能为空！", Toast.LENGTH_SHORT).show()
            //     btnSubmit.isEnabled = true
            //     return@setOnClickListener
            // }

            // 合并标签选择和其他需求
            val allPreferences = mutableListOf<String>()
            allPreferences.addAll(selectedTags)
            if (otherDemand.isNotEmpty()) {
                allPreferences.add(otherDemand)
            }

            // 发送数据到云服务器
            generateTravelPlan(
                departure = departure,
                destination = destination,
                startDate = departureDate,
                endDate = returnDate,
                preferences = allPreferences,
                otherDemand = otherDemand
            )
            val intent = Intent(this, CreateTripActivity::class.java)
            // （可选）如果需要传递数据，可添加Extra
            // intent.putExtra("key", "value")
            // 启动Activity
            startActivity(intent)
        }

        // 返回按钮逻辑
        btnBack.setOnClickListener {
            finish()
        }
    }

    /**
     * 通用日历选择对话框（点击日期控件时弹出）
     */
    private fun showDatePickerDialog(onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this@TravelActivity, { _, selectYear, selectMonth, selectDay ->
            val dateStr = "$selectYear-${selectMonth + 1}-$selectDay"
            onDateSelected(dateStr)
        }, year, month, day).show()
    }

    /**
     * 发送数据到云服务器生成旅行计划
     */
    private fun generateTravelPlan(
        departure: String,
        destination: String,
        startDate: String,
        endDate: String,
        preferences: List<String>,
        otherDemand: String = ""
    ) {
        val url = "http://39.97.43.117:8081/api/travel/generate-plan"

        // 构建JSON请求体 - 使用用户实际输入的数据
        val requestData = JSONObject()
        requestData.put("departure", departure)
        requestData.put("destination", destination)
        requestData.put("startDate", startDate)
        requestData.put("endDate", endDate)

        val prefArray = JSONArray()
        for (preference in preferences) {
            prefArray.put(preference)
        }
        requestData.put("preferences", prefArray)

        if (otherDemand.isNotEmpty()) {
            requestData.put("otherDemand", otherDemand)
        }

        // 显示加载提示
        Toast.makeText(this@TravelActivity, "正在生成旅行计划...", Toast.LENGTH_SHORT).show()

        // 使用StringRequest而不是JsonObjectRequest，因为服务器可能返回纯文本Markdown
        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                btnSubmit.isEnabled = true // 请求完成后重新启用按钮
                Log.d("TravelPlan", "收到的旅行规划：\n$response")

                // 直接使用响应内容，假设服务器返回的是纯Markdown文本
                displayTravelPlan(response)
            },
            Response.ErrorListener { error ->
                btnSubmit.isEnabled = true // 请求失败后也要重新启用按钮
                Log.e("TravelPlan", "生成失败: ${error.message}")
                showError("生成旅行计划失败，请重试")
            }
        ) {
            override fun getBodyContentType(): String {
                return "application/json; charset=utf-8"
            }

            override fun getBody(): ByteArray {
                return requestData.toString().toByteArray(Charsets.UTF_8)
            }
        }


        // 设置重试策略
        stringRequest.retryPolicy = DefaultRetryPolicy(
            30000, // 30秒超时
            0, // 不重试
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        // 获取Volley请求队列并添加请求
        val requestQueue = Volley.newRequestQueue(this@TravelActivity)
        requestQueue.add(stringRequest)
    }

    /**
     * 显示旅行规划结果
     */
    private fun displayTravelPlan(markdownPlan: String) {
        // 打印日志查看结果
        Log.d("TravelPlan", "收到的旅行规划：\n$markdownPlan")

        // 显示成功提示
        Toast.makeText(this@TravelActivity, "旅行计划生成成功！", Toast.LENGTH_LONG).show()

    }

    /**
     * 显示错误提示
     */
    private fun showError(message: String) {
        Toast.makeText(this@TravelActivity, message, Toast.LENGTH_SHORT).show()
    }
}