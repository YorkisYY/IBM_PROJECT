// utils/SharedPrefsHelper.kt
package utils

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences 工具類 - 統一管理本地存儲
 */
class SharedPrefsHelper(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "weather_app_prefs"
    }
    
    private val sharedPrefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * 存儲字符串
     */
    fun putString(key: String, value: String) {
        sharedPrefs.edit().putString(key, value).apply()
    }
    
    /**
     * 獲取字符串
     */
    fun getString(key: String, defaultValue: String? = null): String? {
        return sharedPrefs.getString(key, defaultValue)
    }
    
    /**
     * 存儲長整型
     */
    fun putLong(key: String, value: Long) {
        sharedPrefs.edit().putLong(key, value).apply()
    }
    
    /**
     * 獲取長整型
     */
    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return sharedPrefs.getLong(key, defaultValue)
    }
    
    /**
     * 存儲雙精度浮點數
     */
    fun putDouble(key: String, value: Double) {
        sharedPrefs.edit().putString(key, value.toString()).apply()
    }
    
    /**
     * 獲取雙精度浮點數
     */
    fun getDouble(key: String, defaultValue: Double = Double.NaN): Double {
        val stringValue = sharedPrefs.getString(key, null)
        return stringValue?.toDoubleOrNull() ?: defaultValue
    }
    
    /**
     * 存儲布爾值
     */
    fun putBoolean(key: String, value: Boolean) {
        sharedPrefs.edit().putBoolean(key, value).apply()
    }
    
    /**
     * 獲取布爾值
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return sharedPrefs.getBoolean(key, defaultValue)
    }
    
    /**
     * 移除指定鍵值
     */
    fun remove(key: String) {
        sharedPrefs.edit().remove(key).apply()
    }
    
    /**
     * 清空所有數據
     */
    fun clear() {
        sharedPrefs.edit().clear().apply()
    }
    
    /**
     * 檢查是否包含指定鍵
     */
    fun contains(key: String): Boolean {
        return sharedPrefs.contains(key)
    }
}