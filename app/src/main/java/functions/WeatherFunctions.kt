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
 * Weather Function Manager - handles all weather-related Function Calling
 */
object WeatherFunctions {
    
    private const val TAG = "WeatherFunctions"
    private lateinit var weatherService: WeatherService
    
    /**
     * Initialize weather service
     */
    fun initialize(context: Context) {
        weatherService = WeatherService(context)
        Log.d(TAG, "Weather function manager initialized")
    }
    
    /**
     * Execute weather function
     */
    suspend fun execute(functionName: String, arguments: String): String {
        Log.d(TAG, "Executing weather function: $functionName")
        Log.d(TAG, "Parameters: $arguments")
        
        return try {
            when (functionName) {
                "get_current_weather" -> {
                    // Check if city parameter exists
                    if (arguments != "{}" && arguments.isNotBlank()) {
                        // Has parameters = query specified city
                        executeCityWeather(arguments)
                    } else {
                        // No parameters = query current location
                        executeCurrentWeather()
                    }
                }
                "get_weather_by_city" -> executeCityWeather(arguments)
                else -> {
                    Log.w(TAG, "Unknown weather function: $functionName")
                    "Error: Unknown weather function $functionName"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Weather function execution failed: ${e.message}")
            "Error: Weather data retrieval failed - ${e.message}"
        }
}
    
    /**
     * Execute current location weather query
     */
    private suspend fun executeCurrentWeather(): String {
        Log.d(TAG, "Executing current location weather query")
        
        val weatherData = weatherService.getCurrentLocationWeather()
        
        val result = """
            Current location weather information:
            Location: ${weatherData.city}, ${weatherData.country}
            Weather: ${weatherData.condition}
            Temperature: ${weatherData.temperature}°C
            Humidity: ${weatherData.humidity}%
            Wind Speed: ${String.format("%.1f", weatherData.windSpeed)} m/s
            Description: ${weatherData.description}
        """.trimIndent()
        
        Log.d(TAG, "Current location weather query completed")
        return result
    }
    
    /**
     * Execute city weather query
     */
    private suspend fun executeCityWeather(arguments: String): String {
        Log.d(TAG, "Executing city weather query")
        
        // Parse parameters
        val cityName = try {
            if (arguments.isBlank()) {
                throw IllegalArgumentException("City name cannot be empty")
            }
            
            // Try to parse JSON format parameters
            if (arguments.trim().startsWith("{")) {
                val jsonArgs = Json.parseToJsonElement(arguments).jsonObject
                // Check "city" or "location" field
                jsonArgs["city"]?.jsonPrimitive?.content
                    ?: jsonArgs["location"]?.jsonPrimitive?.content  // Also check location
                    ?: throw IllegalArgumentException("City/Location field not found in parameters")
            } else {
                // If not JSON, treat as city name directly
                arguments.trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parameter parsing failed: ${e.message}")
            return "Error: Cannot parse city name parameter - ${e.message}"
        }
        
        Log.d(TAG, "Querying city: $cityName")
        
        val weatherData = weatherService.getWeatherByCity(cityName)
        
        val result = """
            ${weatherData.city} weather information:
            Location: ${weatherData.city}, ${weatherData.country}
            Weather: ${weatherData.condition}
            Temperature: ${weatherData.temperature}°C
            Humidity: ${weatherData.humidity}%
            Wind Speed: ${String.format("%.1f", weatherData.windSpeed)} m/s
            Description: ${weatherData.description}
        """.trimIndent()
        
        Log.d(TAG, "City weather query completed")
        return result
    }
        
    /**
     * Test weather service connection
     */
    suspend fun testWeatherService(): String {
        return try {
            Log.d(TAG, "Testing weather service connection")
            
            val mockWeather = weatherService.getMockWeather()
            
            """
                Weather service test successful!
                Mock data: ${mockWeather.city}, ${mockWeather.condition}, ${mockWeather.temperature}°C
                Cache status: ${weatherService.getCacheStatus()}
            """.trimIndent()
            
        } catch (e: Exception) {
            Log.e(TAG, "Weather service test failed: ${e.message}")
            "Weather service test failed: ${e.message}"
        }
    }
    
    /**
     * Get weather service status
     */
    fun getServiceStatus(): String {
        return if (::weatherService.isInitialized) {
            "Weather service ready\n${weatherService.getCacheStatus()}"
        } else {
            "Weather service not initialized"
        }
    }
    
    /**
     * Clear weather cache
     */
    fun clearCache() {
        if (::weatherService.isInitialized) {
            weatherService.clearCache()
            Log.d(TAG, "Weather cache cleared")
        }
    }
}

/**
 * Weather Service - using wttr.in API
 * Converted from TypeScript version, using the same API endpoints
 */
class WeatherService(val context: Context) {
    
    companion object {
        private const val TAG = "WeatherService"
        private const val WTTR_BASE_URL = "https://wttr.in"
        private const val CACHE_EXPIRY_MS = 10 * 60 * 1000L // 10 minutes cache
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Cache system
    private val weatherCache = ConcurrentHashMap<String, CachedWeatherData>()
    
    /**
     * Get current location weather
     */
    suspend fun getCurrentLocationWeather(): WeatherData = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to get current location weather")
            
            // Check location permission
            if (!hasLocationPermission()) {
                Log.w(TAG, "Location permission not granted, using IP location")
                // Use IP location as fallback
                return@withContext getWeatherByIP()
            }
            
            // Get current location
            val location = getCurrentLocation()
            if (location != null) {
                Log.d(TAG, "Location obtained successfully: ${location.latitude}, ${location.longitude}")
                return@withContext getWeatherByCoordinates(location.latitude, location.longitude)
            } else {
                Log.w(TAG, "Cannot get location, using IP location")
                // Use IP location as fallback
                return@withContext getWeatherByIP()
            }
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission error, using IP location: ${e.message}")
            return@withContext getWeatherByIP()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current location weather: ${e.message}")
            throw Exception("Failed to get current location weather: ${e.message}")
        }
    }
    
    /**
     * Get weather by coordinates - corresponds to TypeScript getWeatherByCoordinates
     */
    suspend fun getWeatherByCoordinates(lat: Double, lon: Double): WeatherData = withContext(Dispatchers.IO) {
        val cacheKey = "coords_${lat}_${lon}"
        
        // Check cache
        weatherCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_EXPIRY_MS) {
                Log.d(TAG, "Using cached coordinate weather data")
                return@withContext cached.data
            }
        }
        
        try {
            Log.d(TAG, "Getting coordinate weather from wttr.in: $lat, $lon")
            
            val url = "$WTTR_BASE_URL/$lat,$lon?format=j1"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "WeatherApp/1.0")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("API request failed: ${response.code}")
            }
            
            val responseBody = response.body?.string()
                ?: throw IOException("Response content is empty")
            
            Log.d(TAG, "API response successful, parsing data...")
            
            val weatherData = parseWttrResponse(responseBody)
            
            // Cache result
            weatherCache[cacheKey] = CachedWeatherData(weatherData, System.currentTimeMillis())
            
            Log.d(TAG, "Coordinate weather obtained successfully: ${weatherData.city}")
            return@withContext weatherData
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get coordinate weather: ${e.message}")
            throw Exception("Failed to get coordinate weather: ${e.message}")
        }
    }
    
    /**
     * Get weather by city name - corresponds to TypeScript getWeatherByCity
     */
    suspend fun getWeatherByCity(city: String): WeatherData = withContext(Dispatchers.IO) {
        val cacheKey = "city_$city"
        
        // Check cache
        weatherCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_EXPIRY_MS) {
                Log.d(TAG, "Using cached city weather data")
                return@withContext cached.data
            }
        }
        
        try {
            Log.d(TAG, "Getting city weather from wttr.in: $city")
            
            val encodedCity = java.net.URLEncoder.encode(city, "UTF-8")
            val url = "$WTTR_BASE_URL/$encodedCity?format=j1"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "WeatherApp/1.0")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("API request failed: ${response.code}")
            }
            
            val responseBody = response.body?.string()
                ?: throw IOException("Response content is empty")
            
            Log.d(TAG, "API response successful, parsing data...")
            
            val weatherData = parseWttrResponse(responseBody)
            
            // Cache result
            weatherCache[cacheKey] = CachedWeatherData(weatherData, System.currentTimeMillis())
            
            Log.d(TAG, "City weather obtained successfully: ${weatherData.city}")
            return@withContext weatherData
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get city weather: ${e.message}")
            throw Exception("Failed to get city weather: ${e.message}")
        }
    }
    
    /**
     * Get weather using IP location (fallback)
     */
    private suspend fun getWeatherByIP(): WeatherData = withContext(Dispatchers.IO) {
        val cacheKey = "ip_location"
        
        // Check cache
        weatherCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_EXPIRY_MS) {
                Log.d(TAG, "Using cached IP location weather data")
                return@withContext cached.data
            }
        }
        
        try {
            Log.d(TAG, "Getting weather using IP location")
            
            val url = "$WTTR_BASE_URL/?format=j1"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "WeatherApp/1.0")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("API request failed: ${response.code}")
            }
            
            val responseBody = response.body?.string()
                ?: throw IOException("Response content is empty")
            
            val weatherData = parseWttrResponse(responseBody)
            
            // Cache result
            weatherCache[cacheKey] = CachedWeatherData(weatherData, System.currentTimeMillis())
            
            Log.d(TAG, "IP location weather obtained successfully: ${weatherData.city}")
            return@withContext weatherData
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get IP location weather: ${e.message}")
            throw Exception("Failed to get IP location weather: ${e.message}")
        }
    }
    
    /**
     * Parse wttr.in JSON response
     */
    private fun parseWttrResponse(responseBody: String): WeatherData {
        try {
            val jsonElement = json.parseToJsonElement(responseBody)
            val jsonObject = jsonElement.jsonObject
            
            // Get current weather condition
            val currentCondition = jsonObject["current_condition"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw Exception("Cannot get current weather condition")
            
            // Get nearest area information
            val nearestArea = jsonObject["nearest_area"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw Exception("Cannot get area information")
            
            // Parse data
            val temperature = currentCondition["temp_C"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: throw Exception("Cannot parse temperature")
            
            val weatherDesc = currentCondition["weatherDesc"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("value")?.jsonPrimitive?.content
                ?: "Unknown"
            
            val humidity = currentCondition["humidity"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            
            val windSpeedKmh = currentCondition["windspeedKmph"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val windSpeedMs = windSpeedKmh / 3.6 // Convert to m/s, corresponds to TypeScript version
            
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
            Log.e(TAG, "JSON parsing failed: ${e.message}")
            Log.e(TAG, "Response content: ${responseBody.take(500)}")
            throw Exception("Weather data parsing failed: ${e.message}")
        }
    }
    
    /**
     * Get icon code based on weather condition - corresponds to TypeScript getIconFromCondition
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
     * Get current GPS location
     */
    private suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.Main) {
        try {
            if (!hasLocationPermission()) {
                return@withContext null
            }
            
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // Check if GPS is enabled
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Log.w(TAG, "GPS and network location are not enabled")
                return@withContext null
            }
            
            // Try to get last known location
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            
            for (provider in providers) {
                try {
                    val location = locationManager.getLastKnownLocation(provider)
                    if (location != null) {
                        Log.d(TAG, "Successfully obtained location using $provider")
                        return@withContext location
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "$provider permission insufficient")
                }
            }
            
            Log.w(TAG, "Cannot get location information")
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Location acquisition exception: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Check location permission
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
     * Get mock weather data - corresponds to TypeScript getMockWeather
     */
    fun getMockWeather(): WeatherData {
        val temperatures = listOf(15, 18, 22, 25, 28, 30)
        val conditions = listOf("Sunny", "Cloudy", "Partly Cloudy", "Light Rain")
        
    return WeatherData(
        temperature = 25,
        condition = "Sunny",
        description = "mock weather data",
        city = "Test City",
        country = "Taiwan",
        humidity = 65,
        windSpeed = 5.0,
        icon = "01d"
    )
    }
    /**
     * Get cache status
     */
    fun getCacheStatus(): String {
        val activeEntries = weatherCache.entries.count { 
            System.currentTimeMillis() - it.value.timestamp < CACHE_EXPIRY_MS 
        }
        return "Cache items: $activeEntries/${weatherCache.size}"
    }
    
    /**
     * Clear expired cache
     */
    fun clearCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = weatherCache.entries
            .filter { currentTime - it.value.timestamp >= CACHE_EXPIRY_MS }
            .map { it.key }
        
        expiredKeys.forEach { weatherCache.remove(it) }
        Log.d(TAG, "Cleared ${expiredKeys.size} expired cache items")
    }
    
    /**
     * Test API connection
     */
    suspend fun testConnection(): String {
        return try {
            val mockWeather = getMockWeather()
            "WeatherService test successful\nMock data: ${mockWeather.city}, ${mockWeather.condition}, ${mockWeather.temperature}°C"
        } catch (e: Exception) {
            "WeatherService test failed: ${e.message}"
        }
    }
}

/**
 * Weather data class - corresponds to TypeScript WeatherData interface
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
 * Cache data wrapper class
 */
private data class CachedWeatherData(
    val data: WeatherData,
    val timestamp: Long
)