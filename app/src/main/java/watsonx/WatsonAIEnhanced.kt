// watsonx/WatsonAIEnhanced.kt
package watsonx

import android.content.Context
import android.util.Log
import functions.WeatherFunctions
import functions.SMSFunctions
import functions.NewsFunctions
import functions.PodcastFunctions
import functions.LocationFunctions
import watsonx.ContextManager
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import watsonx.FunctionCallManager
import watsonx.PromptManager

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
    
    // 創建 PromptManager 實例
    private val promptManager = PromptManager()
    
    fun initialize(context: Context) {
        WeatherFunctions.initialize(context)
        SMSFunctions.initialize(context)
        NewsFunctions.initialize(context)
        PodcastFunctions.initialize(context)
        LocationFunctions.initialize(context)
        Log.d(TAG, "✅ WatsonAI Enhanced service initialized (supports weather + SMS + news + podcast + context)")
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
            
            // 🆕 使用 ContextManager 添加用户消息到历史记录
            ContextManager.addToHistory(userMessage, "user")
            
            val response = processWithFunctionCalling(userMessage.trim())
            
            // 🆕 使用 ContextManager 添加助手响应到历史记录
            ContextManager.addToHistory(response, "assistant")
            
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
     * Process Function Calling - supports weather and SMS
     */
    private suspend fun processWithFunctionCalling(userMessage: String): String {
        // Step 1: 使用 PromptManager 检查是否需要函数调用
        if (promptManager.mightNeedFunctionCall(userMessage)) {
            Log.d(TAG, "🔍 Detected potential function call needed, using function calling prompt")
            return handleWithFunctionCallingPrompt(userMessage)
        } else {
            Log.d(TAG, "💬 Normal conversation, using regular prompt")
            return handleNormalConversation(userMessage)
        }
    }
    
    /**
     * Process using Function Calling Prompt
     */
    private suspend fun handleWithFunctionCallingPrompt(userMessage: String): String {
        Log.d(TAG, "🔧 Using Function Calling Prompt")
        
        // 使用 ContextManager 构建带上下文的函数调用提示词
        val functionPrompt = ContextManager.buildContextualPrompt(userMessage, isFunction = true)
        
        // Call Watson AI
        val aiResponse = callWatsonAI(functionPrompt)
        
        // 使用 PromptManager 检查是否包含函数调用
        return if (promptManager.containsFunctionCall(aiResponse)) {
            Log.d(TAG, "✅ AI recognized need to call function")
            executeFunctionAndGenerateResponse(aiResponse, userMessage)
        } else {
            Log.d(TAG, "💬 AI decided to answer directly")
            aiResponse
        }
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
            
            // 🆕🆕🆕 加入這段：修正錯誤的函數名稱 🆕🆕🆕
            var correctedName = functionCall.name
            if (functionCall.name == "read_latest_message") {
                correctedName = "get_latest_message"
                Log.d(TAG, "🔄 修正函數名稱: read_latest_message → get_latest_message")
            }
            
            Log.d(TAG, "🎯 Executing function: $correctedName")  // 改用 correctedName
            Log.d(TAG, "📝 Function parameters: ${functionCall.arguments}")
            
            // 🆕 Call corresponding service based on function type
            val functionResult = when {
                correctedName.startsWith("get_") && correctedName.contains("weather") -> {  // 改用 correctedName
                    WeatherFunctions.execute(correctedName, functionCall.arguments)
                }
                correctedName in listOf(  // 改用 correctedName
                    "read_unread_messages", "read_recent_messages", "get_message_summary",
                    "get_message_by_index", "get_latest_message"
                ) -> {
                    SMSFunctions.execute(correctedName, functionCall.arguments)
                }
                correctedName in listOf(  // 改用 correctedName
                    "get_current_location", "get_user_location", "get_location_info"
                ) -> {
                    LocationFunctions.execute(correctedName, functionCall.arguments)
                }
                // ... 其他函數判斷也改用 correctedName
                else -> {
                    Log.w(TAG, "⚠️ Unknown function type: $correctedName")
                    "Sorry, unrecognized function request."
                }
            }
            
            Log.d(TAG, "✅ Function execution completed")
            Log.d(TAG, "📊 Function returned:\n$functionResult")
            
            // Generate final user-friendly response
            generateFinalResponse(originalMessage, functionResult, correctedName)  // 改用 correctedName
            
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
        // 使用 PromptManager 构建最终响应提示词
        val finalPrompt = promptManager.buildFinalResponsePrompt(originalMessage, functionResult, functionName)
        
        return try {
            val finalResponse = callWatsonAI(finalPrompt)
            Log.d(TAG, "🎉 Final answer generation completed")
            finalResponse
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to generate final answer: ${e.message}")
            // 使用 PromptManager 生成回退响应
            promptManager.generateFallbackResponse(functionResult, functionName)
        }
    }
    
    /**
     * Process normal conversation - with context
     */
    private suspend fun handleNormalConversation(userMessage: String): String {
        Log.d(TAG, "💬 Processing normal conversation")
        // 使用 ContextManager 构建带上下文的普通对话提示词
        val contextualPrompt = ContextManager.buildContextualPrompt(userMessage, isFunction = false)
        return callWatsonAI(contextualPrompt)
    }
    
    /**
     * 🆕 Clear conversation history - 委托给 ContextManager
     */
    fun clearConversationHistory() {
        ContextManager.clearConversationHistory()
    }
    
    /**
     * 🆕 Get conversation history summary - 委托给 ContextManager
     */
    fun getConversationSummary(): String {
        return ContextManager.getConversationSummary()
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
            
            // Test News function
            try {
                val newsTest = NewsFunctions.testNewsService()
                testResults.add("News Service: ✅ $newsTest")
            } catch (e: Exception) {
                testResults.add("News Service: ❌ ${e.message}")
            }
            
            // Test Podcast function
            try {
                val podcastTest = PodcastFunctions.testPodcastService()
                testResults.add("Podcast Service: ✅ $podcastTest")
            } catch (e: Exception) {
                testResults.add("Podcast Service: ❌ ${e.message}")
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
                response = "🔧 Enhanced Service Test Results:\n\n$overallResult\n\n💬 Conversation History: ${ContextManager.getHistorySize()} entries",
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
        val newsStatus = NewsFunctions.getServiceStatus()
        val podcastStatus = PodcastFunctions.getServiceStatus()
        val conversationStatus = getConversationSummary()
        
        return """
            🤖 Watson AI Enhanced Status: $baseStatus
            
            🌤️ $weatherStatus
            
            📱 $smsStatus
            
            📰 $newsStatus
            
            🎧 $podcastStatus
            
            💬 $conversationStatus
        """.trimIndent()
    }
    
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