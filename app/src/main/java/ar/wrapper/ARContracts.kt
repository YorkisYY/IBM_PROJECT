package ar.wrapper

import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import android.view.MotionEvent
import com.google.ar.core.*
import com.google.android.filament.Engine
import io.github.sceneview.collision.HitResult
import io.github.sceneview.collision.CollisionSystem
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.CameraNode

/**
 * AR 會話管理器接口 - 對應你的 ARSceneViewRenderer
 * 修正版：支持 nullable 參數，適合測試環境
 */
interface ARSessionManager {
    // Compose 狀態觀察 - 保持你的 mutableStateOf 風格
    val detectedPlanesCount: State<Int>
    val placedModelsCount: State<Int>
    val trackingStatus: State<String>
    val planeDetectionStatus: State<String>
    val canPlaceObjects: State<Boolean>
    
    // AR 會話生命週期 - 改成 nullable 支持測試
    fun configureSession(arSession: Session?, config: Config?)
    fun onSessionCreated(arSession: Session?)
    fun onSessionResumed(arSession: Session?)
    fun onSessionPaused(arSession: Session?)
    fun onSessionFailed(exception: Exception)
    fun onSessionUpdated(arSession: Session?, updatedFrame: Frame?)
    
    // 模型管理 - 保持不變
    fun clearAllModels(childNodes: MutableList<Node>)
    fun incrementModelCount()
    fun isReadyForPlacement(): Boolean
    fun getUserFriendlyStatus(): String
    fun getDebugInfo(): String
}

/**
 * AR 交互管理器接口 - 對應你的 ARTouchHandler
 * 修正版：支持 nullable 參數，適合測試環境
 */
interface ARInteractionManager {
    // 主要觸摸處理 - 關鍵參數改成 nullable
    fun handleSceneViewTouchDown(
        motionEvent: MotionEvent,
        hitResult: HitResult?,
        frame: Frame?,                   // 改成 nullable
        session: Session?,               // 改成 nullable
        modelLoader: ModelLoader?,       // 改成 nullable
        childNodes: MutableList<Node>,
        engine: Engine?,                 // 改成 nullable
        arSessionManager: ARSessionManager,
        collisionSystem: CollisionSystem?, // 改成 nullable
        cameraNode: CameraNode?,         // 改成 nullable
        onFirstCatCreated: (ModelNode?) -> Unit
    )
    
    // 觸摸手勢處理 - 保持不變
    fun handleImprovedTouchMove(motionEvent: MotionEvent)
    fun handleImprovedTouchUp(arSessionManager: ARSessionManager)
    fun updateSmoothRotation()
    
    // 狀態查詢 - 保持不變
    fun getSelectedNode(): ModelNode?
    fun getFirstCatModel(): ModelNode?
    fun getFirstCatBoundingHeight(): Float
    fun getPlacedModelsCount(): Int
    fun getModelScreenPosition(modelName: String): Offset?
    
    // 管理操作 - 保持不變
    fun clearAllCats(childNodes: MutableList<Node>, arSessionManager: ARSessionManager)
    fun configureCollisionDetection(safePlacementDistance: Float, touchDetectionRadius: Float)
    fun debugCollisionDetection()
    fun isValidPlacementPosition(worldPosition: Position, collisionSystem: CollisionSystem?): Boolean
    
    // 配置屬性 - 保持不變
    var rotationSensitivityX: Float
    var rotationSensitivityY: Float
    fun resetSensitivityToDefault()
}