// app/src/androidTest/java/flows/ConversationFlowTest.kt
package flows

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule
import org.junit.Assert.*
import watsonx.WatsonAIEnhanced
import watsonx.ContextManager

fun assertContains(actual: String, expected: String) {
    assertTrue("Expected '$actual' to contain '$expected'", actual.contains(expected, ignoreCase = true))
}

/**
 * Conversation Flow Test - 測試對話流程
 */
@RunWith(AndroidJUnit4::class)
class ConversationFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun testBasicConversationFlow() = runBlocking {
        // 初始化AI服務
        WatsonAIEnhanced.initialize(context)
        WatsonAIEnhanced.clearConversationHistory()
        
        // 測試基本對話
        val result1 = WatsonAIEnhanced.getEnhancedAIResponse("Hello")
        assertTrue(result1.success)
        assertTrue(result1.response.isNotEmpty())
        
        // 測試後續對話
        val result2 = WatsonAIEnhanced.getEnhancedAIResponse("How are you?")
        assertTrue(result2.success)
        assertTrue(result2.response.isNotEmpty())
        
        // 驗證對話歷史
        val summary = WatsonAIEnhanced.getConversationSummary()
        assertContains(summary, "4 total messages")
    }

    @Test
    fun testWeatherQueryFlow() = runBlocking {
        WatsonAIEnhanced.initialize(context)
        WatsonAIEnhanced.clearConversationHistory()
        
        // 測試天氣查詢
        val weatherResult = WatsonAIEnhanced.getEnhancedAIResponse("What's the weather like?")
        assertTrue(weatherResult.success)
        
        // 後續相關問題
        val followUp = WatsonAIEnhanced.getEnhancedAIResponse("Is it good for outdoor activities?")
        assertTrue(followUp.success)
        
        // 驗證上下文連續性
        val historySize = ContextManager.getHistorySize()
        assertTrue(historySize >= 4)
    }

    @Test
    fun testFunctionCallingFlow() = runBlocking {
        WatsonAIEnhanced.initialize(context)
        WatsonAIEnhanced.clearConversationHistory()
        
        // 測試需要函數調用的查詢
        val queries = listOf(
            "What's the weather?",
            "Do I have messages?",
            "Tell me the news",
            "What podcasts do you recommend?"
        )
        
        for (query in queries) {
            val result = WatsonAIEnhanced.getEnhancedAIResponse(query)
            assertTrue("Query '$query' should succeed", result.success)
            assertTrue("Response should not be empty", result.response.isNotEmpty())
        }
    }

    @Test
    fun testContextualConversation() = runBlocking {
        WatsonAIEnhanced.initialize(context)
        WatsonAIEnhanced.clearConversationHistory()
        
        // 建立初始上下文
        val setup = WatsonAIEnhanced.getEnhancedAIResponse("I'm planning a trip to London")
        assertTrue(setup.success)
        
        // 相關的後續問題
        val weather = WatsonAIEnhanced.getEnhancedAIResponse("What's the weather like there?")
        assertTrue(weather.success)
        
        val recommendations = WatsonAIEnhanced.getEnhancedAIResponse("Any travel recommendations?")
        assertTrue(recommendations.success)
        
        // 驗證上下文理解
        val hasContext = ContextManager.hasRelevantContext("London travel")
        assertTrue(hasContext)
    }

    @Test
    fun testErrorHandlingFlow() = runBlocking {
        WatsonAIEnhanced.initialize(context)
        
        // 測試空消息處理
        val emptyResult = WatsonAIEnhanced.getEnhancedAIResponse("")
        assertFalse(emptyResult.success)
        assertNotNull(emptyResult.error)
        
        // 測試正常消息恢復
        val normalResult = WatsonAIEnhanced.getEnhancedAIResponse("Hello")
        assertTrue(normalResult.success)
    }

    @Test
    fun testMultiServiceFlow() = runBlocking {
        WatsonAIEnhanced.initialize(context)
        WatsonAIEnhanced.clearConversationHistory()
        
        // 測試不同服務的連續調用
        val weather = WatsonAIEnhanced.getEnhancedAIResponse("How's the weather?")
        assertTrue(weather.success)
        
        val news = WatsonAIEnhanced.getEnhancedAIResponse("What's in the news?")
        assertTrue(news.success)
        
        val location = WatsonAIEnhanced.getEnhancedAIResponse("Where am I?")
        assertTrue(location.success)
        
        // 驗證所有對話都被記錄
        val historySize = ContextManager.getHistorySize()
        assertEquals(6, historySize) // 3 queries * 2 (user + assistant)
    }

    @Test
    fun testLongConversationFlow() = runBlocking {
        WatsonAIEnhanced.initialize(context)
        WatsonAIEnhanced.clearConversationHistory()
        
        // 模擬長對話
        repeat(10) { i ->
            val result = WatsonAIEnhanced.getEnhancedAIResponse("Message number $i")
            assertTrue("Message $i should succeed", result.success)
        }
        
        // 驗證歷史記錄管理
        val historySize = ContextManager.getHistorySize()
        assertTrue("History should be managed", historySize <= 40) // 最大限制
        
        // 驗證最新消息仍然存在
        val contextStr = ContextManager.getContextString()
        assertContains(contextStr, "Message number 9")
    }

    @Test
    fun testConversationReset() = runBlocking {
        WatsonAIEnhanced.initialize(context)
        
        // 建立一些對話歷史
        WatsonAIEnhanced.getEnhancedAIResponse("Hello")
        WatsonAIEnhanced.getEnhancedAIResponse("How are you?")
        
        // 確認有歷史記錄
        var historySize = ContextManager.getHistorySize()
        assertTrue(historySize > 0)
        
        // 清空歷史記錄
        WatsonAIEnhanced.clearConversationHistory()
        
        // 驗證歷史記錄已清空
        historySize = ContextManager.getHistorySize()
        assertEquals(0, historySize)
        
        // 測試清空後可以正常對話
        val result = WatsonAIEnhanced.getEnhancedAIResponse("New conversation")
        assertTrue(result.success)
    }

    @Test
    fun testSpecificFunctionTriggers() = runBlocking {
        WatsonAIEnhanced.initialize(context)
        WatsonAIEnhanced.clearConversationHistory()
        
        // 測試特定關鍵詞觸發功能調用
        val weatherTriggers = listOf(
            "weather",
            "temperature",
            "How hot is it?",
            "Is it raining?"
        )
        
        for (trigger in weatherTriggers) {
            val result = WatsonAIEnhanced.getEnhancedAIResponse(trigger)
            assertTrue("Weather trigger '$trigger' should work", result.success)
        }
        
        // 測試SMS觸發器
        val smsTriggers = listOf(
            "messages",
            "Do I have SMS?",
            "Check my texts"
        )
        
        for (trigger in smsTriggers) {
            val result = WatsonAIEnhanced.getEnhancedAIResponse(trigger)
            assertTrue("SMS trigger '$trigger' should work", result.success)
        }
    }

    @Test
    fun testConversationPersistence() = runBlocking {
        WatsonAIEnhanced.initialize(context)
        WatsonAIEnhanced.clearConversationHistory()
        
        // 建立對話
        val message1 = "I like pizza"
        val result1 = WatsonAIEnhanced.getEnhancedAIResponse(message1)
        assertTrue(result1.success)
        
        // 測試上下文持久性
        val message2 = "What did I just say I like?"
        val result2 = WatsonAIEnhanced.getEnhancedAIResponse(message2)
        assertTrue(result2.success)
        
        // 驗證AI能記住之前的對話
        // (這個測試可能需要實際的AI響應來驗證)
        assertTrue(result2.response.isNotEmpty())
    }
}