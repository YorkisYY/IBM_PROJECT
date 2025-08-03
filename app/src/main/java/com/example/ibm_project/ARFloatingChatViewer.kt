package com.example.ibm_project

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.*
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.utils.Utils
import dev.romainguy.kotlin.math.*
import java.nio.ByteBuffer

class ARFloatingChatViewer(private val context: Context) {

    companion object {
        init { Utils.init() }
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var uiHelper: UiHelper
    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var view: View
    private lateinit var camera: Camera
    private var swapChain: SwapChain? = null
    private var modelAsset: FilamentAsset? = null
    private var isInitialized = false

    // 浮動對話框狀態
    var chatMessage by mutableStateOf("")
        private set
    var isChatVisible by mutableStateOf(false)
        private set
    var chatBubblePosition by mutableStateOf(Pair(0f, 0f))
        private set

    // 模型頭頂偏移（調整這個值來控制對話框位置）
    private val headOffsetY = 1.0f

    fun getSurfaceView(): SurfaceView {
        if (!::surfaceView.isInitialized) {
            surfaceView = SurfaceView(context)
            setupFilament()
            setupScene()
        }
        return surfaceView
    }

    private fun setupFilament() {
        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        camera = engine.createCamera(engine.entityManager.create())

        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = SurfaceCallback()
        uiHelper.attachTo(surfaceView)
        
        isInitialized = true
    }

    private fun setupScene() {
        camera.setProjection(45.0, 1.0, 0.1, 20.0, Camera.Fov.VERTICAL)
        camera.lookAt(0.0, 1.0, 6.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        view.camera = camera
        view.scene = scene

        val light = engine.entityManager.create()
        LightManager.Builder(LightManager.Type.SUN)
            .color(1.0f, 1.0f, 1.0f)
            .intensity(100000.0f)
            .direction(0.0f, -1.0f, -1.0f)
            .build(engine, light)
        scene.addEntity(light)
    }

    fun loadGLBModel(assetPath: String) {
        if (!isInitialized) return
        
        try {
            val materialProvider = com.google.android.filament.gltfio.UbershaderProvider(engine)
            val assetLoader = AssetLoader(engine, materialProvider, engine.entityManager)
            val resourceLoader = ResourceLoader(engine)

            context.assets.open(assetPath).use { input ->
                val bytes = input.readBytes()
                val asset: FilamentAsset = assetLoader.createAsset(ByteBuffer.wrap(bytes))!!
                
                resourceLoader.loadResources(asset)
                asset.releaseSourceData()
                
                // 儲存模型引用
                modelAsset = asset
                
                // 縮放和旋轉模型
                val tm = engine.transformManager
                val rootTransform = tm.getInstance(asset.root)
                val scale = 0.5f
                
                val cos270 = 0f
                val sin270 = -1f
                
                tm.setTransform(rootTransform, floatArrayOf(
                    scale * cos270, 0f, scale * sin270, 0f,
                    0f, scale, 0f, 0f,
                    scale * -sin270, 0f, scale * cos270, 0f,
                    0f, 0f, 0f, 1f
                ))
                
                scene.addEntities(asset.entities)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 顯示貓咪的回復對話框
    fun showCatReply(message: String) {
        chatMessage = message
        isChatVisible = true
        // 移除自動隱藏功能
    }

    // 隱藏對話框
    fun hideChatBubble() {
        isChatVisible = false
    }

    // 計算貓咪頭頂的螢幕座標
    private fun updateChatBubblePosition() {
        modelAsset?.let { asset ->
            try {
                val tm = engine.transformManager
                val rootTransform = tm.getInstance(asset.root)
                
                // 獲取模型的世界變換矩陣
                val modelMatrix = FloatArray(16)
                tm.getTransform(rootTransform, modelMatrix)
                
                // 計算頭頂世界位置
                val headWorldPos = Float3(
                    modelMatrix[12], // X
                    modelMatrix[13] + headOffsetY, // Y + 頭頂偏移
                    modelMatrix[14]  // Z
                )
                
                // 轉換為螢幕座標
                val screenPos = worldToScreen(headWorldPos)
                chatBubblePosition = Pair(screenPos.x, screenPos.y)
                
            } catch (e: Exception) {
                // 如果計算失敗，使用預設位置
                val viewport = view.viewport
                chatBubblePosition = Pair(
                    viewport.width / 2f, 
                    viewport.height / 3f
                )
            }
        }
    }

    private fun worldToScreen(worldPos: Float3): Float3 {
        try {
            // 獲取相機矩陣 - 改為 DoubleArray
            val viewMatrix = DoubleArray(16)  // 改這裡：Float -> Double
            val projMatrix = DoubleArray(16)  // 改這裡：Float -> Double
            
            camera.getViewMatrix(viewMatrix)
            camera.getProjectionMatrix(projMatrix)
            
            // 世界座標轉視圖座標
            val viewPos = Float4(worldPos.x, worldPos.y, worldPos.z, 1.0f)
            
            // 簡化的投影計算
            val viewport = view.viewport
            val screenX = viewport.width / 2f + (worldPos.x * 100f)
            val screenY = viewport.height / 3f - (worldPos.y * 50f)
            
            return Float3(screenX, screenY, 0f)
            
        } catch (e: Exception) {
            // 備用位置
            val viewport = view.viewport
            return Float3(viewport.width / 2f, viewport.height / 3f, 0f)
        }
    }

    private inner class SurfaceCallback : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface?) {
            swapChain?.let { engine.destroySwapChain(it) }
            surface?.let { 
                swapChain = engine.createSwapChain(it)
            }
        }

        override fun onDetachedFromSurface() {
            swapChain?.let { 
                engine.destroySwapChain(it)
                swapChain = null
            }
        }

        override fun onResized(width: Int, height: Int) {
            view.viewport = Viewport(0, 0, width, height)
            camera.setProjection(45.0, width.toDouble() / height, 0.1, 20.0, Camera.Fov.VERTICAL)
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isInitialized) {
                Choreographer.getInstance().postFrameCallback(this)
                
                // 每幀更新對話框位置
                if (isChatVisible) {
                    updateChatBubblePosition()
                }
                
                swapChain?.let { chain ->
                    if (renderer.beginFrame(chain, frameTimeNanos)) {
                        renderer.render(view)
                        renderer.endFrame()
                    }
                }
            }
        }
    }

    fun onResume() {
        if (isInitialized) {
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun onPause() {
        if (isInitialized) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
    }

    fun onDestroy() {
        if (isInitialized) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            engine.destroy()
        }
    }
}

/**
 * 浮動在貓咪頭頂的對話框 Compose 組件
 */
@Composable
fun FloatingChatBubble(
    message: String,
    isVisible: Boolean,
    position: Pair<Float, Float>,
    modifier: Modifier = Modifier
) {
    if (isVisible && message.isNotEmpty()) {
        val density = LocalDensity.current
        
        Box(
            modifier = modifier
                .offset(
                    x = with(density) { (position.first - 150).dp }, // 居中對話框
                    y = with(density) { position.second.dp }
                )
                .widthIn(min = 80.dp, max = 300.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .background(
                    color = Color(0xFF2196F3),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 18.sp
            )
        }
    }
}