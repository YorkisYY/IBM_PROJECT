package watsonx

import android.util.Log

/**
 * PromptManager - 專門管理提示詞模板和生成邏輯
 * 單一職責：只負責提示詞生成，不處理上下文管理
 */
class PromptManager {
    
    private val TAG = "PromptManager"
    
    /**
     * 構建函數調用提示詞
     */
    fun buildFunctionCallingPrompt(userMessage: String, contextStr: String): String {
    return """
You are an AI assistant with function calling capabilities. When users request specific functions, you must call the corresponding functions.

Mandatory calling rules:
1. User asks about SMS/messages → immediately call read_unread_messages
2. User asks about weather → immediately call get_current_weather (uses GPS/IP + wttr.in for location)
3. User asks about location/where they are → immediately call get_current_location (uses same location method as weather)
4. User asks about news without specific category → call get_recommended_news
5. User asks about specific news category → call get_news_by_category
6. User asks about podcasts without specific category → call get_recommended_podcasts
7. User asks about specific podcast category → call get_podcasts_by_category
8. Don't say "I cannot" or "I would call", ACTUALLY call functions directly

Available functions:
SMS Functions:
- read_unread_messages() ← SMS-related questions must call this
- read_recent_messages(limit) ← recent messages
- get_message_summary() ← message summary
- get_latest_message() ← latest message

Weather Functions:
- get_current_weather() ← current weather (automatically detects location via GPS/IP + wttr.in API)
- get_weather_by_city(city) ← weather for specific city

Location Functions (uses same location detection as weather):
- get_current_location() ← when user asks "where am I" or "my location" (GPS/IP + wttr.in geocoding)
- get_user_location() ← where user is living/current position (same method as weather location)
- get_location_info() ← detailed location information (includes area type, detection method)

News Functions:
- get_recommended_news() ← when user asks "what news" or "news recommendations"
- get_latest_news(limit) ← latest breaking news
- get_news_by_category(category, limit) ← news by category (health, business, technology, science, sports, general)
- search_news(query, limit) ← search news by keyword
- get_health_news() ← health news
- get_business_news() ← business news
- get_technology_news() ← technology news
- get_science_news() ← science news

Podcast Functions:
- get_recommended_podcasts() ← when user asks "what podcasts" or "podcast recommendations"
- get_podcasts_by_category(category, limit) ← podcasts by category (health_fitness, history, education, news, etc.)
- search_podcasts(query, limit) ← search podcasts by keyword
- get_health_podcasts() ← health podcasts
- get_history_podcasts() ← history podcasts
- get_education_podcasts() ← education podcasts
- get_news_podcasts() ← news podcasts

Function call format: FUNCTION_CALL: {"name": "function_name", "arguments": {}}

Judgment logic:
User says "I want to know news" or "what news" → call get_recommended_news()
User says "I want to know podcasts" or "what podcasts" → call get_recommended_podcasts()
User says "where am I" or "my location" or "where do I live" → call get_current_location()
User says "weather" or "how is weather" → call get_current_weather()
User says "health news" → call get_health_news()
User says "history podcasts" → call get_history_podcasts()

Important: Both weather and location functions use the same location detection method (GPS/IP + wttr.in API) for consistency.

$contextStr

Now processing user request: $userMessage

You MUST call the appropriate function. Do not explain what you would do - just call the function.
""".trimIndent()
}
    
    /**
     * 構建普通對話提示詞
     */
    fun buildNormalPrompt(userMessage: String, contextStr: String): String {
        return """
You are a friendly AR pet assistant, specifically designed to help elderly people. Please respond with a warm, caring tone and keep the conversation natural and smooth.
$contextStr
User: $userMessage
Assistant:
""".trimIndent()
    }
    
    /**
     * 檢查響應是否包含函數調用
     */
    fun containsFunctionCall(response: String): Boolean {
        return response.contains("FUNCTION_CALL:", ignoreCase = true)
    }
    
    /**
     * 🆕 增強版：生成最終用戶友好響應的提示詞 - 保留詳細區域信息
     */
    fun buildFinalResponsePrompt(originalMessage: String, functionResult: String, functionName: String): String {
        val functionType = when {
            functionName.contains("weather") -> "weather"
            functionName.contains("message") || functionName.contains("sms") -> "SMS"
            functionName.contains("location") -> "location"
            functionName.contains("news") -> "news"
            functionName.contains("podcast") -> "podcast"
            else -> "function"
        }
        
        return """
User asked: $originalMessage

The ${functionType} information I obtained is:
$functionResult

Please provide a natural, friendly, and detailed response based on this information.

CRITICAL REQUIREMENTS:
1. 🚨 PRESERVE ALL LOCATION DETAILS: If the data shows specific areas like "Camden Town, London" or "Westminster, London", you MUST mention the FULL area name including the specific district/neighborhood. DO NOT simplify "Camden Town, London" to just "London".

2. 🚨 LOCATION ACCURACY: When mentioning location information:
   - Keep the exact area/district name as provided (e.g., "Camden Town", "Westminster", "Greenwich")
   - Include both the specific area AND the city (e.g., "Camden Town in London")
   - Do not generalize or simplify location details

3. Use a warm and caring tone suitable for elderly people

4. Don't mention technical terms like "function", "API", or "system"

5. Response guidelines by type:
   - Weather: Care for the user like a friend AND include the specific area where they are
   - Location: Tell them their exact area/district, not just the city
   - SMS: Help understand content and provide suggestions
   - News: Summarize key points in easy-to-understand way
   - Podcast: Recommend suitable content and explain why interesting

6. Keep the response natural and smooth

EXAMPLES of correct location responses:
❌ Wrong: "You're in London, United Kingdom"
✅ Correct: "You're in Camden Town, London, United Kingdom"
✅ Correct: "You're currently in the lovely area of Westminster in London"

Answer:
""".trimIndent()
    }
    
    /**
     * 🆕 增強版：生成回退響應 - 保留詳細區域信息
     */
    fun generateFallbackResponse(functionResult: String, functionName: String): String {
        val functionType = when {
            functionName.contains("weather") -> "weather"
            functionName.contains("message") || functionName.contains("sms") -> "SMS"
            functionName.contains("location") -> "location"
            functionName.contains("news") -> "news"
            functionName.contains("podcast") -> "podcast"
            else -> "function"
        }
        
        return when (functionType) {
            "weather" -> "Based on the weather information obtained:\n\n$functionResult\n\nHope this information helps! Remember to adjust your clothing according to the weather. Stay warm and take care!"
            "SMS" -> "Based on your SMS information:\n\n$functionResult\n\nIf you need me to read a specific message for you, please let me know."
            "location" -> "Based on your location information:\n\n$functionResult\n\nYou are safe and I hope this helps you know exactly where you are!"
            "news" -> "Based on the latest news information:\n\n$functionResult\n\nStay informed and take care of yourself!"
            "podcast" -> "Based on the podcast recommendations:\n\n$functionResult\n\nEnjoy listening to these shows! Let me know if you'd like more recommendations."
            else -> "Based on the information obtained:\n\n$functionResult\n\nHope this information helps!"
        }
    }
    
    /**
     * 檢查是否需要函數調用 - 關鍵詞檢測邏輯
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
            "where am i living", "where am i living in", // 🆕 添加 "in"
            "what's my location", "my current position",
            "where i live", "where i live in", // 🆕 添加 "in"
            "my place", "location"
        )
        
        val newsKeywords = listOf(
            "news", "headline", "article", "current events", "breaking news",
            "latest news", "today news", "business news", "health news",
            "technology news", "science news", "sports news"
        )
        
        val podcastKeywords = listOf(
            "podcast", "podcasts", "audio show", "listen to", "episode",
            "podcast recommendation", "health podcast", "history podcast",
            "education podcast", "news podcast", "show recommendation"
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
        
        val hasNewsKeyword = newsKeywords.any { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
        
        val hasPodcastKeyword = podcastKeywords.any { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
        
        Log.d(TAG, "🔍 Keyword detection - Weather: $hasWeatherKeyword, SMS: $hasSMSKeyword, Location: $hasLocationKeyword, News: $hasNewsKeyword, Podcast: $hasPodcastKeyword")
        
        return hasWeatherKeyword || hasSMSKeyword || hasLocationKeyword || hasNewsKeyword || hasPodcastKeyword
    }
}