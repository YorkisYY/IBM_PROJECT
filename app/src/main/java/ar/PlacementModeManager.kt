// ar/PlacementModeManager.kt
package ar

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import com.google.ar.core.*
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.collision.HitResult
import io.github.sceneview.collision.CollisionSystem

/**
 * AR Placement Mode Enum
 */
enum class PlacementMode(
    val displayName: String, 
    val description: String, 
    val icon: String,
    val color: Long
) {
    PLANE_ONLY(
        "Plane", 
        "Stable surface placement", 
        "P",
        0xFF4CAF50
    ),
    INSTANT_ONLY(
        "Instant", 
        "Quick placement anywhere", 
        "I",
        0xFFFF9800
    ),
    AUTO_MIXED(
        "Auto", 
        "Smart mixed placement", 
        "A",
        0xFF2196F3
    )
}

/**
 * PlacementModeManager that truly preserves plane data
 */
class PlacementModeManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PlacementModeManager"
    }
    
    // Current placement mode
    private val _currentMode = mutableStateOf(PlacementMode.PLANE_ONLY)
    val currentMode: State<PlacementMode> = _currentMode
    
    // ARTouchHandler reference
    private var arTouchHandler: ARTouchHandler? = null
    
    // ARCore session reference
    private var currentSession: Session? = null
    
    // ARSceneView reference for plane visualization control
    private var arSceneView: io.github.sceneview.ar.ARSceneView? = null
    
    /**
     * Set ARTouchHandler reference
     */
    fun setARTouchHandler(handler: ARTouchHandler) {
        arTouchHandler = handler
        Log.d(TAG, "ARTouchHandler integrated with PlacementModeManager")
    }
    
    /**
     * Set ARSceneView reference for plane visualization control
     */
    fun setARSceneView(sceneView: io.github.sceneview.ar.ARSceneView) {
        arSceneView = sceneView
        Log.d(TAG, "ARSceneView integrated with PlacementModeManager")
    }
    
    /**
     * Set ARCore session reference
     */
    fun setSession(session: Session?) {
        currentSession = session
    }
    
    /**
     * Real mode switching - only clear models, completely preserve plane data
     */
    fun switchToNextMode(
        childNodes: MutableList<Node>,
        arRenderer: ARSceneViewRenderer,
        onModelsCleared: () -> Unit = {},
        onModeChanged: () -> Unit = {}
    ) {
        val nextMode = when (_currentMode.value) {
            PlacementMode.PLANE_ONLY -> PlacementMode.INSTANT_ONLY
            PlacementMode.INSTANT_ONLY -> PlacementMode.AUTO_MIXED
            PlacementMode.AUTO_MIXED -> PlacementMode.PLANE_ONLY
        }
        
        Log.d(TAG, "Switching mode: ${_currentMode.value.displayName} -> ${nextMode.displayName}")
        
        // Only clear models, don't touch session
        arTouchHandler?.clearAllCats(childNodes, arRenderer)
        onModelsCleared()
        
        // Only update configuration, preserve all plane data
        if (updateConfigurationOnly(nextMode)) {
            _currentMode.value = nextMode
            onModeChanged()
            Log.d(TAG, "Mode switch successful: ${nextMode.displayName} - ALL PLANE DATA PRESERVED")
        } else {
            Log.e(TAG, "Mode switch failed")
        }
    }
    
    /**
     * Set specific mode - only clear models, preserve plane data
     */
    fun setMode(
        mode: PlacementMode,
        childNodes: MutableList<Node>,
        arRenderer: ARSceneViewRenderer,
        clearModels: Boolean = true,
        onModelsCleared: () -> Unit = {},
        onModeChanged: () -> Unit = {}
    ) {
        if (_currentMode.value != mode) {
            Log.d(TAG, "Setting mode: ${_currentMode.value.displayName} -> ${mode.displayName}")
            
            if (clearModels) {
                arTouchHandler?.clearAllCats(childNodes, arRenderer)
                onModelsCleared()
            }
            
            if (updateConfigurationOnly(mode)) {
                _currentMode.value = mode
                onModeChanged()
                Log.d(TAG, "Mode set successful: ${mode.displayName} - ALL PLANE DATA PRESERVED")
            } else {
                Log.e(TAG, "Mode set failed")
            }
        }
    }
    
    /**
     * Only update ARCore configuration, completely don't touch session and plane data
     */
    private fun updateConfigurationOnly(targetMode: PlacementMode): Boolean {
        return try {
            currentSession?.let { session ->
                Log.d(TAG, "ONLY updating config for ${targetMode.displayName} - NO SESSION RESET")
                
                val config = session.config
                
                when (targetMode) {
                    PlacementMode.PLANE_ONLY -> {
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                    }
                    PlacementMode.INSTANT_ONLY -> {
                        // Key: stop new plane detection, but preserve existing data
                        config.planeFindingMode = Config.PlaneFindingMode.DISABLED
                        config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                    }
                    PlacementMode.AUTO_MIXED -> {
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                    }
                }
                
                // Only call configure, don't reset anything
                session.configure(config)
                
                Log.d(TAG, "Configuration updated successfully:")
                Log.d(TAG, "   Mode: ${targetMode.displayName}")
                Log.d(TAG, "   PlaneFindingMode: ${config.planeFindingMode}")
                Log.d(TAG, "   InstantPlacementMode: ${config.instantPlacementMode}")
                Log.d(TAG, "   ALL EXISTING PLANE DATA PRESERVED")
                
                // Only update display, don't affect data
                handlePlaneVisualization(targetMode)
                
                true
            } ?: run {
                Log.e(TAG, "Session is null")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Configuration failed: ${e.message}")
            false
        }
    }
    
    /**
     * Handle plane visualization
     */
    private fun handlePlaneVisualization(targetMode: PlacementMode) {
        try {
            arSceneView?.let { sceneView ->
                when (targetMode) {
                    PlacementMode.PLANE_ONLY -> {
                        Log.d(TAG, "Enabling plane renderer for PLANE_ONLY mode")
                        sceneView.planeRenderer.isEnabled = true
                        sceneView.planeRenderer.isVisible = true
                    }
                    PlacementMode.INSTANT_ONLY -> {
                        Log.d(TAG, "Disabling plane renderer for INSTANT_ONLY mode")
                        sceneView.planeRenderer.isEnabled = false
                        sceneView.planeRenderer.isVisible = false
                    }
                    PlacementMode.AUTO_MIXED -> {
                        Log.d(TAG, "Enabling plane renderer for AUTO_MIXED mode")
                        sceneView.planeRenderer.isEnabled = true
                        sceneView.planeRenderer.isVisible = true
                    }
                }
                Log.d(TAG, "Plane renderer updated for ${targetMode.displayName}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update plane visualization: ${e.message}")
        }
    }
    
    /**
     * Independent complete plane data clearing function
     */
    fun clearPlaneData(onPlaneDataCleared: () -> Unit = {}) {
        Log.d(TAG, "CLEARING ALL PLANE DATA - COMPLETE SESSION RESET")
        resetSessionToClearAllTrackables {
            Log.d(TAG, "All plane data cleared - session completely reset")
            onPlaneDataCleared()
        }
    }
    
    /**
     * Complete session reset method (only used when clicking clear button)
     */
    private fun resetSessionToClearAllTrackables(onSessionReset: () -> Unit = {}) {
        try {
            currentSession?.let { session ->
                Log.d(TAG, "=== COMPLETE SESSION RESET - CLEARING ALL TRACKABLES ===")
                
                try {
                    @Suppress("DEPRECATION")
                    val currentCameraConfigs = session.getSupportedCameraConfigs()
                    val currentConfig = session.cameraConfig
                    
                    val differentConfig = currentCameraConfigs.find { it != currentConfig }
                    
                    if (differentConfig != null) {
                        Log.d(TAG, "Using camera config reset method...")
                        session.pause()
                        session.cameraConfig = differentConfig
                        session.resume()
                        
                        session.pause()
                        session.cameraConfig = currentConfig
                        session.resume()
                        
                        Log.d(TAG, "Camera config reset completed - all trackables cleared")
                        onSessionReset()
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Camera config reset failed: ${e.message}")
                }
                
                Log.d(TAG, "Using session lifecycle reset method...")
                
                session.pause()
                session.close()
                
                val newSession = Session(context)
                val config = newSession.config
                
                // Restore current mode configuration
                when (_currentMode.value) {
                    PlacementMode.PLANE_ONLY -> {
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                    }
                    PlacementMode.INSTANT_ONLY -> {
                        config.planeFindingMode = Config.PlaneFindingMode.DISABLED
                        config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                    }
                    PlacementMode.AUTO_MIXED -> {
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                    }
                }
                
                newSession.configure(config)
                newSession.resume()
                
                currentSession = newSession
                
                Log.d(TAG, "New session created - completely fresh state")
                onSessionReset()
                
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset session: ${e.message}")
        }
    }
    
    /**
     * Handle touch for placement
     */
    fun handleTouchForPlacement(
        motionEvent: MotionEvent,
        hitResult: HitResult?,
        frame: Frame?,
        session: Session?,
        modelLoader: ModelLoader,
        childNodes: MutableList<Node>,
        engine: com.google.android.filament.Engine,
        arRenderer: ARSceneViewRenderer,
        collisionSystem: CollisionSystem,
        cameraNode: io.github.sceneview.node.CameraNode,
        onFirstCatCreated: (ModelNode?) -> Unit
    ) {
        setSession(session)
        
        val currentMode = _currentMode.value
        Log.d(TAG, "Handling touch in ${currentMode.displayName} mode")
        
        arTouchHandler?.handleSceneViewTouchDown(
            motionEvent = motionEvent,
            hitResult = hitResult,
            frame = frame,
            session = session,
            modelLoader = modelLoader,
            childNodes = childNodes,
            engine = engine,
            arRenderer = arRenderer,
            collisionSystem = collisionSystem,
            cameraNode = cameraNode,
            onFirstCatCreated = onFirstCatCreated
        )
    }
    
    /**
     * Clear all models - doesn't affect plane data
     */
    fun clearAllModels(
        childNodes: MutableList<Node>,
        arRenderer: ARSceneViewRenderer
    ) {
        arTouchHandler?.clearAllCats(childNodes, arRenderer)
        Log.d(TAG, "All models cleared in ${_currentMode.value.displayName} mode (plane data preserved)")
    }
    
    /**
     * Get current mode status text
     */
    fun getModeStatusText(): String {
        val mode = _currentMode.value
        val modelCount = arTouchHandler?.getPlacedModelsCount() ?: 0
        return "${mode.icon} ${mode.displayName}: ${mode.description} (${modelCount} models)"
    }
    
    /**
     * Check if there are models
     */
    fun hasModels(): Boolean = (arTouchHandler?.getPlacedModelsCount() ?: 0) > 0
    
    /**
     * Get model count
     */
    fun getModelCount(): Int = arTouchHandler?.getPlacedModelsCount() ?: 0
    
    /**
     * Get currently selected node
     */
    fun getSelectedNode(): ModelNode? = arTouchHandler?.getSelectedNode()
    
    /**
     * Debug information
     */
    fun debugModeInformation() {
        Log.d(TAG, "=== MODE DEBUG ===")
        Log.d(TAG, "Current mode: ${_currentMode.value.displayName}")
        Log.d(TAG, "Model count: ${getModelCount()}")
        Log.d(TAG, "Has models: ${hasModels()}")
        Log.d(TAG, "Session available: ${currentSession != null}")
        
        currentSession?.let { session ->
            val allPlanes = session.getAllTrackables(Plane::class.java)
            val trackingPlanes = allPlanes.filter { it.trackingState == TrackingState.TRACKING }
            val pausedPlanes = allPlanes.filter { it.trackingState == TrackingState.PAUSED }
            
            Log.d(TAG, "PLANE DATA STATUS:")
            Log.d(TAG, "Total planes: ${allPlanes.size}")
            Log.d(TAG, "Tracking planes: ${trackingPlanes.size}")
            Log.d(TAG, "Paused planes: ${pausedPlanes.size}")
            
            if (allPlanes.isNotEmpty()) {
                Log.d(TAG, "PLANE DATA AVAILABLE - INSTANT MODE CAN ROTATE")
            } else {
                Log.d(TAG, "NO PLANE DATA - INSTANT MODE ROTATION WILL FAIL")
            }
            
            val allAnchors = session.getAllAnchors()
            Log.d(TAG, "Total anchors: ${allAnchors.size}")
        }
        
        Log.d(TAG, "=== END DEBUG ===")
    }
}