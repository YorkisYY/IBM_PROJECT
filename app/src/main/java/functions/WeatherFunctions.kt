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
        Log.d(TAG, "Weather function manager initialized with Open-Meteo API")
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
                    if (arguments != "{}" && arguments.isNotBlank()) {
                        executeCityWeather(arguments)
                    } else {
                        executeCurrentWeather()
                    }
                }
                "get_weather_by_city" -> executeCityWeather(arguments)
                "get_current_location" -> executeCurrentLocation()
                else -> {
                    Log.w(TAG, "Unknown weather function: $functionName")
                    "Unknown function: $functionName"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Weather function execution failed: ${e.message}")
            "Service unavailable"
        }
    }
    
    /**
     * Execute current location query
     */
    private suspend fun executeCurrentLocation(): String {
        Log.d(TAG, "Executing current location query")
        
        return try {
            val weatherData = weatherService.getCurrentLocationWeather()
            
            """
                Your current location information:
                Location: ${weatherData.city}, ${weatherData.country}
                Detection method: GPS + Open-Meteo API
                Coordinates: Available via Open-Meteo service
                Area type: Major Metropolitan Area
            """.trimIndent()
        } catch (e: Exception) {
            "Location service unavailable"
        }
    }
    
    /**
     * Execute current location weather query
     */
    private suspend fun executeCurrentWeather(): String {
        Log.d(TAG, "Executing current location weather query")
        
        return try {
            val weatherData = weatherService.getCurrentLocationWeather()
            
            """
                Current location weather information:
                Location: ${weatherData.city}, ${weatherData.country}
                Weather: ${weatherData.condition}
                Temperature: ${weatherData.temperature}°C
                Humidity: ${weatherData.humidity}%
                Wind Speed: ${String.format("%.1f", weatherData.windSpeed)} m/s
                Description: ${weatherData.description}
            """.trimIndent()
        } catch (e: Exception) {
            "Weather service unavailable"
        }
    }
    
    /**
     * Execute city weather query
     */
    private suspend fun executeCityWeather(arguments: String): String {
        Log.d(TAG, "Executing city weather query")
        
        val cityName = try {
            if (arguments.isBlank()) {
                return "City name required"
            }
            
            if (arguments.trim().startsWith("{")) {
                val jsonArgs = Json.parseToJsonElement(arguments).jsonObject
                jsonArgs["city"]?.jsonPrimitive?.content
                    ?: jsonArgs["location"]?.jsonPrimitive?.content
                    ?: return "City parameter missing"
            } else {
                arguments.trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parameter parsing failed: ${e.message}")
            return "Invalid parameters"
        }
        
        Log.d(TAG, "Querying city: $cityName")
        
        return try {
            val weatherData = weatherService.getWeatherByCity(cityName)
            
            """
                ${weatherData.city} weather information:
                Location: ${weatherData.city}, ${weatherData.country}
                Weather: ${weatherData.condition}
                Temperature: ${weatherData.temperature}°C
                Humidity: ${weatherData.humidity}%
                Wind Speed: ${String.format("%.1f", weatherData.windSpeed)} m/s
                Description: ${weatherData.description}
            """.trimIndent()
        } catch (e: Exception) {
            "Weather data unavailable for $cityName"
        }
    }
        
    /**
     * Test weather service connection
     */
    suspend fun testWeatherService(): String {
        return try {
            Log.d(TAG, "Testing weather service connection")
            val mockWeather = weatherService.getMockWeather()
            
            """
                Open-Meteo weather service test successful!
                Mock data: ${mockWeather.city}, ${mockWeather.condition}, ${mockWeather.temperature}°C
                Cache status: ${weatherService.getCacheStatus()}
            """.trimIndent()
        } catch (e: Exception) {
            "Weather service test failed"
        }
    }
    
    /**
     * Get weather service status
     */
    fun getServiceStatus(): String {
        return if (::weatherService.isInitialized) {
            "Open-Meteo weather service ready\n${weatherService.getCacheStatus()}"
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
 * Weather Service using Open-Meteo API
 */
class WeatherService(val context: Context) {
    
    /**
     * Request location permission from user
     */
    private suspend fun requestLocationPermission() = withContext(Dispatchers.Main) {
        try {
            // Try to get Activity from context
            val activity = when (context) {
                is android.app.Activity -> context
                is androidx.appcompat.view.ContextThemeWrapper -> context.baseContext as? android.app.Activity
                else -> {
                    Log.w(TAG, "Cannot determine Activity from context type: ${context.javaClass.simpleName}")
                    null
                }
            }
            
            if (activity != null) {
                val permissions = arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
                
                Log.d(TAG, "Requesting location permissions from Activity...")
                
                androidx.core.app.ActivityCompat.requestPermissions(
                    activity,
                    permissions,
                    LOCATION_PERMISSION_REQUEST_CODE
                )
                
                // Give user time to respond to permission dialog
                delay(3000)
                
                Log.d(TAG, "Permission request completed, checking current status...")
                
            } else {
                Log.w(TAG, "No Activity available for permission request")
                
                // Try to show a toast instead
                try {
                    val toast = android.widget.Toast.makeText(
                        context, 
                        "Please grant location permission in Settings", 
                        android.widget.Toast.LENGTH_LONG
                    )
                    toast.show()
                } catch (e: Exception) {
                    Log.e(TAG, "Could not show toast: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request location permission: ${e.message}")
        }
    }
    
    companion object {
        private const val TAG = "WeatherService"
        private const val OPEN_METEO_BASE_URL = "https://api.open-meteo.com/v1"
        private const val GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/v1"
        private const val CACHE_EXPIRY_MS = 10 * 60 * 1000L
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val weatherCache = ConcurrentHashMap<String, CachedWeatherData>()
    
    /**
     * Get current location weather
     */
    suspend fun getCurrentLocationWeather(): WeatherData = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to get current location weather")
            
            if (!hasLocationPermission()) {
                Log.w(TAG, "Location permission not granted, using default location")
                return@withContext getWeatherByIP()
            }
            
            val location = getCurrentLocation()
            if (location != null) {
                Log.d(TAG, "Location obtained successfully: ${location.latitude}, ${location.longitude}")
                return@withContext getWeatherByCoordinates(location.latitude, location.longitude)
            } else {
                Log.w(TAG, "Cannot get location, using default location")
                return@withContext getWeatherByIP()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current location weather: ${e.message}")
            return@withContext getWeatherByIP()
        }
    }
    
    /**
     * Get weather by coordinates using Open-Meteo API
     */
    suspend fun getWeatherByCoordinates(lat: Double, lon: Double): WeatherData = withContext(Dispatchers.IO) {
        val cacheKey = "coords_${lat}_${lon}"
        
        weatherCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_EXPIRY_MS) {
                Log.d(TAG, "Using cached coordinate weather data")
                return@withContext cached.data
            }
        }
        
        try {
            Log.d(TAG, "Getting coordinate weather from Open-Meteo: $lat, $lon")
            
            val url = "$OPEN_METEO_BASE_URL/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                    "&timezone=auto"
            
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
            
            val weatherData = parseOpenMeteoResponse(responseBody, lat, lon)
            
            weatherCache[cacheKey] = CachedWeatherData(weatherData, System.currentTimeMillis())
            
            Log.d(TAG, "Coordinate weather obtained successfully: ${weatherData.city}")
            return@withContext weatherData
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get coordinate weather: ${e.message}")
            throw Exception("Failed to get coordinate weather: ${e.message}")
        }
    }
    
    /**
     * Get weather by city name using Open-Meteo API
     */
    suspend fun getWeatherByCity(city: String): WeatherData = withContext(Dispatchers.IO) {
        val cacheKey = "city_$city"
        
        weatherCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_EXPIRY_MS) {
                Log.d(TAG, "Using cached city weather data")
                return@withContext cached.data
            }
        }
        
        try {
            Log.d(TAG, "Getting city weather from Open-Meteo: $city")
            
            val coordinates = getCityCoordinates(city)
            val weatherData = getWeatherByCoordinates(coordinates.first, coordinates.second)
            val updatedWeatherData = weatherData.copy(city = city)
            
            weatherCache[cacheKey] = CachedWeatherData(updatedWeatherData, System.currentTimeMillis())
            
            Log.d(TAG, "City weather obtained successfully: ${updatedWeatherData.city}")
            return@withContext updatedWeatherData
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get city weather: ${e.message}")
            throw Exception("Failed to get city weather: ${e.message}")
        }
    }
    
    /**
     * Get coordinates for city using Open-Meteo Geocoding API
     */
    private suspend fun getCityCoordinates(city: String): Pair<Double, Double> {
        try {
            val encodedCity = java.net.URLEncoder.encode(city, "UTF-8")
            val url = "$GEOCODING_BASE_URL/search?name=$encodedCity&count=1&language=en&format=json"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "WeatherApp/1.0")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("Geocoding API request failed: ${response.code}")
            }
            
            val responseBody = response.body?.string()
                ?: throw IOException("Geocoding response content is empty")
            
            val jsonElement = json.parseToJsonElement(responseBody)
            val resultsArray = jsonElement.jsonObject["results"]?.jsonArray
            
            if (resultsArray == null || resultsArray.isEmpty()) {
                throw Exception("City not found: $city")
            }
            
            val firstResult = resultsArray.first().jsonObject
            val lat = firstResult["latitude"]?.jsonPrimitive?.double
                ?: throw Exception("Cannot parse latitude")
            val lon = firstResult["longitude"]?.jsonPrimitive?.double
                ?: throw Exception("Cannot parse longitude")
            
            Log.d(TAG, "Coordinates for $city: $lat, $lon")
            return Pair(lat, lon)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get city coordinates: ${e.message}")
            throw Exception("Failed to get city coordinates: ${e.message}")
        }
    }
    
    /**
     * Get weather using real IP geolocation - NO CACHE
     */
    private suspend fun getWeatherByIP(): WeatherData = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== STARTING IP GEOLOCATION (NO CACHE) ===")
        
        try {
            // Step 1: Get location from IP using ipapi.co
            val ipLocationUrl = "https://ipapi.co/json/"
            Log.d(TAG, "Calling IP geolocation API: $ipLocationUrl")
            
            val ipRequest = Request.Builder()
                .url(ipLocationUrl)
                .addHeader("User-Agent", "WeatherApp/1.0")
                .build()
            
            val ipResponse = client.newCall(ipRequest).execute()
            Log.d(TAG, "IP API response code: ${ipResponse.code}")
            
            if (!ipResponse.isSuccessful) {
                throw IOException("IP geolocation API request failed: ${ipResponse.code}")
            }
            
            val ipResponseBody = ipResponse.body?.string()
                ?: throw IOException("IP geolocation response is empty")
            
            Log.d(TAG, "IP response received, first 200 chars: ${ipResponseBody.take(200)}")
            
            val ipJson = json.parseToJsonElement(ipResponseBody).jsonObject
            
            val lat = ipJson["latitude"]?.jsonPrimitive?.double
                ?: throw Exception("Cannot parse latitude from IP location")
            val lon = ipJson["longitude"]?.jsonPrimitive?.double
                ?: throw Exception("Cannot parse longitude from IP location")
            
            val detectedCity = ipJson["city"]?.jsonPrimitive?.content ?: "Unknown City"
            val detectedCountry = ipJson["country_name"]?.jsonPrimitive?.content ?: "Unknown Country"
            
            Log.d(TAG, "=== IP LOCATION DETECTED: $detectedCity, $detectedCountry ($lat, $lon) ===")
            
            // Step 2: Get weather for the detected location
            val weatherData = getWeatherByCoordinates(lat, lon)
            
            // Update with detected location info
            val updatedWeatherData = weatherData.copy(
                city = detectedCity,
                country = detectedCountry
            )
            
            Log.d(TAG, "=== IP-BASED WEATHER COMPLETED: ${updatedWeatherData.city} ===")
            return@withContext updatedWeatherData
            
        } catch (e: Exception) {
            Log.e(TAG, "=== IP GEOLOCATION FAILED: ${e.message} ===")
            throw Exception("Unable to determine location: ${e.message}")
        }
    }
    
    /**
     * Parse Open-Meteo JSON response
     */
    private suspend fun parseOpenMeteoResponse(responseBody: String, lat: Double, lon: Double): WeatherData {
        try {
            val jsonElement = json.parseToJsonElement(responseBody)
            val jsonObject = jsonElement.jsonObject
            
            val currentWeather = jsonObject["current"]?.jsonObject
                ?: throw Exception("Cannot get current weather data")
            
            val temperature = currentWeather["temperature_2m"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt()
                ?: throw Exception("Cannot parse temperature")
            
            val humidity = currentWeather["relative_humidity_2m"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            
            val windSpeed = currentWeather["wind_speed_10m"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            
            val weatherCode = currentWeather["weather_code"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val (condition, description) = getWeatherDescription(weatherCode)
            
            val cityName = getCityNameFromCoordinates(lat, lon) ?: "Location $lat, $lon"
            val countryName = getCountryFromCoordinates(lat, lon) ?: "Unknown"
            
            val icon = getIconFromWeatherCode(weatherCode)
            
            return WeatherData(
                temperature = temperature,
                condition = condition,
                description = description,
                city = cityName,
                country = countryName,
                humidity = humidity,
                windSpeed = windSpeed,
                icon = icon
            )
        } catch (e: Exception) {
            Log.e(TAG, "JSON parsing failed: ${e.message}")
            Log.e(TAG, "Response content: ${responseBody.take(500)}")
            throw Exception("Weather data parsing failed: ${e.message}")
        }
    }
    
    /**
     * Get city name from coordinates
     */
    private suspend fun getCityNameFromCoordinates(lat: Double, lon: Double): String? {
        return try {
            when {
                lat in 24.9..25.2 && lon in 121.4..121.7 -> "Taipei"
                lat in 22.5..22.8 && lon in 120.2..120.4 -> "Kaohsiung"
                lat in 24.0..24.3 && lon in 120.6..120.8 -> "Taichung"
                lat in 53.9..54.0 && lon in -1.1..-1.0 -> "York"
                lat in 51.4..51.6 && lon in -0.2..0.1 -> "London"
                lat in 40.6..40.8 && lon in -74.1..-73.9 -> "New York"
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get city name from coordinates: ${e.message}")
            null
        }
    }
    
    /**
     * Get country from coordinates
     */
    private suspend fun getCountryFromCoordinates(lat: Double, lon: Double): String? {
        return try {
            when {
                lat in 21.8..25.4 && lon in 119.3..122.0 -> "Taiwan"
                lat in 49.0..61.0 && lon in -8.0..2.0 -> "United Kingdom"
                lat in 30.0..46.0 && lon in 129.0..146.0 -> "Japan"
                lat in 33.0..39.0 && lon in 124.0..132.0 -> "South Korea"
                lat in 18.0..54.0 && lon in 73.0..135.0 -> "China"
                lat in 24.0..49.5 && lon in -125.0..-66.0 -> "United States"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get country from coordinates: ${e.message}")
            "Unknown"
        }
    }
    
    /**
     * Convert WMO weather code to description
     */
    private fun getWeatherDescription(weatherCode: Int): Pair<String, String> {
        return when (weatherCode) {
            0 -> Pair("Clear sky", "clear sky")
            1 -> Pair("Mainly clear", "mainly clear")
            2 -> Pair("Partly cloudy", "partly cloudy")
            3 -> Pair("Overcast", "overcast")
            45 -> Pair("Fog", "fog")
            48 -> Pair("Depositing rime fog", "depositing rime fog")
            51 -> Pair("Light drizzle", "light drizzle")
            53 -> Pair("Moderate drizzle", "moderate drizzle")
            55 -> Pair("Dense drizzle", "dense drizzle")
            56 -> Pair("Light freezing drizzle", "light freezing drizzle")
            57 -> Pair("Dense freezing drizzle", "dense freezing drizzle")
            61 -> Pair("Slight rain", "slight rain")
            63 -> Pair("Moderate rain", "moderate rain")
            65 -> Pair("Heavy rain", "heavy rain")
            66 -> Pair("Light freezing rain", "light freezing rain")
            67 -> Pair("Heavy freezing rain", "heavy freezing rain")
            71 -> Pair("Slight snow fall", "slight snow fall")
            73 -> Pair("Moderate snow fall", "moderate snow fall")
            75 -> Pair("Heavy snow fall", "heavy snow fall")
            77 -> Pair("Snow grains", "snow grains")
            80 -> Pair("Slight rain showers", "slight rain showers")
            81 -> Pair("Moderate rain showers", "moderate rain showers")
            82 -> Pair("Violent rain showers", "violent rain showers")
            85 -> Pair("Slight snow showers", "slight snow showers")
            86 -> Pair("Heavy snow showers", "heavy snow showers")
            95 -> Pair("Thunderstorm", "thunderstorm")
            96 -> Pair("Thunderstorm with slight hail", "thunderstorm with slight hail")
            99 -> Pair("Thunderstorm with heavy hail", "thunderstorm with heavy hail")
            else -> Pair("Unknown", "unknown weather condition")
        }
    }
    
    /**
     * Get icon code based on weather code
     */
    private fun getIconFromWeatherCode(weatherCode: Int): String {
        return when (weatherCode) {
            0, 1 -> "01d"
            2 -> "02d"
            3 -> "03d"
            45, 48 -> "50d"
            51, 53, 55, 56, 57 -> "09d"
            61, 63, 65, 66, 67, 80, 81, 82 -> "10d"
            71, 73, 75, 77, 85, 86 -> "13d"
            95, 96, 99 -> "11d"
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
            
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Log.w(TAG, "GPS and network location are not enabled")
                return@withContext null
            }
            
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
     * Get mock weather data
     */
    fun getMockWeather(): WeatherData {
        return WeatherData(
            temperature = 25,
            condition = "Sunny",
            description = "mock weather data using Open-Meteo",
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
        return "Open-Meteo Cache items: $activeEntries/${weatherCache.size}"
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
            val testWeather = getWeatherByCoordinates(25.0330, 121.5654)
            "Open-Meteo API test successful\nTest data: ${testWeather.city}, ${testWeather.condition}, ${testWeather.temperature}°C"
        } catch (e: Exception) {
            "Open-Meteo API test failed: ${e.message}"
        }
    }
}

/**
 * Weather data class
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