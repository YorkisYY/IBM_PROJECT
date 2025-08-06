package ar

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.google.ar.core.*
import io.github.sceneview.node.Node

/**
 * Simplified AR Scene View Renderer for SceneView 2.3.0
 * Focuses on AR session management and state tracking
 */
class ARSceneViewRenderer {
    
    companion object {
        private const val TAG = "ARSceneViewRenderer"
    }
    
    // AR State - Observable by Compose
    val detectedPlanesCount = mutableStateOf(0)
    val placedModelsCount = mutableStateOf(0)
    val trackingStatus = mutableStateOf("Initializing...")
    val planeDetectionStatus = mutableStateOf("Scanning environment...")
    val canPlaceObjects = mutableStateOf(false)
    
    /**
     * Configure AR Session for optimal tracking
     */
    fun configureSession(arSession: Session, config: Config) {
        Log.d(TAG, "🔧 Configuring AR Session...")
        
        // Enable horizontal and vertical plane detection
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        
        // Enable depth if supported
        if (arSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.depthMode = Config.DepthMode.AUTOMATIC
            Log.d(TAG, "✅ Depth detection enabled")
        } else {
            config.depthMode = Config.DepthMode.DISABLED
            Log.w(TAG, "⚠️ Device does not support depth detection")
        }
        
        // Configure lighting and placement
        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
        config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
        
        Log.d(TAG, "✅ AR Session configured successfully")
    }
    
    /**
     * Handle AR Session Creation
     */
    fun onSessionCreated(arSession: Session) {
        Log.d(TAG, "✅ AR Session created successfully")
        trackingStatus.value = "AR Session Created"
        planeDetectionStatus.value = "Move device slowly to scan environment..."
    }
    
    /**
     * Handle AR Session Resume
     */
    fun onSessionResumed(arSession: Session) {
        Log.d(TAG, "▶️ AR Session resumed")
        trackingStatus.value = "AR Tracking Active"
    }
    
    /**
     * Handle AR Session Pause
     */
    fun onSessionPaused(arSession: Session) {
        Log.d(TAG, "⏸️ AR Session paused")
        trackingStatus.value = "AR Session Paused"
    }
    
    /**
     * Handle AR Session Failure
     */
    fun onSessionFailed(exception: Exception) {
        Log.e(TAG, "❌ AR Session failed: ${exception.message}")
        trackingStatus.value = "AR Session Failed"
        planeDetectionStatus.value = "AR initialization failed - check device compatibility"
    }
    
    /**
     * Handle AR Frame Updates - Core tracking logic
     */
    fun onSessionUpdated(arSession: Session, updatedFrame: Frame) {
        val camera = updatedFrame.camera
        val isTracking = camera.trackingState == TrackingState.TRACKING
        
        if (isTracking) {
            // Get all detected planes
            val allPlanes = arSession.getAllTrackables(Plane::class.java)
            val trackingPlanes = allPlanes.filter { plane ->
                plane.trackingState == TrackingState.TRACKING
            }
            
            // Update plane count
            detectedPlanesCount.value = trackingPlanes.size
            
            // Always allow placement (instant placement + plane detection)
            canPlaceObjects.value = true
            
            // Update status based on plane detection
            when {
                trackingPlanes.isEmpty() -> {
                    planeDetectionStatus.value = "No planes detected - instant placement available"
                    trackingStatus.value = "Ready to Place (Instant Mode)"
                }
                trackingPlanes.size == 1 -> {
                    planeDetectionStatus.value = "1 plane detected - good tracking"
                    trackingStatus.value = "Ready to Place"
                }
                trackingPlanes.size < 5 -> {
                    planeDetectionStatus.value = "${trackingPlanes.size} planes detected - stable tracking"
                    trackingStatus.value = "Tracking Normal"
                }
                else -> {
                    planeDetectionStatus.value = "${trackingPlanes.size} planes detected - excellent tracking"
                    trackingStatus.value = "Tracking Excellent"
                }
            }
            
            // Log plane types for debugging
            if (trackingPlanes.isNotEmpty()) {
                val planeTypes = trackingPlanes.groupBy { it.type }.mapValues { it.value.size }
                Log.v(TAG, "🎯 Detected planes: $planeTypes")
            }
            
        } else {
            // Lost tracking
            trackingStatus.value = when (camera.trackingState) {
                TrackingState.PAUSED -> "Tracking Paused"
                TrackingState.STOPPED -> "Tracking Stopped"
                else -> "Tracking Lost"
            }
            planeDetectionStatus.value = "Tracking lost - ensure good lighting and move device slowly"
            canPlaceObjects.value = false
        }
    }
    
    /**
     * Clear all placed models and reset counters
     */
    fun clearAllModels(childNodes: MutableList<Node>) {
        try {
            childNodes.clear()
            placedModelsCount.value = 0
            planeDetectionStatus.value = "All models cleared - ready to place new cats"
            Log.d(TAG, "🗑️ All models cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing models: ${e.message}", e)
        }
    }
    
    /**
     * Get comprehensive debug info
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== AR Debug Info ===")
            appendLine("Planes Detected: ${detectedPlanesCount.value}")
            appendLine("Models Placed: ${placedModelsCount.value}")
            appendLine("Tracking Status: ${trackingStatus.value}")
            appendLine("Can Place Objects: ${canPlaceObjects.value}")
            appendLine("Plane Status: ${planeDetectionStatus.value}")
            appendLine("===================")
        }
    }
    
    /**
     * Update model count when a new model is placed
     * (Called from MainActivity when placing models)
     */
    fun incrementModelCount() {
        placedModelsCount.value++
        Log.d(TAG, "📈 Model count incremented to: ${placedModelsCount.value}")
    }
    
    /**
     * Check if AR session is ready for placing objects
     */
    fun isReadyForPlacement(): Boolean {
        return canPlaceObjects.value && 
               trackingStatus.value.contains("Ready", ignoreCase = true) ||
               trackingStatus.value.contains("Normal", ignoreCase = true) ||
               trackingStatus.value.contains("Excellent", ignoreCase = true)
    }
    
    /**
     * Get user-friendly status message
     */
    fun getUserFriendlyStatus(): String {
        return when {
            !canPlaceObjects.value -> "Move device to start tracking"
            placedModelsCount.value == 0 -> "Tap anywhere to place your first cat!"
            placedModelsCount.value == 1 -> "Great! Tap the cat to rotate it, or tap elsewhere for more cats"
            else -> "Tap cats to rotate them, or tap empty space to add more cats"
        }
    }
}