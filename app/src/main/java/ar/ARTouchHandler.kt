package ar

import android.util.Log
import android.view.MotionEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import android.graphics.RectF
import com.google.ar.core.*
import io.github.sceneview.collision.HitResult
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.utils.worldToScreen
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.sqrt

class ARTouchHandler {
    
    companion object {
        private const val TAG = "ARTouchHandler"
        private const val GLB_MODEL_PATH = "cute_spooky_cat.glb"
        
        private const val ROTATION_SENSITIVITY_X = 0.3f
        private const val ROTATION_SENSITIVITY_Y = 0.5f
        private const val MIN_ROTATION_DISTANCE = 10f
        
        private const val VELOCITY_DAMPING = 0.85f
        private const val SMOOTH_FACTOR = 0.15f
        private const val MIN_VELOCITY_THRESHOLD = 0.01f
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
    
    // === 新增：精確邊界檢測系統 ===
    
    // 模型邊界數據結構
    data class ModelBounds(
        val centerScreen: Offset,
        val touchRect: RectF,
        val worldBoundingSize: Float
    )
    
    // 存儲每個模型的計算邊界
    private val modelBoundsCache = mutableMapOf<String, ModelBounds>()
    
    // 邊界計算參數（可調整）
    private var BOUNDS_PADDING_FACTOR = 1.2f  // 觸摸區域比實際模型大 20%
    private var MIN_TOUCH_SIZE = 100f  // 最小觸摸區域（像素）
    private var MAX_TOUCH_SIZE = 200f  // 最大觸摸區域（像素）
    
    /**
     * 使用 SceneView 的 worldToScreen API 計算模型邊界 - 修正版本
     */
    private fun updateModelBounds(
        sceneView: io.github.sceneview.SceneView
    ) {
        for (modelNode in placedModelNodes) {
            try {
                val modelName = modelNode.name ?: continue
                
                // 修正：使用 sceneView.view 來調用 worldToScreen 擴展函數
                val worldPosition = modelNode.worldPosition
                val screenPosition = sceneView.view.worldToScreen(worldPosition)
                
                // 計算模型的世界空間大小
                val worldBoundingSize = calculateWorldBoundingSize(modelNode)
                
                // 將世界空間大小轉換為螢幕空間大小
                val screenBoundingSize = calculateScreenBoundingSize(
                    worldBoundingSize, 
                    worldPosition, 
                    sceneView
                )
                
                // 創建觸摸矩形（比模型稍大）
                val touchSize = (screenBoundingSize * BOUNDS_PADDING_FACTOR)
                    .coerceIn(MIN_TOUCH_SIZE, MAX_TOUCH_SIZE)
                
                val touchRect = RectF(
                    screenPosition.x - touchSize / 2,
                    screenPosition.y - touchSize / 2,
                    screenPosition.x + touchSize / 2,
                    screenPosition.y + touchSize / 2
                )
                
                // 儲存邊界信息
                modelBoundsCache[modelName] = ModelBounds(
                    centerScreen = Offset(screenPosition.x, screenPosition.y),
                    touchRect = touchRect,
                    worldBoundingSize = worldBoundingSize
                )
                
                Log.d(TAG, "Updated bounds for $modelName: screen(${screenPosition.x}, ${screenPosition.y}), touch size: $touchSize")
                
            } catch (e: Exception) {
                Log.w(TAG, "Error updating bounds for ${modelNode.name}: ${e.message}")
            }
        }
    }
    
    /**
     * 計算模型的世界空間邊界大小
     */
    private fun calculateWorldBoundingSize(modelNode: ModelNode): Float {
        return try {
            // 基於 scaleToUnits 和 scale 計算實際大小
            val scaleToUnits = 0.3f  // 您代碼中使用的值
            val nodeScale = modelNode.scale
            val actualSize = scaleToUnits * maxOf(nodeScale.x, nodeScale.y, nodeScale.z)
            
            Log.d(TAG, "World bounding size for ${modelNode.name}: $actualSize")
            actualSize
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating world bounding size: ${e.message}")
            0.3f  // 默認大小
        }
    }
    
    /**
     * 將世界空間大小轉換為螢幕空間大小（像素）- 修正版本
     */
    private fun calculateScreenBoundingSize(
        worldSize: Float,
        worldPosition: Position,
        sceneView: io.github.sceneview.SceneView
    ): Float {
        return try {
            // 修正：使用 sceneView.view 來調用 worldToScreen 擴展函數
            val centerScreen = sceneView.view.worldToScreen(worldPosition)
            val edgeWorldPos = Position(
                worldPosition.x + worldSize / 2,
                worldPosition.y,
                worldPosition.z
            )
            val edgeScreen = sceneView.view.worldToScreen(edgeWorldPos)
            
            // 計算螢幕空間中的大小
            val screenSize = kotlin.math.abs(edgeScreen.x - centerScreen.x) * 2
            
            Log.d(TAG, "Screen bounding size: $screenSize pixels")
            screenSize
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating screen bounding size: ${e.message}")
            120f  // 默認螢幕大小
        }
    }
    
    /**
     * 精確的模型觸摸檢測 - 替換原來的 findTouchedModel
     */
    private fun findTouchedModelByCalculatedBounds(screenX: Float, screenY: Float): ModelNode? {
        for (modelNode in placedModelNodes) {
            try {
                val modelName = modelNode.name ?: continue
                val bounds = modelBoundsCache[modelName] ?: continue
                
                // 檢查觸摸點是否在計算的邊界內
                if (bounds.touchRect.contains(screenX, screenY)) {
                    Log.d(TAG, "Hit model: $modelName at screen($screenX, $screenY)")
                    Log.d(TAG, "Model bounds: ${bounds.touchRect}")
                    return modelNode
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in bounds detection: ${e.message}")
                continue
            }
        }
        return null
    }
    
    /**
     * 簡化版邊界大小計算
     */
    private fun calculateSimplifiedBoundingSize(modelNode: ModelNode): Float {
        return try {
            val scaleToUnits = 0.3f  // 您代碼中使用的值
            val nodeScale = modelNode.scale
            scaleToUnits * maxOf(nodeScale.x, nodeScale.y, nodeScale.z)
        } catch (e: Exception) {
            0.3f  // 默認大小
        }
    }
    
    // === 保留原有的模型邊界高度計算方法 ===
    
    /**
     * Calculate model bounding height - simplified version using estimated value
     */
    private fun calculateModelBoundingHeight(modelNode: ModelNode): Float {
        return try {
            // Based on fixed estimated height since can't directly access scaleToUnits property
            val estimatedHeight = 0.4f
            Log.d(TAG, "Using estimated height for ${modelNode.name}: ${estimatedHeight}")
            estimatedHeight
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating bounding height: ${e.message}")
            0.3f
        }
    }
    
    /**
     * Clear all cats and reset state
     */
    fun clearAllCats(childNodes: MutableList<Node>, arRenderer: ar.ARSceneViewRenderer) {
        resetRotationState()
        placedModelNodes.clear()
        modelBoundsCache.clear()  // 新增：清理邊界快取
        firstCatModel = null
        firstCatBoundingHeight = 0.4f
        arRenderer.clearAllModels(childNodes)
        arRenderer.planeDetectionStatus.value = "All cats cleared! Precise bounds detection ready"
        Log.d(TAG, "All cats and bounds cleared")
    }
    
    /**
     * 修改後的觸摸處理方法 - 整合精確邊界檢測
     */
    fun handleImprovedTouchDown(
        motionEvent: MotionEvent,
        hitResult: HitResult?,
        frame: Frame?,
        session: Session?,
        modelLoader: io.github.sceneview.loaders.ModelLoader,
        childNodes: MutableList<Node>,
        engine: com.google.android.filament.Engine,
        arRenderer: ar.ARSceneViewRenderer,
        onFirstCatCreated: (ModelNode?) -> Unit,
        sceneView: io.github.sceneview.SceneView? = null,  // SceneView 參數
        filamentView: com.google.android.filament.View? = null  // 新增 Filament View 參數
    ) {
        lastTouchX = motionEvent.x
        lastTouchY = motionEvent.y
        touchStartX = motionEvent.x
        touchStartY = motionEvent.y
        lastMoveTime = System.currentTimeMillis()
        
        // 選擇觸摸檢測方法
        val touchedModel = if ((sceneView != null || filamentView != null) && placedModelNodes.isNotEmpty()) {
            // 如果提供了 SceneView 或 Filament View，使用精確邊界檢測
            if (sceneView != null) {
                updateModelBounds(sceneView)
            } else if (filamentView != null) {
                updateModelBoundsWithFilamentView(filamentView)
            }
            findTouchedModelByCalculatedBounds(motionEvent.x, motionEvent.y)
        } else {
            // 否則直接返回 null 以總是放置新模型
            null
        }
        
        if (touchedModel != null) {
            Log.d(TAG, "Model selected for rotation: ${touchedModel.name}")
            selectedNode = touchedModel
            isRotating = false
            
            // Get accumulated rotation values from stored mapping
            val modelName = touchedModel.name ?: "unknown"
            val storedRotation = modelRotationMap[modelName]
            
            if (storedRotation != null) {
                accumulatedRotationX = storedRotation.first
                accumulatedRotationY = storedRotation.second
                Log.d(TAG, "Restored rotation for $modelName - X: ${accumulatedRotationX}, Y: ${accumulatedRotationY}")
            } else {
                val currentRotation = touchedModel.rotation
                accumulatedRotationX = currentRotation.x
                accumulatedRotationY = currentRotation.y
                modelRotationMap[modelName] = Pair(accumulatedRotationX, accumulatedRotationY)
                Log.d(TAG, "New model rotation tracking for $modelName - X: ${accumulatedRotationX}, Y: ${accumulatedRotationY}")
            }
            
            targetRotationX = accumulatedRotationX
            targetRotationY = accumulatedRotationY
            currentRotationX = accumulatedRotationX
            currentRotationY = accumulatedRotationY
            
            velocityX = 0f
            velocityY = 0f
            
            val detectionMethod = if (sceneView != null || filamentView != null) "Precise bounds detection" else "Basic detection"
            arRenderer.planeDetectionStatus.value = "Cat selected: ${touchedModel.name} - $detectionMethod"
            return
        }
        
        // If no model touched, place new model
        runBlocking {
            val newCat = placeCatAtTouch(motionEvent, frame, session, modelLoader, childNodes, engine, arRenderer)
            onFirstCatCreated(newCat)
        }
    }
    
    /**
     * 使用 Filament View 來計算模型邊界 - 新增方法
     */
    private fun updateModelBoundsWithFilamentView(
        filamentView: com.google.android.filament.View
    ) {
        for (modelNode in placedModelNodes) {
            try {
                val modelName = modelNode.name ?: continue
                
                // 使用 Filament View 來調用 worldToScreen 擴展函數
                val worldPosition = modelNode.worldPosition
                val screenPosition = filamentView.worldToScreen(worldPosition)
                
                // 計算模型的世界空間大小
                val worldBoundingSize = calculateWorldBoundingSize(modelNode)
                
                // 將世界空間大小轉換為螢幕空間大小
                val screenBoundingSize = calculateScreenBoundingSizeWithFilamentView(
                    worldBoundingSize, 
                    worldPosition, 
                    filamentView
                )
                
                // 創建觸摸矩形（比模型稍大）
                val touchSize = (screenBoundingSize * BOUNDS_PADDING_FACTOR)
                    .coerceIn(MIN_TOUCH_SIZE, MAX_TOUCH_SIZE)
                
                val touchRect = RectF(
                    screenPosition.x - touchSize / 2,
                    screenPosition.y - touchSize / 2,
                    screenPosition.x + touchSize / 2,
                    screenPosition.y + touchSize / 2
                )
                
                // 儲存邊界信息
                modelBoundsCache[modelName] = ModelBounds(
                    centerScreen = Offset(screenPosition.x, screenPosition.y),
                    touchRect = touchRect,
                    worldBoundingSize = worldBoundingSize
                )
                
                Log.d(TAG, "Updated bounds for $modelName using Filament View: screen(${screenPosition.x}, ${screenPosition.y}), touch size: $touchSize")
                
            } catch (e: Exception) {
                Log.w(TAG, "Error updating bounds for ${modelNode.name} with Filament View: ${e.message}")
            }
        }
    }
    
    /**
     * 使用 Filament View 將世界空間大小轉換為螢幕空間大小
     */
    private fun calculateScreenBoundingSizeWithFilamentView(
        worldSize: Float,
        worldPosition: Position,
        filamentView: com.google.android.filament.View
    ): Float {
        return try {
            // 使用 Filament View 來調用 worldToScreen 擴展函數
            val centerScreen = filamentView.worldToScreen(worldPosition)
            val edgeWorldPos = Position(
                worldPosition.x + worldSize / 2,
                worldPosition.y,
                worldPosition.z
            )
            val edgeScreen = filamentView.worldToScreen(edgeWorldPos)
            
            // 計算螢幕空間中的大小
            val screenSize = kotlin.math.abs(edgeScreen.x - centerScreen.x) * 2
            
            Log.d(TAG, "Screen bounding size with Filament View: $screenSize pixels")
            screenSize
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating screen bounding size with Filament View: ${e.message}")
            120f  // 默認螢幕大小
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
            Log.d(TAG, "Smooth rotation completed for: ${selectedNode?.name}")
            arRenderer.planeDetectionStatus.value = "Rotation completed! Precise bounds detection"
            
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
    
    // === 新增：公開方法用於整合對話框系統 ===
    
    /**
     * 獲取模型的螢幕位置（用於對話框定位）
     */
    fun getModelScreenPosition(modelName: String): Offset? {
        return modelBoundsCache[modelName]?.centerScreen
    }
    
    /**
     * 獲取模型的觸摸邊界（用於調試或UI顯示）
     */
    fun getModelTouchBounds(modelName: String): RectF? {
        return modelBoundsCache[modelName]?.touchRect
    }
    
    /**
     * 調整觸摸邊界參數
     */
    fun configureBoundingParameters(
        paddingFactor: Float = 1.2f,
        minTouchSize: Float = 100f,
        maxTouchSize: Float = 200f
    ) {
        BOUNDS_PADDING_FACTOR = paddingFactor
        MIN_TOUCH_SIZE = minTouchSize
        MAX_TOUCH_SIZE = maxTouchSize
        Log.d(TAG, "Bounds parameters updated: padding=$paddingFactor, min=$minTouchSize, max=$maxTouchSize")
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
    
    // === 移除原來有問題的 findTouchedModel 方法 ===
    // 原來的固定區域檢測方法已被 findTouchedModelByCalculatedBounds 替換
    
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
            
            val hitResults = frame.hitTest(touchX, touchY)
            var placedModel: ModelNode? = null
            
            for (hitResult in hitResults) {
                try {
                    val anchor = hitResult.createAnchor()
                    placedModel = createCatModel(anchor, modelLoader, childNodes, engine, arRenderer)
                    if (placedModel != null) break
                } catch (e: Exception) {
                    continue
                }
            }
            
            if (placedModel == null) {
                try {
                    val instantResults = frame.hitTestInstantPlacement(touchX, touchY, 1.0f)
                    if (instantResults.isNotEmpty()) {
                        val anchor = instantResults.first().createAnchor()
                        placedModel = createCatModel(anchor, modelLoader, childNodes, engine, arRenderer)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Instant placement failed: ${e.message}")
                }
            }
            
            if (placedModel == null) {
                try {
                    val camera = frame.camera
                    val cameraPosition = camera.pose
                    val translation = floatArrayOf(0f, 0f, -1f)
                    val rotation = floatArrayOf(0f, 0f, 0f, 1f)
                    val forwardPose = cameraPosition.compose(Pose(translation, rotation))
                    val anchor = session.createAnchor(forwardPose)
                    placedModel = createCatModel(anchor, modelLoader, childNodes, engine, arRenderer)
                } catch (e: Exception) {
                    Log.e(TAG, "All placement methods failed: ${e.message}")
                }
            }
            
            return placedModel
            
        } catch (e: Exception) {
            Log.e(TAG, "Placement error: ${e.message}", e)
            return null
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
                    "First cat placed! Precise bounds detection active"
                } else {
                    "Cat #${arRenderer.placedModelsCount.value} placed! Precise bounds detection for all cats"
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