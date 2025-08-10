package ar

import android.util.Log
import android.view.MotionEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import android.graphics.RectF
import com.google.ar.core.*
import io.github.sceneview.collision.HitResult
import io.github.sceneview.collision.CollisionSystem
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.utils.worldToScreen
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Float2
import kotlin.math.pow
import com.google.android.filament.RenderableManager
import com.google.android.filament.Box

class ARTouchHandler {
    
    companion object {
        private const val TAG = "ARTouchHandler"
        private const val GLB_MODEL_PATH = "cute_spooky_cat.glb"
        
        private const val ROTATION_SENSITIVITY_X = 0.3f
        private const val ROTATION_SENSITIVITY_Y = 0.3f
        private const val MIN_ROTATION_DISTANCE = 10f
        
        private const val VELOCITY_DAMPING = 0.85f
        private const val SMOOTH_FACTOR = 0.15f
        private const val MIN_VELOCITY_THRESHOLD = 0.01f
        
    
        private const val SAFE_PLACEMENT_DISTANCE = 0.3f 
        private const val TOUCH_DETECTION_RADIUS = 0.3f
        private const val MAX_MODELS_ALLOWED = 3 
    }
    
    // Rotation state management
    private var selectedNode: ModelNode? = null
    private var isRotating = false
    
    // Touch state
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    
    // Accumulated rotation values
    private var accumulatedRotationX = 0f
    private var accumulatedRotationY = 0f
    
    // Velocity tracking
    private var velocityX = 0f
    private var velocityY = 0f
    private var lastMoveTime = 0L
    
    // Target rotation values
    private var targetRotationX = 0f
    private var targetRotationY = 0f
    private var currentRotationX = 0f
    private var currentRotationY = 0f
    
    // Adjustable rotation sensitivity
    var rotationSensitivityX = ROTATION_SENSITIVITY_X
    var rotationSensitivityY = ROTATION_SENSITIVITY_Y
    
    // Store all placed model nodes
    private val placedModelNodes = mutableListOf<ModelNode>()
    
    // Store accumulated rotation values for each model
    private val modelRotationMap = mutableMapOf<String, Pair<Float, Float>>()
    
    // First cat state management
    private var firstCatModel: ModelNode? = null
    
    // Store actual bounding box height of first cat - using fixed estimated value
    private var firstCatBoundingHeight: Float = 0.4f
    
    /**
     * 使用簡化的距離檢測方法來選擇模型
     */
    private fun findModelWithDistanceCheck(
        touchX: Float,
        touchY: Float,
        frame: Frame?
    ): ModelNode? {
        // 這個方法保留但不使用，因為我們恢復了原本的邏輯
        return null
    }
    
    /**
     * 檢查新位置是否安全（不會與現有模型太近）
     * 添加詳細的調試信息
     */
    private fun checkPlacementSafety(
        touchX: Float,
        touchY: Float,
        frame: Frame?
    ): Pair<Boolean, Position?> {
        // 這個方法保留但不使用，因為我們恢復了原本的邏輯
        return Pair(true, null)
    }
    
    /**
     * Calculate distance between two world positions
     */
    private fun calculateDistance(pos1: Position, pos2: Position): Float {
        val dx = pos1.x - pos2.x
        val dy = pos1.y - pos2.y
        val dz = pos1.z - pos2.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
    
    /**
     * Set rotation state for selected model
     */
    private fun setupRotationForModel(modelNode: ModelNode) {
        val modelName = modelNode.name ?: "unknown"
        val storedRotation = modelRotationMap[modelName]
        
        if (storedRotation != null) {
            accumulatedRotationX = storedRotation.first
            accumulatedRotationY = storedRotation.second
            Log.d(TAG, "Restored rotation values $modelName - X: ${accumulatedRotationX}, Y: ${accumulatedRotationY}")
        } else {
            val currentRotation = modelNode.rotation
            accumulatedRotationX = currentRotation.x
            accumulatedRotationY = currentRotation.y
            modelRotationMap[modelName] = Pair(accumulatedRotationX, accumulatedRotationY)
            Log.d(TAG, "New model rotation tracking $modelName - X: ${accumulatedRotationX}, Y: ${accumulatedRotationY}")
        }
        
        targetRotationX = accumulatedRotationX
        targetRotationY = accumulatedRotationY
        currentRotationX = accumulatedRotationX
        currentRotationY = accumulatedRotationY
        
        velocityX = 0f
        velocityY = 0f
    }
    
    /**
     * Calculate model bounding height - SceneView version
     */
    private fun calculateModelBoundingHeight(modelNode: ModelNode): Float {
        return try {
            // Use SceneView approach - estimated height based on scale
            val scaleToUnits = 0.3f
            val scaleY = modelNode.scale.y
            val estimatedHeight = scaleToUnits * scaleY * 2f // Approximate height
            
            Log.d(TAG, "Using SceneView height for ${modelNode.name}: ${estimatedHeight}")
            estimatedHeight
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating bounding height: ${e.message}")
            0.4f // Default fallback
        }
    }
    
    /**
     * Clear all cats and reset state
     */
    fun clearAllCats(childNodes: MutableList<Node>, arRenderer: ar.ARSceneViewRenderer) {
        resetRotationState()
        placedModelNodes.clear()
        firstCatModel = null
        firstCatBoundingHeight = 0.4f
        arRenderer.clearAllModels(childNodes)
        arRenderer.planeDetectionStatus.value = "All cats cleared! Distance detection: 0.5m safety zone"
        Log.d(TAG, "All cats cleared - using simplified collision detection")
    }
    
    /**
     * 主要的觸摸處理方法 - 恢復原本的簡單邏輯
     */
    fun handleSceneViewTouchDown(
        motionEvent: MotionEvent,
        hitResult: HitResult?,
        frame: Frame?,
        session: Session?,
        modelLoader: io.github.sceneview.loaders.ModelLoader,
        childNodes: MutableList<Node>,
        engine: com.google.android.filament.Engine,
        arRenderer: ar.ARSceneViewRenderer,
        collisionSystem: CollisionSystem, // 保留參數但不使用，避免修改主程式
        cameraNode: io.github.sceneview.node.CameraNode, // 保留參數但不使用
        onFirstCatCreated: (ModelNode?) -> Unit
    ) {
        lastTouchX = motionEvent.x
        lastTouchY = motionEvent.y
        touchStartX = motionEvent.x
        touchStartY = motionEvent.y
        lastMoveTime = System.currentTimeMillis()
        
        Log.d(TAG, "Touch at: (${motionEvent.x}, ${motionEvent.y})")
        
        // 🔥 恢復你原本的邏輯：先轉換世界座標
        val worldTouchPosition = screenToWorldPosition(motionEvent, frame)
        
        // 🔥 原本邏輯：只有當有現有模型且轉換成功時才檢查選擇
        if (worldTouchPosition != null && placedModelNodes.isNotEmpty()) {
            try {
                // 檢查是否點擊現有模型進行旋轉
                val touchedModel = findModelInTouchRange(worldTouchPosition)
                
                if (touchedModel != null) {
                    Log.d(TAG, "Model selected for rotation: ${touchedModel.name}")
                    
                    // 進入旋轉模式
                    selectedNode = touchedModel
                    isRotating = false
                    setupRotationForModel(touchedModel)
                    
                    arRenderer.planeDetectionStatus.value = "Cat selected: ${touchedModel.name} - rotation mode"
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Model selection failed: ${e.message}")
            }
        }
        
        // 🔥 原本邏輯：檢查重疊（只有當有現有模型時）
        if (worldTouchPosition != null && placedModelNodes.isNotEmpty()) {
            Log.d(TAG, "Checking placement overlap...")
            if (checkPlacementOverlap(worldTouchPosition)) {
                Log.d(TAG, "❌ PLACEMENT BLOCKED - Would overlap with existing model")
                arRenderer.planeDetectionStatus.value = "Cannot place here - too close to existing cat"
                return
            } else {
                Log.d(TAG, "✅ PLACEMENT SAFE - No overlap detected")
            }
        } else {
            if (worldTouchPosition == null) {
                Log.d(TAG, "No world position detected, placing anyway")
            } else {
                Log.d(TAG, "First model, no overlap check needed")
            }
        }
        
        // 🔥 原本邏輯：直接放置新模型（第一個模型會直接通過）
        Log.d(TAG, "Placing new cat")
        runBlocking {
            val newCat = placeCatAtTouch(motionEvent, frame, session, modelLoader, childNodes, engine, arRenderer)
            onFirstCatCreated(newCat)
        }
    }
    
    /**
     * 恢復原本的世界座標轉換方法 - 增強調試
     */
    private fun screenToWorldPosition(motionEvent: MotionEvent, frame: Frame?): Position? {
        Log.d(TAG, "=== screenToWorldPosition DEBUG ===")
        Log.d(TAG, "Frame is null: ${frame == null}")
        
        if (frame == null) {
            Log.e(TAG, "Frame is null!")
            return null
        }
        
        return try {
            Log.d(TAG, "Calling frame.hitTest(${motionEvent.x}, ${motionEvent.y})")
            val hitResults = frame.hitTest(motionEvent.x, motionEvent.y)
            Log.d(TAG, "HitResults count: ${hitResults.size}")
            
            if (hitResults.isNotEmpty()) {
                val hitResult = hitResults.first()
                Log.d(TAG, "First hit result type: ${hitResult.javaClass.simpleName}")
                Log.d(TAG, "Hit result trackable: ${hitResult.trackable?.javaClass?.simpleName}")
                
                val pose = hitResult.hitPose
                val translation = pose.translation
                val position = Position(translation[0], translation[1], translation[2])
                
                Log.d(TAG, "✅ Position found: (${position.x}, ${position.y}, ${position.z})")
                position
            } else {
                Log.w(TAG, "❌ No hit results returned from ARCore")
                
                // 檢查 ARCore 狀態
                val camera = frame.camera
                Log.d(TAG, "Camera tracking state: ${camera.trackingState}")
                
                // 檢查平面檢測
                val planes = frame.getUpdatedTrackables(Plane::class.java)
                Log.d(TAG, "Detected planes count: ${planes.size}")
                
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Hit test exception: ${e.message}", e)
            null
        }
    }
    
    /**
     * 恢復原本的模型範圍檢測方法
     */
    private fun findModelInTouchRange(worldPosition: Position): ModelNode? {
        var closestModel: ModelNode? = null
        var closestDistance = Float.MAX_VALUE
        
        for (modelNode in placedModelNodes) {
            val distance = calculateDistance(worldPosition, modelNode.worldPosition)
            
            Log.d(TAG, "Touch range check ${modelNode.name}: distance=$distance, threshold=$TOUCH_DETECTION_RADIUS")
            
            if (distance <= TOUCH_DETECTION_RADIUS && distance < closestDistance) {
                closestDistance = distance
                closestModel = modelNode
            }
        }
        
        if (closestModel != null) {
            Log.d(TAG, "Touch hit model: ${closestModel.name} at distance: $closestDistance")
        }
        
        return closestModel
    }
    
    /**
     * 恢復原本的重疊檢測方法
     */
    private fun checkPlacementOverlap(newWorldPosition: Position): Boolean {
        return placedModelNodes.any { existingNode ->
            val distance = calculateDistance(newWorldPosition, existingNode.worldPosition)
            val wouldOverlap = distance < SAFE_PLACEMENT_DISTANCE
            
            Log.d(TAG, "Distance to ${existingNode.name}: $distance, overlap: $wouldOverlap")
            wouldOverlap
        }
    }
    
    /**
     * Improved touch move handling - supports 360 degree rotation and velocity damping
     */
    fun handleImprovedTouchMove(motionEvent: MotionEvent) {
        selectedNode?.let { node ->
            val currentTime = System.currentTimeMillis()
            val deltaTime = (currentTime - lastMoveTime).coerceAtLeast(1L)
            
            val deltaX = motionEvent.x - lastTouchX
            val deltaY = motionEvent.y - lastTouchY
            val totalDistance = sqrt(deltaX * deltaX + deltaY * deltaY)
            
            if (totalDistance > MIN_ROTATION_DISTANCE) {
                if (!isRotating) {
                    isRotating = true
                    Log.d(TAG, "Started smooth 360 degree rotation: ${node.name}")
                }
                
                val newVelocityX = deltaX / deltaTime.toFloat()
                val newVelocityY = deltaY / deltaTime.toFloat()
                
                velocityX = velocityX * VELOCITY_DAMPING + newVelocityX * (1f - VELOCITY_DAMPING)
                velocityY = velocityY * VELOCITY_DAMPING + newVelocityY * (1f - VELOCITY_DAMPING)
                
                val rotationDeltaY = velocityX * rotationSensitivityY * deltaTime
                val rotationDeltaX = -velocityY * rotationSensitivityX * deltaTime
                
                accumulatedRotationX += rotationDeltaX
                accumulatedRotationY += rotationDeltaY
                
                targetRotationX = accumulatedRotationX
                targetRotationY = accumulatedRotationY
                
                lastTouchX = motionEvent.x
                lastTouchY = motionEvent.y
                lastMoveTime = currentTime
            }
        }
    }
    
    /**
     * Improved touch release handling
     */
    fun handleImprovedTouchUp(arRenderer: ar.ARSceneViewRenderer) {
        if (isRotating && selectedNode != null) {
            Log.d(TAG, "Rotation complete: ${selectedNode?.name}")
            arRenderer.planeDetectionStatus.value = "Rotation complete! Simplified collision detection normal"
            
            isRotating = false
            velocityX *= 0.5f
            velocityY *= 0.5f
        }
    }
    
    /**
     * Smooth rotation update
     */
    fun updateSmoothRotation() {
        selectedNode?.let { node ->
            currentRotationX += (targetRotationX - currentRotationX) * SMOOTH_FACTOR
            currentRotationY += (targetRotationY - currentRotationY) * SMOOTH_FACTOR
            
            val normalizedX = currentRotationX % 360f
            val normalizedY = currentRotationY % 360f
            
            node.rotation = Rotation(x = normalizedX, y = normalizedY, z = 0f)
            
            val modelName = node.name ?: "unknown"
            modelRotationMap[modelName] = Pair(currentRotationX, currentRotationY)
            
            if (!isRotating && (abs(velocityX) > MIN_VELOCITY_THRESHOLD || abs(velocityY) > MIN_VELOCITY_THRESHOLD)) {
                accumulatedRotationX += velocityX * rotationSensitivityX * 16f
                accumulatedRotationY += velocityY * rotationSensitivityY * 16f
                
                targetRotationX = accumulatedRotationX
                targetRotationY = accumulatedRotationY
                
                velocityX *= 0.95f
                velocityY *= 0.95f
                
                modelRotationMap[modelName] = Pair(accumulatedRotationX, accumulatedRotationY)
            }
        }
    }
    
    // Public methods
    
    /**
     * Get model's screen position (for dialog positioning) - SceneView version
     */
    fun getModelScreenPosition(modelName: String): Offset? {
        // For SceneView, we can project world position to screen
        val model = placedModelNodes.find { it.name == modelName }
        return model?.let {
            // Return center of screen as approximation - you can improve this with proper projection
            Offset(540f, 1000f)
        }
    }
    
    /**
     * Configure collision detection parameters
     */
    fun configureCollisionDetection(
        safePlacementDistance: Float = SAFE_PLACEMENT_DISTANCE,
        touchDetectionRadius: Float = TOUCH_DETECTION_RADIUS
    ) {
        // 更新常量值（在實際實現中，你可能需要使用可變的變量）
        Log.d(TAG, "Collision detection configured: placement=$safePlacementDistance, touch=$touchDetectionRadius")
    }
    
    /**
     * Debug method: print all models' collision information
     */
    fun debugCollisionDetection() {
        Log.d(TAG, "Debug simplified collision detection information")
        
        for (modelNode in placedModelNodes) {
            val worldPos = modelNode.worldPosition
            
            Log.d(TAG, "Model: ${modelNode.name}")
            Log.d(TAG, "  World position: (${worldPos.x}, ${worldPos.y}, ${worldPos.z})")
            Log.d(TAG, "  Scale: ${modelNode.scale}")
        }
        
        Log.d(TAG, "Collision detection parameters:")
        Log.d(TAG, "  Safe placement distance: $SAFE_PLACEMENT_DISTANCE")
        Log.d(TAG, "  Touch detection radius: $TOUCH_DETECTION_RADIUS")
        
        Log.d(TAG, "Debug complete")
    }
    
    /**
     * Check if a position is valid for placement
     */
    fun isValidPlacementPosition(
        worldPosition: Position,
        collisionSystem: CollisionSystem? = null
    ): Boolean {
        return placedModelNodes.all { existingModel ->
            val distance = calculateDistance(worldPosition, existingModel.worldPosition)
            distance >= SAFE_PLACEMENT_DISTANCE
        }
    }
    
    // Getter methods for MainActivity access
    fun getSelectedNode(): ModelNode? = selectedNode
    fun getFirstCatModel(): ModelNode? = firstCatModel
    fun getFirstCatBoundingHeight(): Float = firstCatBoundingHeight
    fun getPlacedModelsCount(): Int = placedModelNodes.size
    
    fun resetSensitivityToDefault() {
        rotationSensitivityX = ROTATION_SENSITIVITY_X
        rotationSensitivityY = ROTATION_SENSITIVITY_Y
    }
    
    private fun resetRotationState() {
        selectedNode = null
        isRotating = false
        accumulatedRotationX = 0f
        accumulatedRotationY = 0f
        velocityX = 0f
        velocityY = 0f
        targetRotationX = 0f
        targetRotationY = 0f
        currentRotationX = 0f
        currentRotationY = 0f
        modelRotationMap.clear()
    }
    
    private suspend fun placeCatAtTouch(
        motionEvent: MotionEvent?,
        frame: Frame?,
        session: Session?,
        modelLoader: io.github.sceneview.loaders.ModelLoader,
        childNodes: MutableList<Node>,
        engine: com.google.android.filament.Engine,
        arRenderer: ar.ARSceneViewRenderer
    ): ModelNode? {
        if (frame == null || session == null) {
            Log.w(TAG, "Frame or Session is null")
            return null
        }
        
        try {
            val touchX = motionEvent?.x ?: 540f
            val touchY = motionEvent?.y ?: 1200f
            
            Log.d(TAG, "=== PLACEMENT ATTEMPT ===")
            Log.d(TAG, "Touch: ($touchX, $touchY)")
            
            var placedModel: ModelNode? = null
            var placementPosition: Position? = null
            
            // 🔥 修正：統一的碰撞檢測邏輯
            fun isPositionSafe(position: Position): Boolean {
                if (placedModelNodes.isEmpty()) return true
                
                val wouldOverlap = placedModelNodes.any { existingModel ->
                    val distance = calculateDistance(position, existingModel.worldPosition)
                    val tooClose = distance < SAFE_PLACEMENT_DISTANCE
                    
                    Log.d(TAG, "Distance to ${existingModel.name}: $distance (threshold: $SAFE_PLACEMENT_DISTANCE), too close: $tooClose")
                    tooClose
                }
                
                if (wouldOverlap) {
                    Log.d(TAG, "❌ Position unsafe - too close to existing models")
                    return false
                }
                
                Log.d(TAG, "✅ Position safe - can place model")
                return true
            }
            
            // 第一種方法：標準 hit test
            val hitResults = frame.hitTest(touchX, touchY)
            Log.d(TAG, "Standard hit results: ${hitResults.size}")
            
            for (hitResult in hitResults) {
                try {
                    val pose = hitResult.hitPose
                    placementPosition = Position(pose.translation[0], pose.translation[1], pose.translation[2])
                    
                    Log.d(TAG, "Checking standard hit test position: $placementPosition")
                    
                    if (isPositionSafe(placementPosition)) {
                        val anchor = hitResult.createAnchor()
                        placedModel = createCatModel(anchor, modelLoader, childNodes, engine, arRenderer)
                        Log.d(TAG, "✅ Using standard hit test")
                        if (placedModel != null) break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Standard placement failed: ${e.message}")
                    continue
                }
            }
            
            // 第二種方法：Instant Placement
            if (placedModel == null) {
                Log.d(TAG, "Trying instant placement...")
                try {
                    val instantResults = frame.hitTestInstantPlacement(touchX, touchY, 1.0f)
                    Log.d(TAG, "Instant placement results: ${instantResults.size}")
                    
                    if (instantResults.isNotEmpty()) {
                        val instantResult = instantResults.first()
                        val pose = instantResult.hitPose
                        placementPosition = Position(pose.translation[0], pose.translation[1], pose.translation[2])
                        
                        Log.d(TAG, "Checking instant placement position: $placementPosition")
                        
                        if (isPositionSafe(placementPosition)) {
                            val anchor = instantResult.createAnchor()
                            placedModel = createCatModel(anchor, modelLoader, childNodes, engine, arRenderer)
                            Log.d(TAG, "✅ Using instant placement")
                        } else {
                            Log.d(TAG, "❌ Instant placement blocked by distance check")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Instant placement failed: ${e.message}")
                }
            }
            
            // 第三種方法：相機前方放置
            if (placedModel == null) {
                Log.d(TAG, "Trying camera forward placement...")
                try {
                    val camera = frame.camera
                    val cameraPosition = camera.pose
                    val translation = floatArrayOf(0f, 0f, -1f)
                    val rotation = floatArrayOf(0f, 0f, 0f, 1f)
                    val forwardPose = cameraPosition.compose(Pose(translation, rotation))
                    
                    placementPosition = Position(
                        forwardPose.translation[0],
                        forwardPose.translation[1],
                        forwardPose.translation[2]
                    )
                    
                    Log.d(TAG, "Checking camera forward position: $placementPosition")
                    
                    if (isPositionSafe(placementPosition)) {
                        val anchor = session.createAnchor(forwardPose)
                        placedModel = createCatModel(anchor, modelLoader, childNodes, engine, arRenderer)
                        Log.d(TAG, "✅ Using camera forward placement")
                    } else {
                        Log.d(TAG, "❌ Camera forward placement blocked by distance check")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Camera forward placement failed: ${e.message}")
                }
            }
            
            if (placedModel != null) {
                Log.d(TAG, "🎉 Model placed successfully at: $placementPosition")
            } else {
                Log.d(TAG, "❌ All placement methods failed or blocked by distance checks")
            }
            
            return placedModel
            
        } catch (e: Exception) {
            Log.e(TAG, "Placement error: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 檢查位置是否會與現有模型重疊
     */
    private fun checkPositionOverlap(newPosition: Position): Boolean {
        return placedModelNodes.any { existingModel ->
            val distance = calculateDistance(newPosition, existingModel.worldPosition)
            val wouldOverlap = distance < SAFE_PLACEMENT_DISTANCE
            
            Log.d(TAG, "Distance to ${existingModel.name}: $distance (threshold: $SAFE_PLACEMENT_DISTANCE), overlap: $wouldOverlap")
            wouldOverlap
        }
    }
    
    private fun createCatModel(
        anchor: Anchor,
        modelLoader: io.github.sceneview.loaders.ModelLoader,
        childNodes: MutableList<Node>,
        engine: com.google.android.filament.Engine,
        arRenderer: ar.ARSceneViewRenderer
    ): ModelNode? {
        return try {
            val modelInstance = modelLoader.createModelInstance(GLB_MODEL_PATH)
            if (modelInstance != null) {
                val anchorNode = AnchorNode(engine = engine, anchor = anchor)
                
                val modelNode = ModelNode(
                    modelInstance = modelInstance,
                    scaleToUnits = 0.3f
                ).apply {
                    name = "cat_model_${arRenderer.placedModelsCount.value + 1}"
                    rotation = Rotation(x = 0f, y = 90f, z = 0f)
                    position = Position(x = 0f, y = 0f, z = 0f)
                }
                
                placedModelNodes.add(modelNode)
                anchorNode.addChildNode(modelNode)
                childNodes.add(anchorNode)
                
                arRenderer.placedModelsCount.value++
                Log.d(TAG, "Cat #${arRenderer.placedModelsCount.value} placed: ${modelNode.name}")
                
                val statusMessage = if (arRenderer.placedModelsCount.value == 1) {
                    "First cat placed! Simplified collision detection activated"
                } else {
                    "Cat #${arRenderer.placedModelsCount.value} placed! Simplified collision detection"
                }
                
                arRenderer.planeDetectionStatus.value = statusMessage
                
                // Set as first cat if it's the first one
                if (firstCatModel == null) {
                    firstCatModel = modelNode
                    firstCatBoundingHeight = calculateModelBoundingHeight(modelNode)
                    Log.d(TAG, "First cat set for dialog binding: ${modelNode.name}, height: ${firstCatBoundingHeight}")
                }
                
                modelNode
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model creation failed: ${e.message}", e)
            null
        }
    }
}