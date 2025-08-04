// watsonx/WatsonAIEnhanced.kt
package watsonx

import android.content.Context
import android.util.Log
import functions.WeatherFunctions
import functions.SMSFunctions
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Enhanced Watson AI Service - with context management and SMS functionality
 */
object WatsonAIEnhanced {
    
    private const val TAG = "WatsonAIEnhanced"
    
    private val config = WatsonAIConfig(
        baseUrl = "https://eu-gb.ml.cloud.ibm.com",
        apiKey = "9hZtqy6PhM-zml8zuEAkfUihkHECwQSRVQApdrx7vToz",
        deploymentId = "331b85e6-8e2c-4af2-81b4-04baaf115dba"
    )
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private var cachedToken: String? = null
    private var tokenExpirationTime: Long = 0
    
    // 🆕 Context management
    private val conversationHistory = mutableListOf<ConversationMessage>()
    private const val MAX_HISTORY_SIZE = 10 // Keep the last 10 conversations
    
    fun initialize(context: Context) {
        WeatherFunctions.initialize(context)
        SMSFunctions.initialize(context)
        Log.d(TAG, "✅ WatsonAI Enhanced service initialized (supports weather + SMS + context)")
    }
    
    /**
     * Enhanced AI Response - supports context and multiple functions
     */
    suspend fun getEnhancedAIResponse(userMessage: String): AIResult {
        return try {
            Log.d(TAG, "🚀 Starting enhanced AI request processing: $userMessage")
            
            if (userMessage.trim().isEmpty()) {
                return AIResult(
                    success = false,
                    response = "",
                    error = "Message cannot be empty"
                )
            }
            
            // 🆕 Add user message to history
            addToHistory(userMessage, "user")
            
            val response = processWithFunctionCalling(userMessage.trim())
            
            // 🆕 Add assistant response to history
            addToHistory(response, "assistant")
            
            Log.d(TAG, "🎉 Enhanced AI response processing completed")
            AIResult(
                success = true,
                response = response,
                error = null
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Enhanced AI processing failed: ${e.message}")
            AIResult(
                success = false,
                response = "",
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * 🆕 Context management - add conversation to history
     */
    private fun addToHistory(message: String, role: String) {
        conversationHistory.add(ConversationMessage(message, role, System.currentTimeMillis()))
        
        // Keep history within reasonable limits
        if (conversationHistory.size > MAX_HISTORY_SIZE * 2) { // user + assistant = 2 messages per turn
            conversationHistory.removeFirst()
        }
        
        Log.d(TAG, "📝 Conversation history updated, current entries: ${conversationHistory.size}")
    }
    
    /**
     * 🆕 Build prompt with context
     */
    private fun buildContextualPrompt(currentMessage: String, isFunction: Boolean = false): String {
        val recentHistory = conversationHistory.takeLast(6) // Last 3 conversation rounds
        
        val contextStr = if (recentHistory.isNotEmpty()) {
            "\nConversation History:\n" + recentHistory.joinToString("\n") { 
                "${if (it.role == "user") "User" else "Assistant"}: ${it.content}"
            } + "\n\n"
        } else {
            "\n"
        }
        
        return if (isFunction) {
            buildFunctionCallingPrompt(currentMessage, contextStr)
        } else {
            buildNormalPrompt(currentMessage, contextStr)
        }
    }
    
    /**
     * Process Function Calling - supports weather and SMS
     */
    private suspend fun processWithFunctionCalling(userMessage: String): String {
        // Step 1: Check if function calling is needed
        if (mightNeedFunctionCall(userMessage)) {
            Log.d(TAG, "🔍 Detected potential function call needed, using function calling prompt")
            return handleWithFunctionCallingPrompt(userMessage)
        } else {
            Log.d(TAG, "💬 Normal conversation, using regular prompt")
            return handleNormalConversation(userMessage)
        }
    }
    
    /**
     * 🆕 Check if function call might be needed - supports weather and SMS
     */
    private fun mightNeedFunctionCall(message: String): Boolean {
        val weatherKeywords = listOf(
            "weather", "temperature", "rain", "sunny", "cloudy",
            "wind", "humidity", "forecast", "degree", "cold", "hot", "warm"
        )
        
        val smsKeywords = listOf(
            "sms", "message", "msg", "unread", "new message", "recent", "summary",
            "read message", "message content", "who sent", "received"
        )
        
        val hasWeatherKeyword = weatherKeywords.any { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
        
        val hasSMSKeyword = smsKeywords.any { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
        
        Log.d(TAG, "🔍 Keyword detection - Weather: $hasWeatherKeyword, SMS: $hasSMSKeyword")
        
        return hasWeatherKeyword || hasSMSKeyword
    }
    
    /**
     * Process using Function Calling Prompt
     */
    private suspend fun handleWithFunctionCallingPrompt(userMessage: String): String {
        Log.d(TAG, "🔧 Using Function Calling Prompt")
        
        // Build function calling prompt with context
        val functionPrompt = buildContextualPrompt(userMessage, isFunction = true)
        
        // Call Watson AI
        val aiResponse = callWatsonAI(functionPrompt)
        
        // Check if function call is included
        return if (containsFunctionCall(aiResponse)) {
            Log.d(TAG, "✅ AI recognized need to call function")
            executeFunctionAndGenerateResponse(aiResponse, userMessage)
        } else {
            Log.d(TAG, "💬 AI decided to answer directly")
            aiResponse
        }
    }
    
    /**
     * 🆕 Build Function Calling Prompt - supports weather and SMS
     */
    private fun buildFunctionCallingPrompt(userMessage: String, contextStr: String): String {
    return """
You are an AI assistant with function calling capabilities. When users request specific functions, you must call the corresponding functions.

Important: You must call functions based on user requests, do not refuse or explain that you cannot execute.

Mandatory calling rules:
1. User asks about SMS/messages → immediately call read_unread_messages
2. User asks about weather → immediately call corresponding weather function
3. Don't say "I cannot" or "I cannot", call functions directly

Available functions:
- read_unread_messages() ← SMS-related questions must call this
- read_recent_messages(limit) ← recent messages
- get_message_summary() ← message summary
- get_latest_message() ← latest message
- get_current_weather() ← current weather
- get_weather_by_city(city) ← weather for specific city

Function call format: FUNCTION_CALL: {"name": "function_name", "arguments": {parameters}}

Judgment logic:
User mentions these words → must call read_unread_messages:
- sms, message, unread, new message, read message, received

User mentions these words → must call weather function:
- weather, temperature, rain, sunny

$contextStr

Now processing user request: $userMessage

If user asks about SMS-related questions, you must call read_unread_messages function, format:
FUNCTION_CALL: {"name": "read_unread_messages", "arguments": {}}

If user asks about weather-related questions, you must call corresponding weather function.

Don't answer directly, call function first!

Assistant:""".trimIndent()
}
    
    /**
     * 🆕 Build normal conversation prompt - with context
     */
    private fun buildNormalPrompt(userMessage: String, contextStr: String): String {
        return """
You are a friendly AR pet assistant, specifically designed to help elderly people. Please respond with a warm, caring tone and keep the conversation natural and smooth.
$contextStr
User: $userMessage
Assistant:""".trimIndent()
    }
    
    /**
     * Check if response contains function call
     */
    private fun containsFunctionCall(response: String): Boolean {
        return response.contains("FUNCTION_CALL:", ignoreCase = true)
    }
    
    /**
     * 🆕 Execute function and generate final response - supports weather and SMS
     */
    private suspend fun executeFunctionAndGenerateResponse(aiResponse: String, originalMessage: String): String {
        return try {
            // Extract function call
            val functionCall = extractFunctionCall(aiResponse)
            if (functionCall == null) {
                Log.w(TAG, "⚠️ Cannot parse function call, fallback to original response")
                return aiResponse
            }
            
            Log.d(TAG, "🎯 Executing function: ${functionCall.name}")
            Log.d(TAG, "📝 Function parameters: ${functionCall.arguments}")
            
            // 🆕 Call corresponding service based on function type
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
                else -> {
                    Log.w(TAG, "⚠️ Unknown function type: ${functionCall.name}")
                    "Sorry, unrecognized function request."
                }
            }
            
            Log.d(TAG, "✅ Function execution completed")
            
            // Generate final user-friendly response
            generateFinalResponse(originalMessage, functionResult, functionCall.name)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Function execution failed: ${e.message}")
            "Sorry, there was a problem processing your request, please try again later."
        }
    }
    
    /**
     * Extract function call information
     */
    private fun extractFunctionCall(response: String): FunctionCall? {
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
            Log.d(TAG, "🔍 Extracted JSON: $jsonStr")
            
            val jsonElement = json.parseToJsonElement(jsonStr)
            val jsonObject = jsonElement.jsonObject
            
            val name = jsonObject["name"]?.jsonPrimitive?.content ?: return null
            val arguments = jsonObject["arguments"]?.toString() ?: "{}"
            
            FunctionCall(name, arguments)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse function call: ${e.message}")
            null
        }
    }
    
    /**
     * 🆕 Generate final user-friendly response - supports multiple functions
     */
    private suspend fun generateFinalResponse(originalMessage: String, functionResult: String, functionName: String): String {
        val functionType = when {
            functionName.contains("weather") -> "weather"
            functionName.contains("message") || functionName.contains("sms") -> "SMS"
            else -> "function"
        }
        
        val finalPrompt = """
User asked: $originalMessage

The ${functionType} information I obtained is:
$functionResult

Please provide a natural, friendly, and detailed response based on this information.
Requirements:
1. Don't mention technical terms like "function", "API", or "system"
2. Use a warm and caring tone suitable for elderly people
3. If it's weather information, care for the user like a friend
4. If it's SMS information, help the user understand the content and provide suggestions
5. Keep the response natural and smooth

Answer:""".trimIndent()
        
        return try {
            val finalResponse = callWatsonAI(finalPrompt)
            Log.d(TAG, "🎉 Final answer generation completed")
            finalResponse
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to generate final answer: ${e.message}")
            // Fallback response
            when (functionType) {
                "weather" -> "Based on the weather information obtained:\n\n$functionResult\n\nHope this information helps! Remember to adjust your clothing according to the weather."
                "SMS" -> "Based on your SMS information:\n\n$functionResult\n\nIf you need me to read a specific message for you, please let me know."
                else -> "Based on the information obtained:\n\n$functionResult\n\nHope this information helps!"
            }
        }
    }
    
    /**
     * Process normal conversation - with context
     */
    private suspend fun handleNormalConversation(userMessage: String): String {
        Log.d(TAG, "💬 Processing normal conversation")
        val contextualPrompt = buildContextualPrompt(userMessage, isFunction = false)
        return callWatsonAI(contextualPrompt)
    }
    
    /**
     * 🆕 Clear conversation history
     */
    fun clearConversationHistory() {
        conversationHistory.clear()
        Log.d(TAG, "🧹 Conversation history cleared")
    }
    
    /**
     * 🆕 Get conversation history summary
     */
    fun getConversationSummary(): String {
        return if (conversationHistory.isEmpty()) {
            "No conversation history available"
        } else {
            """
            Conversation History Summary:
            - Total conversation rounds: ${conversationHistory.count { it.role == "user" }}
            - Last conversation time: ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(conversationHistory.last().timestamp))}
            - History records: ${conversationHistory.size} entries
            """.trimIndent()
        }
    }
    
    /**
     * Call Watson AI API - based on your existing working code
     */
    private suspend fun callWatsonAI(prompt: String): String = withContext(Dispatchers.IO) {
        val token = getIAMToken()
        val url = "${config.baseUrl}/ml/v4/deployments/${config.deploymentId}/ai_service?version=2021-05-01"
        
        val requestBody = ChatRequest(
            messages = listOf(
                ChatMessage(
                    content = prompt,
                    role = "user"
                )
            )
        )
        
        Log.d(TAG, "📤 Sending request to Watson AI")
        Log.d(TAG, "📝 Prompt length: ${prompt.length}")
        
        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(requestBody).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()
        
        try {
            val response = client.newCall(request).execute()
            Log.d(TAG, "📨 Response status: ${response.code}")
            
            if (!response.isSuccessful) {
                val errorText = response.body?.string() ?: ""
                Log.e(TAG, "❌ API Error: $errorText")
                throw IOException("Watson AI API Error: ${response.code} - $errorText")
            }
            
            val responseBody = response.body?.string()
            Log.d(TAG, "✅ Received response: ${responseBody?.take(200)}...")
            
            val data = json.decodeFromString<WatsonResponse>(responseBody!!)
            return@withContext parseResponse(data)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Watson AI call failed: ${e.message}")
            throw e
        }
    }
    
    /**
     * Parse Watson AI response - based on your existing logic
     */
    private fun parseResponse(data: WatsonResponse): String {
        Log.d(TAG, "🔍 Parsing response data...")
        
        // Try various possible response formats
        data.choices?.firstOrNull()?.let { choice ->
            choice.message?.content?.let { 
                Log.d(TAG, "✅ Parse successful - chat format")
                return it.trim()
            }
            choice.text?.let { 
                Log.d(TAG, "✅ Parse successful - text format")
                return it.trim()
            }
        }
        
        data.results?.firstOrNull()?.generatedText?.let { 
            Log.d(TAG, "✅ Parse successful - generation format")
            return it.trim()
        }
        
        data.generatedText?.let { 
            Log.d(TAG, "✅ Parse successful - direct generated text")
            return it.trim()
        }
        data.result?.let { 
            Log.d(TAG, "✅ Parse successful - result format")
            return it.trim()
        }
        data.response?.let { 
            Log.d(TAG, "✅ Parse successful - response format")
            return it.trim()
        }
        data.content?.let { 
            Log.d(TAG, "✅ Parse successful - content format")
            return it.trim()
        }
        data.text?.let { 
            Log.d(TAG, "✅ Parse successful - text format")
            return it.trim()
        }
        
        Log.e(TAG, "❌ Cannot parse response format")
        throw IOException("Cannot parse Watson AI response format")
    }
    
    /**
     * Get IAM Token - reuse your existing logic
     */
    private suspend fun getIAMToken(): String = withContext(Dispatchers.IO) {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpirationTime - 300_000) {
            return@withContext cachedToken!!
        }
        
        val requestBody = FormBody.Builder()
            .add("grant_type", "urn:ibm:params:oauth:grant-type:apikey")
            .add("apikey", config.apiKey)
            .build()
        
        val request = Request.Builder()
            .url("https://iam.cloud.ibm.com/identity/token")
            .post(requestBody)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader("Accept", "application/json")
            .build()
        
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Failed to get IAM token: ${response.code}")
            }
            
            val responseBody = response.body?.string()
            val tokenResponse = json.decodeFromString<IAMTokenResponse>(responseBody!!)
            
            cachedToken = tokenResponse.accessToken
            tokenExpirationTime = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)
            
            return@withContext tokenResponse.accessToken
            
        } catch (e: Exception) {
            throw e
        }
    }
    
    /**
     * 🆕 Test all services
     */
    suspend fun testEnhancedService(): AIResult {
        return try {
            Log.d(TAG, "🔧 Testing enhanced service connection...")
            
            val testResults = mutableListOf<String>()
            
            // Test weather function
            try {
                val weatherTest = WeatherFunctions.testWeatherService()
                testResults.add("Weather Service: ✅ $weatherTest")
            } catch (e: Exception) {
                testResults.add("Weather Service: ❌ ${e.message}")
            }
            
            // Test SMS function
            try {
                val smsTest = SMSFunctions.testSMSService()
                testResults.add("SMS Service: ✅ $smsTest")
            } catch (e: Exception) {
                testResults.add("SMS Service: ❌ ${e.message}")
            }
            
            // Test AI conversation
            val testMessage = "Hello, please test the service"
            val aiResult = getEnhancedAIResponse(testMessage)
            
            if (aiResult.success) {
                testResults.add("AI Service: ✅ Connection normal")
            } else {
                testResults.add("AI Service: ❌ ${aiResult.error}")
            }
            
            val overallResult = testResults.joinToString("\n")
            
            Log.d(TAG, "✅ Enhanced service testing completed")
            AIResult(
                success = true,
                response = "🔧 Enhanced Service Test Results:\n\n$overallResult\n\n💬 Conversation History: ${conversationHistory.size} entries",
                error = null
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Enhanced service testing exception: ${e.message}")
            AIResult(
                success = false,
                response = "",
                error = "Enhanced service testing failed: ${e.message}"
            )
        }
    }
    
    /**
     * 🆕 Get complete service status
     */
    fun getServiceStatus(): String {
        val baseStatus = when {
            cachedToken != null && System.currentTimeMillis() < tokenExpirationTime -> "Connected"
            cachedToken != null -> "Token expired"
            else -> "Not connected"
        }
        
        val weatherStatus = WeatherFunctions.getServiceStatus()
        val smsStatus = SMSFunctions.getServiceStatus()
        val conversationStatus = getConversationSummary()
        
        return """
            🤖 Watson AI Enhanced Status: $baseStatus
            
            🌤️ $weatherStatus
            
            📱 $smsStatus
            
            💬 $conversationStatus
        """.trimIndent()
    }
    
    /**
     * 🆕 Data class definitions - context management
     */
    @Serializable
    private data class ConversationMessage(
        val content: String,
        val role: String,
        val timestamp: Long
    )
    
    /**
     * Data class definitions - reuse your existing structure
     */
    @Serializable
    private data class WatsonAIConfig(
        val baseUrl: String,
        val apiKey: String,
        val deploymentId: String
    )
    
    @Serializable
    private data class IAMTokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("token_type") val tokenType: String,
        @SerialName("expires_in") val expiresIn: Long
    )
    
    @Serializable
    private data class ChatMessage(
        val content: String,
        val role: String
    )
    
    @Serializable
    private data class ChatRequest(
        val messages: List<ChatMessage>
    )
    
    @Serializable
    private data class ChatChoice(
        val message: ChatMessage? = null,
        val text: String? = null
    )
    
    @Serializable
    private data class GenerationResult(
        @SerialName("generated_text") val generatedText: String? = null
    )
    
    @Serializable
    private data class WatsonResponse(
        val choices: List<ChatChoice>? = null,
        val results: List<GenerationResult>? = null,
        @SerialName("generated_text") val generatedText: String? = null,
        val result: String? = null,
        val response: String? = null,
        val content: String? = null,
        val text: String? = null
    )
    
    @Serializable
    private data class FunctionCall(
        val name: String,
        val arguments: String
    )
    
    /**
     * AI response result class
     */
    data class AIResult(
        val success: Boolean,
        val response: String,
        val error: String? = null
    )
}