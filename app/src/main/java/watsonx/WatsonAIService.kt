// WatsonAIService.kt - 放在 watsonx 包下
package watsonx

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Watson AI 服务 - 独立文件，供 MainActivity 调用
 */
object WatsonAIService {
    private const val TAG = "WatsonAIService"

    // Watson AI 配置 - 基于您的 React 代码
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

    // 缓存的访问令牌
    private var cachedToken: String? = null
    private var tokenExpirationTime: Long = 0

    /**
     * 数据类定义
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

    /**
     * AI 回复结果类
     */
    data class AIResult(
        val success: Boolean,
        val response: String,
        val error: String? = null
    )

    /**
     * 获取 IAM Token - 带缓存机制
     */
    private suspend fun getIAMToken(): String = withContext(Dispatchers.IO) {
        // 检查缓存的令牌是否有效（提前 5 分钟过期）
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
                val errorText = response.body?.string() ?: ""
                throw IOException("Failed to get IAM token: ${response.code} ${response.message} - $errorText")
            }

            val responseBody = response.body?.string()
            val tokenResponse = json.decodeFromString<IAMTokenResponse>(responseBody!!)

            if (tokenResponse.accessToken.isEmpty()) {
                throw IOException("No access token in IAM response")
            }

            // 缓存令牌
            cachedToken = tokenResponse.accessToken
            tokenExpirationTime = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)

            Log.d(TAG, "✅ IAM Token 获取成功")
            return@withContext tokenResponse.accessToken

        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取 IAM Token 失败: ${e.message}")
            throw e
        }
    }

    /**
     * 调用 Watson AI API
     */
    private suspend fun callWatsonAI(userMessage: String): String = withContext(Dispatchers.IO) {
        val token = getIAMToken()
        val url = "${config.baseUrl}/ml/v4/deployments/${config.deploymentId}/ai_service?version=2021-05-01"

        // 构建请求体 - 与您的 React 代码格式一致
        val requestBody = ChatRequest(
            messages = listOf(
                ChatMessage(
                    content = userMessage,
                    role = "user"
                )
            )
        )

        Log.d(TAG, "🚀 發送請求到 Watson AI")
        Log.d(TAG, "📝 請求內容: ${json.encodeToString(requestBody)}")

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
            Log.e(TAG, "❌ Watson AI 调用失败: ${e.message}")
            throw e
        }
    }

    /**
     * 解析 Watson AI 响应 - 基于您的 React 代码逻辑
     */
    private fun parseResponse(data: WatsonResponse): String {
        Log.d(TAG, "🔍 解析響應數據...")

        // 尝试各种可能的响应格式
        // 1. 聊天响应格式
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

        // 2. 生成式 AI 响应
        data.results?.firstOrNull()?.generatedText?.let { 
            Log.d(TAG, "✅ 解析成功 - 生成格式")
            return it.trim()
        }

        // 3. 其他可能的格式
        data.generatedText?.let { 
            Log.d(TAG, "✅ 解析成功 - 直接生成文本")
            return it.trim()
        }
        data.result?.let { 
            Log.d(TAG, "✅ 解析成功 - 结果格式")
            return it.trim()
        }
        data.response?.let { 
            Log.d(TAG, "✅ 解析成功 - 响应格式")
            return it.trim()
        }
        data.content?.let { 
            Log.d(TAG, "✅ 解析成功 - 内容格式")
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
     * 主要的公开方法：获取 AI 回复
     * @param userMessage 用户输入的消息
     * @return AIResult 包含成功状态、回复内容和错误信息
     */
    suspend fun getAIResponse(userMessage: String): AIResult {
        return try {
            Log.d(TAG, "🤖 开始处理用户消息: $userMessage")
            
            if (userMessage.trim().isEmpty()) {
                return AIResult(
                    success = false,
                    response = "",
                    error = "消息不能为空"
                )
            }

            val response = callWatsonAI(userMessage.trim())
            
            Log.d(TAG, "🎉 成功获取 AI 回复")
            AIResult(
                success = true,
                response = response,
                error = null
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ AI 处理失败: ${e.message}")
            AIResult(
                success = false,
                response = "",
                error = e.message ?: "未知错误"
            )
        }
    }

    /**
     * 测试 Watson AI 连接
     */
    suspend fun testConnection(): AIResult {
        return try {
            Log.d(TAG, "🔧 测试 Watson AI 连接...")
            
            val testMessage = "Hello, please introduce yourself briefly."
            val result = getAIResponse(testMessage)
            
            if (result.success) {
                Log.d(TAG, "✅ 连接测试成功")
                AIResult(
                    success = true,
                    response = "连接测试成功！\n\n${result.response}",
                    error = null
                )
            } else {
                Log.e(TAG, "❌ 连接测试失败: ${result.error}")
                result
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 连接测试异常: ${e.message}")
            AIResult(
                success = false,
                response = "",
                error = "连接测试失败: ${e.message}"
            )
        }
    }

    /**
     * 清理缓存的令牌（可选）
     */
    fun clearTokenCache() {
        cachedToken = null
        tokenExpirationTime = 0
        Log.d(TAG, "🧹 Token 缓存已清理")
    }

    /**
     * 检查服务状态
     */
    fun getServiceStatus(): String {
        return when {
            cachedToken != null && System.currentTimeMillis() < tokenExpirationTime -> "已连接"
            cachedToken != null -> "令牌已过期"
            else -> "未连接"
        }
    }
}