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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlin.math.abs
import kotlin.math.roundToInt
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
import io.github.sceneview.utils.worldToScreen

// ARScene imports
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.ar.rememberARCameraStream
import io.github.sceneview.ar.node.AnchorNode

/**
 * AR Cat Interaction App - Watson對話框綁定到第一隻貓咪
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val GLB_MODEL_PATH = "cute_spooky_cat.glb"
        
        // 改善的旋轉參數
        private const val ROTATION_SENSITIVITY_X = 0.3f
        private const val ROTATION_SENSITIVITY_Y = 0.5f
        private const val MIN_ROTATION_DISTANCE = 10f
        
        // 速度阻尼和平滑參數
        private const val VELOCITY_DAMPING = 0.85f
        private const val SMOOTH_FACTOR = 0.15f
        private const val MIN_VELOCITY_THRESHOLD = 0.01f
    }
    
    // AR Renderer
    private lateinit var arRenderer: ARSceneViewRenderer
    
    // 旋轉狀態管理
    private var selectedNode: ModelNode? = null
    private var isRotating = false
    
    // 觸摸狀態
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    
    // 累積旋轉值
    private var accumulatedRotationX = 0f
    private var accumulatedRotationY = 0f
    
    // 速度追蹤
    private var velocityX = 0f
    private var velocityY = 0f
    private var lastMoveTime = 0L
    
    // 目標旋轉值
    private var targetRotationX = 0f
    private var targetRotationY = 0f
    private var currentRotationX = 0f
    private var currentRotationY = 0f
    
    // 可調節的旋轉靈敏度
    private var rotationSensitivityX = ROTATION_SENSITIVITY_X
    private var rotationSensitivityY = ROTATION_SENSITIVITY_Y
    
    // 存儲所有放置的模型節點
    private val placedModelNodes = mutableListOf<ModelNode>()
    
    // 為每個模型存儲累積旋轉值
    private val modelRotationMap = mutableMapOf<String, Pair<Float, Float>>()
    
    // 第一隻貓咪狀態管理
    private var firstCatModel: ModelNode? = null
    
    // 存儲第一隻貓咪的實際邊界框高度 - 使用固定預估值
    private var firstCatBoundingHeight: Float = 0.4f // 固定預估高度
    
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

    /**
     * 計算模型的邊界框高度 - 簡化版本，使用預估值
     */
    private fun calculateModelBoundingHeight(modelNode: ModelNode): Float {
        return try {
            // 基於固定預估高度，因為無法直接訪問scaleToUnits屬性
            val estimatedHeight = 0.4f // 固定預估高度
            Log.d(TAG, "📏 Using estimated height for ${modelNode.name}: ${estimatedHeight}")
            estimatedHeight
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error calculating bounding height: ${e.message}")
            0.3f // 預設高度
        }
    }

    @Composable
    private fun ARInterface() {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val coroutineScope = rememberCoroutineScope()
        val density = LocalDensity.current
        
        // UI state
        var inputText by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var chatMessage by remember { mutableStateOf("") }
        var isChatVisible by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        
        // 對話框綁定到第一隻貓咪的位置
        var firstCatDialogPosition by remember { mutableStateOf(Offset.Zero) }
        var hasFirstCat by remember { mutableStateOf(false) }
        
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
        
        // 獲取第一隻貓咪的屏幕坐標 - 優化版本
        LaunchedEffect(firstCatModel, frame) {
            firstCatModel?.let { cat ->
                try {
                    // 使用貓咪的基礎世界位置，不需要額外高度
                    val catWorldPosition = Position(
                        x = cat.worldPosition.x,
                        y = cat.worldPosition.y + 0.1f, // 只添加20cm高度
                        z = cat.worldPosition.z
                    )
                    
                    // 轉換為屏幕坐標
                    val screenPos = view.worldToScreen(catWorldPosition)
                    
                    // 只有當Y坐標是正數且合理時才更新
                    if (screenPos.y > 0f && screenPos.y < 2000f) {
                        val newPosition = Offset(screenPos.x, screenPos.y)
                        
                        // 只有當位置變化較大時才更新（減少不必要的重組）
                        val distance = kotlin.math.sqrt(
                            (newPosition.x - firstCatDialogPosition.x).let { it * it } +
                            (newPosition.y - firstCatDialogPosition.y).let { it * it }
                        )
                        
                        if (distance > 10f) { // 只有移動超過10像素才更新
                            firstCatDialogPosition = newPosition
                            hasFirstCat = true
                            Log.d(TAG, "🎯 First cat dialog position updated: screenPos=(${screenPos.x}, ${screenPos.y})")
                        } else if (!hasFirstCat) {
                            firstCatDialogPosition = newPosition
                            hasFirstCat = true
                            Log.d(TAG, "🎯 First cat dialog position initialized: screenPos=(${screenPos.x}, ${screenPos.y})")
                        }
                    } else {
                        Log.w(TAG, "⚠️ Cat position off screen: Y=${screenPos.y}")
                        hasFirstCat = false
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error updating first cat dialog position: ${e.message}")
                    hasFirstCat = false
                }
            } ?: run {
                hasFirstCat = false
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
                            handleImprovedTouchDown(motionEvent, hitResult, frame, session, modelLoader, childNodes, engine) { newFirstCat ->
                                // 當創建第一隻貓咪時更新狀態
                                if (firstCatModel == null && newFirstCat != null) {
                                    firstCatModel = newFirstCat
                                    // 計算並存儲第一隻貓咪的預估邊界框高度
                                    firstCatBoundingHeight = calculateModelBoundingHeight(newFirstCat)
                                    Log.d(TAG, "🎯 First cat set for dialog binding: ${newFirstCat.name}, height: ${firstCatBoundingHeight}")
                                }
                            }
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
            
            // Watson對話框 - 綁定到第一隻貓咪，更寬的對話框
            if (isChatVisible && chatMessage.isNotEmpty() && hasFirstCat && firstCatModel != null) {
                BoxWithConstraints {
                    val dialogOffset = with(density) {
                        val dialogWidth = 340.dp.toPx() // 增加寬度從280到340
                        val maxDialogHeight = 160.dp.toPx() // 稍微增加高度
                        val margin = 20.dp.toPx()
                        
                        // 使用 BoxWithConstraints 提供的 maxWidth 和 maxHeight
                        val screenWidthPx = maxWidth.toPx()
                        val screenHeightPx = maxHeight.toPx()
                        
                        // 計算安全邊界
                        val safeMaxX: Float = (screenWidthPx - dialogWidth).coerceAtLeast(margin)
                        val safeMinY: Float = 120.dp.toPx() // 預留控制面板空間
                        
                        // 對話框位置：確保在屏幕可見範圍內
                        val catScreenY = firstCatDialogPosition.y
                        
                        // 確保對話框在屏幕可見範圍內
                        val dialogTopY = if (catScreenY > 200.dp.toPx()) {
                            // 如果貓咪位置足夠低，對話框在貓咪上方
                            (catScreenY - maxDialogHeight - 20.dp.toPx()).coerceAtLeast(safeMinY)
                        } else {
                            // 如果貓咪位置太高，對話框在貓咪下方
                            (catScreenY + 50.dp.toPx()).coerceAtMost(screenHeightPx - maxDialogHeight - 50.dp.toPx())
                        }
                        
                        IntOffset(
                            x = (firstCatDialogPosition.x - (dialogWidth / 2)).roundToInt()
                                .coerceIn(margin.roundToInt(), safeMaxX.roundToInt()),
                            y = dialogTopY.roundToInt()
                        )
                    }
                    
                    // Watson對話框 - 更寬的對話框設計
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { dialogOffset },
                        contentAlignment = Alignment.TopStart
                    ) {
                        Column {
                            // 對話框內容 - 可變高度，支持滾動
                            Box(
                                modifier = Modifier
                                    .width(340.dp) // 更寬的對話框
                                    .heightIn(min = 90.dp, max = 160.dp) // 稍微增加最小和最大高度
                                    .shadow(8.dp, RoundedCornerShape(16.dp))
                                    .background(
                                        color = Color(0xFF2196F3),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(18.dp), // 增加內邊距讓內容更舒適
                                contentAlignment = Alignment.TopStart
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp) // 稍微增加間距
                                ) {
                                    // 顯示第一隻貓咪名稱和位置信息
                                    Text(
                                        text = "🐱 ${firstCatModel?.name ?: "First Cat"} (${firstCatDialogPosition.x.toInt()}, ${firstCatDialogPosition.y.toInt()})",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp, // 稍微小一點顯示坐標
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    // 對話內容 - 支持滾動的區域
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f), // 佔據剩餘空間
                                        verticalArrangement = Arrangement.Top
                                    ) {
                                        item {
                                            Text(
                                                text = chatMessage,
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                lineHeight = 22.sp, // 增加行高讓文字更好讀
                                                textAlign = TextAlign.Start
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // 指向貓咪的小箭頭 - 緊貼對話框底部，調整位置配合新寬度
                            Box(
                                modifier = Modifier
                                    .padding(start = (340.dp / 2 - 6.dp)) // 調整箭頭位置配合新寬度
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
                // 如果沒有第一隻貓咪，顯示在屏幕中央 - 也支持滾動和更寬設計
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 120.dp)
                            .widthIn(min = 240.dp, max = 360.dp) // 增加最大寬度
                            .heightIn(min = 70.dp, max = 220.dp) // 增加高度範圍
                            .shadow(8.dp, RoundedCornerShape(16.dp))
                            .background(
                                color = Color(0xFF2196F3),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(18.dp) // 增加內邊距
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.Top
                        ) {
                            item {
                                Text(
                                    text = chatMessage,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp, // 增加行高
                                    textAlign = TextAlign.Start
                                )
                            }
                        }
                    }
                }
            }
            
            // 控制面板
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
                            text = "AR Mode + Wider Dialog",
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
                    
                    // Status
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
                                text = "$modelsCount Cats" + if (firstCatModel != null) " (Wide Dialog)" else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        // Clear button
                        if (modelsCount > 0) {
                            TextButton(
                                onClick = {
                                    clearAllCats(childNodes)
                                    // 清除對話框
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
                                text = "💬 Watson wider dialog with smart positioning and reduced updates",
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
                                    // 清除上一個對話框
                                    isChatVisible = false
                                    
                                    val reply = processUserMessage(userMessage)
                                    chatMessage = reply
                                    isChatVisible = true
                                    
                                    // 移除自動消失 - 等下次輸入才消失
                                    // delay(5000)
                                    // isChatVisible = false
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Processing failed", e)
                                    chatMessage = "Sorry, something went wrong 😿"
                                    isChatVisible = true
                                    // 錯誤消息3秒後消失
                                    delay(3000)
                                    isChatVisible = false
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    placeholder = when {
                        selectedNode != null -> "Selected: ${selectedNode?.name} - Rotate with drag..."
                        modelsCount > 0 -> "Chat with spooky cat & dialog bound to first cat. Tap cats to rotate!"
                        else -> "Tap anywhere to place cats..."
                    },
                    isLoading = isLoading
                )
            }
        }
    }
    
    /**
     * 清除所有貓咪並重置狀態
     */
    private fun clearAllCats(childNodes: MutableList<Node>) {
        resetRotationState()
        placedModelNodes.clear()
        firstCatModel = null // 重置第一隻貓咪引用
        firstCatBoundingHeight = 0.4f // 重置高度
        arRenderer.clearAllModels(childNodes)
        arRenderer.planeDetectionStatus.value = "All cats cleared! Place first cat for wide Watson dialog"
        Log.d(TAG, "🗑️ All cats cleared, ready for new wide dialog first cat")
    }
    
    /**
     * 改善的觸摸按下處理 - 增加第一隻貓咪回調
     */
    private fun handleImprovedTouchDown(
        motionEvent: MotionEvent,
        hitResult: HitResult?,
        frame: Frame?,
        session: Session?,
        modelLoader: io.github.sceneview.loaders.ModelLoader,
        childNodes: MutableList<Node>,
        engine: com.google.android.filament.Engine,
        onFirstCatCreated: (ModelNode?) -> Unit
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
            
            // 從存儲的映射中獲取該模型的累積旋轉值
            val modelName = touchedModel.name ?: "unknown"
            val storedRotation = modelRotationMap[modelName]
            
            if (storedRotation != null) {
                accumulatedRotationX = storedRotation.first
                accumulatedRotationY = storedRotation.second
                Log.d(TAG, "📋 Restored rotation for $modelName - X: ${accumulatedRotationX}°, Y: ${accumulatedRotationY}°")
            } else {
                val currentRotation = touchedModel.rotation
                accumulatedRotationX = currentRotation.x
                accumulatedRotationY = currentRotation.y
                modelRotationMap[modelName] = Pair(accumulatedRotationX, accumulatedRotationY)
                Log.d(TAG, "🆕 New model rotation tracking for $modelName - X: ${accumulatedRotationX}°, Y: ${accumulatedRotationY}°")
            }
            
            targetRotationX = accumulatedRotationX
            targetRotationY = accumulatedRotationY
            currentRotationX = accumulatedRotationX
            currentRotationY = accumulatedRotationY
            
            velocityX = 0f
            velocityY = 0f
            
            arRenderer.planeDetectionStatus.value = "Cat selected: ${touchedModel.name} - Wide Watson dialog"
            return
        }
        
        // 如果沒有觸摸到模型，放置新模型
        kotlinx.coroutines.runBlocking {
            val newCat = placeCatAtTouch(motionEvent, frame, session, modelLoader, childNodes, engine)
            onFirstCatCreated(newCat)
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
            
            if (totalDistance > MIN_ROTATION_DISTANCE) {
                if (!isRotating) {
                    isRotating = true
                    Log.d(TAG, "🔄 Started smooth 360° rotation: ${node.name}")
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
     * 改善的觸摸釋放處理
     */
    private fun handleImprovedTouchUp() {
        if (isRotating && selectedNode != null) {
            Log.d(TAG, "✅ Smooth rotation completed for: ${selectedNode?.name}")
            arRenderer.planeDetectionStatus.value = "Rotation completed! Wide Watson dialog"
            
            isRotating = false
            velocityX *= 0.5f
            velocityY *= 0.5f
        }
    }
    
    /**
     * 平滑旋轉更新
     */
    private fun updateSmoothRotation() {
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
    
    private fun findTouchedModel(screenX: Float, screenY: Float): ModelNode? {
        for (modelNode in placedModelNodes) {
            try {
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
    
    private suspend fun placeCatAtTouch(
        motionEvent: MotionEvent?,
        frame: Frame?,
        session: Session?,
        modelLoader: io.github.sceneview.loaders.ModelLoader,
        childNodes: MutableList<Node>,
        engine: com.google.android.filament.Engine
    ): ModelNode? {
        if (frame == null || session == null) {
            Log.w(TAG, "⚠️ Frame or Session is null")
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
                    placedModel = createCatModel(anchor, modelLoader, childNodes, engine)
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
                        placedModel = createCatModel(anchor, modelLoader, childNodes, engine)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Instant placement failed: ${e.message}")
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
                    placedModel = createCatModel(anchor, modelLoader, childNodes, engine)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ All placement methods failed: ${e.message}")
                }
            }
            
            return placedModel
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Placement error: ${e.message}", e)
            return null
        }
    }
    
    private fun createCatModel(
        anchor: Anchor,
        modelLoader: io.github.sceneview.loaders.ModelLoader,
        childNodes: MutableList<Node>,
        engine: com.google.android.filament.Engine
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
                Log.d(TAG, "🎉 Cat #${arRenderer.placedModelsCount.value} placed: ${modelNode.name}")
                
                val statusMessage = if (arRenderer.placedModelsCount.value == 1) {
                    "First cat placed! Wide Watson dialog (340dp) will bind to this cat"
                } else {
                    "Cat #${arRenderer.placedModelsCount.value} placed! Wide Watson dialog stays bound to first cat"
                }
                
                arRenderer.planeDetectionStatus.value = statusMessage
                modelNode
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Model creation failed: ${e.message}", e)
            null
        }
    }
    
    private suspend fun processUserMessage(message: String): String {
        return try {
            when (message.lowercase()) {
                "help" -> {
                    "🐱 Tap screen to place cats in AR!\n" +
                    "🔄 Tap any cat then drag for smooth 360° rotation\n" +
                    "💬 Watson wide dialog follows first cat with smart positioning\n" +
                    "🗑️ Use 'clear' to remove all cats and reset"
                }
                "clear" -> {
                    "All cats cleared! Place a new first cat for wide dialog binding! 🗑️"
                }
                else -> {
                    val result = WatsonAIEnhanced.getEnhancedAIResponse(message)
                    if (result.success && result.response.isNotEmpty()) {
                        result.response
                    } else {
                        "Meow~ Watson wide dialog is bound to the first cat! 🐱✨"
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