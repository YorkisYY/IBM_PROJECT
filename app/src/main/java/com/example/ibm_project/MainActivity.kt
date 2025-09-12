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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ibm_project.ui.theme.IBM_PROJECTTheme
import com.example.ibm_project.auth.AuthRepository
import com.example.ibm_project.auth.AuthState
import com.example.ibm_project.auth.LoginScreen
import com.example.ibm_project.ui.ARControlButtons
import com.example.ibm_project.ui.SettingsDialog
import com.example.ibm_project.ui.LogoutDialog
import com.google.ar.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.components.UserInputField
import watsonx.WatsonAIEnhanced
import ar.ARSceneViewRenderer
import ar.ARTouchHandler
import ar.ARDialogTracker
import ar.PlacementModeManager
import kotlin.math.roundToInt

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
import com.example.ibm_project.chat.ChatRepository

/**
 * AR Cat Interaction App - with Firebase Auth
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }
    
    // AR components
    private lateinit var authRepository: AuthRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var arRenderer: ARSceneViewRenderer
    private lateinit var touchHandler: ARTouchHandler
    private lateinit var dialogTracker: ARDialogTracker
    private lateinit var placementModeManager: PlacementModeManager
    
    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResults(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        
        // Initialize repositories
        authRepository = AuthRepository(this)
        chatRepository = ChatRepository()
        
        // Initialize AR components
        arRenderer = ARSceneViewRenderer()
        touchHandler = ARTouchHandler()
        dialogTracker = ARDialogTracker()
        placementModeManager = PlacementModeManager(this)
        
        // Integrate components
        placementModeManager.setARTouchHandler(touchHandler)
        
        // Setup Compose UI
        setContent {
            IBM_PROJECTTheme {
                // Check auth state and show appropriate screen
                val authState by authRepository.authState.collectAsState()
                
                when (authState) {
                    is AuthState.Authenticated -> {
                        // User is logged in, show AR interface
                        LaunchedEffect(Unit) {
                            requestNecessaryPermissions()
                        }
                        ARInterface()
                    }
                    else -> {
                        // User not logged in, show login screen
                        LoginScreen(
                            authRepository = authRepository,
                            onLoginSuccess = {
                                // Will automatically switch to AR when auth state changes
                            }
                        )
                    }
                }
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
        var showLogoutDialog by remember { mutableStateOf(false) }
        
        // Dialog binding to first cat position
        var firstCatDialogPosition by remember { mutableStateOf(Offset.Zero) }
        var hasFirstCat by remember { mutableStateOf(false) }
        
        // AR state from renderer
        val planesCount = arRenderer.detectedPlanesCount.value
        val modelsCount = arRenderer.placedModelsCount.value
        val canPlace = arRenderer.canPlaceObjects.value
        val trackingStatus = arRenderer.trackingStatus.value
        val planeStatus = arRenderer.planeDetectionStatus.value
        
        // Get current mode
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
                
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null && !currentUser.isAnonymous) {
                    WatsonAIEnhanced.initialize(context as MainActivity, chatRepository)
                    Log.d(TAG, "Initialized with ChatRepository for authenticated user")
                } else {
                    WatsonAIEnhanced.initialize(context as MainActivity, null)
                    Log.d(TAG, "Initialized with ContextManager for anonymous user")
                }
                
                Log.d(TAG, "Initialization completed")
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed: ${e.message}", e)
            }
        }
        
        // Listen for authentication changes
        LaunchedEffect(authRepository.authState.collectAsState().value) {
            chatRepository.onAuthenticationChanged()
        }
        
        // Load chat history
        LaunchedEffect(Unit) {
            try {
                chatRepository.loadChatHistory()
                Log.d(TAG, "Chat history loaded")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load chat history: ${e.message}")
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
                        } else if (!hasFirstCat) {
                            firstCatDialogPosition = newPosition
                            hasFirstCat = true
                        }
                    } else {
                        hasFirstCat = false
                    }
                } catch (e: Exception) {
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
                
                onTouchEvent = { motionEvent: MotionEvent, hitResult: HitResult? ->
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN -> {
                            focusManager.clearFocus()
                            
                            placementModeManager.setSession(session)
                            
                            touchHandler.handleSceneViewTouchDown(
                                motionEvent = motionEvent,
                                hitResult = hitResult,
                                frame = frame,
                                session = session,
                                modelLoader = modelLoader,
                                childNodes = childNodes,
                                engine = engine,
                                arRenderer = arRenderer,
                                collisionSystem = collisionSystem,
                                cameraNode = cameraNode,
                                onFirstCatCreated = { newFirstCat: ModelNode? ->
                                    Log.d(TAG, "First cat created: ${newFirstCat?.name ?: "unknown"}")
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
                // Center dialog when no first cat
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

            // Right control area - using extracted component
            ARControlButtons(
                currentMode = currentMode,
                modelsCount = modelsCount,
                onModeToggle = {
                    placementModeManager.switchToNextMode(
                        childNodes = childNodes,
                        arRenderer = arRenderer,
                        onModelsCleared = {
                            touchHandler.clearAllCats(childNodes, arRenderer)
                            hasFirstCat = false
                            firstCatDialogPosition = Offset.Zero
                            isChatVisible = false
                            chatMessage = ""
                        }
                    )
                },
                onClearPlanes = {
                    placementModeManager.clearPlaneData {
                        Log.d(TAG, "Plane data cleared")
                    }
                },
                onSettings = { showSettings = true },
                onClearModels = {
                    placementModeManager.clearAllModels(childNodes, arRenderer)
                    hasFirstCat = false
                    firstCatDialogPosition = Offset.Zero
                    isChatVisible = false
                    chatMessage = ""
                },
                onLogout = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 16.dp, top = 120.dp)
            )

            // Settings dialog - using extracted component
            SettingsDialog(
                showSettings = showSettings,
                touchHandler = touchHandler,
                onDismiss = { showSettings = false }
            )

            // Logout dialog - using extracted component
            LogoutDialog(
                showLogoutDialog = showLogoutDialog,
                onConfirm = {
                    authRepository.signOut()
                    showLogoutDialog = false
                },
                onDismiss = { showLogoutDialog = false }
            )

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
                        touchHandler.getSelectedNode() != null -> "Rotate with drag..."
                        modelsCount > 0 -> "Start your chat !"
                        else -> "Tap to place models"
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
                    "Use 'clear' to remove all cats and reset\n" +
                    "Type 'history' to clear chat history\n" +
                    "Type 'stats' to see chat statistics"
                }
                "clear" -> {
                    "All cats cleared! Place a new first cat for dialog binding! Current mode: ${placementModeManager.currentMode.value.displayName}"
                }
                "history" -> {
                    chatRepository.clearChatHistory()
                    val storageType = if (authRepository.getCurrentUser()?.isAnonymous == false) "Firebase" else "local memory"
                    "Chat history cleared from $storageType! Starting fresh conversation."
                }
                "stats" -> {
                    val stats = chatRepository.getChatStats()
                    val storageLocation = if (authRepository.getCurrentUser()?.isAnonymous == false) {
                        "stored in Firebase (visible to admin)"
                    } else {
                        "stored locally (private, temporary)"
                    }
                    
                    "Chat Stats:\n" +
                    "• Messages: ${stats.totalMessages}\n" +
                    "• Storage: ${stats.storageType}\n" +
                    "• Location: $storageLocation\n" +
                    if (stats.firstMessageTime > 0) {
                        "• First message: ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(stats.firstMessageTime))}"
                    } else {
                        "• This is your first message!"
                    }
                }
                else -> {
                    // Get conversation context for AI
                    val conversationContext = chatRepository.getConversationContext()
                    
                    // Prepare enhanced prompt with context
                    val enhancedMessage = if (conversationContext.isNotEmpty()) {
                        "Previous conversation:\n$conversationContext\n\nCurrent message: $message"
                    } else {
                        message
                    }
                    
                    val result = WatsonAIEnhanced.getEnhancedAIResponse(enhancedMessage)
                    val aiResponse = if (result.success && result.response.isNotEmpty()) {
                        result.response
                    } else {
                        "There is problem of dialog creation, please reopen the app and start the conversation sometimes it takes several times to reactive watsonx.ai, or you can email to my creator York! (yyisyork@gmail.com)"
                    }
                    
                    // Save conversation (Firebase for Google users, local memory for anonymous)
                    try {
                        chatRepository.saveChatMessage(message, aiResponse)
                        val storageType = if (authRepository.getCurrentUser()?.isAnonymous == false) "Firebase" else "local"
                        Log.d(TAG, "Chat message saved to $storageType storage")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save chat message: ${e.message}")
                        // Don't affect user experience if saving fails
                    }
                    
                    aiResponse
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