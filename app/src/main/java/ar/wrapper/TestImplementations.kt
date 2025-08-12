package ar.wrapper

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import android.view.MotionEvent
import com.google.ar.core.*
import io.github.sceneview.collision.HitResult
import io.github.sceneview.collision.CollisionSystem
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node

/**
 * 測試用假 AR 會話管理器 - 完全避開 ARCore 原生調用
 * 模擬你的 Compose 狀態管理，但不觸碰任何原生代碼
 */
class FakeARSessionManager : ARSessionManager {
    
    // 模擬你的 Compose 狀態 - 完全相同的類型
    private val _detectedPlanesCount = mutableStateOf(0)
    private val _placedModelsCount = mutableStateOf(0)
    private val _trackingStatus = mutableStateOf("測試模式就緒")
    private val _planeDetectionStatus = mutableStateOf("測試平面檢測啟動")
    private val _canPlaceObjects = mutableStateOf(true)
    
    override val detectedPlanesCount: State<Int> = _detectedPlanesCount
    override val placedModelsCount: State<Int> = _placedModelsCount
    override val trackingStatus: State<String> = _trackingStatus
    override val planeDetectionStatus: State<String> = _planeDetectionStatus
    override val canPlaceObjects: State<Boolean> = _canPlaceObjects
    
    // 假會話管理 - 不調用任何 ARCore API，但模擬行為
    override fun configureSession(arSession: Session, config: Config) {
        // 測試模式：記錄調用但不執行 ARCore 邏輯
        _trackingStatus.value = "測試會話已配置"
        println("測試日誌: configureSession 被調用")
    }
    
    override fun onSessionCreated(arSession: Session) {
        _trackingStatus.value = "測試會話已創建"
        println("測試日誌: onSessionCreated 被調用")
    }
    
    override fun onSessionResumed(arSession: Session) {
        _trackingStatus.value = "測試會話已恢復"
        _canPlaceObjects.value = true
    }
    
    override fun onSessionPaused(arSession: Session) {
        _trackingStatus.value = "測試會話已暫停"
        _canPlaceObjects.value = false
    }
    
    override fun onSessionFailed(exception: Exception) {
        _trackingStatus.value = "測試會話失敗: ${exception.message}"
        _canPlaceObjects.value = false
    }
    
    override fun onSessionUpdated(arSession: Session, updatedFrame: Frame) {
        // 模擬平面檢測進度
        if (_detectedPlanesCount.value < 5) {
            _detectedPlanesCount.value++
            _planeDetectionStatus.value = "測試檢測到 ${_detectedPlanesCount.value} 個平面"
        }
    }
    
    override fun clearAllModels(childNodes: MutableList<Node>) {
        _placedModelsCount.value = 0
        _planeDetectionStatus.value = "測試: 所有模型已清除"
    }
    
    override fun incrementModelCount() {
        _placedModelsCount.value++
    }
    
    override fun isReadyForPlacement(): Boolean = _canPlaceObjects.value
    
    override fun getUserFriendlyStatus(): String = 
        "測試模式: 準備放置貓咪! (已放置 ${_placedModelsCount.value} 隻)"
    
    override fun getDebugInfo(): String = """
        === 測試 AR 調試信息 ===
        平面數: ${_detectedPlanesCount.value}
        模型數: ${_placedModelsCount.value}
        狀態: ${_trackingStatus.value}
        可放置: ${_canPlaceObjects.value}
        模式: 測試模式 (無 ARCore)
        ========================
    """.trimIndent()
    
    // 測試輔助方法 - 用於模擬各種場景
    fun simulatePlaneDetection(count: Int) {
        _detectedPlanesCount.value = count
        _planeDetectionStatus.value = "測試: 模擬檢測到 $count 個平面"
    }
    
    fun simulateTrackingLoss() {
        _trackingStatus.value = "測試追蹤丟失"
        _canPlaceObjects.value = false
    }
    
    fun simulateTrackingRecovered() {
        _trackingStatus.value = "測試追蹤恢復"
        _canPlaceObjects.value = true
    }
}

/**
 * 測試用假交互管理器 - 模擬觸摸交互，但不調用 ARCore
 */
class FakeARInteractionManager : ARInteractionManager {
    
    private var fakeSelectedNode: ModelNode? = null
    private var fakeFirstCatModel: ModelNode? = null
    private var fakePlacedModelsCount = 0
    
    // 模擬你的配置屬性
    override var rotationSensitivityX: Float = 0.3f
    override var rotationSensitivityY: Float = 0.3f
    
    override fun handleSceneViewTouchDown(
        motionEvent: MotionEvent,
        hitResult: HitResult?,
        frame: Frame?,
        session: Session?,
        modelLoader: ModelLoader,
        childNodes: MutableList<Node>,
        engine: com.google.android.filament.Engine,
        arSessionManager: ARSessionManager,
        collisionSystem: CollisionSystem,
        cameraNode: io.github.sceneview.node.CameraNode,
        onFirstCatCreated: (ModelNode?) -> Unit
    ) {
        // 測試模式：模擬放置邏輯，不調用 ARCore
        fakePlacedModelsCount++
        arSessionManager.incrementModelCount()
        
        println("測試日誌: 模擬在 (${motionEvent.x}, ${motionEvent.y}) 放置貓咪")
        
        // 模擬第一隻貓咪創建
        if (fakeFirstCatModel == null) {
            fakeFirstCatModel = null  // 測試模式使用 null
            onFirstCatCreated(null)
        }
    }
    
    // 其他方法的測試實現
    override fun handleImprovedTouchMove(motionEvent: MotionEvent) {
        println("測試日誌: 模擬觸摸移動")
    }
    
    override fun handleImprovedTouchUp(arSessionManager: ARSessionManager) {
        println("測試日誌: 模擬觸摸結束")
    }
    
    override fun updateSmoothRotation() {
        // 測試模式：不做實際旋轉
    }
    
    override fun getSelectedNode(): ModelNode? = fakeSelectedNode
    override fun getFirstCatModel(): ModelNode? = fakeFirstCatModel
    override fun getFirstCatBoundingHeight(): Float = 0.4f
    override fun getPlacedModelsCount(): Int = fakePlacedModelsCount
    override fun getModelScreenPosition(modelName: String): Offset? = Offset(540f, 1000f)
    
    override fun clearAllCats(childNodes: MutableList<Node>, arSessionManager: ARSessionManager) {
        fakePlacedModelsCount = 0
        fakeFirstCatModel = null
        fakeSelectedNode = null
        arSessionManager.clearAllModels(childNodes)
        println("測試日誌: 清除所有測試貓咪")
    }
    
    override fun configureCollisionDetection(safePlacementDistance: Float, touchDetectionRadius: Float) {
        println("測試日誌: 配置碰撞檢測 - 安全距離: $safePlacementDistance, 觸摸半徑: $touchDetectionRadius")
    }
    
    override fun debugCollisionDetection() {
        println("測試日誌: 調試碰撞檢測")
    }
    
    override fun isValidPlacementPosition(worldPosition: Position, collisionSystem: CollisionSystem?): Boolean {
        println("測試日誌: 檢查位置有效性 - 位置: $worldPosition")
        return true  // 測試模式總是返回有效
    }
    
    override fun resetSensitivityToDefault() {
        rotationSensitivityX = 0.3f
        rotationSensitivityY = 0.3f
        println("測試日誌: 重設旋轉靈敏度為預設值")
    }
    
    // 測試輔助方法
    fun simulateModelSelection() {
        fakeSelectedNode = null  // 測試模式使用 null
        println("測試日誌: 模擬選中模型")
    }
    
    fun simulatePlaceFirstCat() {
        fakeFirstCatModel = null
        fakePlacedModelsCount = 1
        println("測試日誌: 模擬放置第一隻貓")
    }
}