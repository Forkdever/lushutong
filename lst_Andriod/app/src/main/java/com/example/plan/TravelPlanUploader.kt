package com.example.lushutong

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TravelPlanUploader(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())

    interface UploadCallback {
        fun onSuccess(savedPlan: TravelPlan)
        fun onFailure(errorMsg: String)
    }

    // 上传新行程
    fun uploadTravelPlan(plan: TravelPlan, callback: UploadCallback) {
        ApiClient.apiService.createTravelPlan(plan).enqueue(object : Callback<TravelPlan> {
            override fun onResponse(call: Call<TravelPlan>, response: Response<TravelPlan>) {
                if (response.isSuccessful) {
                    val savedPlan = response.body()
                    savedPlan?.let { callback.onSuccess(it) }
                        ?: callback.onFailure("上传成功但返回数据为空")
                } else {
                    callback.onFailure("上传失败: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<TravelPlan>, t: Throwable) {
                callback.onFailure("网络错误: ${t.message}")
            }
        })
    }

    // 更新现有行程
    fun updateTravelPlanByPlanId(planId: String, plan: TravelPlan, callback: UploadCallback) {
        ApiClient.apiService.updateTravelPlanByPlanId(planId, plan).enqueue(object : Callback<TravelPlan> {
            override fun onResponse(call: Call<TravelPlan>, response: Response<TravelPlan>) {
                if (response.isSuccessful) {
                    val updatedPlan = response.body()
                    updatedPlan?.let { callback.onSuccess(it) }
                        ?: callback.onFailure("更新成功但返回数据为空")
                } else {
                    callback.onFailure("更新失败: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<TravelPlan>, t: Throwable) {
                callback.onFailure("网络错误: ${t.message}")
            }
        })
    }

    // 显示Toast（确保在主线程）
    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}