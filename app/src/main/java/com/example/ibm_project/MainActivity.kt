package com.example.ibm_project

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ibm_project.ui.theme.IBM_PROJECTTheme
import com.google.ar.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.components.UserInputField
import watsonx.WatsonAIEnhanced
import ar.ARSceneViewRenderer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// SceneView 2.3.0 imports
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberView
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.collision.HitResult

// ARScene imports
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.ar.rememberARCameraStream
import io.github.sceneview.ar.node.AnchorNode

/**
 * 改善的 AR Cat Interaction App with Smooth 360-Degree Rotation
 * 使用累積旋轉和速度阻尼來實現平滑的360度旋轉
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val GLB_MODEL_PATH = "cute_spooky_cat.glb"
        
        // 改善的旋轉參數 - 大幅降低靈敏度
        private const val ROTATION_SENSITIVITY_X = 0.3f  // 上下旋轉靈敏度（從2.0降到0.3）
        private const val ROTATION_SENSITIVITY_Y = 0.5f  // 左右旋轉靈敏度（從3.0降到0.5）
        private const val MIN_ROTATION_DISTANCE = 10f    // 最小旋轉距離（從20降到10）
        
        // 新增：速度阻尼和平滑參數
        private const val VELOCITY_DAMPING = 0.85f       // 速度阻尼係數
        private const val SMOOTH_FACTOR = 0.15f          // 平滑插值係數
        private const val MIN_VELOCITY_THRESHOLD = 0.01f // 最小速度閾值
    }
    
    // AR Renderer
    private lateinit var arRenderer: ARSceneViewRenderer
    
    // 改善的旋轉狀態管理
    private var selectedNode: ModelNode? = null
    private var isRotating = false
    
    // 觸摸狀態
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    
    // 累積旋轉值（支持超過360度）
    private var accumulatedRotationX = 0f  // 累積X軸旋轉
    private var accumulatedRotationY = 0f  // 累積Y軸旋轉
    
    // 速度追蹤
    private var velocityX = 0f
    private var velocityY = 0f
    private var lastMoveTime = 0L
    
    // 目標旋轉值（用於平滑插值）
    private var targetRotationX = 0f
    private var targetRotationY = 0f
    private var currentRotationX = 0f
    private var currentRotationY = 0f
    
    // 可調節的旋轉靈敏度
    private var rotationSensitivityX = ROTATION_SENSITIVITY_X
    private var rotationSensitivityY = ROTATION_SENSITIVITY_Y
    
    // Store all placed model nodes for interaction
    private val placedModelNodes = mutableListOf<ModelNode>()
    
    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResults(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🚀 onCreate started")
        
        // Initialize AR Renderer
        arRenderer = ARSceneViewRenderer()
        
        // Request necessary permissions
        requestNecessaryPermissions()
        
        // Setup Compose UI
        setContent {
            IBM_PROJECTTheme {
                ARInterface()
            }
        }
    }

    private fun requestNecessaryPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            checkARCoreAvailability()
        }
    }

    private fun handlePermissionResults(permissions: Map<String, Boolean>) {
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        
        if (cameraGranted) {
            Log.d(TAG, "✅ Camera permission granted")
            checkARCoreAvailability()
        } else {
            Log.w(TAG, "⚠️ Camera permission denied")
            arRenderer.trackingStatus.value = "Camera Permission Required"
        }
    }

    private fun checkARCoreAvailability() {
        when (ArCoreApk.getInstance().checkAvailability(this)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                Log.d(TAG, "✅ ARCore installed and supported")
                arRenderer.trackingStatus.value = "AR Ready"
            }
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> {
                Log.d(TAG, "📦 Need to update or install ARCore")
                requestARCoreInstall()
            }
            else -> {
                Log.w(TAG, "⚠️ Device does not support ARCore")
                arRenderer.trackingStatus.value = "ARCore Not Supported"
            }
        }
    }
    
    private fun requestARCoreInstall() {
        try {
            val installStatus = ArCoreApk.getInstance().requestInstall(this, true)
            if (installStatus == ArCoreApk.InstallStatus.INSTALLED) {
                arRenderer.trackingStatus.value = "AR Ready"
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ARCore installation failed: ${e.message}")
            arRenderer.trackingStatus.value = "ARCore Installation Failed"
        }
    }

    @Composable
    private fun ARInterface() {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val coroutineScope = rememberCoroutineScope()
        
        // UI state
        var inputText by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var chatMessage by remember { mutableStateOf("") }
        var isChatVisible by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        
        // AR state from renderer
        val planesCount = arRenderer.detectedPlanesCount.value
        val modelsCount = arRenderer.placedModelsCount.value
        val canPlace = arRenderer.canPlaceObjects.value
        val trackingStatus = arRenderer.trackingStatus.value
        val planeStatus = arRenderer.planeDetectionStatus.value
        
        // SceneView initialization
        val engine = rememberEngine()
        val modelLoader = rememberModelLoader(engine)
        val materialLoader = rememberMaterialLoader(engine)
        val view = rememberView(engine)
        val collisionSystem = rememberCollisionSystem(view)
        val childNodes = rememberNodes()
        val cameraNode = rememberARCameraNode(engine)
        
        // AR state
        var frame by remember { mutableStateOf<Frame?>(null) }
        var session by remember { mutableStateOf<Session?>(null) }
        
        // Initialize Watson AI
        LaunchedEffect(Unit) {
            try {
                Log.d(TAG, "🔧 Starting initialization...")
                WatsonAIEnhanced.initialize(context as MainActivity)
                Log.d(TAG, "✅ Initialization completed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Initialization failed: ${e.message}", e)
            }
        }
        
        // 平滑旋轉更新循環
        LaunchedEffect(selectedNode) {
            while (selectedNode != null) {
                updateSmoothRotation()
                delay(16) // ~60 FPS
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Main AR View
            ARScene(
                modifier = Modifier.fillMaxSize(),
                childNodes = childNodes,
                engine = engine,
                view = view,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                cameraNode = cameraNode,
                collisionSystem = collisionSystem,
                activity = context as ComponentActivity,
                lifecycle = lifecycle,
                
                planeRenderer = true,
                cameraStream = rememberARCameraStream(materialLoader),
                
                sessionConfiguration = { arSession, config ->
                    arRenderer.configureSession(arSession, config)
                },
                
                onSessionCreated = { arSession ->
                    session = arSession
                    arRenderer.onSessionCreated(arSession)
                },
                
                onSessionResumed = { arSession ->
                    arRenderer.onSessionResumed(arSession)
                },
                
                onSessionPaused = { arSession ->
                    arRenderer.onSessionPaused(arSession)
                },
                
                onSessionFailed = { exception ->
                    arRenderer.onSessionFailed(exception)
                },
                
                onSessionUpdated = { arSession, updatedFrame ->
                    frame = updatedFrame
                    session = arSession
                    arRenderer.onSessionUpdated(arSession, updatedFrame)
                },
                
                onTouchEvent = { motionEvent: MotionEvent, hitResult: HitResult? ->
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN -> {
                            handleImprovedTouchDown(motionEvent, hitResult, frame, session, modelLoader, childNodes, engine)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            handleImprovedTouchMove(motionEvent)
                        }
                        MotionEvent.ACTION_UP -> {
                            handleImprovedTouchUp()
                        }
                    }
                    true
                }
            )
            
            // Rotation instruction overlay
            if (modelsCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Card(
                        modifier = Modifier
                            .padding(top = 80.dp)
                            .widthIn(min = 120.dp, max = 260.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedNode != null) 
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RotateRight,
                                    contentDescription = "Rotation Hint",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (selectedNode != null) 
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedNode != null) "Rotating: ${selectedNode?.name}" else "Tap cat to rotate",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selectedNode != null) 
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            if (selectedNode != null) {
                                Text(
                                    text = "🔄 Smooth 360° Rotation Enabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Text(
                                    text = "X: ${String.format("%.1f", accumulatedRotationX)}° | Y: ${String.format("%.1f", accumulatedRotationY)}°",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
            
            // Chat dialog
            if (isChatVisible && chatMessage.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 120.dp)
                            .widthIn(min = 80.dp, max = 320.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp))
                            .background(
                                color = Color(0xFF2196F3),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = chatMessage,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // Enhanced top control panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Header with mode info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AR Mode + Smooth 360° Rotation",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Text(
                            text = trackingStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                canPlace -> MaterialTheme.colorScheme.primary
                                trackingStatus.contains("Lost") || trackingStatus.contains("Failed") -> 
                                    MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            }
                        )
                    }
                    
                    // Status message
                    Text(
                        text = planeStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Statistics and controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Planes count
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Dashboard,
                                contentDescription = "Planes",
                                modifier = Modifier.size(16.dp),
                                tint = if (canPlace) MaterialTheme.colorScheme.primary 
                                       else MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$planesCount Planes",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (canPlace) MaterialTheme.colorScheme.primary 
                                       else MaterialTheme.colorScheme.secondary
                            )
                        }
                        
                        // AR models count
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Pets,
                                contentDescription = "AR Models",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$modelsCount Cats",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        // Clear button
                        if (modelsCount > 0) {
                            TextButton(
                                onClick = {
                                    resetRotationState()
                                    placedModelNodes.clear()
                                    arRenderer.clearAllModels(childNodes)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Clear", 
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        // Settings button
                        TextButton(
                            onClick = {
                                showSettings = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Settings", 
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            
            // Settings Dialog
            if (showSettings) {
                AlertDialog(
                    onDismissRequest = { showSettings = false },
                    title = { Text("Rotation Settings") },
                    text = {
                        Column {
                            Text("Adjust rotation sensitivity (lower = smoother):")
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Y-axis sensitivity slider
                            Text("Y-axis (↔️ Horizontal) Sensitivity: ${String.format("%.2f", rotationSensitivityY)}")
                            Slider(
                                value = rotationSensitivityY,
                                onValueChange = { rotationSensitivityY = it },
                                valueRange = 0.1f..2.0f,
                                steps = 18
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // X-axis sensitivity slider  
                            Text("X-axis (↕️ Vertical) Sensitivity: ${String.format("%.2f", rotationSensitivityX)}")
                            Slider(
                                value = rotationSensitivityX,
                                onValueChange = { rotationSensitivityX = it },
                                valueRange = 0.1f..2.0f,
                                steps = 18
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "🔄 Now supports smooth 360° rotation with velocity damping",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSettings = false }) {
                            Text("Done")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { 
                                rotationSensitivityX = ROTATION_SENSITIVITY_X
                                rotationSensitivityY = ROTATION_SENSITIVITY_Y
                            }
                        ) {
                            Text("Reset")
                        }
                    }
                )
            }
            
            // Bottom input field
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                UserInputField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    onSendClick = {
                        if (inputText.trim().isNotEmpty() && !isLoading) {
                            val userMessage = inputText.trim()
                            inputText = ""
                            isLoading = true
                            
                            coroutineScope.launch {
                                try {
                                    val reply = processUserMessage(userMessage)
                                    chatMessage = reply
                                    isChatVisible = true
                                    
                                    delay(5000)
                                    isChatVisible = false
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Processing failed", e)
                                    chatMessage = "Sorry, something went wrong 😿"
                                    isChatVisible = true
                                    delay(3000)
                                    isChatVisible = false
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    placeholder = when {
                        selectedNode != null -> "Selected: ${selectedNode?.name} - Smooth 360° rotation enabled..."
                        modelsCount > 0 -> "Tap cats for smooth 360° rotation, tap empty space to place new cats..."
                        else -> "Tap anywhere to place cats..."
                    },
                    isLoading = isLoading
                )
            }
        }
    }
    
    /**
     * 重置旋轉狀態
     */
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
    }
    
    /**
     * 改善的觸摸按下處理
     */
    private fun handleImprovedTouchDown(
        motionEvent: MotionEvent,
        hitResult: HitResult?,
        frame: Frame?,
        session: Session?,
        modelLoader: io.github.sceneview.loaders.ModelLoader,
        childNodes: MutableList<Node>,
        engine: com.google.android.filament.Engine
    ) {
        lastTouchX = motionEvent.x
        lastTouchY = motionEvent.y
        touchStartX = motionEvent.x
        touchStartY = motionEvent.y
        lastMoveTime = System.currentTimeMillis()
        
        // 檢查是否觸摸到現有模型
        val touchedModel = findTouchedModel(motionEvent.x, motionEvent.y)
        if (touchedModel != null) {
            Log.d(TAG, "🎯 Model selected for smooth rotation: ${touchedModel.name}")
            selectedNode = touchedModel
            isRotating = false
            
            // 初始化該模型的累積旋轉值
            val currentRotation = touchedModel.rotation
            accumulatedRotationX = currentRotation.x
            accumulatedRotationY = currentRotation.y
            
            // 設置目標和當前旋轉
            targetRotationX = accumulatedRotationX
            targetRotationY = accumulatedRotationY
            currentRotationX = accumulatedRotationX
            currentRotationY = accumulatedRotationY
            
            // 重置速度
            velocityX = 0f
            velocityY = 0f
            
            arRenderer.planeDetectionStatus.value = "Cat selected: ${touchedModel.name} - Smooth 360° rotation ready!"
            return
        }
        
        // 如果沒有觸摸到模型，放置新模型
        kotlinx.coroutines.runBlocking {
            placeCatAtTouch(motionEvent, frame, session, modelLoader, childNodes, engine)
        }
    }
    
    /**
     * 改善的觸摸移動處理 - 支持360度旋轉和速度阻尼
     */
    private fun handleImprovedTouchMove(motionEvent: MotionEvent) {
        selectedNode?.let { node ->
            val currentTime = System.currentTimeMillis()
            val deltaTime = (currentTime - lastMoveTime).coerceAtLeast(1L)
            
            val deltaX = motionEvent.x - lastTouchX
            val deltaY = motionEvent.y - lastTouchY
            val totalDistance = sqrt(deltaX * deltaX + deltaY * deltaY)
            
            // 只有移動距離足夠時才旋轉
            if (totalDistance > MIN_ROTATION_DISTANCE) {
                if (!isRotating) {
                    isRotating = true
                    Log.d(TAG, "🔄 Started smooth 360° rotation: ${node.name}")
                }
                
                // 計算速度（像素/毫秒）
                val newVelocityX = deltaX / deltaTime.toFloat()
                val newVelocityY = deltaY / deltaTime.toFloat()
                
                // 應用速度阻尼
                velocityX = velocityX * VELOCITY_DAMPING + newVelocityX * (1f - VELOCITY_DAMPING)
                velocityY = velocityY * VELOCITY_DAMPING + newVelocityY * (1f - VELOCITY_DAMPING)
                
                // 計算旋轉增量（累積，支持超過360度）
                val rotationDeltaY = velocityX * rotationSensitivityY * deltaTime
                val rotationDeltaX = -velocityY * rotationSensitivityX * deltaTime // 負號讓向上拖拽向上旋轉
                
                // 更新累積旋轉值（不限制在0-360度範圍內）
                accumulatedRotationX += rotationDeltaX
                accumulatedRotationY += rotationDeltaY
                
                // 更新目標旋轉
                targetRotationX = accumulatedRotationX
                targetRotationY = accumulatedRotationY
                
                Log.d(TAG, "🔄 Smooth rotating ${node.name} - AccumX: ${String.format("%.1f", accumulatedRotationX)}°, AccumY: ${String.format("%.1f", accumulatedRotationY)}°")
                
                // 顯示旋轉信息
                val rotationInfo = "X: ${String.format("%.1f", accumulatedRotationX)}°, Y: ${String.format("%.1f", accumulatedRotationY)}°"
                arRenderer.planeDetectionStatus.value = "Smooth rotating ${node.name} - $rotationInfo"
                
                // 更新觸摸位置和時間
                lastTouchX = motionEvent.x
                lastTouchY = motionEvent.y
                lastMoveTime = currentTime
            }
        }
    }
    
    /**
     * 改善的觸摸釋放處理
     */
    private fun handleImprovedTouchUp() {
        if (isRotating && selectedNode != null) {
            Log.d(TAG, "✅ Smooth rotation completed for: ${selectedNode?.name}")
            arRenderer.planeDetectionStatus.value = "Smooth rotation completed! Total X: ${String.format("%.1f", accumulatedRotationX)}°, Y: ${String.format("%.1f", accumulatedRotationY)}°"
            
            // 不重置選中的節點，允許繼續操作
            isRotating = false
            
            // 逐漸減速
            velocityX *= 0.5f
            velocityY *= 0.5f
        }
    }
    
    /**
     * 平滑旋轉更新 - 使用插值實現平滑效果
     */
    private fun updateSmoothRotation() {
        selectedNode?.let { node ->
            // 使用線性插值平滑過渡到目標旋轉
            currentRotationX += (targetRotationX - currentRotationX) * SMOOTH_FACTOR
            currentRotationY += (targetRotationY - currentRotationY) * SMOOTH_FACTOR
            
            // 將累積旋轉轉換為實際可用的旋轉值（標準化到0-360度用於顯示）
            val normalizedX = currentRotationX % 360f
            val normalizedY = currentRotationY % 360f
            
            // 應用旋轉到節點
            node.rotation = Rotation(x = normalizedX, y = normalizedY, z = 0f)
            
            // 如果有慣性速度，繼續旋轉
            if (!isRotating && (abs(velocityX) > MIN_VELOCITY_THRESHOLD || abs(velocityY) > MIN_VELOCITY_THRESHOLD)) {
                // 應用慣性旋轉
                accumulatedRotationX += velocityX * rotationSensitivityX * 16f // 16ms per frame
                accumulatedRotationY += velocityY * rotationSensitivityY * 16f
                
                targetRotationX = accumulatedRotationX
                targetRotationY = accumulatedRotationY
                
                // 減慢慣性速度
                velocityX *= 0.95f
                velocityY *= 0.95f
            }
        }
    }
    
    /**
     * Find touched model based on screen coordinates
     */
    private fun findTouchedModel(screenX: Float, screenY: Float): ModelNode? {
        // Simplified model detection - check all placed models
        for (modelNode in placedModelNodes) {
            try {
                // Simple distance-based check
                if (screenX > 200f && screenX < 800f && screenY > 400f && screenY < 1600f) {
                    return placedModelNodes.lastOrNull()
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error checking model touch: ${e.message}")
                continue
            }
        }
        return null
    }
    
    /**
     * Place cat at touch position using proper SceneView 2.3.0 methods
     */
    private suspend fun placeCatAtTouch(
        motionEvent: MotionEvent?,
        frame: Frame?,
        session: Session?,
        modelLoader: io.github.sceneview.loaders.ModelLoader,
        childNodes: MutableList<Node>,
        engine: com.google.android.filament.Engine
    ) {
        if (frame == null || session == null) {
            Log.w(TAG, "⚠️ Frame or Session is null")
            return
        }
        
        try {
            val touchX = motionEvent?.x ?: 540f
            val touchY = motionEvent?.y ?: 1200f
            
            Log.d(TAG, "🎯 Placing cat at: ($touchX, $touchY)")
            
            // Try standard hit test first
            val hitResults = frame.hitTest(touchX, touchY)
            var placed = false
            
            for (hitResult in hitResults) {
                try {
                    val anchor = hitResult.createAnchor()
                    if (createCatModel(anchor, modelLoader, childNodes, engine)) {
                        placed = true
                        break
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            
            // If standard hit test failed, try instant placement
            if (!placed) {
                try {
                    val instantResults = frame.hitTestInstantPlacement(touchX, touchY, 1.0f)
                    if (instantResults.isNotEmpty()) {
                        val anchor = instantResults.first().createAnchor()
                        placed = createCatModel(anchor, modelLoader, childNodes, engine)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Instant placement failed: ${e.message}")
                }
            }
            
            // Final fallback
            if (!placed) {
                try {
                    val camera = frame.camera
                    val cameraPosition = camera.pose
                    val translation = floatArrayOf(0f, 0f, -1f)
                    val rotation = floatArrayOf(0f, 0f, 0f, 1f)
                    val forwardPose = cameraPosition.compose(Pose(translation, rotation))
                    val anchor = session.createAnchor(forwardPose)
                    createCatModel(anchor, modelLoader, childNodes, engine)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ All placement methods failed: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Placement error: ${e.message}", e)
        }
    }
    
    /**
     * Create cat model using proper SceneView 2.3.0 ModelNode
     */
    private fun createCatModel(
        anchor: Anchor,
        modelLoader: io.github.sceneview.loaders.ModelLoader,
        childNodes: MutableList<Node>,
        engine: com.google.android.filament.Engine
    ): Boolean {
        return try {
            val modelInstance = modelLoader.createModelInstance(GLB_MODEL_PATH)
            if (modelInstance != null) {
                val anchorNode = AnchorNode(engine = engine, anchor = anchor)
                
                val modelNode = ModelNode(
                    modelInstance = modelInstance,
                    scaleToUnits = 0.3f
                ).apply {
                    name = "cat_model_${arRenderer.placedModelsCount.value + 1}"
                    
                    // Set initial rotation
                    rotation = Rotation(x = 0f, y = 90f, z = 0f)
                    position = Position(x = 0f, y = 0f, z = 0f)
                }
                
                // Add to tracking list for rotation
                placedModelNodes.add(modelNode)
                
                anchorNode.addChildNode(modelNode)
                childNodes.add(anchorNode)
                
                arRenderer.placedModelsCount.value++
                Log.d(TAG, "🎉 Cat #${arRenderer.placedModelsCount.value} placed: ${modelNode.name}")
                arRenderer.planeDetectionStatus.value = "Cat #${arRenderer.placedModelsCount.value} placed! Tap it for smooth 360° rotation"
                true
            } else {
                Log.e(TAG, "❌ Model instance creation failed")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Model creation failed: ${e.message}", e)
            false
        }
    }
    
    private suspend fun processUserMessage(message: String): String {
        return try {
            when (message.lowercase()) {
                "help" -> {
                    "🐱 Tap screen to place cats in AR!\n" +
                    "🔄 Tap any cat then drag for smooth 360° rotation:\n" +
                    "   ↔️ Horizontal drag for Y-axis rotation\n" +
                    "   ↕️ Vertical drag for X-axis rotation\n" +
                    "   🌟 NEW: Supports unlimited rotation with velocity damping!\n" +
                    "🗑️ Use 'clear' to remove all cats"
                }
                "rotation", "rotate" -> {
                    if (arRenderer.placedModelsCount.value > 0) {
                        "🔄 Tap any of the ${arRenderer.placedModelsCount.value} cats to select it for smooth rotation:\n" +
                        "✨ New Features:\n" +
                        "• 360° unlimited rotation (can spin multiple times)\n" +
                        "• Velocity damping for smooth motion\n" +
                        "• Inertial rotation after releasing touch\n" +
                        "• Accumulated rotation tracking\n" +
                        "Drag slowly for precise control!"
                    } else {
                        "🐱 Place some cats first, then enjoy smooth 360° rotation!"
                    }
                }
                "clear" -> {
                    resetRotationState()
                    "All cats cleared! 🗑️"
                }
                "speed", "sensitivity" -> {
                    "🎛️ Current rotation sensitivity (lower = smoother):\n" +
                    "↔️ Y-axis (horizontal): ${String.format("%.2f", rotationSensitivityY)}\n" +
                    "↕️ X-axis (vertical): ${String.format("%.2f", rotationSensitivityX)}\n" +
                    "🌟 Features: Velocity damping, smooth interpolation, 360° support\n" +
                    "Tap Settings to adjust!"
                }
                "debug" -> {
                    "Debug: ${arRenderer.placedModelsCount.value} cats placed\n" +
                    "Selected: ${selectedNode?.name ?: "None"}\n" +
                    "Rotating: $isRotating\n" +
                    "Accumulated X: ${String.format("%.1f", accumulatedRotationX)}°\n" +
                    "Accumulated Y: ${String.format("%.1f", accumulatedRotationY)}°\n" +
                    "Velocity X: ${String.format("%.3f", velocityX)}, Y: ${String.format("%.3f", velocityY)}\n" +
                    "Sensitivity Y: $rotationSensitivityY, X: $rotationSensitivityX"
                }
                else -> {
                    val result = WatsonAIEnhanced.getEnhancedAIResponse(message)
                    if (result.success && result.response.isNotEmpty()) {
                        result.response
                    } else {
                        "Meow~ Try the new smooth 360° rotation! Tap a cat and drag slowly for precise control! 🐱✨"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ AI processing exception", e)
            "Meow... Connection problem 😿"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🗑️ Application ended")
    }
}