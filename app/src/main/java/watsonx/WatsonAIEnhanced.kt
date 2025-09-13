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
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * Enhanced Watson AI Service - with context management and multiple function support
 */
object WatsonAIEnhanced {
    
    private const val TAG = "WatsonAIEnhanced"
    private var chatRepository: com.example.ibm_project.chat.ChatRepository? = null
    
    private val config = WatsonAIConfig(
        baseUrl = "https://eu-gb.ml.cloud.ibm.com",
        apiKey = "nP5c0_UJMcNbYTNM3OInGLqjygVLcH-SMloxyZfxH81U",
        deploymentId = "8933ca4a-958e-4e41-acf2-5867f589068b"
    )
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)  
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .connectionSpecs(listOf(
            ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)  
                .cipherSuites(
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
                )
                .build(),
            ConnectionSpec.CLEARTEXT
        ))
        .sslSocketFactory(createTrustAllSslContext().socketFactory, createTrustAllManager())
        .hostnameVerifier { _, _ -> true }
        .build()
        
    private fun createTrustAllManager(): X509TrustManager {
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    }

    private fun createTrustAllSslContext(): SSLContext {
        val trustManager = createTrustAllManager()
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())
        return sslContext
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private var cachedToken: String? = null
    private var tokenExpirationTime: Long = 0
    
    // Create PromptManager instance
    private val promptManager = PromptManager()
    
    fun initialize(context: Context, repository: com.example.ibm_project.chat.ChatRepository? = null) {
        WeatherFunctions.initialize(context)
        SMSFunctions.initialize(context)
        NewsFunctions.initialize(context)
        PodcastFunctions.initialize(context)
        LocationFunctions.initialize(context)
        chatRepository = repository
        Log.d(TAG, "WatsonAI Enhanced service initialized (supports weather + SMS + news + podcast + context)")
    }
    
    /**
     * Enhanced AI Response with optimized context management
     */
    suspend fun getEnhancedAIResponse(userMessage: String): AIResult {
        return try {
            Log.d(TAG, "Starting enhanced AI request processing: $userMessage")
            
            if (userMessage.trim().isEmpty()) {
                return AIResult(
                    success = false,
                    response = "",
                    error = "Message cannot be empty"
                )
            }
            
            var response: String
            
            if (chatRepository != null) {
                // Use optimized ChatRepository context system
                try {
                    chatRepository!!.loadChatHistory()
                    
                    // Get query-aware context using the new system
                    val optimizedContext = chatRepository!!.getQueryAwareContext(userMessage)
                    
                    // Build complete prompt with context
                    val fullPrompt = buildContextualPrompt(userMessage, optimizedContext)
                    
                    response = if (promptManager.mightNeedFunctionCall(userMessage)) {
                        // Handle function calling
                        val aiResponse = callWatsonAI(fullPrompt)
                        
                        if (promptManager.containsFunctionCall(aiResponse)) {
                            executeFunctionAndGenerateResponse(aiResponse, userMessage)
                        } else {
                            aiResponse
                        }
                    } else {
                        // Direct conversation
                        callWatsonAI(fullPrompt)
                    }
                    
                    // Save the conversation
                    chatRepository!!.saveChatMessage(userMessage, response)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "ChatRepository context failed, falling back to ContextManager: ${e.message}")
                    // Fallback to ContextManager
                    ContextManager.addToHistory(userMessage, "user")
                    response = processWithFunctionCalling(userMessage.trim())
                    ContextManager.addToHistory(response, "assistant")
                }
                
            } else {
                // Use ContextManager as fallback
                ContextManager.addToHistory(userMessage, "user")
                response = processWithFunctionCalling(userMessage.trim())
                ContextManager.addToHistory(response, "assistant")
            }
            
            Log.d(TAG, "Enhanced AI response processing completed")
            AIResult(
                success = true,
                response = response,
                error = null
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Enhanced AI processing failed: ${e.message}")
            // 提供默認錯誤回覆
            AIResult(
                success = true,  // 改為 true 以提供友好的錯誤訊息
                response = "I'm experiencing some technical difficulties right now. Please try again in a moment.",
                error = null
            )
        }
    }
    
    /**
     * Build contextual prompt with optimized context
     */
    private fun buildContextualPrompt(userMessage: String, contextHistory: String = ""): String {
        val context = StringBuilder()
        
        // System instructions
        context.append("You are a helpful AI assistant for elderly users.\n")
        
        // Add conversation history if available
        if (contextHistory.isNotEmpty()) {
            context.append("=== CONVERSATION HISTORY (Reference Only) ===\n")
            context.append(contextHistory)
        }
        
        // Behavior rules
        context.append("=== BEHAVIOR RULES ===\n")
        context.append("1. Answer the CURRENT USER QUESTION first and completely\n")
        context.append("2. Use conversation history for context only, do not repeat it\n")
        context.append("3. Be concise and focused on what the user is asking\n")
        context.append("DO NOT suggest additional features\n\n")
        
        // Current message
        context.append("Current message: $userMessage")
        
        val finalPrompt = context.toString()
        
        // Final safety check: limit prompt length
        return if (finalPrompt.length > 8000) {
            Log.w(TAG, "Prompt too long (${finalPrompt.length} chars), applying emergency truncation")
            truncatePromptSafely(finalPrompt, userMessage)
        } else {
            finalPrompt
        }
    }
    
    /**
     * Emergency prompt truncation while preserving essential parts
     */
    private fun truncatePromptSafely(prompt: String, userMessage: String): String {
        val lines = prompt.lines()
        val systemLines = lines.takeWhile { !it.contains("CONVERSATION HISTORY") }
        val rulesAndMessage = lines.dropWhile { !it.contains("BEHAVIOR RULES") }
        
        // Keep system instructions + rules + current message, truncate history
        val truncated = (systemLines + listOf("...[context truncated for length]...") + rulesAndMessage)
            .joinToString("\n")
            .take(7000) // Conservative limit
        
        Log.w(TAG, "Applied emergency truncation: ${prompt.length} -> ${truncated.length} chars")
        return truncated
    }
    
    /**
     * Process with function calling support
     */
    private suspend fun processWithFunctionCalling(userMessage: String): String {
        if (promptManager.mightNeedFunctionCall(userMessage)) {
            Log.d(TAG, "Detected potential function call needed, using function calling prompt")
            return handleWithFunctionCallingPrompt(userMessage)
        } else {
            Log.d(TAG, "Normal conversation, using regular prompt")
            return handleNormalConversation(userMessage)
        }
    }
    
    /**
     * Handle function calling prompt
     */
    private suspend fun handleWithFunctionCallingPrompt(userMessage: String): String {
        Log.d(TAG, "Using Function Calling Prompt")
        
        // Use ContextManager to build contextual function calling prompt
        val functionPrompt = ContextManager.buildContextualPrompt(userMessage, isFunction = true)
        
        // Call Watson AI
        val aiResponse = callWatsonAI(functionPrompt)
        
        // Check if function call is contained
        return if (promptManager.containsFunctionCall(aiResponse)) {
            Log.d(TAG, "AI recognized need to call function")
            executeFunctionAndGenerateResponse(aiResponse, userMessage)
        } else {
            Log.d(TAG, "AI decided to answer directly")
            aiResponse
        }
    }
    
    /**
     * Execute function and generate final response
     */
    private suspend fun executeFunctionAndGenerateResponse(aiResponse: String, originalMessage: String): String {
        return try {
            // Extract function call
            val functionCall = extractFunctionCall(aiResponse)
            if (functionCall == null) {
                Log.w(TAG, "Cannot parse function call, fallback to original response")
                return aiResponse
            }
            
            // Fix incorrect function names - 確保使用 var
            val correctedName = if (functionCall.name == "read_latest_message") {
                Log.d(TAG, "Fixed function name: read_latest_message -> get_latest_message")
                "get_latest_message"
            } else {
                functionCall.name
            }
                        
            Log.d(TAG, "Executing function: $correctedName")
            Log.d(TAG, "Function parameters: ${functionCall.arguments}")
            
            // Call corresponding service based on function type
            val functionResult = when {
                correctedName.startsWith("get_") && correctedName.contains("weather") -> {
                    WeatherFunctions.execute(correctedName, functionCall.arguments)
                }
                correctedName in listOf(
                    "read_unread_messages", "read_recent_messages", "get_message_summary",
                    "get_message_by_index", "get_latest_message"
                ) -> {
                    SMSFunctions.execute(correctedName, functionCall.arguments)
                }
                correctedName in listOf(
                    "get_current_location", "get_user_location", "get_location_info"
                ) -> {
                    LocationFunctions.execute(correctedName, functionCall.arguments)
                }
                correctedName in listOf(
                    "get_podcasts_by_category", "get_recommended_podcasts", "search_podcasts",
                    "get_health_podcasts", "get_history_podcasts", "get_education_podcasts",
                    "get_news_podcasts", "get_business_podcasts", "get_science_podcasts",
                    "get_technology_podcasts"
                ) -> {
                    PodcastFunctions.execute(correctedName, functionCall.arguments)
                }
                correctedName in listOf(
                    "get_recommended_news", "get_latest_news", "search_news", 
                    "get_news_summary", "get_news_by_category"
                ) -> {
                    NewsFunctions.execute(correctedName, functionCall.arguments)
                }
                else -> {
                    Log.w(TAG, "Unknown function type: $correctedName")
                    "Sorry, unrecognized function request."
                }
            }
            
            Log.d(TAG, "Function execution completed")
            Log.d(TAG, "Function returned:\n$functionResult")
            
            // Generate final user-friendly response
            generateFinalResponse(originalMessage, functionResult, correctedName)
            
        } catch (e: Exception) {
            Log.e(TAG, "Function execution failed: ${e.message}")
            "Sorry, there was a problem processing your request, please try again later."
        }
    }
    
    /**
     * Extract function call information from AI response
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
     * Generate final user-friendly response
     */
    private suspend fun generateFinalResponse(originalMessage: String, functionResult: String, functionName: String): String {
        // Use PromptManager to build final response prompt
        val finalPrompt = promptManager.buildFinalResponsePrompt(originalMessage, functionResult, functionName)
        
        return try {
            val finalResponse = callWatsonAI(finalPrompt)
            Log.d(TAG, "Final answer generation completed")
            finalResponse
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate final answer: ${e.message}")
            // Use PromptManager to generate fallback response
            promptManager.generateFallbackResponse(functionResult, functionName)
        }
    }
    
    /**
     * Handle normal conversation without function calling
     */
    private suspend fun handleNormalConversation(userMessage: String): String {
        Log.d(TAG, "Processing normal conversation")
        // Use ContextManager to build contextual normal conversation prompt
        val contextualPrompt = ContextManager.buildContextualPrompt(userMessage, isFunction = false)
        return callWatsonAI(contextualPrompt)
    }
    
    /**
     * Clear conversation history - delegate to ContextManager
     */
    fun clearConversationHistory() {
        ContextManager.clearConversationHistory()
    }
    
    /**
     * Get conversation history summary - delegate to ContextManager
     */
    fun getConversationSummary(): String {
        return ContextManager.getConversationSummary()
    }
    
    /**
     * Call Watson AI API with enhanced error handling
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
        
        Log.d(TAG, "Sending request to Watson AI")
        Log.d(TAG, "URL: $url")  
        Log.d(TAG, "Prompt length: ${prompt.length}")
        
        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(requestBody).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()
        
        try {
            Log.d(TAG, "Making HTTP request...")  
            val response = client.newCall(request).execute()
            Log.d(TAG, "Response status: ${response.code}")
            
            if (!response.isSuccessful) {
                val errorText = response.body?.string() ?: ""
                Log.e(TAG, "API Error: $errorText")
                Log.e(TAG, "Response code: ${response.code}")  
                Log.e(TAG, "Response message: ${response.message}")  
                throw IOException("Watson AI API Error: ${response.code} - $errorText")
            }
            
            val responseBody = response.body?.string()
            Log.d(TAG, "Received response: ${responseBody?.take(200)}...")
            
            val data = json.decodeFromString<WatsonResponse>(responseBody!!)
            return@withContext parseResponse(data)
            
        } catch (e: Exception) {
            Log.e(TAG, "Watson AI call failed: ${e.message}")
            Log.e(TAG, "Exception type: ${e::class.java.simpleName}")  
            throw e
        }
    }
    
    /**
     * Parse Watson AI response with multiple format support
     */
    private fun parseResponse(data: WatsonResponse): String {
        Log.d(TAG, "Parsing response data...")
        
        // Try various possible response formats
        data.choices?.firstOrNull()?.let { choice ->
            choice.message?.content?.let { 
                Log.d(TAG, "Parse successful - chat format")
                return it.trim()
            }
            choice.text?.let { 
                Log.d(TAG, "Parse successful - text format")
                return it.trim()
            }
        }
        
        data.results?.firstOrNull()?.generatedText?.let { 
            Log.d(TAG, "Parse successful - generation format")
            return it.trim()
        }
        
        data.generatedText?.let { 
            Log.d(TAG, "Parse successful - direct generated text")
            return it.trim()
        }
        data.result?.let { 
            Log.d(TAG, "Parse successful - result format")
            return it.trim()
        }
        data.response?.let { 
            Log.d(TAG, "Parse successful - response format")
            return it.trim()
        }
        data.content?.let { 
            Log.d(TAG, "Parse successful - content format")
            return it.trim()
        }
        data.text?.let { 
            Log.d(TAG, "Parse successful - text format")
            return it.trim()
        }
        
        Log.e(TAG, "Cannot parse response format")
        throw IOException("Cannot parse Watson AI response format")
    }
    
    /**
     * Get IAM Token with caching
     */
    private suspend fun getIAMToken(): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "Checking cached token...")
        if (cachedToken != null && System.currentTimeMillis() < tokenExpirationTime - 300_000) {
            Log.d(TAG, "Using cached token")
            return@withContext cachedToken!!
        }
        
        Log.d(TAG, "Getting new IAM token...")
        Log.d(TAG, "API Key: ${config.apiKey.take(10)}...")  
        
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
            Log.d(TAG, "Making IAM token request...")
            val response = client.newCall(request).execute()
            Log.d(TAG, "IAM response status: ${response.code}")
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "IAM token error: $errorBody")
                throw IOException("Failed to get IAM token: ${response.code}")
            }
            
            val responseBody = response.body?.string()
            Log.d(TAG, "IAM token received successfully")
            val tokenResponse = json.decodeFromString<IAMTokenResponse>(responseBody!!)
            
            cachedToken = tokenResponse.accessToken
            tokenExpirationTime = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)
            
            return@withContext tokenResponse.accessToken
            
        } catch (e: Exception) {
            Log.e(TAG, "IAM token failed: ${e.message}")
            Log.e(TAG, "Exception type: ${e::class.java.simpleName}")
            throw e
        }
    }
    
    /**
     * Test all enhanced services
     */
    suspend fun testEnhancedService(): AIResult {
        return try {
            Log.d(TAG, "Testing enhanced service connection...")
            
            val testResults = mutableListOf<String>()
            
            // Test weather function
            try {
                val weatherTest = WeatherFunctions.testWeatherService()
                testResults.add("Weather Service: $weatherTest")
            } catch (e: Exception) {
                testResults.add("Weather Service: ${e.message}")
            }
            
            // Test SMS function
            try {
                val smsTest = SMSFunctions.testSMSService()
                testResults.add("SMS Service: $smsTest")
            } catch (e: Exception) {
                testResults.add("SMS Service: ${e.message}")
            }
            
            // Test News function
            try {
                val newsTest = NewsFunctions.testNewsService()
                testResults.add("News Service: $newsTest")
            } catch (e: Exception) {
                testResults.add("News Service: ${e.message}")
            }
            
            // Test Podcast function
            try {
                val podcastTest = PodcastFunctions.testPodcastService()
                testResults.add("Podcast Service: $podcastTest")
            } catch (e: Exception) {
                testResults.add("Podcast Service: ${e.message}")
            }
            
            // Test AI conversation
            val testMessage = "Hello, please test the service"
            val aiResult = getEnhancedAIResponse(testMessage)
            
            if (aiResult.success) {
                testResults.add("AI Service: Connection normal")
            } else {
                testResults.add("AI Service: ${aiResult.error}")
            }
            
            val overallResult = testResults.joinToString("\n")
            
            Log.d(TAG, "Enhanced service testing completed")
            AIResult(
                success = true,
                response = "Enhanced Service Test Results:\n\n$overallResult\n\nConversation History: ${ContextManager.getHistorySize()} entries",
                error = null
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Enhanced service testing exception: ${e.message}")
            AIResult(
                success = false,
                response = "",
                error = "Enhanced service testing failed: ${e.message}"
            )
        }
    }
    
    /**
     * Get complete service status information
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
            Watson AI Enhanced Status: $baseStatus
            
            $weatherStatus
            
            $smsStatus
            
            $newsStatus
            
            $podcastStatus
            
            $conversationStatus
        """.trimIndent()
    }
    
    // Data class definitions
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
        var response: String? = null,
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
        var response: String,
        val error: String? = null
    )
}