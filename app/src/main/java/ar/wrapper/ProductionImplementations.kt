package ar.wrapper

import ar.ARSceneViewRenderer
import ar.ARTouchHandler
import androidx.compose.runtime.State
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
 * 生產環境 AR 會話管理器 - 透明包裝你的 ARSceneViewRenderer
 * 零性能損耗，完全保持你的原始邏輯
 */
class ProductionARSessionManager(
    private val renderer: ARSceneViewRenderer = ARSceneViewRenderer()
) : ARSessionManager {
    
    // 直接暴露你的原始狀態 - 零開銷
    override val detectedPlanesCount: State<Int> = renderer.detectedPlanesCount
    override val placedModelsCount: State<Int> = renderer.placedModelsCount
    override val trackingStatus: State<String> = renderer.trackingStatus
    override val planeDetectionStatus: State<String> = renderer.planeDetectionStatus
    override val canPlaceObjects: State<Boolean> = renderer.canPlaceObjects
    
    // 完全透明的方法委派 - 你的邏輯一個字都不變
    override fun configureSession(arSession: Session, config: Config) = 
        renderer.configureSession(arSession, config)
    
    override fun onSessionCreated(arSession: Session) = 
        renderer.onSessionCreated(arSession)
    
    override fun onSessionResumed(arSession: Session) = 
        renderer.onSessionResumed(arSession)
    
    override fun onSessionPaused(arSession: Session) = 
        renderer.onSessionPaused(arSession)
    
    override fun onSessionFailed(exception: Exception) = 
        renderer.onSessionFailed(exception)
    
    override fun onSessionUpdated(arSession: Session, updatedFrame: Frame) = 
        renderer.onSessionUpdated(arSession, updatedFrame)
    
    override fun clearAllModels(childNodes: MutableList<Node>) = 
        renderer.clearAllModels(childNodes)
    
    override fun incrementModelCount() = 
        renderer.incrementModelCount()
    
    override fun isReadyForPlacement(): Boolean = 
        renderer.isReadyForPlacement()
    
    override fun getUserFriendlyStatus(): String = 
        renderer.getUserFriendlyStatus()
    
    override fun getDebugInfo(): String = 
        renderer.getDebugInfo()
        
    // 提供原始實例訪問（內部使用）
    internal fun getOriginalRenderer(): ARSceneViewRenderer = renderer
}

/**
 * 生產環境 AR 交互管理器 - 透明包裝你的 ARTouchHandler
 */
class ProductionARInteractionManager(
    private val handler: ARTouchHandler = ARTouchHandler()
) : ARInteractionManager {
    
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
        // 安全轉換：從接口獲取原始實現
        val actualRenderer = when (arSessionManager) {
            is ProductionARSessionManager -> arSessionManager.getOriginalRenderer()
            else -> throw IllegalStateException("不支持的 ARSessionManager 實現: ${arSessionManager::class.java}")
        }
        
        // 完全透明調用你的原始方法 - 邏輯完全不變
        handler.handleSceneViewTouchDown(
            motionEvent = motionEvent,
            hitResult = hitResult,
            frame = frame,
            session = session,
            modelLoader = modelLoader,
            childNodes = childNodes,
            engine = engine,
            arRenderer = actualRenderer,
            collisionSystem = collisionSystem,
            cameraNode = cameraNode,
            onFirstCatCreated = onFirstCatCreated
        )
    }
    
    override fun handleImprovedTouchMove(motionEvent: MotionEvent) = 
        handler.handleImprovedTouchMove(motionEvent)
    
    override fun handleImprovedTouchUp(arSessionManager: ARSessionManager) {
        val actualRenderer = when (arSessionManager) {
            is ProductionARSessionManager -> arSessionManager.getOriginalRenderer()
            else -> throw IllegalStateException("不支持的 ARSessionManager 實現")
        }
        handler.handleImprovedTouchUp(actualRenderer)
    }
    
    override fun updateSmoothRotation() = handler.updateSmoothRotation()
    
    // 狀態訪問完全透明
    override fun getSelectedNode(): ModelNode? = handler.getSelectedNode()
    override fun getFirstCatModel(): ModelNode? = handler.getFirstCatModel()
    override fun getFirstCatBoundingHeight(): Float = handler.getFirstCatBoundingHeight()
    override fun getPlacedModelsCount(): Int = handler.getPlacedModelsCount()
    override fun getModelScreenPosition(modelName: String): Offset? = handler.getModelScreenPosition(modelName)
    
    override fun clearAllCats(childNodes: MutableList<Node>, arSessionManager: ARSessionManager) {
        val actualRenderer = when (arSessionManager) {
            is ProductionARSessionManager -> arSessionManager.getOriginalRenderer()
            else -> throw IllegalStateException("不支持的 ARSessionManager 實現")
        }
        handler.clearAllCats(childNodes, actualRenderer)
    }
    
    override fun configureCollisionDetection(safePlacementDistance: Float, touchDetectionRadius: Float) = 
        handler.configureCollisionDetection(safePlacementDistance, touchDetectionRadius)
    
    override fun debugCollisionDetection() = handler.debugCollisionDetection()
    
    override fun isValidPlacementPosition(worldPosition: Position, collisionSystem: CollisionSystem?): Boolean = 
        handler.isValidPlacementPosition(worldPosition, collisionSystem)
    
    // 配置屬性透明代理
    override var rotationSensitivityX: Float
        get() = handler.rotationSensitivityX
        set(value) { handler.rotationSensitivityX = value }
        
    override var rotationSensitivityY: Float
        get() = handler.rotationSensitivityY
        set(value) { handler.rotationSensitivityY = value }
    
    override fun resetSensitivityToDefault() = handler.resetSensitivityToDefault()
}
