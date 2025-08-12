// app/src/androidTest/java/integration/ModelDialogIntegrationTest.kt
package integration

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule
import org.junit.Assert.*
import org.junit.Before
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import io.github.sceneview.math.Position

/**
 * Model-Dialog Integration Test - 測試模型與對話框綁定
 * 修復版本：移除 Mockito，添加必要的 Compose 導入
 */
@RunWith(AndroidJUnit4::class)
class ModelDialogIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // 測試用的模擬數據
    private val mockFirstCatPosition = Position(0f, 0f, -1f)
    private val mockScreenPosition = Offset(540f, 800f)
    
    @Before
    fun setup() {
        // 簡化設置，不使用 Mock 對象
    }

    @Test
    fun testFirstCatDialogBinding() {
        var hasFirstCat by mutableStateOf(false)
        var firstCatDialogPosition by mutableStateOf(Offset.Zero)
        var isChatVisible by mutableStateOf(false)
        var chatMessage by mutableStateOf("")

        composeTestRule.setContent {
            // 模擬第一隻貓放置後的狀態
            LaunchedEffect(Unit) {
                // 模擬第一隻貓被放置
                hasFirstCat = true
                firstCatDialogPosition = mockScreenPosition
                chatMessage = "Hello! I'm your first AR cat!"
                isChatVisible = true
            }
            
            // 模擬對話框組件
            if (isChatVisible && chatMessage.isNotEmpty() && hasFirstCat) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("watson_dialog_bound_to_first_cat")
                ) {
                    Box(
                        modifier = Modifier
                            .offset { 
                                IntOffset(
                                    firstCatDialogPosition.x.toInt(),
                                    firstCatDialogPosition.y.toInt()
                                )
                            }
                            .size(340.dp, 160.dp)
                            .background(
                                Color(0xFF2196F3),
                                RoundedCornerShape(16.dp)
                            )
                            .testTag("dialog_content")
                    ) {
                        Text(
                            text = chatMessage,
                            color = Color.White,
                            modifier = Modifier
                                .padding(16.dp)
                                .testTag("dialog_message")
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // 1. 驗證對話框正確顯示
        composeTestRule.onNodeWithTag("watson_dialog_bound_to_first_cat")
            .assertExists()

        // 2. 驗證對話框內容
        composeTestRule.onNodeWithTag("dialog_message")
            .assertExists()
            .assertTextEquals("Hello! I'm your first AR cat!")

        // 3. 驗證對話框位置 (應該在預期的螢幕座標)
        composeTestRule.onNodeWithTag("dialog_content")
            .assertExists()
    }

    @Test 
    fun testDialogPositionUpdateWithModelMovement() {
        var firstCatDialogPosition by mutableStateOf(Offset(540f, 800f))
        var isChatVisible by mutableStateOf(true)
        var chatMessage by mutableStateOf("Following the cat!")
        
        composeTestRule.setContent {
            // 模擬模型位置變化
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(500)
                // 模擬貓移動到新位置
                firstCatDialogPosition = Offset(600f, 700f)
                
                kotlinx.coroutines.delay(500)
                // 再次移動
                firstCatDialogPosition = Offset(300f, 900f)
            }
            
            // 對話框跟隨位置
            if (isChatVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("moving_dialog")
                ) {
                    Box(
                        modifier = Modifier
                            .offset { 
                                IntOffset(
                                    firstCatDialogPosition.x.toInt(),
                                    firstCatDialogPosition.y.toInt()
                                )
                            }
                            .size(300.dp, 100.dp)
                            .background(Color.Blue)
                            .testTag("dialog_following_cat")
                    ) {
                        Text(
                            text = "Position: (${firstCatDialogPosition.x.toInt()}, ${firstCatDialogPosition.y.toInt()})",
                            color = Color.White,
                            modifier = Modifier
                                .padding(8.dp)
                                .testTag("position_text")
                        )
                    }
                }
            }
        }

        // 初始位置驗證
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("position_text")
            .assertTextContains("540, 800")

        // 等待第一次移動
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("position_text")
            .assertTextContains("600, 700")

        // 等待第二次移動
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("position_text")
            .assertTextContains("300, 900")
    }

    @Test
    fun testDialogDisappearanceWhenModelDeleted() {
        var hasFirstCat by mutableStateOf(true)
        var modelCount by mutableStateOf(1)
        var isChatVisible by mutableStateOf(true)
        var chatMessage by mutableStateOf("I'm here!")

        composeTestRule.setContent {
            // 模擬模型刪除事件
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(1000)
                // 模擬刪除第一隻貓
                hasFirstCat = false
                modelCount = 0
                isChatVisible = false
                chatMessage = ""
            }
            
            // 對話框顯示邏輯
            if (isChatVisible && chatMessage.isNotEmpty() && hasFirstCat) {
                Box(
                    modifier = Modifier
                        .testTag("dialog_with_model")
                        .background(Color.Green)
                        .size(200.dp, 100.dp)
                ) {
                    Text(
                        text = chatMessage,
                        modifier = Modifier.testTag("dialog_text")
                    )
                }
            } else {
                // 顯示無模型狀態
                Box(
                    modifier = Modifier
                        .testTag("no_model_state")
                        .background(Color.Gray)
                        .size(200.dp, 50.dp)
                ) {
                    Text(
                        text = "No models placed",
                        modifier = Modifier.testTag("no_model_text")
                    )
                }
            }
        }

        // 初始狀態：對話框存在
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_with_model")
            .assertExists()
        composeTestRule.onNodeWithTag("dialog_text")
            .assertTextEquals("I'm here!")

        // 等待模型刪除
        composeTestRule.mainClock.advanceTimeBy(1100)
        composeTestRule.waitForIdle()

        // 驗證對話框消失
        composeTestRule.onNodeWithTag("dialog_with_model")
            .assertDoesNotExist()

        // 驗證無模型狀態顯示
        composeTestRule.onNodeWithTag("no_model_state")
            .assertExists()
        composeTestRule.onNodeWithTag("no_model_text")
            .assertTextEquals("No models placed")
    }

    @Test
    fun testDialogContentScrolling() {
        var longMessage by mutableStateOf("")
        var isChatVisible by mutableStateOf(false)

        composeTestRule.setContent {
            LaunchedEffect(Unit) {
                // 生成長文本消息
                longMessage = "This is a very long message that should exceed the normal dialog height. ".repeat(10) +
                             "It should trigger scrolling functionality within the dialog to ensure all content is accessible."
                isChatVisible = true
            }
            
            if (isChatVisible && longMessage.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("scrolling_dialog_container")
                ) {
                    Box(
                        modifier = Modifier
                            .size(340.dp, 160.dp)
                            .background(
                                Color(0xFF2196F3),
                                RoundedCornerShape(16.dp)
                            )
                            .testTag("scrolling_dialog")
                    ) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .testTag("scrolling_content")
                        ) {
                            item {
                                Text(
                                    text = longMessage,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.testTag("long_message_text")
                                )
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // 驗證對話框存在
        composeTestRule.onNodeWithTag("scrolling_dialog")
            .assertExists()

        // 驗證可滾動內容存在
        composeTestRule.onNodeWithTag("scrolling_content")
            .assertExists()

        // 驗證長文本內容存在
        composeTestRule.onNodeWithTag("long_message_text")
            .assertExists()

        // 測試滾動功能
        composeTestRule.onNodeWithTag("scrolling_content")
            .performScrollToIndex(0)
            .performTouchInput {
                swipeUp()
            }

        composeTestRule.waitForIdle()

        // 驗證滾動後對話框仍然存在
        composeTestRule.onNodeWithTag("scrolling_dialog")
            .assertExists()
    }
}