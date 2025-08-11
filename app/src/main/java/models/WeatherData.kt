// models/WeatherData.kt
package models

import kotlinx.serialization.Serializable

/**
 * Weather data model - Based on your TypeScript WeatherData interface
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
        return "$city, $country: $condition, ${temperature}°C, Humidity ${humidity}%, Wind speed ${String.format("%.1f", windSpeed)}m/s"
    }
}

/**
 * wttr.in API response data structure
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