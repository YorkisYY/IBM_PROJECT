package com.example.ibm_project

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import ui.components.UserInputField
import ui.components.ChatBubble
import com.example.ibm_project.ui.theme.IBM_PROJECTTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ibm_project.FilamentViewer
import com.google.ar.core.ArCoreApk
import android.util.Log
import watsonx.WatsonAIEnhanced  // 🔄 改為使用 Enhanced
import utils.rememberTypewriterEffect
import functions.WeatherFunctions 
class MainActivity : ComponentActivity() {

    private var filamentViewer: FilamentViewer? = null

    // 🆕 權限請求啟動器
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        when {
            fineLocationGranted || coarseLocationGranted -> {
                Log.d("MainActivity", "✅ 位置權限已授予")
                // 權限已授予，可以使用位置功能
            }
            else -> {
                Log.w("MainActivity", "⚠️ 位置權限被拒絕，將使用IP定位")
                // 權限被拒絕，將使用IP定位作為備用方案
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.e("TEST_LOG", "=== MainActivity onCreate 開始 ===")
        super.onCreate(savedInstanceState)
        Log.e("TEST_LOG", "=== super.onCreate 完成 ===")

        // 🆕 請求位置權限（如果需要GPS定位）
        requestLocationPermissionIfNeeded()

        // 隱藏標題列
        try {
            actionBar?.hide()
            Log.e("TEST_LOG", "=== actionBar.hide 完成 ===")
        } catch (e: Exception) {
            Log.e("TEST_LOG", "=== actionBar.hide 錯誤: ${e.message} ===")
        }

        Log.e("TEST_LOG", "=== 準備調用 setContent ===")
        setContent {
            Log.e("TEST_LOG", "=== setContent Composable 被調用 ===")
            IBM_PROJECTTheme {
                Log.e("TEST_LOG", "=== Theme 包裝完成 ===")
                GLBChatInterface()
            }
        }
        Log.e("TEST_LOG", "=== onCreate 結束 ===")
    }

    /**
     * 🆕 請求位置權限（如果需要GPS定位）
     */
    private fun requestLocationPermissionIfNeeded() {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (fineLocationPermission != PackageManager.PERMISSION_GRANTED && 
            coarseLocationPermission != PackageManager.PERMISSION_GRANTED) {
            
            Log.d("MainActivity", "🔧 請求位置權限...")
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            Log.d("MainActivity", "✅ 位置權限已存在")
        }
    }

    @Composable
    private fun GLBChatInterface() {
        Log.e("TEST_LOG", "=== GLBChatInterface 開始 ===")
        Log.d("MainActivity", "🔧 GLBChatInterface() 開始")
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val coroutineScope = rememberCoroutineScope()
        var inputText by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        
        // 🆕 使用打字機效果管理器
        val typewriter = rememberTypewriterEffect()
        var isChatVisible by remember { mutableStateOf(false) }
        
        // GLB 模型位置狀態
        var modelTopPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }
        var dynamicBubbleWidth by remember { mutableStateOf(500f) }
        
        // ARCore 和 Filament 狀態
        var arCoreStatus by remember { mutableStateOf("檢查中...") }
        var filamentReady by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        // Watson AI 狀態
        var aiConnectionStatus by remember { mutableStateOf("初始化中...") }

        // 初始化 Filament Viewer 並檢查 ARCore
        LaunchedEffect(Unit) {
            Log.d("MainActivity", "🔧 LaunchedEffect 開始執行")
            try {
                // 檢查 ARCore 可用性
                val availability = ArCoreApk.getInstance().checkAvailability(context)
                
                when {
                    availability.isTransient -> {
                        arCoreStatus = "ARCore 安裝中，暫時使用 3D 模式"
                    }
                    availability.isSupported -> {
                        arCoreStatus = "ARCore 支持，目前使用 3D 模式"
                        println("ARCore 支持，使用 3D 模式（AR 功能待實現）")
                    }
                    else -> {
                        arCoreStatus = "設備不支持 ARCore，使用 3D 模式"
                        println("ARCore 不支持，使用 FilamentViewer 模式")
                    }
                }
                
                // 延遲一點確保 UI 準備好
                delay(100)
                
                // 初始化 FilamentViewer
                Log.d("MainActivity", "🔧 準備創建 FilamentViewer...")
                filamentViewer = FilamentViewer(context).apply {
                    // 設置位置更新回調
                    onModelPositionUpdated = { position ->
                        Log.d("MainActivity", "📍 收到位置更新: $position")
                        modelTopPosition = position
                        
                        // 🔧 當有新對話時，計算動態寬度
                        if (isChatVisible && typewriter.state.value.fullText.isNotEmpty()) {
                            val positionWithWidth = this.getModelTopScreenPosition(typewriter.state.value.fullText)
                            positionWithWidth?.let {
                                modelTopPosition = Pair(it.first, it.second)
                                dynamicBubbleWidth = it.third
                                Log.d("MainActivity", "📏 動態寬度更新: ${it.third}")
                            }
                        }
                    }
                }
                Log.d("MainActivity", "✅ FilamentViewer 創建成功")
                filamentReady = true
                
                // 加載 GLB 模型
                delay(100)
                Log.d("MainActivity", "🔧 開始載入 GLB 模型...")
                filamentViewer?.loadGLBModel("cute_spooky_cat.glb")
                Log.d("MainActivity", "✅ GLB 載入指令已發送")
                
                // 啟動渲染循環
                delay(50)
                filamentViewer?.onResume()
                Log.d("MainActivity", "✅ 渲染循環已啟動")
                
                // 測試 Watson AI Enhanced 連接
                delay(2000)
                testWatsonAIConnection { status ->
                    aiConnectionStatus = status
                }
                
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ 初始化過程中發生錯誤: ${e.message}")
                errorMessage = "初始化失敗：${e.message}"
                arCoreStatus = "初始化失敗，使用備用模式"
                aiConnectionStatus = "AI 連接失敗"
                println("FilamentViewer 初始化異常：${e.message}")
                e.printStackTrace()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // 3D GLB 視圖（全螢幕背景）
            if (filamentReady && filamentViewer != null && errorMessage == null) {
                AndroidView(
                    factory = { 
                        filamentViewer!!.getSurfaceView()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (errorMessage != null) {
                // 錯誤提示
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Text(
                            text = "3D 模型加載失敗",
                            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
                        )
                        androidx.compose.material3.Text(
                            text = errorMessage!!,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                        )
                        androidx.compose.material3.Text(
                            text = "AI 狀態: $aiConnectionStatus",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                // 載入中提示
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.CircularProgressIndicator()
                        androidx.compose.material3.Text(
                            text = arCoreStatus,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        androidx.compose.material3.Text(
                            text = "AI 狀態: $aiConnectionStatus",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // 🎯 智能對話框位置 - 使用打字機效果
            if (isChatVisible) {
                val bubbleWidth = with(density) { 
                    if (modelTopPosition != null && filamentReady) {
                        dynamicBubbleWidth.toDp()
                    } else {
                        configuration.screenWidthDp.dp * 0.7f
                    }
                }
                
                val yPosition = if (modelTopPosition != null && filamentReady) {
                    with(density) { 
                        try {
                            modelTopPosition!!.second.toDp()
                        } catch (e: Exception) {
                            120.dp
                        }
                    }
                } else {
                    120.dp
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = yPosition),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier.width(bubbleWidth)
                    ) {
                        ChatBubble(
                            message = typewriter.state.value.displayedText, // 🎯 使用打字機效果文字
                            isVisible = isChatVisible,
                            position = Pair(0f, 0f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // 底部用戶輸入框
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                UserInputField(
                    value = inputText,
                    onValueChange = { newValue -> inputText = newValue },
                    onSendClick = {
                        if (inputText.trim().isNotEmpty() && !isLoading) {
                            val userMessage = inputText.trim()
                            inputText = ""
                            isLoading = true
                            
                            // 🎯 立即清除舊對話
                            isChatVisible = false
                            typewriter.reset() // 🆕 重置打字機效果

                            coroutineScope.launch {
                                try {
                                    val reply = processUserMessage(userMessage)
                                    
                                    // 🔧 計算新對話的動態寬度和位置
                                    val positionWithWidth = filamentViewer?.getModelTopScreenPosition(reply)
                                    if (positionWithWidth != null) {
                                        modelTopPosition = Pair(positionWithWidth.first, positionWithWidth.second)
                                        dynamicBubbleWidth = positionWithWidth.third
                                        Log.d("MainActivity", "📏 新對話動態寬度: ${positionWithWidth.third}")
                                    }
                                    
                                    // 🎯 顯示新對話並啟動打字機效果
                                    isChatVisible = true
                                    typewriter.startTyping(reply, 200L, coroutineScope) // 🆕 啟動打字機效果
                                    
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "❌ 处理用户消息失败", e)
                                    isChatVisible = true
                                    typewriter.startTyping("抱歉，我現在無法回答 😿", 200L, coroutineScope) // 🆕 錯誤訊息也有打字機效果
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    placeholder = "和你的 3D 貓咪聊天...",
                    isLoading = isLoading
                )
            }
        }
    }

    /**
     * 🔄 使用 Watson AI Enhanced 處理用戶消息 - 支持 Function Calling
     */
    private suspend fun processUserMessage(message: String): String {
        return try {
            Log.d("MainActivity", "🤖 調用 Watson AI Enhanced: $message")
            
            val result = WatsonAIEnhanced.getEnhancedAIResponse(message)
            
            if (result.success && result.response.isNotEmpty()) {
                Log.d("MainActivity", "✅ Enhanced AI 回复成功")
                result.response
            } else {
                Log.e("MainActivity", "❌ Enhanced AI 回复失败: ${result.error}")
                "AI connection failed 😿"
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Enhanced AI 调用异常", e)
            "AI connection failed 😿"
        }
    }

    /**
     * 🔄 測試 Watson AI Enhanced 連接 - 支持 Function Calling
     */
    private suspend fun testWatsonAIConnection(onStatusUpdate: (String) -> Unit) {
        try {
            Log.d("MainActivity", "🔧 測試 Watson AI Enhanced 連接...")
            onStatusUpdate("初始化 Enhanced AI...")
            
            // 🆕 初始化 Enhanced 服務
            WatsonAIEnhanced.initialize(this@MainActivity)
            
            onStatusUpdate("測試 Enhanced AI 連接中...")
            val result = WatsonAIEnhanced.testEnhancedService()
            
            if (result.success) {
                Log.d("MainActivity", "✅ Watson AI Enhanced 連接成功")
                onStatusUpdate("Enhanced AI 已連接 (支持智能功能)")
            } else {
                Log.e("MainActivity", "❌ Watson AI Enhanced 連接失敗: ${result.error}")
                onStatusUpdate("Enhanced AI 連接失敗")
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Watson AI Enhanced 測試異常", e)
            onStatusUpdate("Enhanced AI 連接失敗")
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "🔧 Activity onResume 被調用")
        filamentViewer?.onResume()
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "🔧 Activity onPause 被調用")
        filamentViewer?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "🔧 Activity onDestroy 被調用")
        filamentViewer?.onDestroy()
        filamentViewer = null
    }
}