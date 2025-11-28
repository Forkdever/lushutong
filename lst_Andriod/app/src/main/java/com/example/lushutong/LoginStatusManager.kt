package com.example.lushutong

import android.content.Context
import android.content.SharedPreferences
import com.llw.newmapdemo.R
object LoginStatusManager {
    private const val PREF_NAME = "login_status"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"

    private lateinit var preferences: SharedPreferences

    fun init(context: Context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // 保存登录状态
    fun saveLoginStatus(userId: String, userName: String) {
        preferences.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, userName)
            .apply()
    }

    // 清除登录状态
    fun clearLoginStatus() {
        preferences.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .apply()
    }

    // 检查是否已登录
    fun isLoggedIn(): Boolean {
        return preferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    // 获取用户信息
    fun getUserId(): String? = preferences.getString(KEY_USER_ID, null)
    fun getUserName(): String? = preferences.getString(KEY_USER_NAME, null)
}