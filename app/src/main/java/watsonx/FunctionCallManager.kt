// watsonx/FunctionCallManager.kt
package watsonx

import android.util.Log
import functions.WeatherFunctions
import functions.SMSFunctions
import functions.LocationFunctions
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Function Call Manager - manages all function calling logic
 */
object FunctionCallManager {
    
    private const val TAG = "FunctionCallManager"
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Check if function call is needed - supports weather, SMS and location
     */
    fun mightNeedFunctionCall(message: String): Boolean {
        val weatherKeywords = listOf(
            "weather", "temperature", "rain", "sunny", "cloudy",
            "wind", "humidity", "forecast", "degree", "cold", "hot", "warm"
        )
        
        val smsKeywords = listOf(
            "sms", "message", "msg", "unread", "new message", "recent", "summary",
            "read message", "message content", "who sent", "received"
        )
        
        val locationKeywords = listOf(
            "where am i", "my location", "where do i live", "my address", 
            "current location", "where i am", "my town", "my city",
            "where am i living", "what's my location", "my current position",
            "where i live", "my place", "location"
        )
        
        val hasWeatherKeyword = weatherKeywords.any { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
        
        val hasSMSKeyword = smsKeywords.any { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
        
        val hasLocationKeyword = locationKeywords.any { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
        
        Log.d(TAG, "Keyword detection - Weather: $hasWeatherKeyword, SMS: $hasSMSKeyword, Location: $hasLocationKeyword")
        
        return hasWeatherKeyword || hasSMSKeyword || hasLocationKeyword
    }
    
    /**
     * Check if response contains function call
     */
    fun containsFunctionCall(response: String): Boolean {
        return response.contains("FUNCTION_CALL:", ignoreCase = true)
    }
    
    /**
     * Extract function call information
     */
    fun extractFunctionCall(response: String): FunctionCall? {
        return try {
            // Find JSON after FUNCTION_CALL:
            val startIndex = response.indexOf("FUNCTION_CALL:")
            if (startIndex == -1) return null
            
            val jsonStart = response.indexOf("{", startIndex)
            if (jsonStart == -1) return null
            
            // Find complete JSON object end
            var braceCount = 0
            var jsonEnd = jsonStart
            
            for (i in jsonStart until response.length) {
                when (response[i]) {
                    '{' -> braceCount++
                    '}' -> {
                        braceCount--
                        if (braceCount == 0) {
                            jsonEnd = i
                            break
                        }
                    }
                }
            }
            
            if (braceCount != 0) return null // JSON incomplete
            
            val jsonStr = response.substring(jsonStart, jsonEnd + 1)
            Log.d(TAG, "Extracted JSON: $jsonStr")
            
            val jsonElement = json.parseToJsonElement(jsonStr)
            val jsonObject = jsonElement.jsonObject
            
            val name = jsonObject["name"]?.jsonPrimitive?.content ?: return null
            val arguments = jsonObject["arguments"]?.toString() ?: "{}"
            
            FunctionCall(name, arguments)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse function call: ${e.message}")
            null
        }
    }
    
    /**
     * Execute function and get result - supports weather, SMS and location
     */
    suspend fun executeFunction(functionCall: FunctionCall): String {
        return try {
            Log.d(TAG, "Executing function: ${functionCall.name}")
            Log.d(TAG, "Function parameters: ${functionCall.arguments}")
            
            // Call corresponding service based on function type
            val functionResult = when {
                functionCall.name.startsWith("get_") && functionCall.name.contains("weather") -> {
                    WeatherFunctions.execute(functionCall.name, functionCall.arguments)
                }
                functionCall.name in listOf(
                    "read_unread_messages", "read_recent_messages", "get_message_summary",
                    "get_message_by_index", "get_latest_message"
                ) -> {
                    SMSFunctions.execute(functionCall.name, functionCall.arguments)
                }
                functionCall.name in listOf(
                    "get_current_location", "get_user_location", "get_location_info"
                ) -> {
                    LocationFunctions.execute(functionCall.name, functionCall.arguments)
                }
                else -> {
                    Log.w(TAG, "Unknown function type: ${functionCall.name}")
                    "Sorry, unrecognized function request."
                }
            }
            
            Log.d(TAG, "Function execution completed")
            functionResult
            
        } catch (e: Exception) {
            Log.e(TAG, "Function execution failed: ${e.message}")
            "Sorry, there was a problem processing your request, please try again later."
        }
    }
    
    /**
     * Get function type
     */
    fun getFunctionType(functionName: String): String {
        return when {
            functionName.contains("weather") -> "weather"
            functionName.contains("message") || functionName.contains("sms") -> "SMS"
            functionName.contains("location") -> "location"
            else -> "function"
        }
    }
    
    /**
     * Generate fallback response
     */
    fun generateFallbackResponse(functionType: String, functionResult: String): String {
        return when (functionType) {
            "weather" -> "Based on the weather information obtained:\n\n$functionResult\n\nHope this information helps! Remember to adjust your clothing according to the weather."
            "SMS" -> "Based on your SMS information:\n\n$functionResult\n\nIf you need me to read a specific message for you, please let me know."
            "location" -> "Based on your location information:\n\n$functionResult\n\nYou are safe and I hope this helps you know where you are!"
            else -> "Based on the information obtained:\n\n$functionResult\n\nHope this information helps!"
        }
    }
    
    /**
     * Function call data class
     */
    @Serializable
    data class FunctionCall(
        val name: String,
        val arguments: String
    )
}