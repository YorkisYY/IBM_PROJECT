// functions/LocationFunctions.kt
package functions

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.serialization.*

/**
 * Location Function Manager - reuses WeatherFunctions location detection method
 */
object LocationFunctions {
    
    private const val TAG = "LocationFunctions"
    private lateinit var weatherService: WeatherService
    
    /**
     * Initialize location service - reuse WeatherService
     */
    fun initialize(context: Context) {
        weatherService = WeatherService(context)
        Log.d(TAG, "✅ Location function manager initialized (using WeatherFunctions location method)")
    }
    
    /**
     * Execute location function
     */
    suspend fun execute(functionName: String, arguments: String): String {
        Log.d(TAG, "🔧 Executing location function: $functionName")
        Log.d(TAG, "📝 Parameters: $arguments")
        
        return try {
            when (functionName) {
                "get_current_location" -> executeCurrentLocation()
                "get_user_location" -> executeUserLocation()
                "get_location_info" -> executeLocationInfo()
                else -> {
                    Log.w(TAG, "⚠️ Unknown location function: $functionName")
                    "Error: Unknown location function $functionName"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Location function execution failed: ${e.message}")
            "Error: Location data retrieval failed - ${e.message}"
        }
    }
    
    /**
     * Execute current location query - using exact same method as weather
     */
    private suspend fun executeCurrentLocation(): String {
        Log.d(TAG, "📍 Executing current location query (using weather location method)")
        
        // 🎯 使用與天氣完全相同的location檢測方式
        val weatherData = weatherService.getCurrentLocationWeather()
        
        val result = """
            Your current location information:
            Location: ${weatherData.city}, ${weatherData.country}
            Detection method: ${if (hasLocationPermission()) "GPS + wttr.in API" else "IP location + wttr.in API"}
            Coordinates: Available via wttr.in service
            Area type: ${getAreaType(weatherData.city)}
        """.trimIndent()
        
        Log.d(TAG, "✅ Current location query completed using weather method")
        return result
    }
    
    /**
     * Execute user location query (same as current location)
     */
    private suspend fun executeUserLocation(): String {
        Log.d(TAG, "🏠 Executing user location query")
        return executeCurrentLocation()
    }
    
    /**
     * Execute detailed location info query
     */
    private suspend fun executeLocationInfo(): String {
        Log.d(TAG, "🏘️ Executing detailed location info query")
        
        // 使用與天氣相同的location檢測方式
        val weatherData = weatherService.getCurrentLocationWeather()
        
        val result = """
            Detailed location information:
            City: ${weatherData.city}
            Country: ${weatherData.country}
            Detection method: ${if (hasLocationPermission()) "GPS coordinates + wttr.in geocoding" else "IP-based location + wttr.in geocoding"}
            Location accuracy: ${if (hasLocationPermission()) "High (GPS-based)" else "Medium (IP-based)"}
            Area classification: ${getAreaType(weatherData.city)}
            Service provider: wttr.in location API
            Current temperature: ${weatherData.temperature}°C (bonus weather info)
        """.trimIndent()
        
        Log.d(TAG, "✅ Detailed location info query completed")
        return result
    }
    
    /**
     * Check location permission - directly use WeatherService context
     */
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            weatherService.context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            weatherService.context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Get area type information
     */
    private fun getAreaType(city: String): String {
        val majorCities = listOf(
            "London", "New York", "Tokyo", "Beijing", "Shanghai", 
            "Los Angeles", "Chicago", "Taipei", "Hong Kong", "Singapore",
            "Paris", "Berlin", "Sydney", "Toronto", "Mumbai"
        )
        
        return if (majorCities.any { city.contains(it, ignoreCase = true) }) {
            "Major Metropolitan Area"
        } else {
            "Urban/Suburban Area"
        }
    }
    
    /**
     * Test location service connection
     */
    suspend fun testLocationService(): String {
        return try {
            Log.d(TAG, "🔧 Testing location service connection (using weather location method)")
            
            // 測試與天氣相同的location檢測
            val weatherData = weatherService.getCurrentLocationWeather()
            
            """
                Location service test successful!
                Test location: ${weatherData.city}, ${weatherData.country}
                Detection method: ${if (hasLocationPermission()) "GPS + wttr.in" else "IP + wttr.in"}
                Service status: Ready (using WeatherFunctions location logic)
                Cache status: ${weatherService.getCacheStatus()}
            """.trimIndent()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Location service test failed: ${e.message}")
            "Location service test failed: ${e.message}"
        }
    }
    
    /**
     * Get location service status
     */
    fun getServiceStatus(): String {
        return if (::weatherService.isInitialized) {
            val hasPermission = hasLocationPermission()
            """
                Location service ready (using WeatherFunctions method)
                Permission: ${if (hasPermission) "Granted (GPS available)" else "Using IP location"}
                API: wttr.in location service
                Cache: ${weatherService.getCacheStatus()}
            """.trimIndent()
        } else {
            "Location service not initialized"
        }
    }
}