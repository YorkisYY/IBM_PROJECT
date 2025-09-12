package watsonx

import android.util.Log

/**
 * PromptManager - Specialized for managing prompt templates and generation logic
 * Single responsibility: Only responsible for prompt generation, not context management
 */
class PromptManager {
    
    private val TAG = "PromptManager"
    
    // 追蹤最近呼叫的 function
    private var lastFunctionCalled: String? = null
    private var lastFunctionTime: Long = 0L
    private val FUNCTION_COOLDOWN = 30000L // 30秒冷卻
    
    /**
     * 更精確的 function call 檢測
     */
    fun mightNeedFunctionCall(message: String): Boolean {
        val lowerMessage = message.lowercase()
        
        // 更嚴格的檢測邏輯
        val needsWeather = checkWeatherIntent(lowerMessage)
        val needsSMS = checkSMSIntent(lowerMessage)
        val needsLocation = checkLocationIntent(lowerMessage)
        val needsPodcast = checkPodcastIntent(lowerMessage)
        val needsNews = checkNewsIntent(lowerMessage)
        
        Log.d(TAG, "Precise detection - W:$needsWeather S:$needsSMS L:$needsLocation P:$needsPodcast N:$needsNews")
        
        return needsWeather || needsSMS || needsLocation || needsPodcast || needsNews
    }
    
    private fun checkWeatherIntent(message: String): Boolean {
        // 必須明確詢問天氣
        val weatherPhrases = listOf(
            "weather", "temperature", "rain", "sunny", 
            "forecast", "how hot", "how cold"
        )
        val hasWeatherWord = weatherPhrases.any { message.contains(it) }
        
        // 排除否定句
        val isNegative = message.contains("don't") || message.contains("not")
        
        return hasWeatherWord && !isNegative
    }
    
    private fun checkSMSIntent(message: String): Boolean {
        // 必須明確要求讀取訊息
        val smsPhrases = listOf(
            "read message", "read my message", "full message",
            "what's the message", "unread message", "new message",
            "latest message", "show message", "check message"
        )
        return smsPhrases.any { message.contains(it) }
    }
    
    private fun checkLocationIntent(message: String): Boolean {
        val locationPhrases = listOf(
            "where am i", "my location", "current location",
            "where do i live", "my address", "my position"
        )
        return locationPhrases.any { message.contains(it) }
    }
    
    private fun checkPodcastIntent(message: String): Boolean {
        return message.contains("podcast") || 
               message.contains("listen to something")
    }
    
    private fun checkNewsIntent(message: String): Boolean {
        return message.contains("news") || 
               message.contains("headline") || 
               message.contains("current events")
    }
    
    /**
     * Build function calling prompt - 配合 ChatRepository 的上下文
     */
    fun buildFunctionCallingPrompt(userMessage: String, contextStr: String): String {
        // 分析用戶真正需要什麼
        val requiredFunction = identifyRequiredFunction(userMessage)
        
        return """
$contextStr

=== FUNCTION CALLING MODE ===
CRITICAL: Based on the CURRENT USER QUESTION above, you need to call: $requiredFunction

STRICT RULES:
1. ONLY call the function that directly answers the current question
2. DO NOT call multiple functions unless explicitly asked
3. DO NOT call functions not related to the current question
4. If the question doesn't need a function, answer directly

${getTargetedFunctionDescription(requiredFunction)}

Function call format: FUNCTION_CALL: {"name": "function_name", "arguments": {}}

IMPORTANT: Only call $requiredFunction or answer directly if no function is needed.
""".trimIndent()
    }
    
    /**
     * 識別需要的 function
     */
    private fun identifyRequiredFunction(message: String): String {
        val lower = message.lowercase()
        
        return when {
            checkWeatherIntent(lower) -> "weather function"
            checkSMSIntent(lower) -> "message function"
            checkLocationIntent(lower) -> "location function"
            checkPodcastIntent(lower) -> "podcast function"
            checkNewsIntent(lower) -> "news function"
            else -> "NO FUNCTION (answer directly)"
        }
    }
    
    /**
     * 只提供相關的 function 描述
     */
    private fun getTargetedFunctionDescription(requiredFunction: String): String {
        return when (requiredFunction) {
            "weather function" -> """
Available Weather Functions:
- get_current_weather() ← current location weather
- get_current_weather(city) ← specific city weather

Examples:
"weather" → {"name": "get_current_weather", "arguments": {}}
"weather in Paris" → {"name": "get_current_weather", "arguments": {"city": "Paris"}}
            """.trimIndent()
            
            "message function" -> """
Available SMS Functions:
- get_latest_message() ← latest message
- read_unread_messages() ← unread messages
- get_message_summary() ← message summary
            """.trimIndent()
            
            "location function" -> """
Available Location Functions:
- get_current_location() ← current location
            """.trimIndent()
            
            "podcast function" -> """
Available Podcast Functions:
- get_recommended_podcasts() ← podcast recommendations
- get_podcasts_by_category(category) ← specific category
            """.trimIndent()
            
            "news function" -> """
Available News Functions:
- get_recommended_news() ← news recommendations
- get_news_by_category(category) ← specific category
            """.trimIndent()
            
            else -> "No function needed. Answer the question directly."
        }
    }
    
    /**
     * Build normal conversation prompt -
     */
    fun buildNormalPrompt(userMessage: String, contextStr: String): String {
        return """
$contextStr

You are a friendly AR pet assistant for elderly people.
Respond warmly and naturally to the current user question.
Do not mention functions or technical details.
""".trimIndent()
    }
    
    /**
     * Generate final response prompt
     */
    fun buildFinalResponsePrompt(originalMessage: String, functionResult: String, functionName: String): String {
        val functionType = extractFunctionType(functionName)
        
        
        val shouldBeMinimal = checkIfRecentlyCalled(functionName)
        
        return """
User asked: $originalMessage
Function called: $functionName
Result: $functionResult

Generate a natural response following these rules:
1. Answer the user's question using the function result
2. Keep response warm and friendly for elderly users
3. Preserve all location details (e.g., "Camden Town, London" not just "London")
${if (shouldBeMinimal) "4. Do NOT suggest other services - user just asked about this" else ""}
${getResponseGuideline(functionType, originalMessage)}

Response:
""".trimIndent()
    }
    
    private fun extractFunctionType(functionName: String): String {
        return when {
            functionName.contains("weather") -> "weather"
            functionName.contains("message") || functionName.contains("sms") -> "message"
            functionName.contains("location") -> "location"
            functionName.contains("podcast") -> "podcast"
            functionName.contains("news") -> "news"
            else -> "general"
        }
    }
    
    private fun getResponseGuideline(type: String, originalMessage: String): String {
        val lower = originalMessage.lowercase()
        
        
        if (lower.contains("just") || lower.contains("only")) {
            return "\nIMPORTANT: User wants ONLY this information. Do NOT suggest other services."
        }
        
        return when (type) {
            "message" -> "\nFocus on the message content. Only suggest weather if it seems relevant."
            "weather" -> "\nFocus on weather information. Keep it concise."
            "location" -> "\nBe specific about the location. Include district/area names."
            else -> ""
        }
    }
    
    private fun checkIfRecentlyCalled(functionName: String): Boolean {
        if (lastFunctionCalled == null) return false
        
        val timeSince = System.currentTimeMillis() - lastFunctionTime
        val sameType = extractFunctionType(functionName) == extractFunctionType(lastFunctionCalled!!)
        
        return sameType && timeSince < FUNCTION_COOLDOWN
    }
    
    /**
     * 記錄 function 呼叫
     */
    fun recordFunctionCall(functionName: String) {
        lastFunctionCalled = functionName
        lastFunctionTime = System.currentTimeMillis()
        Log.d(TAG, "Recorded function call: $functionName")
    }
    
    /**
     * Check if response contains function call
     */
    fun containsFunctionCall(response: String): Boolean {
        return response.contains("FUNCTION_CALL:", ignoreCase = true)
    }
    
    /**
     * Generate fallback response 
     */
    fun generateFallbackResponse(functionResult: String, functionName: String): String {
        val functionType = extractFunctionType(functionName)
        
        return when (functionType) {
            "weather" -> "Here's the weather information:\n$functionResult\n\nTake care!"
            "message" -> "Your message:\n$functionResult"
            "location" -> "Your location:\n$functionResult"
            "news" -> "Latest news:\n$functionResult"
            "podcast" -> "Podcast recommendations:\n$functionResult"
            else -> "$functionResult"
        }
    }
}