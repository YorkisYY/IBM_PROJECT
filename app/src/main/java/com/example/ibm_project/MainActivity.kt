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
 * Main Activity - AR Cat Interaction App with Working SceneView 2.3.0 Dual-Axis Rotation Support
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val GLB_MODEL_PATH = "cute_spooky_cat.glb"
        private const val ROTATION_SENSITIVITY_X = 2.0f // 上下旋轉靈敏度
        private const val ROTATION_SENSITIVITY_Y = 3.0f // 左右旋轉靈敏度
        private const val MIN_ROTATION_DISTANCE = 20f   // 最小旋轉距離
    }
    
    // AR Renderer (simplified for proper SceneView usage)
    private lateinit var arRenderer: ARSceneViewRenderer
    
    // Rotation state
    private var selectedNode: ModelNode? = null
    private var isRotating = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var rotationStartX = 0f  // 記錄初始X軸旋轉
    private var rotationStartY = 0f  // 記錄初始Y軸旋轉
    private var touchStartX = 0f     // 記錄觸摸起始X位置
    private var touchStartY = 0f     // 記錄觸摸起始Y位置
    
    // Rotation sensitivity (adjustable)
    private var rotationSensitivityX = 2.0f
    private var rotationSensitivityY = 3.0f
    
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

        Box(modifier = Modifier.fillMaxSize()) {
            // Main AR View with correct SceneView 2.3.0 syntax
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
                
                // Enable plane rendering
                planeRenderer = true,
                
                // AR camera stream
                cameraStream = rememberARCameraStream(materialLoader),
                
                // Session configuration - CORRECT SYNTAX
                sessionConfiguration = { arSession, config ->
                    arRenderer.configureSession(arSession, config)
                },
                
                // Session lifecycle callbacks - CORRECT SYNTAX
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
                
                // Frame update handling - CORRECT SYNTAX
                onSessionUpdated = { arSession, updatedFrame ->
                    frame = updatedFrame
                    session = arSession
                    arRenderer.onSessionUpdated(arSession, updatedFrame)
                },
                
                // Touch event handling - CORRECT SYNTAX FOR SceneView 2.3.0
                onTouchEvent = { motionEvent: MotionEvent, hitResult: HitResult? ->
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN -> {
                            handleTouchDown(motionEvent, hitResult, frame, session, modelLoader, childNodes, engine)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            handleTouchMove(motionEvent)
                        }
                        MotionEvent.ACTION_UP -> {
                            handleTouchUp()
                        }
                    }
                    true // Always consume touch events
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
                            .widthIn(min = 120.dp, max = 220.dp),
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
                                    text = "↔️ Drag horizontally: Y-axis rotation",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Text(
                                    text = "↕️ Drag vertically: X-axis rotation",
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
                            text = "AR Mode + Rotation",
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
                                    selectedNode = null
                                    isRotating = false
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
                            Text("Adjust rotation sensitivity:")
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Y-axis sensitivity slider
                            Text("Y-axis (↔️ Horizontal) Sensitivity: ${String.format("%.1f", rotationSensitivityY)}")
                            Slider(
                                value = rotationSensitivityY,
                                onValueChange = { rotationSensitivityY = it },
                                valueRange = 0.5f..8.0f,
                                steps = 14
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // X-axis sensitivity slider  
                            Text("X-axis (↕️ Vertical) Sensitivity: ${String.format("%.1f", rotationSensitivityX)}")
                            Slider(
                                value = rotationSensitivityX,
                                onValueChange = { rotationSensitivityX = it },
                                valueRange = 0.5f..8.0f,
                                steps = 14
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Higher values = faster rotation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                        selectedNode != null -> "Selected: ${selectedNode?.name} - Drag to rotate..."
                        modelsCount > 0 -> "Tap cats to rotate (↔️ Y-axis, ↕️ X-axis), tap empty space to place new cats..."
                        else -> "Tap anywhere to place cats..."
                    },
                    isLoading = isLoading
                )
            }
        }
    }
    
    /**
     * Handle touch down event
     */
    private fun handleTouchDown(
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
        
        // Check if touching an existing model for rotation
        val touchedModel = findTouchedModel(motionEvent.x, motionEvent.y)
        if (touchedModel != null) {
            Log.d(TAG, "🎯 Model selected for rotation: ${touchedModel.name}")
            selectedNode = touchedModel
            isRotating = false
            
            // 記錄當前的旋轉值作為起始點
            rotationStartX = touchedModel.rotation.x
            rotationStartY = touchedModel.rotation.y
            
            arRenderer.planeDetectionStatus.value = "Cat selected: ${touchedModel.name} - Drag to rotate!"
            return
        }
        
        // If no model touched, place new model
        kotlinx.coroutines.runBlocking {
            placeCatAtTouch(motionEvent, frame, session, modelLoader, childNodes, engine)
        }
    }
    
    /**
     * Handle touch move event for dual-axis rotation
     */
    private fun handleTouchMove(motionEvent: MotionEvent) {
        selectedNode?.let { node ->
            val deltaX = motionEvent.x - touchStartX  // 水平移動距離
            val deltaY = motionEvent.y - touchStartY  // 垂直移動距離
            val totalDistance = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
            
            // Only rotate if movement is significant enough
            if (totalDistance > MIN_ROTATION_DISTANCE) {
                if (!isRotating) {
                    isRotating = true
                    Log.d(TAG, "🔄 Started dual-axis rotating: ${node.name}")
                }
                
                // 計算雙軸旋轉
                // 水平移動控制Y軸旋轉（左右轉頭）
                val rotationDeltaY = deltaX * rotationSensitivityY
                val newRotationY = (rotationStartY + rotationDeltaY) % 360f
                
                // 垂直移動控制X軸旋轉（上下點頭），限制角度範圍
                val rotationDeltaX = -deltaY * rotationSensitivityX  // 負號讓向上拖拽向上旋轉
                var newRotationX = rotationStartX + rotationDeltaX
                
                // 限制X軸旋轉範圍，避免過度翻轉
                newRotationX = newRotationX.coerceIn(-90f, 90f)
                
                // 應用旋轉
                node.rotation = Rotation(x = newRotationX, y = newRotationY, z = 0f)
                
                // 判斷主要旋轉方向並顯示對應信息
                val rotationInfo = when {
                    kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) * 1.5f -> {
                        "Y-axis: ${String.format("%.1f", newRotationY)}°"
                    }
                    kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) * 1.5f -> {
                        "X-axis: ${String.format("%.1f", newRotationX)}°"
                    }
                    else -> {
                        "X: ${String.format("%.1f", newRotationX)}°, Y: ${String.format("%.1f", newRotationY)}°"
                    }
                }
                
                Log.d(TAG, "🔄 Rotating ${node.name} - X: $newRotationX°, Y: $newRotationY°")
                arRenderer.planeDetectionStatus.value = "Rotating ${node.name} - $rotationInfo"
                
                // 更新最後觸摸位置
                lastTouchX = motionEvent.x
                lastTouchY = motionEvent.y
            }
        }
    }
    
    /**
     * Handle touch up event
     */
    private fun handleTouchUp() {
        if (isRotating && selectedNode != null) {
            Log.d(TAG, "✅ Rotation completed for: ${selectedNode?.name}")
            arRenderer.planeDetectionStatus.value = "Rotation completed! Tap cat to rotate or tap empty space to place new cat"
            isRotating = false
        }
    }
    
    /**
     * Find touched model based on screen coordinates (simplified approach)
     */
    private fun findTouchedModel(screenX: Float, screenY: Float): ModelNode? {
        // Simplified model detection - check all placed models
        for (modelNode in placedModelNodes) {
            try {
                // Simple distance-based check (you could implement more sophisticated hit testing)
                // For now, we'll use a proximity-based approach
                // This is a simplified implementation - in production you'd want proper ray casting
                
                // For demo purposes, return the most recently placed model if touch is in center area
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
                arRenderer.planeDetectionStatus.value = "Cat #${arRenderer.placedModelsCount.value} placed! Tap it to rotate"
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
                    "🔄 Tap any cat then drag to rotate:\n" +
                    "   ↔️ Drag horizontally for Y-axis rotation\n" +
                    "   ↕️ Drag vertically for X-axis rotation\n" +
                    "🗑️ Use 'clear' to remove all cats"
                }
                "rotation", "rotate" -> {
                    if (arRenderer.placedModelsCount.value > 0) {
                        "🔄 Tap any of the ${arRenderer.placedModelsCount.value} cats to select it, then:\n" +
                        "↔️ Drag horizontally to rotate around Y-axis (left/right)\n" +
                        "↕️ Drag vertically to rotate around X-axis (up/down)\n" +
                        "You can combine both movements for dual-axis rotation!"
                    } else {
                        "🐱 Place some cats first, then you can tap and drag to rotate them!"
                    }
                }
                "clear" -> {
                    selectedNode = null
                    isRotating = false
                    "All cats cleared! 🗑️"
                }
                "speed", "sensitivity" -> {
                    "🎛️ Current rotation sensitivity:\n" +
                    "↔️ Y-axis (horizontal): ${String.format("%.1f", rotationSensitivityY)}\n" +
                    "↕️ X-axis (vertical): ${String.format("%.1f", rotationSensitivityX)}\n" +
                    "Tap the Settings button in the control panel to adjust!"
                }
                "debug" -> {
                    "Debug: ${arRenderer.placedModelsCount.value} cats placed\n" +
                    "Selected: ${selectedNode?.name ?: "None"}\n" +
                    "Rotating: $isRotating\n" +
                    "Sensitivity Y: $rotationSensitivityY, X: $rotationSensitivityX"
                }
                else -> {
                    val result = WatsonAIEnhanced.getEnhancedAIResponse(message)
                    if (result.success && result.response.isNotEmpty()) {
                        result.response
                    } else {
                        "Meow~ Try tapping a cat and dragging to rotate it! 🐱"
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