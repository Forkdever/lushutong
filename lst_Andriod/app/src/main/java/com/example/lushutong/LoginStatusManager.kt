package com.example.lushutong

import android.content.Context
import android.content.SharedPreferences

/**
 * 登录状态管理类，用于持久化存储和获取登录状态
 */
object LoginStatusManager {
    private const val PREF_NAME = "login_status_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_CURRENT_USER = "current_user"

    private lateinit var sharedPreferences: SharedPreferences

    /**
     * 初始化SharedPreferences（在Application或MainActivity中调用）
     */
    @JvmStatic // 新增：让Java能静态调用
    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 设置登录状态
     */
    @JvmStatic // 新增：让Java能静态调用
    fun setLoggedIn(isLoggedIn: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_IS_LOGGED_IN, isLoggedIn).apply()
    }

    /**
     * 判断是否已登录
     */
    @JvmStatic // 新增：让Java能静态调用
    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    /**
     * 存储当前登录用户
     */
    @JvmStatic // 新增：让Java能静态调用
    fun setCurrentUser(userPhone: String) {
        sharedPreferences.edit().putString(KEY_CURRENT_USER, userPhone).apply()
    }

    /**
     * 获取当前登录用户
     */
    @JvmStatic // 新增：让Java能静态调用
    fun getCurrentUser(): String? {
        return sharedPreferences.getString(KEY_CURRENT_USER, null)
    }

    /**
     * 退出登录（清空状态）
     */
    @JvmStatic // 新增：让Java能静态调用
    fun logout() {
        sharedPreferences.edit().clear().apply()
    }
}