package com.example.ibm_project

import android.content.Context
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.*
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.utils.Utils
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.*

class FilamentViewer(private val context: Context) {

    companion object {
        private const val TAG = "FilamentViewer"
        init { 
            Log.e("FILAMENT_INIT", "🚀 FilamentViewer class 開始載入")
            try {
                Utils.init()
                Log.e("FILAMENT_INIT", "✅ Utils.init() 完成")
            } catch (e: Exception) {
                Log.e("FILAMENT_INIT", "❌ Utils.init() 失敗: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var uiHelper: UiHelper
    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var view: View
    private lateinit var camera: Camera
    private var swapChain: SwapChain? = null
    private var isInitialized = false
    
    // 儲存 GLB 資產和位置回調
    private var loadedAsset: FilamentAsset? = null
    var onModelPositionUpdated: ((Pair<Float, Float>?) -> Unit)? = null

    init {
        Log.e("FILAMENT_INIT", "🔧 FilamentViewer 實例創建中...")
    }

    fun getSurfaceView(): SurfaceView {
        Log.e("FILAMENT_INIT", "🔧 getSurfaceView() 被調用")
        if (!::surfaceView.isInitialized) {
            Log.e("FILAMENT_INIT", "🔧 開始初始化 SurfaceView...")
            surfaceView = SurfaceView(context)
            setupFilament()
            setupScene()
            Log.e("FILAMENT_INIT", "✅ SurfaceView 初始化完成")
        } else {
            Log.e("FILAMENT_INIT", "ℹ️ SurfaceView 已存在，直接返回")
        }
        return surfaceView
    }

    private fun setupFilament() {
        Log.e("FILAMENT_INIT", "🔧 開始設置 Filament...")
        try {
            engine = Engine.create()
            Log.e("FILAMENT_INIT", "✅ Engine 創建成功")
            
            renderer = engine.createRenderer()
            Log.e("FILAMENT_INIT", "✅ Renderer 創建成功")
            
            scene = engine.createScene()
            Log.e("FILAMENT_INIT", "✅ Scene 創建成功")
            
            view = engine.createView()
            Log.e("FILAMENT_INIT", "✅ View 創建成功")
            
            camera = engine.createCamera(engine.entityManager.create())
            Log.e("FILAMENT_INIT", "✅ Camera 創建成功")

            uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
            uiHelper.renderCallback = SurfaceCallback()
            uiHelper.attachTo(surfaceView)
            Log.e("FILAMENT_INIT", "✅ UiHelper 設置成功")
            
            isInitialized = true
            Log.e("FILAMENT_INIT", "🎉 Filament 設置完成")
        } catch (ex: Exception) {
            Log.e("FILAMENT_INIT", "❌ Filament 設置失敗: ${ex.message}")
            ex.printStackTrace()
        }
    }

    private fun setupScene() {
        Log.d(TAG, "🔧 開始設置場景...")
        try {
            camera.setProjection(45.0, 1.0, 0.1, 20.0, Camera.Fov.VERTICAL)
            camera.lookAt(0.0, 1.0, 6.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
            view.camera = camera
            view.scene = scene
            Log.d(TAG, "✅ 相機設置成功")

            val light = engine.entityManager.create()
            LightManager.Builder(LightManager.Type.SUN)
                .color(1.0f, 1.0f, 1.0f)
                .intensity(100000.0f)
                .direction(0.0f, -1.0f, -1.0f)
                .build(engine, light)
            scene.addEntity(light)
            Log.d(TAG, "✅ 光源設置成功")
            
            Log.d(TAG, "🎉 場景設置完成")
        } catch (ex: Exception) {
            Log.e(TAG, "❌ 場景設置失敗: ${ex.message}")
            ex.printStackTrace()
        }
    }

    fun loadGLBModel(assetPath: String) {
        Log.d(TAG, "🔧 loadGLBModel() 被調用，路徑: $assetPath")
        
        if (!isInitialized) {
            Log.e(TAG, "❌ FilamentViewer 未初始化，無法載入模型")
            return
        }
        
        try {
            // 1. 檢查文件是否存在
            Log.d(TAG, "🔍 步驟1: 檢查文件是否存在...")
            val inputStream: InputStream = context.assets.open(assetPath)
            val fileSize = inputStream.available()
            inputStream.close()
            Log.d(TAG, "✅ 文件存在！大小: $fileSize bytes")
            
            // 2. 創建載入器
            Log.d(TAG, "🔧 步驟2: 創建載入器...")
            val materialProvider = com.google.android.filament.gltfio.UbershaderProvider(engine)
            val assetLoader = AssetLoader(engine, materialProvider, engine.entityManager)
            val resourceLoader = ResourceLoader(engine)
            Log.d(TAG, "✅ 載入器創建成功")

            // 3. 讀取並創建資產
            Log.d(TAG, "🔧 步驟3: 讀取文件並創建資產...")
            val inputStream2: InputStream = context.assets.open(assetPath)
            val bytes = inputStream2.readBytes()
            inputStream2.close()
            Log.d(TAG, "✅ 文件讀取成功，大小: ${bytes.size} bytes")
            
            val asset = assetLoader.createAsset(ByteBuffer.wrap(bytes))
            if (asset == null) {
                Log.e(TAG, "❌ 步驟3失敗: FilamentAsset 創建失敗！")
                return
            }
            Log.d(TAG, "✅ FilamentAsset 創建成功")
                
            // 4. 載入資源
            Log.d(TAG, "🔧 步驟4: 載入資源...")
            resourceLoader.loadResources(asset)
            asset.releaseSourceData()
            Log.d(TAG, "✅ 資源載入完成")
            
            // 5. 儲存引用
            loadedAsset = asset
            Log.d(TAG, "✅ Asset 引用已儲存")
            
            // 6. 設置變換（縮放 + 270度旋轉）
            Log.d(TAG, "🔧 步驟5: 設置模型變換...")
            val tm = engine.transformManager
            val rootTransform = tm.getInstance(asset.root)
            
            if (rootTransform != 0) {
                val scale = 0.5f
                
                // 270度旋轉 = -90度 = -PI/2 弧度
                val rotationY = -PI.toFloat() / 2f  // 270度旋轉
                val cosY = cos(rotationY)
                val sinY = sin(rotationY)
                
                // 組合矩陣：縮放 + Y軸旋轉270度
                tm.setTransform(rootTransform, floatArrayOf(
                    scale * cosY,  0f, scale * sinY,  0f,  // 第一行
                    0f,            scale, 0f,          0f,  // 第二行
                    scale * -sinY, 0f, scale * cosY,  0f,  // 第三行
                    0f,            0f, 0f,            1f   // 第四行
                ))
                Log.d(TAG, "✅ 模型變換設置成功 (縮放: $scale, Y軸旋轉: 270度)")
            } else {
                Log.w(TAG, "⚠️ 無法獲取根變換")
            }
            
            // 7. 添加到場景
            Log.d(TAG, "🔧 步驟6: 添加實體到場景...")
            val entityArray = asset.entities
            Log.d(TAG, "📊 要添加的實體數量: ${entityArray.size}")
            
            scene.addEntities(entityArray)
            Log.d(TAG, "✅ 所有實體已添加到場景")
            
            Log.d(TAG, "🎉🎉🎉 GLB 模型載入完全成功！🎉🎉🎉")
        } catch (ex: Exception) {
            Log.e(TAG, "❌ GLB 載入過程中發生錯誤: ${ex.message}")
            ex.printStackTrace()
        }
    }

    /**
     * 獲取模型最高點的螢幕位置（以模型中軸為基準）
     * 根據文字內容動態調整對話框大小
     */
    fun getModelTopScreenPosition(messageText: String = ""): Triple<Float, Float, Float>? {
        Log.d(TAG, "🔧 getModelTopScreenPosition() 被調用，文字長度: ${messageText.length}")
        if (!isInitialized || loadedAsset == null) {
            Log.w(TAG, "⚠️ 無法計算位置：isInitialized=$isInitialized, hasAsset=${loadedAsset != null}")
            return null
        }
        
        try {
            val asset = loadedAsset!!
            val tm = engine.transformManager
            val rootTransform = tm.getInstance(asset.root)
            
            if (rootTransform == 0) {
                Log.w(TAG, "⚠️ 無法獲取根變換，使用螢幕中央")
                return getScreenCenterWithWidth(messageText)
            }
            
            // 獲取模型的邊界框
            val aabb = asset.boundingBox
            
            // 計算模型的最高點（Y軸最大值）和中心X座標
            val modelCenterX = aabb.center[0]  // 模型中心X座標
            val modelTopY = aabb.center[1] + aabb.halfExtent[1]  // 最高點
            val modelCenterZ = aabb.center[2]
            
            // 世界座標中的模型頂部中心點
            val worldPosition = floatArrayOf(modelCenterX, modelTopY, modelCenterZ, 1f)
            
            // 修正：使用 Filament 1.5.6 的正確 API
            // getViewMatrix 返回 FloatArray
            val viewMatrix = camera.getViewMatrix(FloatArray(16))
            // getProjectionMatrix 返回 DoubleArray
            val projMatrixDouble = camera.getProjectionMatrix(DoubleArray(16))
            // 轉換為 FloatArray 供 worldToScreen 使用
            val projMatrix = FloatArray(16) { projMatrixDouble[it].toFloat() }
            
            // 將世界座標轉換為螢幕座標
            val screenPosition = worldToScreen(worldPosition, viewMatrix, projMatrix, view.viewport)
            
            if (screenPosition != null) {
                val screenWidth = view.viewport.width.toFloat()
                
                // 🔧 動態計算對話框寬度
                val bubbleWidth = calculateBubbleWidth(messageText, screenWidth)
                val bubbleHalfWidth = bubbleWidth / 2f
                
                // 🔧 安全邊距
                val safeMargin = 20f  // 20px 安全邊距
                val leftBound = safeMargin + bubbleHalfWidth
                val rightBound = screenWidth - safeMargin - bubbleHalfWidth
                
                // 調整X座標，確保對話框不會超出邊界
                val adjustedX = screenPosition.first.coerceIn(leftBound, rightBound)
                
                // 🔧 Y軸安全檢查 - 避免遮蓋狀態欄
                val topMargin = 80f  // 避免遮蓋狀態欄
                val safeY = (screenPosition.second - 10f).coerceAtLeast(topMargin)
                
                Log.d(TAG, "📍 動態對話框位置: (原始: ${screenPosition.first}, 調整後: $adjustedX, Y: $safeY)")
                Log.d(TAG, "📏 動態寬度: 螢幕寬度=$screenWidth, 對話框寬度=$bubbleWidth")
                val centerX = screenWidth / 2f
                return Triple(centerX, safeY, bubbleWidth)
            } else {
                Log.w(TAG, "⚠️ 無法計算螢幕位置，使用螢幕中央")
                return getScreenCenterWithWidth(messageText)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 位置計算錯誤: ${e.message}")
            return getScreenCenterWithWidth(messageText)
        }
    }
    
    /**
     * 動態計算對話框寬度
     * 根據文字長度和內容智能調整
     */
    private fun calculateBubbleWidth(messageText: String, screenWidth: Float): Float {
        val textLength = messageText.length
        
        return when {
            textLength <= 10 -> {
                // 極短文字 (如: "好的", "謝謝")
                (screenWidth * 0.25f).coerceAtLeast(120f)  // 最小120px
            }
            textLength <= 30 -> {
                // 短文字 (如: "你好！我是3D貓咪")  
                screenWidth * 0.45f
            }
            textLength <= 60 -> {
                // 中等長度 (如: 一般對話)
                screenWidth * 0.65f  
            }
            textLength <= 100 -> {
                // 較長文字
                screenWidth * 0.75f
            }
            else -> {
                // 很長文字 (如: 長篇解釋)
                screenWidth * 0.85f
            }
        }.coerceAtMost(screenWidth - 40f)  // 最大寬度 = 螢幕寬度 - 40px邊距
    }
    
    /**
     * 獲取螢幕中央位置（包含動態寬度）
     */
    private fun getScreenCenterWithWidth(messageText: String): Triple<Float, Float, Float> {
        val screenWidth = view.viewport.width.toFloat()
        val screenHeight = view.viewport.height.toFloat()
        val bubbleWidth = calculateBubbleWidth(messageText, screenWidth)
        
        return Triple(
            screenWidth / 2f,
            screenHeight / 2f, 
            bubbleWidth
        )
    }
    
    /**
     * 將世界座標轉換為螢幕座標
     * 使用 Filament 1.5.6 的正確 API
     */
    private fun worldToScreen(
        worldPos: FloatArray, 
        viewMatrix: FloatArray, 
        projMatrix: FloatArray, 
        viewport: Viewport
    ): Pair<Float, Float>? {
        
        // 組合視圖和投影矩陣 (MVP = Projection * View)
        val mvpMatrix = FloatArray(16)
        multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)
        
        val clipSpace = FloatArray(4)
        
        // 將世界座標轉換為裁剪空間
        clipSpace[0] = worldPos[0] * mvpMatrix[0] + worldPos[1] * mvpMatrix[4] + worldPos[2] * mvpMatrix[8] + worldPos[3] * mvpMatrix[12]
        clipSpace[1] = worldPos[0] * mvpMatrix[1] + worldPos[1] * mvpMatrix[5] + worldPos[2] * mvpMatrix[9] + worldPos[3] * mvpMatrix[13]
        clipSpace[2] = worldPos[0] * mvpMatrix[2] + worldPos[1] * mvpMatrix[6] + worldPos[2] * mvpMatrix[10] + worldPos[3] * mvpMatrix[14]
        clipSpace[3] = worldPos[0] * mvpMatrix[3] + worldPos[1] * mvpMatrix[7] + worldPos[2] * mvpMatrix[11] + worldPos[3] * mvpMatrix[15]
        
        // 透視除法
        if (abs(clipSpace[3]) < 0.0001f) return null
        
        val ndcX = clipSpace[0] / clipSpace[3]
        val ndcY = clipSpace[1] / clipSpace[3]
        
        // 轉換為螢幕座標
        val screenX = (ndcX + 1f) * 0.5f * viewport.width
        val screenY = (1f - ndcY) * 0.5f * viewport.height  // Y軸翻轉
        
        return Pair(screenX, screenY)
    }
    
    /**
     * 矩陣乘法輔助函數
     */
    private fun multiplyMM(result: FloatArray, resultOffset: Int, lhs: FloatArray, lhsOffset: Int, rhs: FloatArray, rhsOffset: Int) {
        for (i in 0..3) {
            val rhs_i0 = rhs[rhsOffset + i * 4 + 0]
            val rhs_i1 = rhs[rhsOffset + i * 4 + 1]
            val rhs_i2 = rhs[rhsOffset + i * 4 + 2]
            val rhs_i3 = rhs[rhsOffset + i * 4 + 3]
            for (j in 0..3) {
                result[resultOffset + i * 4 + j] = 
                    lhs[lhsOffset + j] * rhs_i0 +
                    lhs[lhsOffset + 4 + j] * rhs_i1 +
                    lhs[lhsOffset + 8 + j] * rhs_i2 +
                    lhs[lhsOffset + 12 + j] * rhs_i3
            }
        }
    }

    private inner class SurfaceCallback : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface?) {
            Log.d(TAG, "🔧 Surface 改變: $surface")
            swapChain?.let { 
                engine.destroySwapChain(it)
                Log.d(TAG, "🗑️ 舊 SwapChain 已銷毀")
            }
            surface?.let { 
                swapChain = engine.createSwapChain(it)
                Log.d(TAG, "✅ 新 SwapChain 創建成功")
            }
        }

        override fun onDetachedFromSurface() {
            Log.d(TAG, "🔧 Surface 分離")
            swapChain?.let { 
                engine.destroySwapChain(it)
                swapChain = null
                Log.d(TAG, "🗑️ SwapChain 已銷毀")
            }
        }

        override fun onResized(width: Int, height: Int) {
            Log.d(TAG, "🔧 Surface 大小改變: ${width}x${height}")
            view.viewport = Viewport(0, 0, width, height)
            camera.setProjection(45.0, width.toDouble() / height, 0.1, 20.0, Camera.Fov.VERTICAL)
            Log.d(TAG, "✅ 視口和相機已更新")
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        private var frameCount = 0
        
        override fun doFrame(frameTimeNanos: Long) {
            if (isInitialized) {
                Choreographer.getInstance().postFrameCallback(this)
                
                swapChain?.let { chain ->
                    if (renderer.beginFrame(chain, frameTimeNanos)) {
                        renderer.render(view)
                        renderer.endFrame()
                        
                        // 每 30 幀更新一次位置並輸出狀態
                        frameCount++
                        if (frameCount >= 30) {
                            frameCount = 0
                            // 更新模型位置 - 傳入空字串作為預設
                            val position = getModelTopScreenPosition("")
                            // 轉換為舊格式的回調（保持相容性）
                            val oldFormat = position?.let { Pair(it.first, it.second) }
                            onModelPositionUpdated?.invoke(oldFormat)
                            
                            Log.d(TAG, "📊 渲染狀態: 正常運行中... (Asset: ${loadedAsset != null})")
                        }
                    }
                }
            }
        }
    }

    fun onResume() {
        Log.d(TAG, "🔧 onResume")
        if (isInitialized) {
            Choreographer.getInstance().postFrameCallback(frameCallback)
            Log.d(TAG, "✅ FrameCallback 已註冊 - 渲染循環啟動")
        } else {
            Log.w(TAG, "⚠️ onResume 被調用但 Filament 未初始化")
        }
    }

    fun onPause() {
        Log.d(TAG, "🔧 onPause")
        if (isInitialized) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            Log.d(TAG, "✅ FrameCallback 已移除")
        }
    }

    fun onDestroy() {
        Log.d(TAG, "🔧 onDestroy")
        if (isInitialized) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            loadedAsset = null
            engine.destroy()
            Log.d(TAG, "✅ 資源已清理")
        }
    }
}