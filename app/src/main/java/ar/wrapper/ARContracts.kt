package ar.wrapper

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
 * AR 會話管理器接口 - 對應你的 ARSceneViewRenderer
 */
interface ARSessionManager {
    // Compose 狀態觀察 - 保持你的 mutableStateOf 風格
    val detectedPlanesCount: State<Int>
    val placedModelsCount: State<Int>
    val trackingStatus: State<String>
    val planeDetectionStatus: State<String>
    val canPlaceObjects: State<Boolean>
    
    // AR 會話生命週期
    fun configureSession(arSession: Session, config: Config)
    fun onSessionCreated(arSession: Session)
    fun onSessionResumed(arSession: Session)
    fun onSessionPaused(arSession: Session)
    fun onSessionFailed(exception: Exception)
    fun onSessionUpdated(arSession: Session, updatedFrame: Frame)
    
    // 模型管理
    fun clearAllModels(childNodes: MutableList<Node>)
    fun incrementModelCount()
    fun isReadyForPlacement(): Boolean
    fun getUserFriendlyStatus(): String
    fun getDebugInfo(): String
}

/**
 * AR 交互管理器接口 - 對應你的 ARTouchHandler
 */
interface ARInteractionManager {
    // 主要觸摸處理
    fun handleSceneViewTouchDown(
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
    )
    
    // 觸摸手勢處理
    fun handleImprovedTouchMove(motionEvent: MotionEvent)
    fun handleImprovedTouchUp(arSessionManager: ARSessionManager)
    fun updateSmoothRotation()
    
    // 狀態查詢
    fun getSelectedNode(): ModelNode?
    fun getFirstCatModel(): ModelNode?
    fun getFirstCatBoundingHeight(): Float
    fun getPlacedModelsCount(): Int
    fun getModelScreenPosition(modelName: String): Offset?
    
    // 管理操作
    fun clearAllCats(childNodes: MutableList<Node>, arSessionManager: ARSessionManager)
    fun configureCollisionDetection(safePlacementDistance: Float, touchDetectionRadius: Float)
    fun debugCollisionDetection()
    fun isValidPlacementPosition(worldPosition: Position, collisionSystem: CollisionSystem?): Boolean
    
    // 配置屬性
    var rotationSensitivityX: Float
    var rotationSensitivityY: Float
    fun resetSensitivityToDefault()
}
