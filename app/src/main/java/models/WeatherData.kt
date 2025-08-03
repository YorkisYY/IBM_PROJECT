// models/WeatherData.kt
package models

import kotlinx.serialization.Serializable

/**
 * 天氣數據模型 - 基於您的 TypeScript WeatherData interface
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
) {
    fun toJson(): String {
        return """
        {
            "temperature": $temperature,
            "condition": "$condition",
            "description": "$description", 
            "city": "$city",
            "country": "$country",
            "humidity": $humidity,
            "windSpeed": $windSpeed,
            "icon": "$icon"
        }
        """.trimIndent()
    }
    
    fun toReadableString(): String {
        return "$city, $country: $condition, ${temperature}°C, 濕度 ${humidity}%, 風速 ${String.format("%.1f", windSpeed)}m/s"
    }
}

/**
 * wttr.in API 回應數據結構
 */
@Serializable
data class WttrResponse(
    val current_condition: List<CurrentCondition>,
    val nearest_area: List<NearestArea>
)

@Serializable
data class CurrentCondition(
    val temp_C: String,
    val weatherDesc: List<WeatherDescription>,
    val humidity: String,
    val windspeedKmph: String
)

@Serializable
data class WeatherDescription(
    val value: String
)

@Serializable
data class NearestArea(
    val areaName: List<AreaName>,
    val country: List<Country>
)

@Serializable
data class AreaName(
    val value: String
)

@Serializable
data class Country(
    val value: String
)