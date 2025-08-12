// app/src/androidTest/java/integration/ARStateUIIntegrationTest.kt
package integration

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule
import org.junit.Assert.*
import ar.ARSceneViewRenderer
import ar.ARTouchHandler
import ar.PlacementModeManager
import ar.PlacementMode

fun assertContains(actual: String, expected: String) {
    assertTrue("Expected '$actual' to contain '$expected'", actual.contains(expected, ignoreCase = true))
}

/**
 * AR State UI Integration Test - 測試AR狀態與UI的整合
 */
@RunWith(AndroidJUnit4::class)
class ARStateUIIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun testARRendererInitialization() {
        val arRenderer = ARSceneViewRenderer()
        
        // 測試初始狀態
        assertEquals(0, arRenderer.detectedPlanesCount.value)
        assertEquals(0, arRenderer.placedModelsCount.value)
        assertEquals(false, arRenderer.canPlaceObjects.value)
        assertEquals("Initializing...", arRenderer.trackingStatus.value)
    }

    @Test
    fun testARTouchHandlerInitialization() {
        val touchHandler = ARTouchHandler()
        
        // 測試初始狀態
        assertNull(touchHandler.getSelectedNode())
        assertNull(touchHandler.getFirstCatModel())
        assertEquals(0, touchHandler.getPlacedModelsCount())
    }

    @Test
    fun testPlacementModeManager() {
        val placementModeManager = PlacementModeManager(context)
        
        // 測試初始模式
        assertEquals(PlacementMode.PLANE_ONLY, placementModeManager.currentMode.value)
        
        // 測試模式狀態文本
        val statusText = placementModeManager.getModeStatusText()
        assertContains(statusText, "Plane")
        assertContains(statusText, "0 models")
    }

    @Test
    fun testARStateUpdates() {
        val arRenderer = ARSceneViewRenderer()
        
        // 模擬狀態更新
        arRenderer.placedModelsCount.value = 1
        arRenderer.detectedPlanesCount.value = 2
        arRenderer.canPlaceObjects.value = true
        arRenderer.trackingStatus.value = "Ready to Place"
        
        // 驗證狀態更新
        assertEquals(1, arRenderer.placedModelsCount.value)
        assertEquals(2, arRenderer.detectedPlanesCount.value)
        assertTrue(arRenderer.canPlaceObjects.value)
        assertEquals("Ready to Place", arRenderer.trackingStatus.value)
    }

    @Test
    fun testPlacementModeTransitions() {
        val placementModeManager = PlacementModeManager(context)
        val arRenderer = ARSceneViewRenderer()
        val childNodes = mutableListOf<io.github.sceneview.node.Node>()
        
        // 測試模式切換
        val initialMode = placementModeManager.currentMode.value
        
        placementModeManager.switchToNextMode(
            childNodes = childNodes,
            arRenderer = arRenderer
        )
        
        // 驗證模式已改變
        assertNotEquals(initialMode, placementModeManager.currentMode.value)
    }

    @Test
    fun testARDebugInfo() {
        val arRenderer = ARSceneViewRenderer()
        
        // 設置一些測試數據
        arRenderer.detectedPlanesCount.value = 3
        arRenderer.placedModelsCount.value = 2
        arRenderer.trackingStatus.value = "Tracking Normal"
        
        // 獲取調試信息
        val debugInfo = arRenderer.getDebugInfo()
        
        // 驗證調試信息內容
        assertContains(debugInfo, "Planes Detected: 3")
        assertContains(debugInfo, "Models Placed: 2")
        assertContains(debugInfo, "Tracking Normal")
    }

    @Test
    fun testUserFriendlyStatus() {
        val arRenderer = ARSceneViewRenderer()
        
        // 測試不同狀態下的用戶友好提示
        arRenderer.canPlaceObjects.value = false
        var status = arRenderer.getUserFriendlyStatus()
        assertContains(status, "Move device")
        
        arRenderer.canPlaceObjects.value = true
        arRenderer.placedModelsCount.value = 0
        status = arRenderer.getUserFriendlyStatus()
        assertContains(status, "first cat")
        
        arRenderer.placedModelsCount.value = 1
        status = arRenderer.getUserFriendlyStatus()
        assertContains(status, "rotate")
    }

    @Test
    fun testPlacementReadiness() {
        val arRenderer = ARSceneViewRenderer()
        
        // 測試未準備好的狀態
        arRenderer.canPlaceObjects.value = false
        arRenderer.trackingStatus.value = "Initializing..."
        assertFalse(arRenderer.isReadyForPlacement())
        
        // 測試準備好的狀態
        arRenderer.canPlaceObjects.value = true
        arRenderer.trackingStatus.value = "Ready to Place"
        assertTrue(arRenderer.isReadyForPlacement())
    }

    @Test
    fun testModelCountManagement() {
        val arRenderer = ARSceneViewRenderer()
        
        // 測試模型計數
        assertEquals(0, arRenderer.placedModelsCount.value)
        
        arRenderer.incrementModelCount()
        assertEquals(1, arRenderer.placedModelsCount.value)
        
        arRenderer.incrementModelCount()
        assertEquals(2, arRenderer.placedModelsCount.value)
    }

    @Test
    fun testClearAllModels() {
        val arRenderer = ARSceneViewRenderer()
        val touchHandler = ARTouchHandler()
        val childNodes = mutableListOf<io.github.sceneview.node.Node>()
        
        // 設置一些模型數據
        arRenderer.placedModelsCount.value = 3
        
        // 清空所有模型
        touchHandler.clearAllCats(childNodes, arRenderer)
        
        // 驗證模型已清空
        assertEquals(0, arRenderer.placedModelsCount.value)
    }

    @Test
    fun testARSessionStates() {
        val arRenderer = ARSceneViewRenderer()
        
        // 模擬AR會話狀態變化
        arRenderer.trackingStatus.value = "AR Session Created"
        assertEquals("AR Session Created", arRenderer.trackingStatus.value)
        
        arRenderer.trackingStatus.value = "AR Tracking Active"
        assertEquals("AR Tracking Active", arRenderer.trackingStatus.value)
        
        arRenderer.trackingStatus.value = "Tracking Lost"
        assertEquals("Tracking Lost", arRenderer.trackingStatus.value)
    }

    @Test
    fun testPlaneDetectionStates() {
        val arRenderer = ARSceneViewRenderer()
        
        // 測試平面檢測狀態變化
        arRenderer.planeDetectionStatus.value = "No planes detected"
        arRenderer.detectedPlanesCount.value = 0
        
        assertEquals("No planes detected", arRenderer.planeDetectionStatus.value)
        assertEquals(0, arRenderer.detectedPlanesCount.value)
        
        // 模擬檢測到平面
        arRenderer.planeDetectionStatus.value = "3 planes detected"
        arRenderer.detectedPlanesCount.value = 3
        
        assertEquals("3 planes detected", arRenderer.planeDetectionStatus.value)
        assertEquals(3, arRenderer.detectedPlanesCount.value)
    }

    @Test
    fun testModeSpecificBehavior() {
        val placementModeManager = PlacementModeManager(context)
        val arRenderer = ARSceneViewRenderer()
        val childNodes = mutableListOf<io.github.sceneview.node.Node>()
        
        // 測試每種模式的行為
        for (mode in PlacementMode.values()) {
            placementModeManager.setMode(
                mode = mode,
                childNodes = childNodes,
                arRenderer = arRenderer,
                clearModels = false
            )
            
            assertEquals(mode, placementModeManager.currentMode.value)
            
            val statusText = placementModeManager.getModeStatusText()
            assertContains(statusText, mode.displayName)
        }
    }

    @Test
    fun testClearPlaneData() {
        val placementModeManager = PlacementModeManager(context)
        var planeDataCleared = false
        
        // 測試清除平面數據
        placementModeManager.clearPlaneData {
            planeDataCleared = true
        }
        
        // 驗證回調被調用
        assertTrue(planeDataCleared)
    }

    @Test
    fun testCompleteARWorkflow() {
        val arRenderer = ARSceneViewRenderer()
        val touchHandler = ARTouchHandler()
        val placementModeManager = PlacementModeManager(context)
        val childNodes = mutableListOf<io.github.sceneview.node.Node>()
        
        // 1. 初始化狀態
        assertEquals(PlacementMode.PLANE_ONLY, placementModeManager.currentMode.value)
        assertEquals(0, arRenderer.placedModelsCount.value)
        
        // 2. 模擬AR會話啟動
        arRenderer.trackingStatus.value = "AR Tracking Active"
        arRenderer.canPlaceObjects.value = true
        
        // 3. 模擬放置模型
        arRenderer.incrementModelCount()
        
        // 4. 切換模式
        placementModeManager.switchToNextMode(childNodes, arRenderer)
        
        // 5. 驗證狀態
        assertTrue(arRenderer.canPlaceObjects.value)
        assertEquals("AR Tracking Active", arRenderer.trackingStatus.value)
    }

    @Test
    fun testTouchHandlerConfiguration() {
        val touchHandler = ARTouchHandler()
        
        // 測試碰撞檢測配置
        touchHandler.configureCollisionDetection(0.5f, 0.4f)
        
        // 測試靈敏度重置
        touchHandler.resetSensitivityToDefault()
        
        // 測試調試信息
        touchHandler.debugCollisionDetection()
        
        // 這些方法應該正常執行而不拋出異常
        assertTrue(true)
    }
}