package watsonx

import android.util.Log

/**
 * PromptManager - Specialized for managing prompt templates and generation logic
 * Single responsibility: Only responsible for prompt generation, not context management
 */
class PromptManager {
    
    private val TAG = "PromptManager"
    
    // Track recently called functions to avoid repetitive suggestions
    private var lastFunctionCalled: String? = null
    private var lastFunctionTime: Long = 0L
    private val FUNCTION_COOLDOWN = 30000L // 30 second cooldown
    
    /**
     * Precise detection of whether a message might need function calling
     */
    fun mightNeedFunctionCall(message: String): Boolean {
        val lowerMessage = message.lowercase()
        
        // Check each function category with strict criteria
        val needsWeather = checkWeatherIntent(lowerMessage)
        val needsSMS = checkSMSIntent(lowerMessage)
        val needsLocation = checkLocationIntent(lowerMessage)
        val needsPodcast = checkPodcastIntent(lowerMessage)
        val needsNews = checkNewsIntent(lowerMessage)
        
        Log.d(TAG, "Precise detection - W:$needsWeather S:$needsSMS L:$needsLocation P:$needsPodcast N:$needsNews")
        
        return needsWeather || needsSMS || needsLocation || needsPodcast || needsNews
    }
    
    /**
     * Check if message explicitly asks about weather
     */
    private fun checkWeatherIntent(message: String): Boolean {
        // Must explicitly mention weather-related terms
        val weatherPhrases = listOf(
            "weather", "temperature", "rain", "sunny", 
            "forecast", "how hot", "how cold", "cloudy",
            "humid", "wind"
        )
        val hasWeatherWord = weatherPhrases.any { message.contains(it) }
        
        // Exclude negative statements
        val isNegative = message.contains("don't") || message.contains("not")
        
        return hasWeatherWord && !isNegative
    }
    
    /**
     * Check if message explicitly asks to read messages
     */
    private fun checkSMSIntent(message: String): Boolean {
        // Must explicitly request message reading
        val smsPhrases = listOf(
            "read message", "read my message", "full message",
            "what's the message", "unread message", "new message",
            "latest message", "show message", "check message"
        )
        return smsPhrases.any { message.contains(it) }
    }
    
    /**
     * Check if message asks about current location
     */
    private fun checkLocationIntent(message: String): Boolean {
        val locationPhrases = listOf(
            "where am i", "my location", "current location",
            "where do i live", "my address", "my position"
        )
        return locationPhrases.any { message.contains(it) }
    }
    
    /**
     * Check if message asks about podcasts
     */
    private fun checkPodcastIntent(message: String): Boolean {
        return message.contains("podcast") || 
               message.contains("listen to something")
    }
    
    /**
     * Check if message asks about news
     */
    private fun checkNewsIntent(message: String): Boolean {
        return message.contains("news") || 
               message.contains("headline") || 
               message.contains("current events")
    }
    
    /**
     * Build function calling prompt with proper function definitions
     * CRITICAL: Clear distinction between weather and location functions
     */
    fun buildFunctionCallingPrompt(userMessage: String, contextStr: String): String {
        // Identify which function is needed based on user message
        val requiredFunction = identifyRequiredFunction(userMessage)
        
        return """
$contextStr

=== CRITICAL FUNCTION DISTINCTION ===
⚠️ WEATHER vs LOCATION - DO NOT CONFUSE THESE:
- Any question about weather/temperature/rain → Use weather functions
- Questions about "where am I" → Use get_current_location
- NEVER use get_current_location for weather questions
- NEVER use weather functions for location questions

=== FUNCTION CALLING MODE ===
Based on the user's question, you need to call: $requiredFunction

${getTargetedFunctionDescription(requiredFunction)}

=== DECISION GUIDE ===
User asks about weather? → Use appropriate weather function
User asks where they are? → get_current_location
User asks about messages? → appropriate message function
User asks about news? → appropriate news function
User asks about podcasts? → appropriate podcast function

Function call format: FUNCTION_CALL: {"name": "function_name", "arguments": {}}

ANALYZE THE USER'S QUESTION CAREFULLY AND CALL THE CORRECT FUNCTION.
""".trimIndent()
    }
    
    /**
     * Identify which function category is needed
     */
    private fun identifyRequiredFunction(message: String): String {
        val lower = message.lowercase()
        
        return when {
            checkWeatherIntent(lower) -> "WEATHER FUNCTIONS"
            checkSMSIntent(lower) -> "MESSAGE FUNCTION"
            checkLocationIntent(lower) -> "LOCATION FUNCTION (get_current_location)"
            checkPodcastIntent(lower) -> "PODCAST FUNCTION"
            checkNewsIntent(lower) -> "NEWS FUNCTION"
            else -> "NO FUNCTION (answer directly)"
        }
    }
    
    /**
     * Provide detailed function descriptions with exact formats
     * CRITICAL: This ensures AI uses correct function names and parameters
     */
    private fun getTargetedFunctionDescription(requiredFunction: String): String {
        return when {
            requiredFunction.contains("WEATHER") -> """
=== TWO SEPARATE WEATHER FUNCTIONS ===
⚠️ IMPORTANT: We have TWO different weather functions. Choose the right one!

1. CURRENT LOCATION WEATHER:
   ✅ Function: get_current_weather (NO parameters, empty arguments)
   Format: FUNCTION_CALL: {"name": "get_current_weather", "arguments": {}}
   
   Use when user asks:
   - "weather of my current location" ← USE THIS ONE!
   - "How is the weather" (no location specified)
   - "What's the weather like" (no location specified)
   - "Weather here"
   - "Current weather"
   - "Weather at my location"
   - "Weather outside"

2. SPECIFIC CITY WEATHER:
   ✅ Function: get_weather_by_city (REQUIRES city parameter)
   Format: FUNCTION_CALL: {"name": "get_weather_by_city", "arguments": {"city": "CityName"}}
   
   Use when user asks:
   - "Weather in [City]"
   - "[City] weather"
   - "How is the weather in [City]"
   - "What's the temperature in [City]"
   - Any weather question that mentions a specific city name

CRITICAL EXAMPLES:
✅ CORRECT:
- "weather of my current location" → {"name": "get_current_weather", "arguments": {}}
- "How is the weather" → {"name": "get_current_weather", "arguments": {}}
- "Weather in York" → {"name": "get_weather_by_city", "arguments": {"city": "York"}}
- "What's the weather" → {"name": "get_current_weather", "arguments": {}}
- "London weather" → {"name": "get_weather_by_city", "arguments": {"city": "London"}}
- "Weather in Taipei" → {"name": "get_weather_by_city", "arguments": {"city": "Taipei"}}
- "Current weather" → {"name": "get_current_weather", "arguments": {}}

❌ WRONG - NEVER DO THIS:
- "weather of my current location" → {"name": "get_current_location", "arguments": {}} ← ABSOLUTELY WRONG!
- "Weather in York" → {"name": "get_current_weather", "arguments": {"city": "York"}} ← NO! Use get_weather_by_city
- "How is the weather" → {"name": "get_weather_by_city", "arguments": {}} ← NO! Use get_current_weather
- Using get_current_location for ANY weather question ← NEVER do this!

RULE: If "weather" appears in the question, NEVER use get_current_location!
RULE: If a city is mentioned, ALWAYS use get_weather_by_city. Otherwise use get_current_weather.
            """.trimIndent()
            
            requiredFunction.contains("MESSAGE") -> """
=== MESSAGE FUNCTIONS ===

1. Get latest message:
   FUNCTION_CALL: {"name": "get_latest_message", "arguments": {}}
   
2. Read unread messages:
   FUNCTION_CALL: {"name": "read_unread_messages", "arguments": {}}
   
3. Get message summary:
   FUNCTION_CALL: {"name": "get_message_summary", "arguments": {}}

Examples:
- "Read my latest message" → {"name": "get_latest_message", "arguments": {}}
- "Any unread messages?" → {"name": "read_unread_messages", "arguments": {}}
            """.trimIndent()
            
            requiredFunction.contains("LOCATION") -> """
=== LOCATION FUNCTION ONLY ===
✅ CORRECT FUNCTION: get_current_location
❌ WRONG: get_current_weather, get_weather_by_city

Usage (ALWAYS empty arguments):
FUNCTION_CALL: {"name": "get_current_location", "arguments": {}}

Examples of LOCATION questions:
- "Where am I" → {"name": "get_current_location", "arguments": {}}
- "What's my location" → {"name": "get_current_location", "arguments": {}}
- "My current position" → {"name": "get_current_location", "arguments": {}}

⚠️ This function NEVER takes parameters!
⚠️ NEVER use weather functions for location questions!
            """.trimIndent()
            
            requiredFunction.contains("PODCAST") -> """
=== PODCAST FUNCTIONS ===

1. Get recommendations (no parameters):
   FUNCTION_CALL: {"name": "get_recommended_podcasts", "arguments": {}}
   
2. Get by category (with category parameter):
   FUNCTION_CALL: {"name": "get_podcasts_by_category", "arguments": {"category": "news"}}

Examples:
- "Recommend some podcasts" → {"name": "get_recommended_podcasts", "arguments": {}}
- "News podcasts" → {"name": "get_podcasts_by_category", "arguments": {"category": "news"}}
            """.trimIndent()
            
            requiredFunction.contains("NEWS") -> """
=== NEWS FUNCTIONS ===

1. Get recommendations (no parameters):
   FUNCTION_CALL: {"name": "get_recommended_news", "arguments": {}}
   
2. Get by category (with category parameter):
   FUNCTION_CALL: {"name": "get_news_by_category", "arguments": {"category": "technology"}}

Examples:
- "What's in the news" → {"name": "get_recommended_news", "arguments": {}}
- "Tech news" → {"name": "get_news_by_category", "arguments": {"category": "technology"}}
            """.trimIndent()
            
            else -> "No function needed. Answer the question directly without calling any functions."
        }
    }
    
    /**
     * Build normal conversation prompt for non-function responses
     */
    fun buildNormalPrompt(userMessage: String, contextStr: String): String {
        return """
$contextStr

You are a friendly AI assistant for elderly users.
Respond warmly and naturally to the current user question.
Do not mention functions or technical details.
Keep your response concise and easy to understand.
""".trimIndent()
    }
    
    /**
     * Generate final response prompt after function execution
     */
    fun buildFinalResponsePrompt(originalMessage: String, functionResult: String, functionName: String): String {
        val functionType = extractFunctionType(functionName)
        
        // Check if this function was recently called
        val shouldBeMinimal = checkIfRecentlyCalled(functionName)
        
        return """
User asked: $originalMessage
Function called: $functionName
Result: $functionResult

Generate a natural response following these rules:
1. Answer the user's question using the function result
2. Keep response warm and friendly for elderly users
3. Preserve all location details (e.g., "Camden Town, London" not just "London")
4. Be concise - avoid unnecessary information
${if (shouldBeMinimal) "5. Do NOT suggest other services - user just asked about this" else ""}
${getResponseGuideline(functionType, originalMessage)}

Response:
""".trimIndent()
    }
    
    /**
     * Extract function type from function name
     */
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
    
    /**
     * Get specific response guidelines based on function type
     */
    private fun getResponseGuideline(type: String, originalMessage: String): String {
        val lower = originalMessage.lowercase()
        
        // Check if user wants minimal response
        if (lower.contains("just") || lower.contains("only")) {
            return "\nIMPORTANT: User wants ONLY this information. Do NOT suggest other services."
        }
        
        return when (type) {
            "message" -> "\nFocus on the message content. Only suggest weather if it seems relevant."
            "weather" -> "\nFocus on weather information. Keep it concise and clear."
            "location" -> "\nBe specific about the location. Include district/area names if available."
            "podcast" -> "\nPresent podcast recommendations clearly."
            "news" -> "\nSummarize news briefly and clearly."
            else -> ""
        }
    }
    
    /**
     * Check if a similar function was recently called
     */
    private fun checkIfRecentlyCalled(functionName: String): Boolean {
        if (lastFunctionCalled == null) return false
        
        val timeSince = System.currentTimeMillis() - lastFunctionTime
        val sameType = extractFunctionType(functionName) == extractFunctionType(lastFunctionCalled!!)
        
        return sameType && timeSince < FUNCTION_COOLDOWN
    }
    
    /**
     * Record function call for cooldown tracking
     */
    fun recordFunctionCall(functionName: String) {
        lastFunctionCalled = functionName
        lastFunctionTime = System.currentTimeMillis()
        Log.d(TAG, "Recorded function call: $functionName")
    }
    
    /**
     * Check if response contains a function call
     */
    fun containsFunctionCall(response: String): Boolean {
        return response.contains("FUNCTION_CALL:", ignoreCase = true)
    }
    
    /**
     * Generate fallback response when AI fails to generate proper response
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