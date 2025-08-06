package ar

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.Camera
import com.google.ar.core.TrackingState
import com.google.ar.core.HitResult
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.collision.Vector3
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AR 場景渲染器 - 使用 SceneView 庫簡化 ARCore 整合
 * 
 * SceneView 優點：
 * 1. 自動處理 ARCore 和 Filament 的整合
 * 2. 簡化模型載入和放置
 * 3. 內建手勢控制
 * 4. 自動處理生命週期
 */
class ARSceneViewRenderer(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    
    companion object {
        private const val TAG = "ARSceneViewRenderer"
        private const val MAX_MODELS = 10  // 最多同時顯示的模型數量
    }
    
    // AR 場景視圖
    lateinit var arSceneView: ARSceneView
        private set
    
    // 模型載入器
    private lateinit var modelLoader: ModelLoader
    private lateinit var materialLoader: MaterialLoader
    
    // 已載入的模型實例
    private var loadedModelInstance: ModelInstance? = null
    
    // 放置的模型節點列表
    private val placedModels = mutableListOf<AnchorNode>()
    
    // ✅ 重要：在 SceneView 2.3.0 中，需要手動保存當前 frame
    public var currentFrame: Frame? = null
    
    // 狀態和回調
    var isInitialized = false
        private set
    
    var onPlaneDetected: ((Int) -> Unit)? = null
    var onTrackingStateChanged: ((String) -> Unit)? = null
    var onModelPlaced: ((Float3) -> Unit)? = null
    var onTap: ((MotionEvent, ARSceneView) -> Unit)? = null
    
    /**
     * 創建並初始化 AR 場景視圖
     */
    fun createARSceneView(): ARSceneView {
        Log.d(TAG, "🔧 創建 ARSceneView...")
        
        arSceneView = ARSceneView(context).apply {
            // 設置生命週期（自動管理）
            lifecycle = lifecycleOwner.lifecycle
            
            // 配置 AR Session
            configureSession { session, config ->
                // 平面偵測設置
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                
                // 深度模式（如果設備支援）
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    config.depthMode = Config.DepthMode.AUTOMATIC
                }
                
                // 光照估計
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                
                // 即時放置（無需等待平面偵測）
                config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                
                Log.d(TAG, "✅ AR Session 配置完成")
            }
            
            // 設置平面渲染器（可視化偵測到的平面）
            planeRenderer.isEnabled = true
            planeRenderer.isVisible = true
            planeRenderer.isShadowReceiver = true
            
            // 處理相機權限 (SceneView 2.3.0 移除了 instructions)
            // 改用自定義文字提示
            
            // 設置手勢監聽器
            setOnTouchListener { _, event ->
                handleTouch(event)
                true
            }
            
            // 追蹤狀態變更
            onTrackingFailureChanged = { reason ->
                val description = reason?.getDescription(context) ?: "追蹤正常"
                Log.d(TAG, "📍 追蹤狀態: $description")
                onTrackingStateChanged?.invoke(description)
            }
            
            // ✅ 關鍵：平面偵測和 frame 更新回調
            onSessionUpdated = { session, frame ->
                // 保存當前 frame 供其他方法使用
                currentFrame = frame
                
                // 獲取更新的平面
                val updatedPlanes = frame.getUpdatedPlanes()
                if (updatedPlanes.isNotEmpty()) {
                    val planeCount = session.allAnchors.count { 
                        it.trackingState == com.google.ar.core.TrackingState.TRACKING 
                    }
                    onPlaneDetected?.invoke(planeCount)
                }
            }
        }
        
        // 初始化載入器
        modelLoader = ModelLoader(arSceneView.engine, context)
        materialLoader = MaterialLoader(arSceneView.engine, context)
        
        isInitialized = true
        Log.d(TAG, "✅ ARSceneView 初始化完成")
        
        return arSceneView
    }
    
    /**
     * 預載入 GLB 模型
     */
    suspend fun preloadModel(assetPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔧 開始載入模型: $assetPath")
                
                // 使用 ModelLoader 載入 GLB
                val modelInstance = modelLoader.createModelInstance(
                    assetFileLocation = assetPath
                )
                
                if (modelInstance != null) {
                    loadedModelInstance = modelInstance
                    Log.d(TAG, "✅ 模型載入成功")
                    true
                } else {
                    Log.e(TAG, "❌ 模型載入失敗")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 載入模型異常: ${e.message}")
                false
            }
        }
    }
    
    /**
     * 處理觸摸事件 - 在點擊位置放置模型
     */
    private fun handleTouch(event: MotionEvent): Boolean {
        // 自定義觸摸處理
        onTap?.invoke(event, arSceneView)
        
        // 單擊放置模型
        if (event.action == MotionEvent.ACTION_UP) {
            placeModelAtTouch(event.x, event.y)
        }
        
        return true
    }
    
    /**
     * 在觸摸點放置模型
     */
    fun placeModelAtTouch(x: Float, y: Float) {
        if (!isInitialized || loadedModelInstance == null) {
            Log.w(TAG, "⚠️ 系統未初始化或模型未載入")
            return
        }
        
        // 檢查是否達到最大模型數量
        if (placedModels.size >= MAX_MODELS) {
            Log.w(TAG, "⚠️ 已達到最大模型數量，移除最舊的模型")
            removeOldestModel()
        }
        
        // ✅ 使用保存的 currentFrame（由 onSessionUpdated 更新）
        val frame = currentFrame ?: return
        
        // 執行命中測試
        val hitResults = frame.hitTest(x, y)
        
        // 找到第一個平面命中點
        val hitResult = hitResults.firstOrNull { hit: HitResult ->
            when (val trackable = hit.trackable) {
                is Plane -> {
                    trackable.isPoseInPolygon(hit.hitPose) &&
                    trackable.type in listOf(
                        Plane.Type.HORIZONTAL_UPWARD_FACING,
                        Plane.Type.HORIZONTAL_DOWNWARD_FACING,
                        Plane.Type.VERTICAL
                    )
                }
                else -> false
            }
        }
        
        if (hitResult != null) {
            // 創建錨點 - 使用正確的 createAnchorOrNull 擴展函數
            val anchor = hitResult.trackable.createAnchorOrNull(hitResult.hitPose)
            if (anchor != null) {
                placeModelAtAnchor(anchor)
            }
        } else {
            // 如果沒有偵測到平面，使用即時放置
            val instantHit = hitResults.firstOrNull()
            if (instantHit != null) {
                val anchor = instantHit.trackable.createAnchorOrNull(instantHit.hitPose)
                if (anchor != null) {
                    Log.d(TAG, "📍 使用即時放置模式")
                    placeModelAtAnchor(anchor)
                }
            }
        }
    }
    
    /**
     * 在錨點放置模型
     */
    private fun placeModelAtAnchor(anchor: com.google.ar.core.Anchor) {
        val modelInstance = loadedModelInstance ?: return
        
        try {
            // 創建錨點節點
            val anchorNode = AnchorNode(arSceneView.engine, anchor).apply {
                isEditable = true  // 允許編輯（縮放、旋轉）
            }
            
            // 創建模型節點
            val modelNode = ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 0.5f  // 縮放到 0.5 米
            ).apply {
                // 設置模型屬性
                isEditable = true
                
                // 添加動畫（如果模型有動畫）
                if (animationCount > 0) {
                    playAnimation(0)  // 播放第一個動畫
                }
                
                // 設置位置（相對於錨點）
                position = Float3(0f, 0f, 0f)
                
                // 可選：添加旋轉
                // rotation = Float3(0f, 180f, 0f)  // Y軸旋轉180度
            }
            
            // 將模型添加到錨點
            anchorNode.addChildNode(modelNode)
            
            // 將錨點添加到場景
            arSceneView.addChildNode(anchorNode)
            
            // 保存到列表
            placedModels.add(anchorNode)
            
            // 回調
            val worldPosition = anchorNode.worldPosition
            onModelPlaced?.invoke(Float3(worldPosition.x, worldPosition.y, worldPosition.z))
            
            Log.d(TAG, "✅ 模型已放置，當前模型數: ${placedModels.size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 放置模型失敗: ${e.message}")
        }
    }
    
    /**
     * 移除最舊的模型
     */
    private fun removeOldestModel() {
        if (placedModels.isNotEmpty()) {
            val oldestModel = placedModels.removeAt(0)
            arSceneView.removeChildNode(oldestModel)
            oldestModel.anchor?.detach()
            oldestModel.destroy()
            Log.d(TAG, "🗑️ 移除最舊的模型")
        }
    }
    
    /**
     * 清除所有模型
     */
    fun clearAllModels() {
        placedModels.forEach { node ->
            arSceneView.removeChildNode(node)
            node.anchor?.detach()
            node.destroy()
        }
        placedModels.clear()
        Log.d(TAG, "🗑️ 清除所有模型")
    }
    
    /**
     * 切換平面可視化
     */
    fun togglePlaneVisibility() {
        arSceneView.planeRenderer.isVisible = !arSceneView.planeRenderer.isVisible
        Log.d(TAG, "👁️ 平面可視化: ${arSceneView.planeRenderer.isVisible}")
    }
    
    /**
     * 設置指導文字（2.3.0 版本已移除 instructions，改為自定義實現）
     */
    fun setInstructionText(text: String) {
        // SceneView 2.3.0 已移除 instructions
        // 可以在 UI 層自行實現提示文字
        Log.d(TAG, "提示: $text")
    }
    
    /**
     * 獲取當前偵測到的平面數量
     */
    fun getDetectedPlanesCount(): Int {
        return arSceneView.session?.allAnchors?.count { 
            it.trackingState == com.google.ar.core.TrackingState.TRACKING 
        } ?: 0
    }
    
    /**
     * 檢查 AR 是否正在追蹤
     */
    fun isTracking(): Boolean {
        // ✅ 使用保存的 currentFrame
        return currentFrame?.camera?.trackingState == com.google.ar.core.TrackingState.TRACKING
    }
    
    /**
     * 銷毀資源
     */
    fun destroy() {
        clearAllModels()
        // SceneView 2.3.0 的 ModelInstance 不需要手動 destroy
        loadedModelInstance = null
        currentFrame = null
        modelLoader.destroy()
        materialLoader.destroy()
        isInitialized = false
        Log.d(TAG, "🗑️ 資源已銷毀")
    }
}