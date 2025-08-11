// WatsonAIService.kt - Place under watsonx package
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
 * Watson AI Service - Independent file for MainActivity to call
 */
object WatsonAIService {
    private const val TAG = "WatsonAIService"

    // Watson AI Configuration - Based on your React code
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

    // Cached access token
    private var cachedToken: String? = null
    private var tokenExpirationTime: Long = 0

    /**
     * Data class definitions
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
     * AI response result class
     */
    data class AIResult(
        val success: Boolean,
        val response: String,
        val error: String? = null
    )

    /**
     * Get IAM Token - with caching mechanism
     */
    private suspend fun getIAMToken(): String = withContext(Dispatchers.IO) {
        // Check if cached token is valid (expire 5 minutes early)
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

            // Cache token
            cachedToken = tokenResponse.accessToken
            tokenExpirationTime = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)

            Log.d(TAG, "IAM Token obtained successfully")
            return@withContext tokenResponse.accessToken

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get IAM Token: ${e.message}")
            throw e
        }
    }

    /**
     * Call Watson AI API
     */
    private suspend fun callWatsonAI(userMessage: String): String = withContext(Dispatchers.IO) {
        val token = getIAMToken()
        val url = "${config.baseUrl}/ml/v4/deployments/${config.deploymentId}/ai_service?version=2021-05-01"

        // Build request body - consistent with your React code format
        val requestBody = ChatRequest(
            messages = listOf(
                ChatMessage(
                    content = userMessage,
                    role = "user"
                )
            )
        )

        Log.d(TAG, "Sending request to Watson AI")
        Log.d(TAG, "Request content: ${json.encodeToString(requestBody)}")

        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(requestBody).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            Log.d(TAG, "Response status: ${response.code}")

            if (!response.isSuccessful) {
                val errorText = response.body?.string() ?: ""
                Log.e(TAG, "API error: $errorText")
                throw IOException("Watson AI API Error: ${response.code} - $errorText")
            }

            val responseBody = response.body?.string()
            Log.d(TAG, "Received response: ${responseBody?.take(200)}...")

            val data = json.decodeFromString<WatsonResponse>(responseBody!!)
            return@withContext parseResponse(data)

        } catch (e: Exception) {
            Log.e(TAG, "Watson AI call failed: ${e.message}")
            throw e
        }
    }

    /**
     * Parse Watson AI response - based on your React code logic
     */
    private fun parseResponse(data: WatsonResponse): String {
        Log.d(TAG, "Parsing response data...")

        // Try various possible response formats
        // 1. Chat response format
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

        // 2. Generative AI response
        data.results?.firstOrNull()?.generatedText?.let { 
            Log.d(TAG, "Parse successful - generation format")
            return it.trim()
        }

        // 3. Other possible formats
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

        Log.e(TAG, "Unable to parse response format")
        throw IOException("Unable to parse Watson AI response format")
    }

    /**
     * Main public method: Get AI response
     * @param userMessage User input message
     * @return AIResult containing success status, response content and error information
     */
    suspend fun getAIResponse(userMessage: String): AIResult {
        return try {
            Log.d(TAG, "Starting to process user message: $userMessage")
            
            if (userMessage.trim().isEmpty()) {
                return AIResult(
                    success = false,
                    response = "",
                    error = "Message cannot be empty"
                )
            }

            val response = callWatsonAI(userMessage.trim())
            
            Log.d(TAG, "Successfully obtained AI response")
            AIResult(
                success = true,
                response = response,
                error = null
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "AI processing failed: ${e.message}")
            AIResult(
                success = false,
                response = "",
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Test Watson AI connection
     */
    suspend fun testConnection(): AIResult {
        return try {
            Log.d(TAG, "Testing Watson AI connection...")
            
            val testMessage = "Hello, please introduce yourself briefly."
            val result = getAIResponse(testMessage)
            
            if (result.success) {
                Log.d(TAG, "Connection test successful")
                AIResult(
                    success = true,
                    response = "Connection test successful!\n\n${result.response}",
                    error = null
                )
            } else {
                Log.e(TAG, "Connection test failed: ${result.error}")
                result
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Connection test exception: ${e.message}")
            AIResult(
                success = false,
                response = "",
                error = "Connection test failed: ${e.message}"
            )
        }
    }

    /**
     * Clear cached token (optional)
     */
    fun clearTokenCache() {
        cachedToken = null
        tokenExpirationTime = 0
        Log.d(TAG, "Token cache cleared")
    }

    /**
     * Check service status
     */
    fun getServiceStatus(): String {
        return when {
            cachedToken != null && System.currentTimeMillis() < tokenExpirationTime -> "Connected"
            cachedToken != null -> "Token expired"
            else -> "Not connected"
        }
    }
}