package ar

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
            Log.e("FILAMENT_INIT", "FilamentViewer class starting to load")
            try {
                Utils.init()
                Log.e("FILAMENT_INIT", "Utils.init() completed")
            } catch (e: Exception) {
                Log.e("FILAMENT_INIT", "Utils.init() failed: ${e.message}")
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
    
    // Store GLB assets and position callbacks
    private var loadedAsset: FilamentAsset? = null
    var onModelPositionUpdated: ((Pair<Float, Float>?) -> Unit)? = null

    init {
        Log.e("FILAMENT_INIT", "FilamentViewer instance creating...")
    }

    fun getSurfaceView(): SurfaceView {
        Log.e("FILAMENT_INIT", "getSurfaceView() called")
        if (!::surfaceView.isInitialized) {
            Log.e("FILAMENT_INIT", "Starting to initialize SurfaceView...")
            surfaceView = SurfaceView(context)
            setupFilament()
            setupScene()
            Log.e("FILAMENT_INIT", "SurfaceView initialization completed")
        } else {
            Log.e("FILAMENT_INIT", "SurfaceView already exists, returning directly")
        }
        return surfaceView
    }

    private fun setupFilament() {
        Log.e("FILAMENT_INIT", "Starting to setup Filament...")
        try {
            engine = Engine.create()
            Log.e("FILAMENT_INIT", "Engine created successfully")
            
            renderer = engine.createRenderer()
            Log.e("FILAMENT_INIT", "Renderer created successfully")
            
            scene = engine.createScene()
            Log.e("FILAMENT_INIT", "Scene created successfully")
            
            view = engine.createView()
            Log.e("FILAMENT_INIT", "View created successfully")
            
            camera = engine.createCamera(engine.entityManager.create())
            Log.e("FILAMENT_INIT", "Camera created successfully")

            uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
            uiHelper.renderCallback = SurfaceCallback()
            uiHelper.attachTo(surfaceView)
            Log.e("FILAMENT_INIT", "UiHelper setup successfully")
            
            isInitialized = true
            Log.e("FILAMENT_INIT", "Filament setup completed")
        } catch (ex: Exception) {
            Log.e("FILAMENT_INIT", "Filament setup failed: ${ex.message}")
            ex.printStackTrace()
        }
    }

    private fun setupScene() {
        Log.d(TAG, "Starting to setup scene...")
        try {
            camera.setProjection(45.0, 1.0, 0.1, 20.0, Camera.Fov.VERTICAL)
            camera.lookAt(0.0, 1.0, 6.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
            view.camera = camera
            view.scene = scene
            Log.d(TAG, "Camera setup successfully")

            val light = engine.entityManager.create()
            LightManager.Builder(LightManager.Type.SUN)
                .color(1.0f, 1.0f, 1.0f)
                .intensity(100000.0f)
                .direction(0.0f, -1.0f, -1.0f)
                .build(engine, light)
            scene.addEntity(light)
            Log.d(TAG, "Light setup successfully")
            
            Log.d(TAG, "Scene setup completed")
        } catch (ex: Exception) {
            Log.e(TAG, "Scene setup failed: ${ex.message}")
            ex.printStackTrace()
        }
    }

    fun loadGLBModel(assetPath: String) {
        Log.d(TAG, "loadGLBModel() called, path: $assetPath")
        
        if (!isInitialized) {
            Log.e(TAG, "FilamentViewer not initialized, cannot load model")
            return
        }
        
        try {
            // 1. Check if file exists
            Log.d(TAG, "Step 1: Check if file exists...")
            val inputStream: InputStream = context.assets.open(assetPath)
            val fileSize = inputStream.available()
            inputStream.close()
            Log.d(TAG, "File exists! Size: $fileSize bytes")
            
            // 2. Create loaders
            Log.d(TAG, "Step 2: Create loaders...")
            val materialProvider = com.google.android.filament.gltfio.UbershaderProvider(engine)
            val assetLoader = AssetLoader(engine, materialProvider, engine.entityManager)
            val resourceLoader = ResourceLoader(engine)
            Log.d(TAG, "Loaders created successfully")

            // 3. Read and create asset
            Log.d(TAG, "Step 3: Read file and create asset...")
            val inputStream2: InputStream = context.assets.open(assetPath)
            val bytes = inputStream2.readBytes()
            inputStream2.close()
            Log.d(TAG, "File read successfully, size: ${bytes.size} bytes")
            
            val asset = assetLoader.createAsset(ByteBuffer.wrap(bytes))
            if (asset == null) {
                Log.e(TAG, "Step 3 failed: FilamentAsset creation failed!")
                return
            }
            Log.d(TAG, "FilamentAsset created successfully")
                
            // 4. Load resources
            Log.d(TAG, "Step 4: Load resources...")
            resourceLoader.loadResources(asset)
            asset.releaseSourceData()
            Log.d(TAG, "Resources loaded successfully")
            
            // 5. Store reference
            loadedAsset = asset
            Log.d(TAG, "Asset reference stored")
            
            // 6. Setup transform (scale + 270 degree rotation)
            Log.d(TAG, "Step 5: Setup model transform...")
            val tm = engine.transformManager
            val rootTransform = tm.getInstance(asset.root)
            
            if (rootTransform != 0) {
                val scale = 0.5f
                
                // 270 degree rotation = -90 degrees = -PI/2 radians
                val rotationY = -PI.toFloat() / 2f  // 270 degree rotation
                val cosY = cos(rotationY)
                val sinY = sin(rotationY)
                
                // Combined matrix: scale + Y-axis 270 degree rotation
                tm.setTransform(rootTransform, floatArrayOf(
                    scale * cosY,  0f, scale * sinY,  0f,  // First row
                    0f,            scale, 0f,          0f,  // Second row
                    scale * -sinY, 0f, scale * cosY,  0f,  // Third row
                    0f,            0f, 0f,            1f   // Fourth row
                ))
                Log.d(TAG, "Model transform setup successfully (scale: $scale, Y-axis rotation: 270 degrees)")
            } else {
                Log.w(TAG, "Unable to get root transform")
            }
            
            // 7. Add to scene
            Log.d(TAG, "Step 6: Add entities to scene...")
            val entityArray = asset.entities
            Log.d(TAG, "Number of entities to add: ${entityArray.size}")
            
            scene.addEntities(entityArray)
            Log.d(TAG, "All entities added to scene")
            
            Log.d(TAG, "GLB model loaded completely successfully!")
        } catch (ex: Exception) {
            Log.e(TAG, "Error occurred during GLB loading: ${ex.message}")
            ex.printStackTrace()
        }
    }

    /**
     * Get screen position of model's highest point (based on model's center axis)
     * Dynamically adjust dialog size based on text content
     */
    fun getModelTopScreenPosition(messageText: String = ""): Triple<Float, Float, Float>? {
        Log.d(TAG, "getModelTopScreenPosition() called, text length: ${messageText.length}")
        if (!isInitialized || loadedAsset == null) {
            Log.w(TAG, "Cannot calculate position: isInitialized=$isInitialized, hasAsset=${loadedAsset != null}")
            return null
        }
        
        try {
            val asset = loadedAsset!!
            val tm = engine.transformManager
            val rootTransform = tm.getInstance(asset.root)
            
            if (rootTransform == 0) {
                Log.w(TAG, "Unable to get root transform, using screen center")
                return getScreenCenterWithWidth(messageText)
            }
            
            // Get model's bounding box
            val aabb = asset.boundingBox
            
            // Calculate model's highest point (Y-axis maximum) and center X coordinate
            val modelCenterX = aabb.center[0]  // Model center X coordinate
            val modelTopY = aabb.center[1] + aabb.halfExtent[1]  // Highest point
            val modelCenterZ = aabb.center[2]
            
            // Model top center point in world coordinates
            val worldPosition = floatArrayOf(modelCenterX, modelTopY, modelCenterZ, 1f)
            
            // Fix: Use correct API for Filament 1.5.6
            // getViewMatrix returns FloatArray
            val viewMatrix = camera.getViewMatrix(FloatArray(16))
            // getProjectionMatrix returns DoubleArray
            val projMatrixDouble = camera.getProjectionMatrix(DoubleArray(16))
            // Convert to FloatArray for worldToScreen usage
            val projMatrix = FloatArray(16) { projMatrixDouble[it].toFloat() }
            
            // Convert world coordinates to screen coordinates
            val screenPosition = worldToScreen(worldPosition, viewMatrix, projMatrix, view.viewport)
            
            if (screenPosition != null) {
                val screenWidth = view.viewport.width.toFloat()
                
                // Dynamically calculate dialog width
                val bubbleWidth = calculateBubbleWidth(messageText, screenWidth)
                val bubbleHalfWidth = bubbleWidth / 2f
                
                // Safe margins
                val safeMargin = 20f  // 20px safe margin
                val leftBound = safeMargin + bubbleHalfWidth
                val rightBound = screenWidth - safeMargin - bubbleHalfWidth
                
                // Adjust X coordinate to ensure dialog doesn't exceed boundaries
                val adjustedX = screenPosition.first.coerceIn(leftBound, rightBound)
                
                // Y-axis safety check - avoid covering status bar
                val topMargin = 80f  // Avoid covering status bar
                val safeY = (screenPosition.second - 10f).coerceAtLeast(topMargin)
                
                Log.d(TAG, "Dynamic dialog position: (original: ${screenPosition.first}, adjusted: $adjustedX, Y: $safeY)")
                Log.d(TAG, "Dynamic width: screen width=$screenWidth, dialog width=$bubbleWidth")
                val centerX = screenWidth / 2f
                return Triple(centerX, safeY, bubbleWidth)
            } else {
                Log.w(TAG, "Unable to calculate screen position, using screen center")
                return getScreenCenterWithWidth(messageText)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Position calculation error: ${e.message}")
            return getScreenCenterWithWidth(messageText)
        }
    }
    
    /**
     * Dynamically calculate dialog width
     * Intelligently adjust based on text length and content
     */
    private fun calculateBubbleWidth(messageText: String, screenWidth: Float): Float {
        val textLength = messageText.length
        
        return when {
            textLength <= 10 -> {
                // Very short text (e.g.: "OK", "Thanks")
                (screenWidth * 0.25f).coerceAtLeast(120f)  // Minimum 120px
            }
            textLength <= 30 -> {
                // Short text (e.g.: "Hello! I'm a 3D cat")  
                screenWidth * 0.45f
            }
            textLength <= 60 -> {
                // Medium length (e.g.: general conversation)
                screenWidth * 0.65f  
            }
            textLength <= 100 -> {
                // Longer text
                screenWidth * 0.75f
            }
            else -> {
                // Very long text (e.g.: long explanations)
                screenWidth * 0.85f
            }
        }.coerceAtMost(screenWidth - 40f)  // Maximum width = screen width - 40px margin
    }
    
    /**
     * Get screen center position (including dynamic width)
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
     * Convert world coordinates to screen coordinates
     * Using correct API for Filament 1.5.6
     */
    private fun worldToScreen(
        worldPos: FloatArray, 
        viewMatrix: FloatArray, 
        projMatrix: FloatArray, 
        viewport: Viewport
    ): Pair<Float, Float>? {
        
        // Combine view and projection matrices (MVP = Projection * View)
        val mvpMatrix = FloatArray(16)
        multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)
        
        val clipSpace = FloatArray(4)
        
        // Convert world coordinates to clip space
        clipSpace[0] = worldPos[0] * mvpMatrix[0] + worldPos[1] * mvpMatrix[4] + worldPos[2] * mvpMatrix[8] + worldPos[3] * mvpMatrix[12]
        clipSpace[1] = worldPos[0] * mvpMatrix[1] + worldPos[1] * mvpMatrix[5] + worldPos[2] * mvpMatrix[9] + worldPos[3] * mvpMatrix[13]
        clipSpace[2] = worldPos[0] * mvpMatrix[2] + worldPos[1] * mvpMatrix[6] + worldPos[2] * mvpMatrix[10] + worldPos[3] * mvpMatrix[14]
        clipSpace[3] = worldPos[0] * mvpMatrix[3] + worldPos[1] * mvpMatrix[7] + worldPos[2] * mvpMatrix[11] + worldPos[3] * mvpMatrix[15]
        
        // Perspective division
        if (abs(clipSpace[3]) < 0.0001f) return null
        
        val ndcX = clipSpace[0] / clipSpace[3]
        val ndcY = clipSpace[1] / clipSpace[3]
        
        // Convert to screen coordinates
        val screenX = (ndcX + 1f) * 0.5f * viewport.width
        val screenY = (1f - ndcY) * 0.5f * viewport.height  // Y-axis flip
        
        return Pair(screenX, screenY)
    }
    
    /**
     * Matrix multiplication helper function
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
            Log.d(TAG, "Surface changed: $surface")
            swapChain?.let { 
                engine.destroySwapChain(it)
                Log.d(TAG, "Old SwapChain destroyed")
            }
            surface?.let { 
                swapChain = engine.createSwapChain(it)
                Log.d(TAG, "New SwapChain created successfully")
            }
        }

        override fun onDetachedFromSurface() {
            Log.d(TAG, "Surface detached")
            swapChain?.let { 
                engine.destroySwapChain(it)
                swapChain = null
                Log.d(TAG, "SwapChain destroyed")
            }
        }

        override fun onResized(width: Int, height: Int) {
            Log.d(TAG, "Surface size changed: ${width}x${height}")
            view.viewport = Viewport(0, 0, width, height)
            camera.setProjection(45.0, width.toDouble() / height, 0.1, 20.0, Camera.Fov.VERTICAL)
            Log.d(TAG, "Viewport and camera updated")
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
                        
                        // Update position every 30 frames and output status
                        frameCount++
                        if (frameCount >= 30) {
                            frameCount = 0
                            // Update model position - pass empty string as default
                            val position = getModelTopScreenPosition("")
                            // Convert to old format callback (maintain compatibility)
                            val oldFormat = position?.let { Pair(it.first, it.second) }
                            onModelPositionUpdated?.invoke(oldFormat)
                            
                            Log.d(TAG, "Render status: Running normally... (Asset: ${loadedAsset != null})")
                        }
                    }
                }
            }
        }
    }

    fun onResume() {
        Log.d(TAG, "onResume")
        if (isInitialized) {
            Choreographer.getInstance().postFrameCallback(frameCallback)
            Log.d(TAG, "FrameCallback registered - render loop started")
        } else {
            Log.w(TAG, "onResume called but Filament not initialized")
        }
    }

    fun onPause() {
        Log.d(TAG, "onPause")
        if (isInitialized) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            Log.d(TAG, "FrameCallback removed")
        }
    }

    fun onDestroy() {
        Log.d(TAG, "onDestroy")
        if (isInitialized) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            loadedAsset = null
            engine.destroy()
            Log.d(TAG, "Resources cleaned up")
        }
    }
}