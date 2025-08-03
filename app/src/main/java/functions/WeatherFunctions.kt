// functions/WeatherFunctions.kt
package functions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 天氣函數管理器 - 處理所有天氣相關的 Function Calling
 */
object WeatherFunctions {
    
    private const val TAG = "WeatherFunctions"
    private lateinit var weatherService: WeatherService
    
    /**
     * 初始化天氣服務
     */
    fun initialize(context: Context) {
        weatherService = WeatherService(context)
        Log.d(TAG, "✅ 天氣函數管理器已初始化")
    }
    
    /**
     * 執行天氣函數
     */
    suspend fun execute(functionName: String, arguments: String): String {
        Log.d(TAG, "🔧 執行天氣函數: $functionName")
        Log.d(TAG, "📝 參數: $arguments")
        
        return try {
            when (functionName) {
                "get_current_weather" -> executeCurrentWeather()
                "get_weather_by_city" -> executeCityWeather(arguments)
                else -> {
                    Log.w(TAG, "⚠️ 未知的天氣函數: $functionName")
                    "錯誤：未知的天氣函數 $functionName"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 天氣函數執行失敗: ${e.message}")
            "錯誤：天氣資料獲取失敗 - ${e.message}"
        }
    }
    
    /**
     * 執行當前位置天氣查詢
     */
    private suspend fun executeCurrentWeather(): String {
        Log.d(TAG, "🌍 執行當前位置天氣查詢")
        
        val weatherData = weatherService.getCurrentLocationWeather()
        
        val result = """
            當前位置天氣資訊：
            地點：${weatherData.city}, ${weatherData.country}
            天氣：${weatherData.condition}
            溫度：${weatherData.temperature}°C
            濕度：${weatherData.humidity}%
            風速：${String.format("%.1f", weatherData.windSpeed)} m/s
            描述：${weatherData.description}
        """.trimIndent()
        
        Log.d(TAG, "✅ 當前位置天氣查詢完成")
        return result
    }
    
    /**
     * 執行城市天氣查詢
     */
    private suspend fun executeCityWeather(arguments: String): String {
        Log.d(TAG, "🏙️ 執行城市天氣查詢")
        
        // 解析參數
        val cityName = try {
            if (arguments.isBlank()) {
                throw IllegalArgumentException("城市名稱不能為空")
            }
            
            // 嘗試解析 JSON 格式的參數
            if (arguments.trim().startsWith("{")) {
                val jsonArgs = Json.parseToJsonElement(arguments).jsonObject
                jsonArgs["city"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("參數中未找到 city 欄位")
            } else {
                // 如果不是 JSON，直接當作城市名稱
                arguments.trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 參數解析失敗: ${e.message}")
            return "錯誤：無法解析城市名稱參數 - ${e.message}"
        }
        
        Log.d(TAG, "🎯 查詢城市: $cityName")
        
        val weatherData = weatherService.getWeatherByCity(cityName)
        
        val result = """
            ${weatherData.city}天氣資訊：
            地點：${weatherData.city}, ${weatherData.country}
            天氣：${weatherData.condition}
            溫度：${weatherData.temperature}°C
            濕度：${weatherData.humidity}%
            風速：${String.format("%.1f", weatherData.windSpeed)} m/s
            描述：${weatherData.description}
        """.trimIndent()
        
        Log.d(TAG, "✅ 城市天氣查詢完成")
        return result
    }
    
    /**
     * 測試天氣服務連接
     */
    suspend fun testWeatherService(): String {
        return try {
            Log.d(TAG, "🔧 測試天氣服務連接")
            
            val mockWeather = weatherService.getMockWeather()
            
            """
                天氣服務測試成功！
                模擬數據：${mockWeather.city}, ${mockWeather.condition}, ${mockWeather.temperature}°C
                緩存狀態：${weatherService.getCacheStatus()}
            """.trimIndent()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 天氣服務測試失敗: ${e.message}")
            "天氣服務測試失敗：${e.message}"
        }
    }
    
    /**
     * 獲取天氣服務狀態
     */
    fun getServiceStatus(): String {
        return if (::weatherService.isInitialized) {
            "天氣服務已就緒\n${weatherService.getCacheStatus()}"
        } else {
            "天氣服務未初始化"
        }
    }
    
    /**
     * 清理天氣緩存
     */
    fun clearCache() {
        if (::weatherService.isInitialized) {
            weatherService.clearCache()
            Log.d(TAG, "🧹 天氣緩存已清理")
        }
    }
}

/**
 * 天氣服務 - 使用 wttr.in API
 * 轉換自 TypeScript 版本，使用相同的 API 端點
 */
class WeatherService(private val context: Context) {
    
    companion object {
        private const val TAG = "WeatherService"
        private const val WTTR_BASE_URL = "https://wttr.in"
        private const val CACHE_EXPIRY_MS = 10 * 60 * 1000L // 10分鐘快取
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // 快取系統
    private val weatherCache = ConcurrentHashMap<String, CachedWeatherData>()
    
    /**
     * 獲取當前位置的天氣
     */
    suspend fun getCurrentLocationWeather(): WeatherData = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🌍 開始獲取當前位置天氣")
            
            // 檢查位置權限
            if (!hasLocationPermission()) {
                Log.w(TAG, "⚠️ 位置權限未授予，使用IP定位")
                // 直接使用IP定位作為備用方案
                return@withContext getWeatherByIP()
            }
            
            // 獲取當前位置
            val location = getCurrentLocation()
            if (location != null) {
                Log.d(TAG, "📍 位置獲取成功: ${location.latitude}, ${location.longitude}")
                return@withContext getWeatherByCoordinates(location.latitude, location.longitude)
            } else {
                Log.w(TAG, "⚠️ 無法獲取位置，使用IP定位")
                // 使用IP定位作為備用方案
                return@withContext getWeatherByIP()
            }
            
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ 權限錯誤，使用IP定位: ${e.message}")
            return@withContext getWeatherByIP()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 獲取當前位置天氣失敗: ${e.message}")
            throw Exception("獲取當前位置天氣失敗: ${e.message}")
        }
    }
    
    /**
     * 根據座標獲取天氣 - 對應 TypeScript 的 getWeatherByCoordinates
     */
    suspend fun getWeatherByCoordinates(lat: Double, lon: Double): WeatherData = withContext(Dispatchers.IO) {
        val cacheKey = "coords_${lat}_${lon}"
        
        // 檢查快取
        weatherCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_EXPIRY_MS) {
                Log.d(TAG, "📦 使用快取的座標天氣資料")
                return@withContext cached.data
            }
        }
        
        try {
            Log.d(TAG, "🌐 從 wttr.in 獲取座標天氣: $lat, $lon")
            
            val url = "$WTTR_BASE_URL/$lat,$lon?format=j1"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "WeatherApp/1.0")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("API 請求失敗: ${response.code}")
            }
            
            val responseBody = response.body?.string()
                ?: throw IOException("回應內容為空")
            
            Log.d(TAG, "✅ API 回應成功，解析資料中...")
            
            val weatherData = parseWttrResponse(responseBody)
            
            // 快取結果
            weatherCache[cacheKey] = CachedWeatherData(weatherData, System.currentTimeMillis())
            
            Log.d(TAG, "🎉 座標天氣獲取成功: ${weatherData.city}")
            return@withContext weatherData
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 座標天氣獲取失敗: ${e.message}")
            throw Exception("座標天氣獲取失敗: ${e.message}")
        }
    }
    
    /**
     * 根據城市名稱獲取天氣 - 對應 TypeScript 的 getWeatherByCity
     */
    suspend fun getWeatherByCity(city: String): WeatherData = withContext(Dispatchers.IO) {
        val cacheKey = "city_$city"
        
        // 檢查快取
        weatherCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_EXPIRY_MS) {
                Log.d(TAG, "📦 使用快取的城市天氣資料")
                return@withContext cached.data
            }
        }
        
        try {
            Log.d(TAG, "🏙️ 從 wttr.in 獲取城市天氣: $city")
            
            val encodedCity = java.net.URLEncoder.encode(city, "UTF-8")
            val url = "$WTTR_BASE_URL/$encodedCity?format=j1"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "WeatherApp/1.0")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("API 請求失敗: ${response.code}")
            }
            
            val responseBody = response.body?.string()
                ?: throw IOException("回應內容為空")
            
            Log.d(TAG, "✅ API 回應成功，解析資料中...")
            
            val weatherData = parseWttrResponse(responseBody)
            
            // 快取結果
            weatherCache[cacheKey] = CachedWeatherData(weatherData, System.currentTimeMillis())
            
            Log.d(TAG, "🎉 城市天氣獲取成功: ${weatherData.city}")
            return@withContext weatherData
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 城市天氣獲取失敗: ${e.message}")
            throw Exception("城市天氣獲取失敗: ${e.message}")
        }
    }
    
    /**
     * 使用IP定位獲取天氣（備用方案）
     */
    private suspend fun getWeatherByIP(): WeatherData = withContext(Dispatchers.IO) {
        val cacheKey = "ip_location"
        
        // 檢查快取
        weatherCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_EXPIRY_MS) {
                Log.d(TAG, "📦 使用快取的IP定位天氣資料")
                return@withContext cached.data
            }
        }
        
        try {
            Log.d(TAG, "🌐 使用IP定位獲取天氣")
            
            val url = "$WTTR_BASE_URL/?format=j1"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "WeatherApp/1.0")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("API 請求失敗: ${response.code}")
            }
            
            val responseBody = response.body?.string()
                ?: throw IOException("回應內容為空")
            
            val weatherData = parseWttrResponse(responseBody)
            
            // 快取結果
            weatherCache[cacheKey] = CachedWeatherData(weatherData, System.currentTimeMillis())
            
            Log.d(TAG, "🎉 IP定位天氣獲取成功: ${weatherData.city}")
            return@withContext weatherData
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ IP定位天氣獲取失敗: ${e.message}")
            throw Exception("IP定位天氣獲取失敗: ${e.message}")
        }
    }
    
    /**
     * 解析 wttr.in 的 JSON 回應
     */
    private fun parseWttrResponse(responseBody: String): WeatherData {
        try {
            val jsonElement = json.parseToJsonElement(responseBody)
            val jsonObject = jsonElement.jsonObject
            
            // 獲取當前天氣條件
            val currentCondition = jsonObject["current_condition"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw Exception("無法獲取當前天氣條件")
            
            // 獲取最近地區資訊
            val nearestArea = jsonObject["nearest_area"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw Exception("無法獲取地區資訊")
            
            // 解析資料
            val temperature = currentCondition["temp_C"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: throw Exception("無法解析溫度")
            
            val weatherDesc = currentCondition["weatherDesc"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("value")?.jsonPrimitive?.content
                ?: "Unknown"
            
            val humidity = currentCondition["humidity"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            
            val windSpeedKmh = currentCondition["windspeedKmph"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val windSpeedMs = windSpeedKmh / 3.6 // 轉換為 m/s，對應 TypeScript 版本
            
            val cityName = nearestArea["areaName"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("value")?.jsonPrimitive?.content ?: "Unknown Location"
            
            val countryName = nearestArea["country"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("value")?.jsonPrimitive?.content ?: "Unknown Country"
            
            val icon = getIconFromCondition(weatherDesc)
            
            return WeatherData(
                temperature = temperature,
                condition = weatherDesc,
                description = weatherDesc.lowercase(),
                city = cityName,
                country = countryName,
                humidity = humidity,
                windSpeed = windSpeedMs,
                icon = icon
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ JSON 解析失敗: ${e.message}")
            Log.e(TAG, "🔍 回應內容: ${responseBody.take(500)}")
            throw Exception("天氣資料解析失敗: ${e.message}")
        }
    }
    
    /**
     * 根據天氣條件獲取圖示代碼 - 對應 TypeScript 的 getIconFromCondition
     */
    private fun getIconFromCondition(condition: String): String {
        val conditionLower = condition.lowercase()
        
        return when {
            conditionLower.contains("sunny") || conditionLower.contains("clear") -> "01d"
            conditionLower.contains("cloudy") -> "03d"
            conditionLower.contains("rain") -> "10d"
            conditionLower.contains("snow") -> "13d"
            conditionLower.contains("thunder") -> "11d"
            conditionLower.contains("mist") || conditionLower.contains("fog") -> "50d"
            else -> "01d"
        }
    }
    
    /**
     * 獲取當前GPS位置
     */
    private suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.Main) {
        try {
            if (!hasLocationPermission()) {
                return@withContext null
            }
            
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // 檢查GPS是否開啟
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Log.w(TAG, "⚠️ GPS和網路定位都未開啟")
                return@withContext null
            }
            
            // 嘗試獲取最後已知位置
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            
            for (provider in providers) {
                try {
                    val location = locationManager.getLastKnownLocation(provider)
                    if (location != null) {
                        Log.d(TAG, "📍 使用 $provider 獲取位置成功")
                        return@withContext location
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "⚠️ $provider 權限不足")
                }
            }
            
            Log.w(TAG, "⚠️ 無法獲取位置資訊")
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 位置獲取異常: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * 檢查位置權限
     */
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 獲取模擬天氣資料 - 對應 TypeScript 的 getMockWeather
     */
    fun getMockWeather(): WeatherData {
        val temperatures = listOf(15, 18, 22, 25, 28, 30)
        val conditions = listOf("Sunny", "Cloudy", "Partly Cloudy", "Light Rain")
        
    return WeatherData(
        temperature = 25,
        condition = "Sunny",
        description = "mock weather data",
        city = "測試城市",
        country = "台灣",
        humidity = 65,
        windSpeed = 5.0,
        icon = "01d"
    )
    }
    /**
     * 獲取快取狀態
     */
    fun getCacheStatus(): String {
        val activeEntries = weatherCache.entries.count { 
            System.currentTimeMillis() - it.value.timestamp < CACHE_EXPIRY_MS 
        }
        return "快取項目: $activeEntries/${weatherCache.size}"
    }
    
    /**
     * 清理過期快取
     */
    fun clearCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = weatherCache.entries
            .filter { currentTime - it.value.timestamp >= CACHE_EXPIRY_MS }
            .map { it.key }
        
        expiredKeys.forEach { weatherCache.remove(it) }
        Log.d(TAG, "🧹 清理了 ${expiredKeys.size} 個過期快取項目")
    }
    
    /**
     * 測試API連接
     */
    suspend fun testConnection(): String {
        return try {
            val mockWeather = getMockWeather()
            "✅ WeatherService 測試成功\n模擬資料: ${mockWeather.city}, ${mockWeather.condition}, ${mockWeather.temperature}°C"
        } catch (e: Exception) {
            "❌ WeatherService 測試失敗: ${e.message}"
        }
    }
}

/**
 * 天氣資料類 - 對應 TypeScript 的 WeatherData 接口
 */
@Serializable
data class WeatherData(
    val temperature: Int,
    val condition: String,
    val description: String,
    val city: String,
    val country: String,
    val humidity: Int,
    val windSpeed: Double,
    val icon: String
)

/**
 * 快取資料包裝類
 */
private data class CachedWeatherData(
    val data: WeatherData,
    val timestamp: Long
)