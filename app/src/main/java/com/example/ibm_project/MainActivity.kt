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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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
import ar.ARTouchHandler
import ar.ARDialogTracker
import ar.PlacementModeManager // ✅ 新增：模式管理器
import ar.PlacementModeToggleButton // ✅ 新增：模式切換按鈕
import kotlin.math.roundToInt
import androidx.compose.foundation.BorderStroke
// SceneView 2.3.0 imports
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberView
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.collision.HitResult
import io.github.sceneview.math.Position
import io.github.sceneview.utils.worldToScreen
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node

// ARScene imports
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.ar.rememberARCameraStream

/**
 * AR Cat Interaction App - Watson dialog bound to first cat
 * 基於你的原始設計，添加 PlacementModeManager 的模式切換功能
 * 保持原本的觸摸處理邏輯和碰撞檢測系統
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }
    
    // AR components
    private lateinit var arRenderer: ARSceneViewRenderer
    private lateinit var touchHandler: ARTouchHandler
    private lateinit var dialogTracker: ARDialogTracker
    private lateinit var placementModeManager: PlacementModeManager // ✅ 新增：模式管理器
    
    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResults(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        
        // Initialize AR components
        arRenderer = ARSceneViewRenderer()
        touchHandler = ARTouchHandler()
        dialogTracker = ARDialogTracker()
        placementModeManager = PlacementModeManager(this) // ✅ 新增：初始化模式管理器
        
        // ✅ 新增：整合 Handler 和 Manager
        placementModeManager.setARTouchHandler(touchHandler)
        
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
            Log.d(TAG, "Camera permission granted")
            checkARCoreAvailability()
        } else {
            Log.w(TAG, "Camera permission denied")
            arRenderer.trackingStatus.value = "Camera Permission Required"
        }
    }

    private fun checkARCoreAvailability() {
        when (ArCoreApk.getInstance().checkAvailability(this)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                Log.d(TAG, "ARCore installed and supported")
                arRenderer.trackingStatus.value = "AR Ready"
            }
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> {
                Log.d(TAG, "Need to update or install ARCore")
                requestARCoreInstall()
            }
            else -> {
                Log.w(TAG, "Device does not support ARCore")
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
            Log.e(TAG, "ARCore installation failed: ${e.message}")
            arRenderer.trackingStatus.value = "ARCore Installation Failed"
        }
    }

    @Composable
    private fun ARInterface() {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val coroutineScope = rememberCoroutineScope()
        val density = LocalDensity.current
        val focusManager = LocalFocusManager.current
        
        // UI state
        var inputText by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var chatMessage by remember { mutableStateOf("") }
        var isChatVisible by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        
        // Dialog binding to first cat position
        var firstCatDialogPosition by remember { mutableStateOf(Offset.Zero) }
        var hasFirstCat by remember { mutableStateOf(false) }
        
        // AR state from renderer
        val planesCount = arRenderer.detectedPlanesCount.value
        val modelsCount = arRenderer.placedModelsCount.value
        val canPlace = arRenderer.canPlaceObjects.value
        val trackingStatus = arRenderer.trackingStatus.value
        val planeStatus = arRenderer.planeDetectionStatus.value
        
        // ✅ 新增：獲取當前模式
        val currentMode by placementModeManager.currentMode
        
        // SceneView 2.3.0 initialization
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
                Log.d(TAG, "Starting initialization...")
                WatsonAIEnhanced.initialize(context as MainActivity)
                Log.d(TAG, "Initialization completed")
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed: ${e.message}", e)
            }
        }
        
        // Smooth rotation update loop
        LaunchedEffect(touchHandler.getSelectedNode()) {
            while (touchHandler.getSelectedNode() != null) {
                touchHandler.updateSmoothRotation()
                delay(16) // ~60 FPS
            }
        }
        
        // First cat screen coordinate tracking logic
        LaunchedEffect(touchHandler.getFirstCatModel(), frame) {
            touchHandler.getFirstCatModel()?.let { cat ->
                try {
                    val catWorldPosition = Position(
                        x = cat.worldPosition.x,
                        y = cat.worldPosition.y + 0.1f,
                        z = cat.worldPosition.z
                    )
                    
                    val screenPos = view.worldToScreen(catWorldPosition)
                    
                    if (screenPos.y > 0f && screenPos.y < 2000f) {
                        val newPosition = Offset(screenPos.x, screenPos.y)
                        
                        val distance = kotlin.math.sqrt(
                            (newPosition.x - firstCatDialogPosition.x).let { it * it } +
                            (newPosition.y - firstCatDialogPosition.y).let { it * it }
                        )
                        
                        if (distance > 10f) {
                            firstCatDialogPosition = newPosition
                            hasFirstCat = true
                            Log.d(TAG, "First cat dialog position updated: screenPos=(${screenPos.x}, ${screenPos.y})")
                        } else if (!hasFirstCat) {
                            firstCatDialogPosition = newPosition
                            hasFirstCat = true
                            Log.d(TAG, "First cat dialog position initialized: screenPos=(${screenPos.x}, ${screenPos.y})")
                        }
                    } else {
                        Log.w(TAG, "Cat position off screen: Y=${screenPos.y}")
                        hasFirstCat = false
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating first cat dialog position: ${e.message}")
                    hasFirstCat = false
                }
            } ?: run {
                hasFirstCat = false
            }
        }

        // Add click outside area to cancel input focus functionality
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    focusManager.clearFocus()
                }
        ) {
            // Main AR View - 保持你的原始設計
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
                    // ✅ 新增：讓 Manager 知道 Session
                    placementModeManager.setSession(arSession)
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
                
                // ✅ 保持你的原始觸摸處理方式
                onTouchEvent = { motionEvent: MotionEvent, hitResult: HitResult? ->
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // Clear input focus
                            focusManager.clearFocus()
                            
                            // ✅ 新增：讓 Manager 知道當前 Session（用於配置平面檢測）
                            placementModeManager.setSession(session)
                            
                            // ✅ 保持你的原始觸摸處理：直接調用 Handler
                            touchHandler.handleSceneViewTouchDown(
                                motionEvent = motionEvent,
                                hitResult = hitResult,
                                frame = frame,
                                session = session,
                                modelLoader = modelLoader,
                                childNodes = childNodes,
                                engine = engine,
                                arRenderer = arRenderer,
                                collisionSystem = collisionSystem, // ✅ 直接可用
                                cameraNode = cameraNode, // ✅ 直接可用
                                onFirstCatCreated = { newFirstCat: ModelNode? ->
                                    Log.d(TAG, "First cat created for dialog binding: ${newFirstCat?.name ?: "unknown"}")
                                }
                            )
                        }
                        MotionEvent.ACTION_MOVE -> {
                            touchHandler.handleImprovedTouchMove(motionEvent)
                        }
                        MotionEvent.ACTION_UP -> {
                            touchHandler.handleImprovedTouchUp(arRenderer)
                        }
                    }
                    true
                }
            )
            
            // Watson dialog - bound to first cat
            if (isChatVisible && chatMessage.isNotEmpty() && hasFirstCat && touchHandler.getFirstCatModel() != null) {
                BoxWithConstraints {
                    val dialogOffset = with(density) {
                        val dialogWidth = 340.dp.toPx()
                        val maxDialogHeight = 160.dp.toPx()
                        val margin = 20.dp.toPx()
                        
                        val screenWidthPx = maxWidth.toPx()
                        val screenHeightPx = maxHeight.toPx()
                        
                        val safeMaxX: Float = (screenWidthPx - dialogWidth).coerceAtLeast(margin)
                        val safeMinY: Float = 120.dp.toPx()
                        
                        val catScreenY = firstCatDialogPosition.y
                        
                        val dialogTopY = if (catScreenY > 200.dp.toPx()) {
                            (catScreenY - maxDialogHeight - 20.dp.toPx()).coerceAtLeast(safeMinY)
                        } else {
                            (catScreenY + 50.dp.toPx()).coerceAtMost(screenHeightPx - maxDialogHeight - 50.dp.toPx())
                        }
                        
                        IntOffset(
                            x = (firstCatDialogPosition.x - (dialogWidth / 2)).roundToInt()
                                .coerceIn(margin.roundToInt(), safeMaxX.roundToInt()),
                            y = dialogTopY.roundToInt()
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { dialogOffset },
                        contentAlignment = Alignment.TopStart
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .width(340.dp)
                                    .heightIn(min = 90.dp, max = 160.dp)
                                    .shadow(8.dp, RoundedCornerShape(16.dp))
                                    .background(
                                        color = Color(0xFF2196F3),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(18.dp),
                                contentAlignment = Alignment.TopStart
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "${touchHandler.getFirstCatModel()?.name ?: "First Cat"} (${firstCatDialogPosition.x.toInt()}, ${firstCatDialogPosition.y.toInt()})",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement = Arrangement.Top
                                    ) {
                                        item {
                                            Text(
                                                text = chatMessage,
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                lineHeight = 22.sp,
                                                textAlign = TextAlign.Start
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .padding(start = (340.dp / 2 - 6.dp))
                                    .size(12.dp)
                                    .background(
                                        Color(0xFF2196F3),
                                        RoundedCornerShape(topStart = 6.dp)
                                    )
                                    .offset(y = (-1).dp)
                            )
                        }
                    }
                }
            } else if (isChatVisible && chatMessage.isNotEmpty() && !hasFirstCat) {
                // If no first cat, display in screen center
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 120.dp)
                            .widthIn(min = 240.dp, max = 360.dp)
                            .heightIn(min = 70.dp, max = 220.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp))
                            .background(
                                color = Color(0xFF2196F3),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(18.dp)
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.Top
                        ) {
                            item {
                                Text(
                                    text = chatMessage,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp,
                                    textAlign = TextAlign.Start
                                )
                            }
                        }
                    }
                }
            }

            // ✅ 新增：Right side control area (mode switching + Settings)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 16.dp, top = 120.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                // ✅ 新增：Mode switching button
                PlacementModeToggleButton(
                    placementModeManager = placementModeManager,
                    childNodes = childNodes,
                    arRenderer = arRenderer,
                    modelCount = modelsCount,
                    onModelsCleared = {
                        // ✅ 新增：清除所有狀態保持同步
                        touchHandler.clearAllCats(childNodes, arRenderer)
                        
                        // Clear UI state
                        hasFirstCat = false
                        firstCatDialogPosition = Offset.Zero
                        isChatVisible = false
                        chatMessage = ""
                        
                        Log.d(TAG, "Mode switched - all states cleared")
                    }
                )
                
                // Settings button
                Card(
                    modifier = Modifier
                        .width(100.dp)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        
                        Button(
                            onClick = { showSettings = true },
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        
            // Control panel - ✅ 更新顯示模式資訊
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
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AR Cat App",
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
                    
                    // ✅ 更新：顯示模式和狀態
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
                                text = "$modelsCount Cats" + if (touchHandler.getFirstCatModel() != null) " (Dialog)" else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        // Clear button
                        if (modelsCount > 0) {
                            TextButton(
                                onClick = {
                                    // ✅ 使用 Manager 的清除方法
                                    placementModeManager.clearAllModels(childNodes, arRenderer)
                                    hasFirstCat = false
                                    firstCatDialogPosition = Offset.Zero
                                    isChatVisible = false
                                    chatMessage = ""
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
                            Text("Y-axis (Horizontal) Sensitivity: ${String.format("%.2f", touchHandler.rotationSensitivityY)}")
                            Slider(
                                value = touchHandler.rotationSensitivityY,
                                onValueChange = { touchHandler.rotationSensitivityY = it },
                                valueRange = 0.1f..2.0f,
                                steps = 18
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // X-axis sensitivity slider  
                            Text("X-axis (Vertical) Sensitivity: ${String.format("%.2f", touchHandler.rotationSensitivityX)}")
                            Slider(
                                value = touchHandler.rotationSensitivityX,
                                onValueChange = { touchHandler.rotationSensitivityX = it },
                                valueRange = 0.1f..2.0f,
                                steps = 18
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Watson dialog with smart positioning and mode switching",
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
                                touchHandler.resetSensitivityToDefault()
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
                                    isChatVisible = false
                                    
                                    val reply = processUserMessage(userMessage)
                                    chatMessage = reply
                                    isChatVisible = true
                                    
                                } catch (e: Exception) {
                                    Log.e(TAG, "Processing failed", e)
                                    chatMessage = "Sorry, something went wrong"
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
                        touchHandler.getSelectedNode() != null -> "Selected: ${touchHandler.getSelectedNode()?.name} - Rotate with drag..."
                        modelsCount > 0 -> "Chat with spooky cats! (${currentMode.displayName} mode)" // ✅ 顯示當前模式
                        else -> "Tap anywhere to place cats... (${currentMode.displayName} mode)" // ✅ 顯示當前模式
                    },
                    isLoading = isLoading
                )
            }
        }
    }
    
    private suspend fun processUserMessage(message: String): String {
        return try {
            when (message.lowercase()) {
                "help" -> {
                    "Tap screen to place cats in AR!\n" +
                    "Tap any cat then drag for smooth 360 degree rotation\n" +
                    "Use right panel to switch placement modes\n" +
                    "Watson dialog follows first cat with smart positioning\n" +
                    "Use 'clear' to remove all cats and reset"
                }
                "clear" -> {
                    "All cats cleared! Place a new first cat for dialog binding! Current mode: ${placementModeManager.currentMode.value.displayName}" // ✅ 顯示當前模式
                }
                else -> {
                    val result = WatsonAIEnhanced.getEnhancedAIResponse(message)
                    if (result.success && result.response.isNotEmpty()) {
                        result.response
                    } else {
                        "Meow~ Watson dialog is bound to the first cat! Mode: ${placementModeManager.currentMode.value.displayName}" // ✅ 顯示當前模式
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI processing exception", e)
            "Meow... Connection problem"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Application ended")
    }
}