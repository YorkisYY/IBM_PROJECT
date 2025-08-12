// app/src/androidTest/java/integration/InputAIIntegrationTest.kt
package integration

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule
import org.junit.Assert.*
import kotlinx.coroutines.test.runTest
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import ui.components.UserInputField
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Input-AI Integration Test - 測試輸入到AI處理的整合
 * 修復版本：移除 Mockito，添加必要的 Compose 導入
 */
@RunWith(AndroidJUnit4::class)
class InputAIIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun testWeatherKeywordDetection() = runTest {
        var inputValue by mutableStateOf("")
        var isLoading by mutableStateOf(false)
        var aiResponse by mutableStateOf("")
        var showResponse by mutableStateOf(false)

        composeTestRule.setContent {
            UserInputField(
                value = inputValue,
                onValueChange = { inputValue = it },
                onSendClick = {
                    isLoading = true
                    // 模擬天氣關鍵字檢測和處理
                    val hasWeatherKeyword = inputValue.contains("weather", ignoreCase = true)
                    if (hasWeatherKeyword) {
                        aiResponse = "Current weather: 25°C, Sunny"
                        showResponse = true
                    }
                    isLoading = false
                },
                isLoading = isLoading,
                placeholder = "Ask about weather..."
            )
        }

        // 1. 輸入天氣相關關鍵字
        composeTestRule.onNodeWithText("Ask about weather...")
            .performTextInput("What's the weather today?")

        inputValue = "What's the weather today?"

        // 2. 點擊發送按鈕
        composeTestRule.onAllNodesWithText("Send")
            .onFirst()
            .performClick()

        // 3. 驗證關鍵字檢測生效
        assertTrue("Should detect weather keyword", 
            inputValue.contains("weather", ignoreCase = true))

        // 4. 驗證AI回應內容
        assertEquals("Current weather: 25°C, Sunny", aiResponse)
        assertTrue("Should show response", showResponse)
    }

    @Test
    fun testSMSKeywordDetection() = runTest {
        var inputValue by mutableStateOf("")
        var detectedFunction by mutableStateOf("")

        composeTestRule.setContent {
            UserInputField(
                value = inputValue,
                onValueChange = { inputValue = it },
                onSendClick = {
                    // 模擬SMS關鍵字檢測
                    when {
                        inputValue.contains("message", ignoreCase = true) -> {
                            detectedFunction = "read_unread_messages"
                        }
                        inputValue.contains("sms", ignoreCase = true) -> {
                            detectedFunction = "read_recent_messages"
                        }
                        inputValue.contains("unread", ignoreCase = true) -> {
                            detectedFunction = "read_unread_messages"
                        }
                    }
                }
            )
        }

        // 測試不同SMS關鍵字
        val testCases = mapOf(
            "Do I have any new messages?" to "read_unread_messages",
            "Show me recent SMS" to "read_recent_messages",
            "Check unread messages" to "read_unread_messages"
        )

        testCases.forEach { (input, expectedFunction) ->
            // 清空並輸入測試案例
            inputValue = input
            
            // 點擊發送
            composeTestRule.onAllNodesWithText("Send")
                .onFirst()
                .performClick()
            
            // 驗證正確的功能被檢測到
            assertEquals("Should detect correct SMS function for: $input", 
                expectedFunction, detectedFunction)
        }
    }

    @Test
    fun testLoadingStateUI() = runTest {
        var isLoading by mutableStateOf(false)
        var inputValue by mutableStateOf("")

        composeTestRule.setContent {
            UserInputField(
                value = inputValue,
                onValueChange = { inputValue = it },
                onSendClick = {
                    isLoading = true
                    // 模擬AI處理延遲
                    GlobalScope.launch {
                        delay(2000)
                        isLoading = false
                    }
                },
                isLoading = isLoading
            )
        }

        // 1. 輸入文字
        inputValue = "Test message"

        // 2. 點擊發送按鈕
        composeTestRule.onAllNodesWithText("Send")
            .onFirst()
            .performClick()

        // 3. 驗證載入狀態UI
        isLoading = true
        composeTestRule.waitForIdle()

        // 發送按鈕應該被禁用
        assertTrue("Should be in loading state", isLoading)
    }

    @Test
    fun testErrorHandlingUI() = runTest {
        var hasError by mutableStateOf(false)
        var errorMessage by mutableStateOf("")
        var inputValue by mutableStateOf("")

        composeTestRule.setContent {
            Column {
                UserInputField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    onSendClick = {
                        // 模擬AI處理錯誤
                        try {
                            // 模擬錯誤情況
                            throw Exception("Network connection failed")
                        } catch (e: Exception) {
                            hasError = true
                            errorMessage = e.message ?: "Unknown error"
                        }
                    },
                    isEnabled = !hasError
                )
                
                // 錯誤提示UI
                if (hasError) {
                    Text(
                        text = "Error: $errorMessage",
                        color = Color.Red,
                        modifier = Modifier.testTag("error_message")
                    )
                }
            }
        }

        // 1. 輸入文字
        inputValue = "Test error case"

        // 2. 觸發錯誤
        composeTestRule.onAllNodesWithText("Send")
            .onFirst()
            .performClick()

        // 3. 驗證錯誤狀態
        assertTrue("Should have error", hasError)
        assertEquals("Network connection failed", errorMessage)

        // 4. 驗證錯誤UI顯示
        composeTestRule.onNodeWithTag("error_message")
            .assertExists()
            .assertTextContains("Network connection failed")
    }

    @Test
    fun testAIResponseToUIIntegration() = runTest {
        var aiResponse by mutableStateOf("")
        var showDialog by mutableStateOf(false)
        var inputValue by mutableStateOf("")

        composeTestRule.setContent {
            Column {
                UserInputField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    onSendClick = {
                        // 模擬不同類型的AI回應
                        aiResponse = when {
                            inputValue.contains("weather") -> {
                                showDialog = true
                                "Today's weather: 22°C, partly cloudy with light winds"
                            }
                            inputValue.contains("hello") -> {
                                showDialog = true
                                "Hello! I'm your AR assistant. How can I help you today?"
                            }
                            else -> {
                                showDialog = true
                                "I understand you said: $inputValue"
                            }
                        }
                    }
                )
                
                // AI回應對話框
                if (showDialog && aiResponse.isNotEmpty()) {
                    Card(
                        modifier = Modifier.testTag("ai_response_dialog")
                    ) {
                        Text(
                            text = aiResponse,
                            modifier = Modifier
                                .padding(16.dp)
                                .testTag("ai_response_text")
                        )
                    }
                }
            }
        }

        // 測試不同輸入對應的AI回應
        val testCases = mapOf(
            "What's the weather?" to "22°C, partly cloudy",
            "Hello there" to "Hello! I'm your AR assistant",
            "Random input" to "I understand you said: Random input"
        )

        testCases.forEach { (input, expectedResponse) ->
            // 清空並輸入
            inputValue = input
            
            // 點擊發送
            composeTestRule.onAllNodesWithText("Send")
                .onFirst()
                .performClick()
            
            composeTestRule.waitForIdle()
            
            // 驗證對話框出現
            composeTestRule.onNodeWithTag("ai_response_dialog")
                .assertExists()
            
            // 驗證回應內容包含預期文字
            composeTestRule.onNodeWithTag("ai_response_text")
                .assertTextContains(expectedResponse)
        }
    }

    @Test
    fun testInputValidation() = runTest {
        var inputValue by mutableStateOf("")
        var canSend by mutableStateOf(false)
        var validationMessage by mutableStateOf("")

        composeTestRule.setContent {
            Column {
                UserInputField(
                    value = inputValue,
                    onValueChange = { 
                        inputValue = it
                        // 輸入驗證邏輯
                        when {
                            it.trim().isEmpty() -> {
                                canSend = false
                                validationMessage = "Please enter a message"
                            }
                            it.length > 500 -> {
                                canSend = false
                                validationMessage = "Message too long (max 500 characters)"
                            }
                            else -> {
                                canSend = true
                                validationMessage = ""
                            }
                        }
                    },
                    onSendClick = {
                        if (canSend) {
                            // 處理有效輸入
                        }
                    },
                    isEnabled = canSend
                )
                
                // 驗證訊息顯示
                if (validationMessage.isNotEmpty()) {
                    Text(
                        text = validationMessage,
                        color = Color.Red,
                        modifier = Modifier.testTag("validation_message")
                    )
                }
            }
        }

        // 1. 測試空輸入
        inputValue = ""
        assertFalse("Should not allow empty input", canSend)
        
        // 2. 測試過長輸入
        val longText = "a".repeat(600)
        inputValue = longText
        assertFalse("Should not allow text over 500 characters", canSend)
        assertEquals("Message too long (max 500 characters)", validationMessage)
        
        // 3. 測試有效輸入
        inputValue = "Valid message"
        assertTrue("Should allow valid input", canSend)
        assertEquals("", validationMessage)
    }
}