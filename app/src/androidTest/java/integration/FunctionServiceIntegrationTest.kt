// app/src/androidTest/java/integration/FunctionServiceIntegrationTest.kt
package integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Before
import kotlinx.coroutines.test.runTest
import functions.WeatherFunctions
import functions.SMSFunctions
import functions.NewsFunctions
import functions.PodcastFunctions
import functions.LocationFunctions
import watsonx.FunctionCallManager
import watsonx.PromptManager

/**
 * Function Service Integration Test - 測試功能服務協調
 * 修復版本：移除 Mockito 依賴，使用真實服務測試
 */
@RunWith(AndroidJUnit4::class)
class FunctionServiceIntegrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var functionCallManager: FunctionCallManager
    private lateinit var promptManager: PromptManager

    @Before
    fun setup() {
        functionCallManager = FunctionCallManager
        promptManager = PromptManager()
        
        // 初始化所有功能服務（使用真實服務進行整合測試）
        WeatherFunctions.initialize(context)
        SMSFunctions.initialize(context)
        NewsFunctions.initialize(context)
        PodcastFunctions.initialize(context)
        LocationFunctions.initialize(context)
    }

    @Test
    fun testWeatherFunctionDetectionAndRouting() = runTest {
        val weatherQueries = listOf(
            "What's the weather today?",
            "How's the temperature outside?", 
            "Is it going to rain?",
            "Weather in London",
            "Tell me about the wind speed"
        )

        weatherQueries.forEach { query ->
            // 1. 測試關鍵字檢測
            val needsFunction = functionCallManager.mightNeedFunctionCall(query)
            assertTrue("Should detect weather function need for: $query", needsFunction)

            // 2. 測試功能類型判斷
            val functionName = if (query.contains("London")) {
                "get_weather_by_city"
            } else {
                "get_current_weather"
            }
            
            val functionType = functionCallManager.getFunctionType(functionName)
            assertEquals("Should identify as weather function", "weather", functionType)

            // 3. 測試路由邏輯（直接創建測試對象）
            val functionCall = FunctionCallManager.FunctionCall(
                name = functionName,
                arguments = if (query.contains("London")) "{\"city\":\"London\"}" else "{}"
            )
            
            // 驗證功能調用結構
            assertNotNull("Function call should be created", functionCall)
            assertEquals("Function name should match", functionName, functionCall.name)
        }
    }

    @Test
    fun testSMSFunctionDetectionAndRouting() = runTest {
        val smsQueries = listOf(
            "Do I have any new messages?",
            "Show me unread SMS",
            "Check my recent messages",
            "Read the latest message",
            "Message summary please"
        )

        val expectedFunctions = listOf(
            "read_unread_messages",
            "read_unread_messages", 
            "read_recent_messages",
            "get_latest_message",
            "get_message_summary"
        )

        smsQueries.forEachIndexed { index, query ->
            // 1. 測試關鍵字檢測
            val needsFunction = functionCallManager.mightNeedFunctionCall(query)
            assertTrue("Should detect SMS function need for: $query", needsFunction)

            // 2. 測試功能路由
            val expectedFunction = expectedFunctions[index]
            val functionType = functionCallManager.getFunctionType(expectedFunction)
            assertEquals("Should identify as SMS function", "SMS", functionType)

            // 3. 驗證功能調用參數
            val functionCall = FunctionCallManager.FunctionCall(
                name = expectedFunction,
                arguments = if (expectedFunction == "read_recent_messages") "{\"limit\":10}" else "{}"
            )
            
            assertNotNull("Function call should be created", functionCall)
            assertEquals("Function name should match expected", expectedFunction, functionCall.name)
        }
    }

    @Test
    fun testLocationFunctionDetectionAndRouting() = runTest {
        val locationQueries = listOf(
            "Where am I?",
            "What's my current location?",
            "Where do I live?",
            "Tell me my address",
            "My location info please"
        )

        locationQueries.forEach { query ->
            // 1. 測試關鍵字檢測
            val needsFunction = functionCallManager.mightNeedFunctionCall(query)
            assertTrue("Should detect location function need for: $query", needsFunction)

            // 2. 測試功能類型
            val functionName = when {
                query.contains("where am i", ignoreCase = true) -> "get_current_location"
                query.contains("current location") -> "get_current_location"
                query.contains("where do i live") -> "get_user_location"
                query.contains("location info") -> "get_location_info"
                else -> "get_current_location"
            }
            
            val functionType = functionCallManager.getFunctionType(functionName)
            assertEquals("Should identify as location function", "location", functionType)
        }
    }

    @Test
    fun testErrorHandlingAcrossServices() = runTest {
        val errorScenarios = mapOf(
            "weather" to "get_current_weather",
            "SMS" to "read_unread_messages", 
            "location" to "get_current_location",
            "news" to "get_latest_news",
            "podcast" to "get_recommended_podcasts"
        )

        errorScenarios.forEach { (serviceType, functionName) ->
            // 1. 測試各服務的錯誤處理一致性
            try {
                val result = when (serviceType) {
                    "weather" -> WeatherFunctions.execute(functionName, "{}")
                    "SMS" -> SMSFunctions.execute(functionName, "{}")
                    "location" -> LocationFunctions.execute(functionName, "{}")
                    "news" -> NewsFunctions.execute(functionName, "{}")
                    "podcast" -> PodcastFunctions.execute(functionName, "{}")
                    else -> "Unknown service"
                }
                
                // 即使出錯，也應該返回有意義的錯誤消息而不是異常
                assertNotNull("Should return error message instead of null", result)
                assertTrue("Error message should not be empty", result.isNotEmpty())
                
            } catch (e: Exception) {
                // 如果拋出異常，驗證是否為預期的異常類型
                assertTrue("Exception should contain service information", 
                    e.message?.contains(serviceType, ignoreCase = true) == true)
            }

            // 2. 測試統一錯誤回應格式
            val fallbackResponse = functionCallManager.generateFallbackResponse(
                serviceType, 
                "Error: Service temporarily unavailable"
            )
            
            assertTrue("Fallback should contain helpful message", 
                fallbackResponse.contains("Sorry") || fallbackResponse.contains("Error"))
        }
    }

    @Test
    fun testServiceStatusAndHealthChecks() = runTest {
        // 1. 測試各服務狀態檢查
        val weatherStatus = WeatherFunctions.getServiceStatus()
        val smsStatus = SMSFunctions.getServiceStatus()
        val newsStatus = NewsFunctions.getServiceStatus()
        val podcastStatus = PodcastFunctions.getServiceStatus()

        // 2. 驗證狀態報告格式一致性
        listOf(weatherStatus, smsStatus, newsStatus, podcastStatus).forEach { status ->
            assertNotNull("Service status should not be null", status)
            assertTrue("Status should contain service info", status.isNotEmpty())
        }

        // 3. 測試服務測試功能
        try {
            val weatherTest = WeatherFunctions.testWeatherService()
            assertNotNull("Weather test should return result", weatherTest)
            assertTrue("Weather test should indicate status", weatherTest.contains("test"))
        } catch (e: Exception) {
            // 允許測試在無網路環境下失敗，但應該有合理的錯誤信息
            assertTrue("Test failure should have meaningful message", 
                e.message?.isNotEmpty() == true)
        }
    }

    @Test
    fun testFunctionCallManagerIntegration() = runTest {
        // 1. 測試FunctionCallManager與各服務的整合
        val testCases = mapOf(
            "get_current_weather" to "{}",
            "read_unread_messages" to "{}",
            "get_current_location" to "{}",
            "get_latest_news" to "{\"limit\":5}",
            "get_recommended_podcasts" to "{}"
        )

        testCases.forEach { (functionName, arguments) ->
            // 2. 測試功能調用創建
            val functionCall = FunctionCallManager.FunctionCall(functionName, arguments)
            
            assertNotNull("Function call should be created", functionCall)
            assertEquals("Function name should match", functionName, functionCall.name)
            assertEquals("Arguments should match", arguments, functionCall.arguments)

            // 3. 測試功能類型識別
            val functionType = functionCallManager.getFunctionType(functionName)
            assertNotNull("Function type should be identified", functionType)
            assertTrue("Function type should not be empty", functionType.isNotEmpty())

            // 4. 測試錯誤回應生成
            val fallbackResponse = functionCallManager.generateFallbackResponse(
                functionType, 
                "Mock successful result"
            )
            
            assertNotNull("Fallback response should be generated", fallbackResponse)
            assertTrue("Fallback should contain mock result", 
                fallbackResponse.contains("Mock successful result"))
        }
    }

    @Test
    fun testPromptManagerFunctionDetection() = runTest {
        // 1. 測試PromptManager的關鍵字檢測能力
        val testQueries = mapOf(
            "What's the weather like?" to true,
            "Do I have any messages?" to true,
            "Where am I located?" to true,
            "Show me latest news" to true,
            "Recommend some podcasts" to true,
            "Hello, how are you?" to false,
            "What is 2 + 2?" to false,
            "Tell me a joke" to false
        )

        testQueries.forEach { (query, shouldDetect) ->
            val detected = promptManager.mightNeedFunctionCall(query)
            assertEquals("Detection should match expected for: $query", shouldDetect, detected)
        }

        // 2. 測試提示生成
        val functionalPrompt = promptManager.buildFunctionCallingPrompt(
            "What's the weather?", 
            "No previous context"
        )
        
        assertNotNull("Function calling prompt should be generated", functionalPrompt)
        assertTrue("Prompt should contain weather function info", 
            functionalPrompt.contains("get_current_weather"))

        val normalPrompt = promptManager.buildNormalPrompt(
            "Hello there", 
            "No context"
        )
        
        assertNotNull("Normal prompt should be generated", normalPrompt)
        assertTrue("Normal prompt should be conversational", 
            normalPrompt.contains("friendly") || normalPrompt.contains("Assistant"))
    }
}