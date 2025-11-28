package com.example.lushutong

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

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

        // 标签按钮逻辑（不变）
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

        // 提交按钮逻辑（不变）
        btnSubmit.setOnClickListener {
            val departure = etDeparture.text.toString().trim()
            val destination = etDestination.text.toString().trim()
            val departureDate = etDepartureDate.text.toString().trim()
            val returnDate = etReturnDate.text.toString().trim()
            val otherDemand = etOtherDemand.text.toString().trim()

            if (departure.isEmpty() || destination.isEmpty() || departureDate.isEmpty() || returnDate.isEmpty()) {
                Toast.makeText(this, "出发地、目的地、出发日期、回程日期不能为空！", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val tagsStr = selectedTags.joinToString("、")
            val jsonResult = """
                {
                    "出发地": "$departure",
                    "目的地": "$destination",
                    "出发日期": "$departureDate",
                    "回程日期": "$returnDate",
                    "个性化需求": "${if (tagsStr.isEmpty()) "无" else tagsStr}",
                    "其他需求": "${if (otherDemand.isEmpty()) "无" else otherDemand}"
                 }""".trimIndent()
            val intent = Intent(this, CreateTripActivity::class.java)
            // 启动跳转
            startActivity(intent)

            // 可选：添加跳转动画（如需要过渡效果）
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // 返回按钮逻辑（不变）
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

        // 原生日历对话框，选择后返回格式化日期
        DatePickerDialog(this, { _, selectYear, selectMonth, selectDay ->
            val dateStr = "$selectYear-${selectMonth + 1}-$selectDay"
            onDateSelected(dateStr)
        }, year, month, day).show()
    }
}