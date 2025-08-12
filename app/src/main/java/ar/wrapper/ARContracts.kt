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
 * AR Session Manager Interface - Corresponding to your ARSceneViewRenderer
 * Fixed version: Support nullable parameters, suitable for test environment
 */
interface ARSessionManager {
    // Compose state observation - Maintain your mutableStateOf style
    val detectedPlanesCount: State<Int>
    val placedModelsCount: State<Int>
    val trackingStatus: State<String>
    val planeDetectionStatus: State<String>
    val canPlaceObjects: State<Boolean>
    
    // AR session lifecycle - Changed to nullable to support testing
    fun configureSession(arSession: Session?, config: Config?)
    fun onSessionCreated(arSession: Session?)
    fun onSessionResumed(arSession: Session?)
    fun onSessionPaused(arSession: Session?)
    fun onSessionFailed(exception: Exception)
    fun onSessionUpdated(arSession: Session?, updatedFrame: Frame?)
    
    // Model management - Keep unchanged
    fun clearAllModels(childNodes: MutableList<Node>)
    fun incrementModelCount()
    fun isReadyForPlacement(): Boolean
    fun getUserFriendlyStatus(): String
    fun getDebugInfo(): String
}

/**
 * AR Interaction Manager Interface - Corresponding to your ARTouchHandler
 * Fixed version: Support nullable parameters, suitable for test environment
 */
interface ARInteractionManager {
    // Main touch handling - Key parameters changed to nullable
    fun handleSceneViewTouchDown(
        motionEvent: MotionEvent,
        hitResult: HitResult?,
        frame: Frame?,                   // Changed to nullable
        session: Session?,               // Changed to nullable
        modelLoader: ModelLoader?,       // Changed to nullable
        childNodes: MutableList<Node>,
        engine: Engine?,                 // Changed to nullable
        arSessionManager: ARSessionManager,
        collisionSystem: CollisionSystem?, // Changed to nullable
        cameraNode: CameraNode?,         // Changed to nullable
        onFirstCatCreated: (ModelNode?) -> Unit
    )
    
    // Touch gesture handling - Keep unchanged
    fun handleImprovedTouchMove(motionEvent: MotionEvent)
    fun handleImprovedTouchUp(arSessionManager: ARSessionManager)
    fun updateSmoothRotation()
    
    // State queries - Keep unchanged
    fun getSelectedNode(): ModelNode?
    fun getFirstCatModel(): ModelNode?
    fun getFirstCatBoundingHeight(): Float
    fun getPlacedModelsCount(): Int
    fun getModelScreenPosition(modelName: String): Offset?
    
    // Management operations - Keep unchanged
    fun clearAllCats(childNodes: MutableList<Node>, arSessionManager: ARSessionManager)
    fun configureCollisionDetection(safePlacementDistance: Float, touchDetectionRadius: Float)
    fun debugCollisionDetection()
    fun isValidPlacementPosition(worldPosition: Position, collisionSystem: CollisionSystem?): Boolean
    
    // Configuration properties - Keep unchanged
    var rotationSensitivityX: Float
    var rotationSensitivityY: Float
    fun resetSensitivityToDefault()
}