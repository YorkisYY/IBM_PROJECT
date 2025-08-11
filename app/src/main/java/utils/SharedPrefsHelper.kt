// utils/SharedPrefsHelper.kt
package utils

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences utility class - Unified management of local storage
 */
class SharedPrefsHelper(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "weather_app_prefs"
    }
    
    private val sharedPrefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Store string
     */
    fun putString(key: String, value: String) {
        sharedPrefs.edit().putString(key, value).apply()
    }
    
    /**
     * Get string
     */
    fun getString(key: String, defaultValue: String? = null): String? {
        return sharedPrefs.getString(key, defaultValue)
    }
    
    /**
     * Store long integer
     */
    fun putLong(key: String, value: Long) {
        sharedPrefs.edit().putLong(key, value).apply()
    }
    
    /**
     * Get long integer
     */
    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return sharedPrefs.getLong(key, defaultValue)
    }
    
    /**
     * Store double precision floating point number
     */
    fun putDouble(key: String, value: Double) {
        sharedPrefs.edit().putString(key, value.toString()).apply()
    }
    
    /**
     * Get double precision floating point number
     */
    fun getDouble(key: String, defaultValue: Double = Double.NaN): Double {
        val stringValue = sharedPrefs.getString(key, null)
        return stringValue?.toDoubleOrNull() ?: defaultValue
    }
    
    /**
     * Store boolean value
     */
    fun putBoolean(key: String, value: Boolean) {
        sharedPrefs.edit().putBoolean(key, value).apply()
    }
    
    /**
     * Get boolean value
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return sharedPrefs.getBoolean(key, defaultValue)
    }
    
    /**
     * Remove specified key-value pair
     */
    fun remove(key: String) {
        sharedPrefs.edit().remove(key).apply()
    }
    
    /**
     * Clear all data
     */
    fun clear() {
        sharedPrefs.edit().clear().apply()
    }
    
    /**
     * Check if contains specified key
     */
    fun contains(key: String): Boolean {
        return sharedPrefs.contains(key)
    }
}