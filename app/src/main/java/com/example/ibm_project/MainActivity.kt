package com.example.ibm_project

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

// ARScene imports
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.ar.rememberARCameraStream

/**
 * AR Cat Interaction App - Watson dialog bound to first cat
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }
    
    // AR components
    private lateinit var arRenderer: ARSceneViewRenderer
    private lateinit var touchHandler: ARTouchHandler
    private lateinit var dialogTracker: ARDialogTracker
    
    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResults(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        
        // Initialize AR Renderer
        arRenderer = ARSceneViewRenderer()
        touchHandler = ARTouchHandler()
        dialogTracker = ARDialogTracker()
        
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
        
        // Dialog position bound to first cat
        var firstCatDialogPosition by remember { mutableStateOf(Offset.Zero) }
        var hasFirstCat by remember { mutableStateOf(false) }
        
        // Get first cat screen coordinates - optimized version
        LaunchedEffect(touchHandler.getFirstCatModel(), frame) {
            touchHandler.getFirstCatModel()?.let { cat ->
                try {
                    // Use cat's base world position without extra height
                    val catWorldPosition = Position(
                        x = cat.worldPosition.x,
                        y = cat.worldPosition.y + 0.1f, // Only add 20cm height
                        z = cat.worldPosition.z
                    )
                    
                    // Convert to screen coordinates
                    val screenPos = view.worldToScreen(catWorldPosition)
                    
                    // Only update when Y coordinate is positive and reasonable
                    if (screenPos.y > 0f && screenPos.y < 2000f) {
                        val newPosition = Offset(screenPos.x, screenPos.y)
                        
                        // Only update when position changes significantly (reduce unnecessary recomposition)
                        val distance = kotlin.math.sqrt(
                            (newPosition.x - firstCatDialogPosition.x).let { it * it } +
                            (newPosition.y - firstCatDialogPosition.y).let { it * it }
                        )
                        
                        if (distance > 10f) { // Only update when movement exceeds 10 pixels
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
                            touchHandler.handleImprovedTouchDown(
                                motionEvent = motionEvent,
                                hitResult = hitResult,
                                frame = frame,
                                session = session,
                                modelLoader = modelLoader,
                                childNodes = childNodes,
                                engine = engine,
                                arRenderer = arRenderer,
                                onFirstCatCreated = { newFirstCat ->
                                    Log.d(TAG, "First cat created for dialog binding: ${newFirstCat?.name}")
                                },
                                sceneView = null,        // 邊緣檢測參數
                                filamentView = view      // 邊緣檢測參數
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
            // Watson Dialog - bound to first cat, wider dialog
            dialogTracker.WatsonDialogBoundToFirstCat(
                isChatVisible = isChatVisible,
                chatMessage = chatMessage,
                firstCatModel = touchHandler.getFirstCatModel(),
                firstCatDialogPosition = firstCatDialogPosition,
                hasFirstCat = hasFirstCat
            )
            
            // Watson Dialog - center when no first cat - also supports scrolling and wider design
            dialogTracker.WatsonDialogCenter(
                isChatVisible = isChatVisible,
                chatMessage = chatMessage,
                hasFirstCat = hasFirstCat
            )
        
            // Control panel
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
                                text = "$modelsCount Cats" + if (touchHandler.getFirstCatModel() != null) " (Wide Dialog)" else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        // Clear button
                        if (modelsCount > 0) {
                            TextButton(
                                onClick = {
                                    touchHandler.clearAllCats(childNodes, arRenderer)
                                    // Clear dialog
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
                                text = "Watson wider dialog with smart positioning and reduced updates",
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
                                    // Clear previous dialog
                                    isChatVisible = false
                                    
                                    val reply = processUserMessage(userMessage)
                                    chatMessage = reply
                                    isChatVisible = true
                                    
                                    // Remove auto-disappear - wait for next input to disappear
                                    // delay(5000)
                                    // isChatVisible = false
                                } catch (e: Exception) {
                                    Log.e(TAG, "Processing failed", e)
                                    chatMessage = "Sorry, something went wrong"
                                    isChatVisible = true
                                    // Error message disappears after 3 seconds
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
                        modelsCount > 0 -> "Chat with spooky cat !"
                        else -> "Tap anywhere to place cats..."
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
                    "Watson wide dialog follows first cat with smart positioning\n" +
                    "Use 'clear' to remove all cats and reset"
                }
                "clear" -> {
                    "All cats cleared! Place a new first cat for wide dialog binding!"
                }
                else -> {
                    val result = WatsonAIEnhanced.getEnhancedAIResponse(message)
                    if (result.success && result.response.isNotEmpty()) {
                        result.response
                    } else {
                        "Meow~ Watson wide dialog is bound to the first cat!"
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