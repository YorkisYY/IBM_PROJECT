// watsonx/WatsonAIEnhanced.kt
package watsonx

import android.content.Context
import android.util.Log
import functions.WeatherFunctions
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * 增強版 Watson AI 服務 - Prompt Engineering 版本
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
    
    fun initialize(context: Context) {
        WeatherFunctions.initialize(context)
        Log.d(TAG, "✅ WatsonAI Enhanced 服務已初始化 (Prompt Engineering 模式)")
    }
    
    /**
     * 增強版 AI 回應 - 使用 Prompt Engineering
     */
    suspend fun getEnhancedAIResponse(userMessage: String): AIResult {
        return try {
            Log.d(TAG, "🚀 開始處理增強 AI 請求: $userMessage")
            
            if (userMessage.trim().isEmpty()) {
                return AIResult(
                    success = false,
                    response = "",
                    error = "消息不能為空"
                )
            }
            
            val response = processWithFunctionCalling(userMessage.trim())
            
            Log.d(TAG, "🎉 增強 AI 回應處理完成")
            AIResult(
                success = true,
                response = response,
                error = null
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 增強 AI 處理失敗: ${e.message}")
            AIResult(
                success = false,
                response = "",
                error = e.message ?: "未知錯誤"
            )
        }
    }
    
    /**
     * 處理 Function Calling - Prompt Engineering 方式
     */
    private suspend fun processWithFunctionCalling(userMessage: String): String {
        // 步驟 1: 先檢查是否可能需要 function calling
        if (mightNeedFunctionCall(userMessage)) {
            Log.d(TAG, "🔍 檢測到可能需要函數調用，使用 function calling prompt")
            return handleWithFunctionCallingPrompt(userMessage)
        } else {
            Log.d(TAG, "💬 普通對話，使用正常 prompt")
            return handleNormalConversation(userMessage)
        }
    }
    
    /**
     * 檢查是否可能需要函數調用
     */
    private fun mightNeedFunctionCall(message: String): Boolean {
        val weatherKeywords = listOf(
            "天氣", "weather", "氣溫", "溫度", "temperature",
            "下雨", "rain", "晴天", "sunny", "陰天", "cloudy",
            "風", "wind", "濕度", "humidity", "預報", "forecast",
            "幾度", "度", "degree", "冷", "熱", "涼", "暖"
        )
        
        return weatherKeywords.any { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
    }
    
    /**
     * 使用 Function Calling Prompt 處理
     */
    private suspend fun handleWithFunctionCallingPrompt(userMessage: String): String {
        Log.d(TAG, "🔧 使用 Function Calling Prompt")
        
        // 構建 function calling prompt
        val functionPrompt = buildFunctionCallingPrompt(userMessage)
        
        // 調用 Watson AI
        val aiResponse = callWatsonAI(functionPrompt)
        
        // 檢查是否包含函數調用
        return if (containsFunctionCall(aiResponse)) {
            Log.d(TAG, "✅ AI 識別到需要調用函數")
            executeFunctionAndGenerateResponse(aiResponse, userMessage)
        } else {
            Log.d(TAG, "💬 AI 決定直接回答")
            aiResponse
        }
    }
    
    /**
     * 構建 Function Calling Prompt
     */
    private fun buildFunctionCallingPrompt(userMessage: String): String {
        return """
你是一個智能助手，具有調用外部函數的能力。

可用函數列表：
1. get_current_weather() - 獲取用戶當前位置的天氣資訊
2. get_weather_by_city(city) - 獲取指定城市的天氣資訊

重要規則：
- 當用戶詢問天氣相關問題時，你必須調用相應的函數
- 如果用戶提到具體城市名稱，使用 get_weather_by_city
- 如果用戶沒有指定城市，使用 get_current_weather
- 函數調用格式必須嚴格按照：FUNCTION_CALL: {"name": "函數名", "arguments": {參數}}
- 對於非天氣問題，正常回答即可

範例：
用戶："今天天氣如何？"
助手：FUNCTION_CALL: {"name": "get_current_weather", "arguments": {}}

用戶："台北的天氣怎樣？"
助手：FUNCTION_CALL: {"name": "get_weather_by_city", "arguments": {"city": "台北"}}

用戶："東京現在幾度？"
助手：FUNCTION_CALL: {"name": "get_weather_by_city", "arguments": {"city": "東京"}}

用戶："你好嗎？"
助手：你好！我很好，謝謝你的關心。有什麼可以幫助你的嗎？

現在請處理用戶的問題：
用戶：$userMessage
助手：""".trimIndent()
    }
    
    /**
     * 檢查回應是否包含函數調用
     */
    private fun containsFunctionCall(response: String): Boolean {
        return response.contains("FUNCTION_CALL:", ignoreCase = true)
    }
    
    /**
     * 執行函數並生成最終回應
     */
    private suspend fun executeFunctionAndGenerateResponse(aiResponse: String, originalMessage: String): String {
        return try {
            // 提取函數調用
            val functionCall = extractFunctionCall(aiResponse)
            if (functionCall == null) {
                Log.w(TAG, "⚠️ 無法解析函數調用，回退到原始回應")
                return aiResponse
            }
            
            Log.d(TAG, "🎯 執行函數: ${functionCall.name}")
            Log.d(TAG, "📝 函數參數: ${functionCall.arguments}")
            
            // 執行函數
            val functionResult = WeatherFunctions.execute(functionCall.name, functionCall.arguments)
            
            Log.d(TAG, "✅ 函數執行完成")
            
            // 生成最終用戶友好的回應
            generateFinalResponse(originalMessage, functionResult)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 函數執行失敗: ${e.message}")
            "抱歉，獲取天氣資訊時出現問題，請稍後再試。"
        }
    }
    
    /**
     * 提取函數調用資訊
     */
    private fun extractFunctionCall(response: String): FunctionCall? {
        return try {
            // 找到 FUNCTION_CALL: 後面的 JSON
            val startIndex = response.indexOf("FUNCTION_CALL:")
            if (startIndex == -1) return null
            
            val jsonStart = response.indexOf("{", startIndex)
            if (jsonStart == -1) return null
            
            // 🔧 修正：找到完整的 JSON 對象結尾
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
            
            if (braceCount != 0) return null // JSON 不完整
            
            val jsonStr = response.substring(jsonStart, jsonEnd + 1)
            Log.d(TAG, "🔍 提取的 JSON: $jsonStr")
            
            val jsonElement = json.parseToJsonElement(jsonStr)
            val jsonObject = jsonElement.jsonObject
            
            val name = jsonObject["name"]?.jsonPrimitive?.content ?: return null
            val arguments = jsonObject["arguments"]?.toString() ?: "{}"
            
            FunctionCall(name, arguments)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 解析函數調用失敗: ${e.message}")
            null
        }
    }
    
    /**
     * 生成最終用戶友好的回應
     */
    private suspend fun generateFinalResponse(originalMessage: String, functionResult: String): String {
        val finalPrompt = """
用戶問了：$originalMessage

我獲取到的天氣資訊是：
$functionResult

請基於這些資訊，給用戶一個自然、友好、詳細的回答。不要提到"函數"或"API"等技術詞彙，就像你親自查看了天氣一樣回答。

回答：""".trimIndent()
        
        return try {
            val finalResponse = callWatsonAI(finalPrompt)
            Log.d(TAG, "🎉 生成最終回答完成")
            finalResponse
        } catch (e: Exception) {
            Log.e(TAG, "❌ 生成最終回答失敗: ${e.message}")
            // 備用回應
            "根據獲取到的天氣資訊：\n\n$functionResult\n\n希望這些資訊對您有幫助！"
        }
    }
    
    /**
     * 處理普通對話
     */
    private suspend fun handleNormalConversation(userMessage: String): String {
        Log.d(TAG, "💬 處理普通對話")
        return callWatsonAI("用戶: $userMessage\n助手:")
    }
    
    /**
     * 調用 Watson AI API - 基於你現有的工作代碼
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
        
        Log.d(TAG, "📤 發送請求到 Watson AI")
        Log.d(TAG, "📝 Prompt 長度: ${prompt.length}")
        
        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(requestBody).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()
        
        try {
            val response = client.newCall(request).execute()
            Log.d(TAG, "📨 響應狀態: ${response.code}")
            
            if (!response.isSuccessful) {
                val errorText = response.body?.string() ?: ""
                Log.e(TAG, "❌ API 錯誤: $errorText")
                throw IOException("Watson AI API Error: ${response.code} - $errorText")
            }
            
            val responseBody = response.body?.string()
            Log.d(TAG, "✅ 收到回復: ${responseBody?.take(200)}...")
            
            val data = json.decodeFromString<WatsonResponse>(responseBody!!)
            return@withContext parseResponse(data)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Watson AI 調用失敗: ${e.message}")
            throw e
        }
    }
    
    /**
     * 解析 Watson AI 響應 - 基於你現有的邏輯
     */
    private fun parseResponse(data: WatsonResponse): String {
        Log.d(TAG, "🔍 解析響應數據...")
        
        // 嘗試各種可能的響應格式
        data.choices?.firstOrNull()?.let { choice ->
            choice.message?.content?.let { 
                Log.d(TAG, "✅ 解析成功 - 聊天格式")
                return it.trim()
            }
            choice.text?.let { 
                Log.d(TAG, "✅ 解析成功 - 文本格式")
                return it.trim()
            }
        }
        
        data.results?.firstOrNull()?.generatedText?.let { 
            Log.d(TAG, "✅ 解析成功 - 生成格式")
            return it.trim()
        }
        
        data.generatedText?.let { 
            Log.d(TAG, "✅ 解析成功 - 直接生成文本")
            return it.trim()
        }
        data.result?.let { 
            Log.d(TAG, "✅ 解析成功 - 結果格式")
            return it.trim()
        }
        data.response?.let { 
            Log.d(TAG, "✅ 解析成功 - 響應格式")
            return it.trim()
        }
        data.content?.let { 
            Log.d(TAG, "✅ 解析成功 - 內容格式")
            return it.trim()
        }
        data.text?.let { 
            Log.d(TAG, "✅ 解析成功 - 文本格式")
            return it.trim()
        }
        
        Log.e(TAG, "❌ 無法解析響應格式")
        throw IOException("無法解析 Watson AI 響應格式")
    }
    
    /**
     * 獲取 IAM Token - 復用你現有的邏輯
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
     * 測試增強服務
     */
    suspend fun testEnhancedService(): AIResult {
        return try {
            Log.d(TAG, "🔧 測試增強服務連接...")
            
            val testMessage = "請告訴我現在的天氣如何？"
            val result = getEnhancedAIResponse(testMessage)
            
            if (result.success) {
                Log.d(TAG, "✅ 增強服務測試成功")
                AIResult(
                    success = true,
                    response = "增強服務測試成功！\n\n${result.response}",
                    error = null
                )
            } else {
                Log.e(TAG, "❌ 增強服務測試失敗: ${result.error}")
                result
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 增強服務測試異常: ${e.message}")
            AIResult(
                success = false,
                response = "",
                error = "增強服務測試失敗: ${e.message}"
            )
        }
    }
    
    /**
     * 獲取服務狀態
     */
    fun getServiceStatus(): String {
        val baseStatus = when {
            cachedToken != null && System.currentTimeMillis() < tokenExpirationTime -> "已連接"
            cachedToken != null -> "令牌已過期"
            else -> "未連接"
        }
        
        val weatherStatus = WeatherFunctions.getServiceStatus()
        
        return """
            Watson AI Enhanced 狀態: $baseStatus (Prompt Engineering 模式)
            $weatherStatus
        """.trimIndent()
    }
    
    /**
     * 數據類定義 - 復用你現有的結構
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
     * AI 回復結果類
     */
    data class AIResult(
        val success: Boolean,
        val response: String,
        val error: String? = null
    )
}