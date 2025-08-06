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
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material.icons.filled.Warning
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
import androidx.lifecycle.lifecycleScope
import com.example.ibm_project.ui.theme.IBM_PROJECTTheme
import com.google.ar.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.components.UserInputField
import watsonx.WatsonAIEnhanced

// ✅ 正確的 SceneView 2.3.0 imports
import io.github.sceneview.Scene
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberView
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.node.ModelNode
import io.github.sceneview.math.Position

// ✅ 正確的 ARScene imports - 基於官方示例
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.ar.rememberARCameraStream
import io.github.sceneview.ar.node.AnchorNode

/**
 * 主活動 - 基於 SceneView 2.3.0 官方示例的正確實現
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val GLB_MODEL_PATH = "cute_spooky_cat.glb"
    }
    
    // 模式狀態
    private val currentMode = mutableStateOf<ViewMode>(ViewMode.MODE_AR)
    private val detectedPlanesCount = mutableStateOf(0)
    private val placedModelsCount = mutableStateOf(0)
    private val trackingStatus = mutableStateOf("初始化中...")
    private val planeDetectionStatus = mutableStateOf("正在掃描環境...")
    private val canPlaceObjects = mutableStateOf(false)
    
    // 視圖模式
    enum class ViewMode {
        MODE_3D,    // 純 3D 模式
        MODE_AR     // AR 模式
    }
    
    // 權限請求
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResults(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🚀 onCreate 開始")
        
        // 請求必要權限
        requestNecessaryPermissions()
        
        // 設置 Compose UI
        setContent {
            IBM_PROJECTTheme {
                IntegratedInterface()
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
            Log.d(TAG, "✅ 相機權限已授予")
            checkARCoreAvailability()
        } else {
            Log.w(TAG, "⚠️ 相機權限被拒絕，只能使用 3D 模式")
            currentMode.value = ViewMode.MODE_3D
        }
    }

    private fun checkARCoreAvailability() {
        when (ArCoreApk.getInstance().checkAvailability(this)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                Log.d(TAG, "✅ ARCore 已安裝並支援")
                trackingStatus.value = "AR 就緒"
            }
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> {
                Log.d(TAG, "📦 需要更新或安裝 ARCore")
                requestARCoreInstall()
            }
            else -> {
                Log.w(TAG, "⚠️ 設備不支援 ARCore，使用 3D 模式")
                currentMode.value = ViewMode.MODE_3D
            }
        }
    }
    
    private fun requestARCoreInstall() {
        try {
            val installStatus = ArCoreApk.getInstance().requestInstall(this, true)
            if (installStatus == ArCoreApk.InstallStatus.INSTALLED) {
                trackingStatus.value = "AR 就緒"
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ARCore 安裝失敗: ${e.message}")
            currentMode.value = ViewMode.MODE_3D
        }
    }

    @Composable
    private fun IntegratedInterface() {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val coroutineScope = rememberCoroutineScope()
        
        // UI 状态
        var inputText by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var chatMessage by remember { mutableStateOf("") }
        var isChatVisible by remember { mutableStateOf(false) }
        
        // 模式和状态
        val mode = currentMode.value
        val planesCount = detectedPlanesCount.value
        val modelsCount = placedModelsCount.value
        val canPlace = canPlaceObjects.value
        val planeStatus = planeDetectionStatus.value
        
        // ✅ 基於官方示例的正確初始化
        val engine = rememberEngine()
        val modelLoader = rememberModelLoader(engine)
        val materialLoader = rememberMaterialLoader(engine)
        val environmentLoader = rememberEnvironmentLoader(engine)
        val view = rememberView(engine)
        val renderer = rememberRenderer(engine)
        val scene = rememberScene(engine)
        val collisionSystem = rememberCollisionSystem(view)
        val childNodes = rememberNodes()
        val cameraNode = rememberARCameraNode(engine)
        
        // ✅ AR 狀態 - 基於官方示例
        var frame by remember { mutableStateOf<Frame?>(null) }
        var session by remember { mutableStateOf<Session?>(null) }
        
        // 初始化
        LaunchedEffect(Unit) {
            try {
                Log.d(TAG, "🔧 開始初始化...")
                WatsonAIEnhanced.initialize(context as MainActivity)
                Log.d(TAG, "✅ 初始化完成")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 初始化失敗: ${e.message}", e)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // 主視圖
            when (mode) {
                ViewMode.MODE_AR -> {
                    Log.d(TAG, "🎯 渲染 AR 視圖")
                    
                    // ✅ 基於官方示例的 ARScene 實現
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
                        
                        // ✅ 啟用平面渲染
                        planeRenderer = true,
                        
                        // ✅ AR 攝像頭流
                        cameraStream = rememberARCameraStream(materialLoader),
                        
                        // ✅ Session 配置 - 基於官方示例
                        sessionConfiguration = { arSession, config ->
                            Log.d(TAG, "🔧 配置 AR Session...")
                            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                            
                            if (arSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                                config.depthMode = Config.DepthMode.AUTOMATIC
                                Log.d(TAG, "✅ 啟用自動深度檢測")
                            } else {
                                config.depthMode = Config.DepthMode.DISABLED
                                Log.w(TAG, "⚠️ 設備不支援深度檢測")
                            }
                            
                            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                            config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                        },
                        
                        // ✅ Session 生命周期
                        onSessionCreated = { arSession ->
                            Log.d(TAG, "✅ AR Session 創建成功")
                            session = arSession
                            trackingStatus.value = "AR Session 已創建"
                            planeDetectionStatus.value = "請慢慢移動設備掃描環境..."
                        },
                        
                        onSessionResumed = { arSession ->
                            Log.d(TAG, "▶️ AR Session 已恢復")
                            trackingStatus.value = "AR 追蹤中..."
                        },
                        
                        onSessionPaused = { arSession ->
                            Log.d(TAG, "⏸️ AR Session 已暫停")
                        },
                        
                        onSessionFailed = { exception ->
                            Log.e(TAG, "❌ AR Session 失敗: ${exception.message}")
                        },
                        
                        // ✅ 關鍵的 Frame 更新處理
                        onSessionUpdated = { arSession, updatedFrame ->
                            frame = updatedFrame
                            session = arSession
                            
                            val camera = updatedFrame.camera
                            val isTracking = camera.trackingState == TrackingState.TRACKING
                            
                            if (isTracking) {
                                // ✅ 平面檢測邏輯
                                val allPlanes = arSession.getAllTrackables(Plane::class.java)
                                val trackingPlanes = allPlanes.filter { plane ->
                                    plane.trackingState == TrackingState.TRACKING 
                                }
                                
                                detectedPlanesCount.value = trackingPlanes.size
                                
                                // 🚀 強制允許放置 - 不管有沒有檢測到平面
                                canPlaceObjects.value = true
                                
                                when {
                                    trackingPlanes.isEmpty() -> {
                                        planeDetectionStatus.value = "未檢測到平面，但可以使用即時放置功能"
                                        trackingStatus.value = "可以放置（即時模式）"
                                    }
                                    trackingPlanes.size < 3 -> {
                                        planeDetectionStatus.value = "已檢測到 ${trackingPlanes.size} 個平面"
                                        trackingStatus.value = "可以放置"
                                    }
                                    else -> {
                                        planeDetectionStatus.value = "已檢測到 ${trackingPlanes.size} 個平面，效果最佳"
                                        trackingStatus.value = "追蹤正常"
                                    }
                                }
                                
                                if (trackingPlanes.isNotEmpty()) {
                                    Log.v(TAG, "🎯 檢測到平面類型: ${trackingPlanes.map { it.type }}")
                                }
                            } else {
                                trackingStatus.value = "追蹤丟失"
                                planeDetectionStatus.value = "追蹤丟失，請確保充足光線"
                                canPlaceObjects.value = false
                            }
                        },
                        
                        // ✅ 手勢處理 - 基於官方示例的正確方式
                        onTouchEvent = { motionEvent: MotionEvent, hitResult ->
                            coroutineScope.launch {
                                handleARTouch(motionEvent, frame, session, modelLoader, childNodes, engine)
                            }
                            true
                        }
                    )
                }
                
                ViewMode.MODE_3D -> {
                    // ✅ 3D 模式
                    Scene(
                        modifier = Modifier.fillMaxSize(),
                        engine = engine,
                        view = view,
                        renderer = renderer,
                        scene = scene,
                        modelLoader = modelLoader,
                        materialLoader = materialLoader,
                        environmentLoader = environmentLoader,
                        collisionSystem = collisionSystem,
                        
                        mainLightNode = rememberMainLightNode(engine) {
                            intensity = 100_000.0f
                        },
                        
                        cameraNode = rememberCameraNode(engine) {
                            position = Position(z = 4.0f)
                        },
                        
                        cameraManipulator = rememberCameraManipulator(),
                        childNodes = childNodes
                    )
                }
            }
            
            // ✅ 移除平面檢測狀態提示 - 直接允許使用
            // 添加使用說明
            if (mode == ViewMode.MODE_AR) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 100.dp, start = 16.dp, end = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "點擊屏幕任意位置放置貓咪 🐱",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // 浮動對話框
            if (isChatVisible && chatMessage.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(min = 80.dp, max = 300.dp)
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
                            lineHeight = 18.sp
                        )
                    }
                }
            }
            
            // 頂部控制面板
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Camera,
                                contentDescription = "模式",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (mode == ViewMode.MODE_AR) "AR 模式" else "3D 模式",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        FilledTonalButton(
                            onClick = {
                                currentMode.value = if (mode == ViewMode.MODE_AR) 
                                    ViewMode.MODE_3D else ViewMode.MODE_AR
                                recreate()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwitchCamera,
                                contentDescription = "切換",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "切換模式", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    
                    if (mode == ViewMode.MODE_AR) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Dashboard,
                                    contentDescription = "平面",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (canPlace) MaterialTheme.colorScheme.primary 
                                           else MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$planesCount 平面",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (canPlace) MaterialTheme.colorScheme.primary 
                                           else MaterialTheme.colorScheme.secondary
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Pets,
                                    contentDescription = "模型",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$modelsCount 貓咪",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            
                            if (modelsCount > 0) {
                                TextButton(
                                    onClick = {
                                        childNodes.clear()
                                        placedModelsCount.value = 0
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "清除",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(text = "清除", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            
                            // 添加測試按鈕
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        Log.d(TAG, "🎯 手動觸發放置")
                                        handleARTouch(null, frame, session, modelLoader, childNodes, engine)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pets,
                                    contentDescription = "測試放置",
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(text = "測試", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = trackingStatus.value,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                canPlace -> MaterialTheme.colorScheme.primary
                                trackingStatus.value.contains("追蹤丟失") -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // 底部輸入框
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
                                    Log.e(TAG, "❌ 處理失敗", e)
                                    chatMessage = "抱歉，出了點問題 😿"
                                    isChatVisible = true
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    placeholder = when {
                        mode == ViewMode.MODE_AR -> "點擊屏幕任意位置放置貓咪，或和貓咪聊天..."
                        else -> "和 3D 貓咪聊天..."
                    },
                    isLoading = isLoading
                )
            }
        }
    }
    
    /**
     * ✅ 修正的 AR 觸摸處理 - 傳入 Session 參數
     */
    private suspend fun handleARTouch(
        motionEvent: MotionEvent?,
        frame: Frame?,
        currentSession: Session?,
        modelLoader: io.github.sceneview.loaders.ModelLoader,
        childNodes: MutableList<io.github.sceneview.node.Node>,
        engine: com.google.android.filament.Engine
    ) {
        if (frame == null) {
            Log.w(TAG, "⚠️ Frame 為 null")
            return
        }
        
        if (currentSession == null) {
            Log.w(TAG, "⚠️ Session 為 null")
            return
        }
        
        try {
            Log.d(TAG, "🎯 開始處理觸摸事件")
            
            // 獲取觸摸位置（如果沒有觸摸事件，使用屏幕中心）
            val touchX = motionEvent?.x ?: 540f // 屏幕中心 X
            val touchY = motionEvent?.y ?: 1200f // 屏幕中心 Y
            
            Log.d(TAG, "📍 觸摸位置: ($touchX, $touchY)")
            
            var modelPlaced = false
            
            // 方法 1: 嘗試標準平面檢測
            try {
                val hitResults = frame.hitTest(touchX, touchY)
                Log.d(TAG, "🔍 HitTest 結果數量: ${hitResults.size}")
                
                for ((index, hitResult) in hitResults.withIndex()) {
                    Log.d(TAG, "🎯 檢查 HitResult [$index]")
                    
                    try {
                        val anchor = hitResult.createAnchor()
                        if (createModelAtAnchor(anchor, modelLoader, childNodes, engine)) {
                            Log.d(TAG, "✅ 方法1成功：標準平面檢測")
                            modelPlaced = true
                            break
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "⚠️ HitResult [$index] 失敗: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 標準 hitTest 失敗: ${e.message}")
            }
            
            // 方法 2: 如果標準方法失敗，嘗試即時放置
            if (!modelPlaced) {
                try {
                    Log.d(TAG, "🚀 嘗試即時放置")
                    val instantResults = frame.hitTestInstantPlacement(touchX, touchY, 1.0f)
                    Log.d(TAG, "🔍 即時放置結果數量: ${instantResults.size}")
                    
                    if (instantResults.isNotEmpty()) {
                        val anchor = instantResults.first().createAnchor()
                        if (createModelAtAnchor(anchor, modelLoader, childNodes, engine)) {
                            Log.d(TAG, "✅ 方法2成功：即時放置")
                            modelPlaced = true
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ 即時放置失敗: ${e.message}")
                }
            }
            
            // 方法 3: 如果還是失敗，直接在相機前方創建錨點
            if (!modelPlaced) {
                try {
                    Log.d(TAG, "🎯 嘗試直接在相機前方放置")
                    
                    val camera = frame.camera
                    val cameraPosition = camera.pose
                    
                    // 在相機前方 1 米的位置創建一個 pose
                    val translation = floatArrayOf(0f, 0f, -1f) // 相機前方1米
                    val rotation = floatArrayOf(0f, 0f, 0f, 1f) // 無旋轉
                    
                    // 轉換到世界坐標
                    val forwardPose = cameraPosition.compose(Pose(translation, rotation))
                    
                    // 使用傳入的 session 創建錨點
                    val anchor = currentSession.createAnchor(forwardPose)
                    if (createModelAtAnchor(anchor, modelLoader, childNodes, engine)) {
                        Log.d(TAG, "✅ 方法3成功：直接前方放置")
                        modelPlaced = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ 直接前方放置失敗: ${e.message}")
                }
            }
            
            // 方法 4: 最後的備用方案 - 在原點創建錨點
            if (!modelPlaced) {
                try {
                    Log.d(TAG, "🎯 最後嘗試：在原點放置")
                    
                    val identityPose = Pose(floatArrayOf(0f, 0f, -1f), floatArrayOf(0f, 0f, 0f, 1f))
                    // 使用傳入的 session 創建錨點
                    val anchor = currentSession.createAnchor(identityPose)
                    if (createModelAtAnchor(anchor, modelLoader, childNodes, engine)) {
                        Log.d(TAG, "✅ 方法4成功：原點放置")
                        modelPlaced = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 原點放置也失敗: ${e.message}")
                }
            }
            
            if (!modelPlaced) {
                Log.e(TAG, "❌ 所有放置方法都失敗了")
                planeDetectionStatus.value = "無法放置模型，請檢查 GLB 文件"
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 觸摸處理總體異常: ${e.message}", e)
            planeDetectionStatus.value = "放置失敗，請重試"
        }
    }
    
    /**
     * 通用的模型創建方法 - 接受任何類型的錨點
     */
    private fun createModelAtAnchor(
        anchor: Anchor,
        modelLoader: io.github.sceneview.loaders.ModelLoader,
        childNodes: MutableList<io.github.sceneview.node.Node>,
        engine: com.google.android.filament.Engine
    ): Boolean {
        return try {
            Log.d(TAG, "🔨 嘗試創建模型，錨點: $anchor")
            
            val modelInstance = modelLoader.createModelInstance(GLB_MODEL_PATH)
            if (modelInstance != null) {
                Log.d(TAG, "✅ 模型實例創建成功")
                
                val anchorNode = AnchorNode(
                    engine = engine,
                    anchor = anchor
                )
                
                val modelNode = ModelNode(
                    modelInstance = modelInstance,
                    scaleToUnits = 0.5f
                )
                
                anchorNode.addChildNode(modelNode)
                childNodes.add(anchorNode)
                
                placedModelsCount.value++
                Log.d(TAG, "🎉 貓咪 #${placedModelsCount.value} 放置成功！")
                planeDetectionStatus.value = "貓咪 #${placedModelsCount.value} 放置成功！"
                true
            } else {
                Log.e(TAG, "❌ 模型實例創建失敗 - 檢查 GLB 文件路徑: $GLB_MODEL_PATH")
                planeDetectionStatus.value = "模型文件載入失敗，請檢查 assets 資料夾"
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 模型創建異常: ${e.message}", e)
            false
        }
    }

    private suspend fun processUserMessage(message: String): String {
        return try {
            when (message.lowercase()) {
                "help", "幫助" -> {
                    "🐱 AR 模式：等待平面檢測完成後點擊屏幕放置貓咪\n3D 模式：開發中\n切換模式：點擊頂部按鈕"
                }
                "clear", "清除" -> {
                    placedModelsCount.value = 0
                    "所有貓咪已清除！"
                }
                "debug", "調試" -> {
                    "調試信息：\n平面檢測：${detectedPlanesCount.value} 個\n放置模型：${placedModelsCount.value} 個\n追蹤狀態：${trackingStatus.value}\n可放置：${canPlaceObjects.value}"
                }
                else -> {
                    val result = WatsonAIEnhanced.getEnhancedAIResponse(message)
                    if (result.success && result.response.isNotEmpty()) {
                        result.response
                    } else {
                        "喵～ 我現在聽不懂呢 😿"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ AI 處理異常", e)
            "喵嗚... 連接出問題了 😿"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🗑️ 應用已結束")
    }
}