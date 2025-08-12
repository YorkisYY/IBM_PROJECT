package ar.wrapper

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
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
 * Fake AR Session Manager for testing - Completely avoid ARCore native calls
 * Fixed version: Match new nullable interface
 */
class FakeARSessionManager : ARSessionManager {
    
    // Simulate your Compose state - exactly same types
    private val _detectedPlanesCount = mutableStateOf(0)
    private val _placedModelsCount = mutableStateOf(0)
    private val _trackingStatus = mutableStateOf("Test mode ready")
    private val _planeDetectionStatus = mutableStateOf("Test plane detection active")
    private val _canPlaceObjects = mutableStateOf(true)
    
    override val detectedPlanesCount: State<Int> = _detectedPlanesCount
    override val placedModelsCount: State<Int> = _placedModelsCount
    override val trackingStatus: State<String> = _trackingStatus
    override val planeDetectionStatus: State<String> = _planeDetectionStatus
    override val canPlaceObjects: State<Boolean> = _canPlaceObjects
    
    // Fake session management - Don't call any ARCore API, but simulate behavior - Fixed method signature
    override fun configureSession(arSession: Session?, config: Config?) {
        // Test mode: Log call but don't execute ARCore logic
        _trackingStatus.value = "Test session configured"
        println("Test log: configureSession called")
    }
    
    override fun onSessionCreated(arSession: Session?) {
        _trackingStatus.value = "Test session created"
        println("Test log: onSessionCreated called")
    }
    
    override fun onSessionResumed(arSession: Session?) {
        _trackingStatus.value = "Test session resumed"
        _canPlaceObjects.value = true
    }
    
    override fun onSessionPaused(arSession: Session?) {
        _trackingStatus.value = "Test session paused"
        _canPlaceObjects.value = false
    }
    
    override fun onSessionFailed(exception: Exception) {
        _trackingStatus.value = "Test session failed: ${exception.message}"
        _canPlaceObjects.value = false
    }
    
    override fun onSessionUpdated(arSession: Session?, updatedFrame: Frame?) {
        // Simulate plane detection progress
        if (_detectedPlanesCount.value < 5) {
            _detectedPlanesCount.value++
            _planeDetectionStatus.value = "Test detected ${_detectedPlanesCount.value} planes"
        }
    }
    
    override fun clearAllModels(childNodes: MutableList<Node>) {
        _placedModelsCount.value = 0
        _planeDetectionStatus.value = "Test: All models cleared"
    }
    
    override fun incrementModelCount() {
        _placedModelsCount.value++
    }
    
    override fun isReadyForPlacement(): Boolean = _canPlaceObjects.value
    
    override fun getUserFriendlyStatus(): String = 
        "Test mode: Ready to place cats! (Placed ${_placedModelsCount.value})"
    
    override fun getDebugInfo(): String = """
        === Test AR Debug Info ===
        Planes: ${_detectedPlanesCount.value}
        Models: ${_placedModelsCount.value}
        Status: ${_trackingStatus.value}
        Can Place: ${_canPlaceObjects.value}
        Mode: Test mode (no ARCore)
        =========================
    """.trimIndent()
    
    // Test helper methods - for simulating various scenarios
    fun simulatePlaneDetection(count: Int) {
        _detectedPlanesCount.value = count
        _planeDetectionStatus.value = "Test: Simulated detection of $count planes"
    }
    
    fun simulateTrackingLoss() {
        _trackingStatus.value = "Test tracking lost"
        _canPlaceObjects.value = false
    }
    
    fun simulateTrackingRecovered() {
        _trackingStatus.value = "Test tracking recovered"
        _canPlaceObjects.value = true
    }
}

/**
 * Fake interaction manager for testing - Simulate touch interaction, but don't call ARCore
 * Fixed version: Match new nullable interface
 */
class FakeARInteractionManager : ARInteractionManager {
    
    private var fakeSelectedNode: ModelNode? = null
    private var fakeFirstCatModel: ModelNode? = null
    private var fakePlacedModelsCount = 0
    
    // Simulate your configuration properties
    override var rotationSensitivityX: Float = 0.3f
    override var rotationSensitivityY: Float = 0.3f
    
    // Fixed method signature to match new interface
    override fun handleSceneViewTouchDown(
        motionEvent: MotionEvent,
        hitResult: HitResult?,
        frame: Frame?,
        session: Session?,
        modelLoader: ModelLoader?,
        childNodes: MutableList<Node>,
        engine: Engine?,
        arSessionManager: ARSessionManager,
        collisionSystem: CollisionSystem?,
        cameraNode: CameraNode?,
        onFirstCatCreated: (ModelNode?) -> Unit
    ) {
        // Test mode: Simulate placement logic, don't call ARCore
        fakePlacedModelsCount++
        arSessionManager.incrementModelCount()
        
        println("Test log: Simulated placing cat at (${motionEvent.x}, ${motionEvent.y})")
        
        // Simulate first cat creation
        if (fakeFirstCatModel == null) {
            fakeFirstCatModel = null  // Test mode uses null
            onFirstCatCreated(null)
        }
    }
    
    // Test implementations for other methods
    override fun handleImprovedTouchMove(motionEvent: MotionEvent) {
        println("Test log: Simulated touch move")
    }
    
    override fun handleImprovedTouchUp(arSessionManager: ARSessionManager) {
        println("Test log: Simulated touch up")
    }
    
    override fun updateSmoothRotation() {
        // Test mode: Don't do actual rotation
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
        println("Test log: Cleared all test cats")
    }
    
    override fun configureCollisionDetection(safePlacementDistance: Float, touchDetectionRadius: Float) {
        println("Test log: Configure collision detection - Safe distance: $safePlacementDistance, Touch radius: $touchDetectionRadius")
    }
    
    override fun debugCollisionDetection() {
        println("Test log: Debug collision detection")
    }
    
    override fun isValidPlacementPosition(worldPosition: Position, collisionSystem: CollisionSystem?): Boolean {
        println("Test log: Check position validity - Position: $worldPosition")
        return true  // Test mode always returns valid
    }
    
    override fun resetSensitivityToDefault() {
        rotationSensitivityX = 0.3f
        rotationSensitivityY = 0.3f
        println("Test log: Reset rotation sensitivity to default values")
    }
    
    // Test helper methods
    fun simulateModelSelection() {
        fakeSelectedNode = null  // Test mode uses null
        println("Test log: Simulated model selection")
    }
    
    fun simulatePlaceFirstCat() {
        fakeFirstCatModel = null
        fakePlacedModelsCount = 1
        println("Test log: Simulated placing first cat")
    }
}