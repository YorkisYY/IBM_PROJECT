// app/src/androidTest/java/integration/ContextAIIntegrationTest.kt
package integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import watsonx.ContextManager
import watsonx.WatsonAIEnhanced

/**
 * Context AI Integration Test - 測試上下文管理和AI對話整合
 */
@RunWith(AndroidJUnit4::class)
class ContextAIIntegrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun testContextManagerBasicFunctionality() {
        // 清空歷史記錄
        ContextManager.clearConversationHistory()
        
        // 測試添加對話
        ContextManager.addConversation("Hello", "Hi there!")
        
        // 驗證歷史記錄
        val historySize = ContextManager.getHistorySize()
        assertEquals(2, historySize) // user + assistant
        
        // 測試上下文字符串生成
        val contextStr = ContextManager.getContextString()
        assertTrue(contextStr.contains("Hello"))
        assertTrue(contextStr.contains("Hi there!"))
        
        // 測試摘要
        val summary = ContextManager.getConversationSummary()
        assertTrue(summary.contains("2 total messages"))
    }

    @Test
    fun testContextPromptBuilding() {
        // 清空並添加測試對話
        ContextManager.clearConversationHistory()
        ContextManager.addConversation("What's the weather?", "Let me check the weather for you.")
        
        // 測試功能調用提示構建
        val functionPrompt = ContextManager.buildContextualPrompt("How about today?", isFunction = true)
        assertTrue(functionPrompt.contains("weather"))
        assertTrue(functionPrompt.contains("How about today?"))
        
        // 測試普通對話提示構建
        val normalPrompt = ContextManager.buildContextualPrompt("Hello again", isFunction = false)
        assertTrue(normalPrompt.contains("Hello again"))
        assertTrue(normalPrompt.contains("Previous conversations"))
    }

    @Test
    fun testContextRelevanceDetection() {
        // 清空並添加相關對話
        ContextManager.clearConversationHistory()
        ContextManager.addConversation("Tell me about cats", "Cats are wonderful pets...")
        
        // 測試相關性檢測
        val hasRelevantContext = ContextManager.hasRelevantContext("More about cats")
        assertTrue(hasRelevantContext)
        
        // 測試不相關的查詢
        val hasIrrelevantContext = ContextManager.hasRelevantContext("What time is it")
        assertFalse(hasIrrelevantContext)
    }

    @Test
    fun testContextHistoryLimits() {
        // 清空歷史記錄
        ContextManager.clearConversationHistory()
        
        // 添加大量對話測試限制
        repeat(25) { i ->
            ContextManager.addConversation("Message $i", "Response $i")
        }
        
        // 驗證歷史記錄不超過限制
        val historySize = ContextManager.getHistorySize()
        assertTrue(historySize <= 40) // 最大20對話 = 40條記錄
        
        // 驗證最新的對話仍然存在
        val contextStr = ContextManager.getContextString()
        assertTrue(contextStr.contains("Message 24"))
        assertTrue(contextStr.contains("Response 24"))
    }

    @Test
    fun testWatsonAIEnhancedInitialization() = runBlocking {
        // 測試初始化
        WatsonAIEnhanced.initialize(context)
        
        // 測試服務狀態
        val status = WatsonAIEnhanced.getServiceStatus()
        assertTrue(status.contains("Watson AI Enhanced"))
        
        // 測試對話摘要
        val summary = WatsonAIEnhanced.getConversationSummary()
        assertTrue(summary.contains("Conversation History"))
    }

    @Test
    fun testEnhancedAIResponseWithContext() = runBlocking {
        // 初始化服務
        WatsonAIEnhanced.initialize(context)
        
        // 清空歷史記錄
        WatsonAIEnhanced.clearConversationHistory()
        
        // 測試第一次對話
        val firstResult = WatsonAIEnhanced.getEnhancedAIResponse("Hello, I'm testing the system")
        assertTrue(firstResult.success)
        
        // 測試上下文相關的第二次對話
        val secondResult = WatsonAIEnhanced.getEnhancedAIResponse("Can you remember what I just said?")
        assertTrue(secondResult.success)
        
        // 驗證歷史記錄已建立
        val summary = WatsonAIEnhanced.getConversationSummary()
        assertTrue(summary.contains("4 total messages")) // 2 user + 2 assistant
    }

    @Test
    fun testFunctionCallingWithContext() = runBlocking {
        // 初始化服務
        WatsonAIEnhanced.initialize(context)
        
        // 清空歷史記錄
        WatsonAIEnhanced.clearConversationHistory()
        
        // 先詢問天氣
        val weatherResult = WatsonAIEnhanced.getEnhancedAIResponse("What's the weather like?")
        assertTrue(weatherResult.success)
        
        // 然後詢問相關問題（測試上下文理解）
        val followUpResult = WatsonAIEnhanced.getEnhancedAIResponse("Is it good weather for walking?")
        assertTrue(followUpResult.success)
        
        // 驗證對話歷史
        val summary = WatsonAIEnhanced.getConversationSummary()
        assertTrue(summary.contains("messages"))
    }

    @Test
    fun testServiceIntegration() = runBlocking {
        // 初始化服務
        WatsonAIEnhanced.initialize(context)
        
        // 測試增強服務
        val testResult = WatsonAIEnhanced.testEnhancedService()
        assertTrue(testResult.success)
        assertTrue(testResult.response.contains("Enhanced Service Test Results"))
        
        // 驗證各個服務狀態
        val status = WatsonAIEnhanced.getServiceStatus()
        assertTrue(status.contains("Weather Service"))
        assertTrue(status.contains("SMS Service"))
        assertTrue(status.contains("News Service"))
        assertTrue(status.contains("Podcast Service"))
    }

    @Test
    fun testMultipleServiceCalls() = runBlocking {
        // 初始化服務
        WatsonAIEnhanced.initialize(context)
        
        // 清空歷史記錄
        WatsonAIEnhanced.clearConversationHistory()
        
        // 測試多個不同類型的請求
        val requests = listOf(
            "What's the weather?",
            "Do I have any messages?",
            "Tell me the news",
            "What podcasts do you recommend?"
        )
        
        for (request in requests) {
            val result = WatsonAIEnhanced.getEnhancedAIResponse(request)
            assertTrue("Request '$request' should succeed", result.success)
        }
        
        // 驗證所有對話都記錄在歷史中
        val historySize = ContextManager.getHistorySize()
        assertEquals(8, historySize) // 4 requests * 2 (user + assistant)
    }

    @Test
    fun testContextualSmartDetection() {
        // 清空並設置特定上下文
        ContextManager.clearConversationHistory()
        ContextManager.addConversation("I love cooking", "That's wonderful! Cooking is a great hobby.")
        
        // 測試上下文相關性檢測
        assertTrue(ContextManager.hasRelevantContext("cooking recipes"))
        assertTrue(ContextManager.hasRelevantContext("kitchen tips"))
        
        // 測試不相關的查詢
        assertFalse(ContextManager.hasRelevantContext("space exploration"))
        assertFalse(ContextManager.hasRelevantContext("mathematics"))
    }

    @Test
    fun testErrorHandlingWithContext() = runBlocking {
        // 初始化服務
        WatsonAIEnhanced.initialize(context)
        
        // 測試空消息處理
        val emptyResult = WatsonAIEnhanced.getEnhancedAIResponse("")
        assertFalse(emptyResult.success)
        assertEquals("Message cannot be empty", emptyResult.error)
        
        // 測試非常長的消息
        val longMessage = "Hello ".repeat(1000)
        val longResult = WatsonAIEnhanced.getEnhancedAIResponse(longMessage)
        // 應該能處理長消息，但可能會截斷
        assertTrue(longResult.success)
    }
}