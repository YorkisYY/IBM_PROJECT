// ar/PlacementModeUI.kt
package ar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sceneview.node.Node

/**
 * Placement mode toggle button component
 */
@Composable
fun PlacementModeToggleButton(
    placementModeManager: PlacementModeManager,
    childNodes: MutableList<Node>,
    arRenderer: ARSceneViewRenderer,
    modelCount: Int,
    onModelsCleared: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentMode by placementModeManager.currentMode
    
    Card(
        modifier = modifier
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
            // Title
            Text(
                text = "Mode",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            // Toggle button
            Button(
                onClick = {
                    placementModeManager.switchToNextMode(
                        childNodes = childNodes,
                        arRenderer = arRenderer,
                        onModelsCleared = onModelsCleared
                    )
                },
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(currentMode.color)
                ),
                contentPadding = PaddingValues(4.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = currentMode.icon,
                        fontSize = 20.sp
                    )
                    Text(
                        text = currentMode.displayName,
                        fontSize = 10.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
            
            // Model count
            Text(
                text = "$modelCount cats",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Clear control buttons component
 */
@Composable
fun ClearControlButtons(
    placementModeManager: PlacementModeManager,
    childNodes: MutableList<Node>,
    arRenderer: ARSceneViewRenderer,
    modelCount: Int,
    onModelsCleared: () -> Unit = {},
    onPlaneDataCleared: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(120.dp)
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
            // Title
            Text(
                text = "Clear",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            // Clear models button
            if (modelCount > 0) {
                Button(
                    onClick = {
                        placementModeManager.clearAllModels(childNodes, arRenderer)
                        onModelsCleared()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Models",
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Models",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Clear plane data button
            Button(
                onClick = {
                    placementModeManager.clearPlaneData {
                        onPlaneDataCleared()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                contentPadding = PaddingValues(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Clear Plane Data",
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Planes",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Status text
            Text(
                text = if (modelCount > 0) "$modelCount cats" else "No models",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Placement mode status bar
 * Used to display current mode information in control panel
 */
@Composable
fun PlacementModeStatusBar(
    placementModeManager: PlacementModeManager,
    trackingStatus: String,
    planeStatus: String,
    canPlace: Boolean,
    modifier: Modifier = Modifier
) {
    val currentMode by placementModeManager.currentMode
    
    Column(modifier = modifier) {
        // Header - display current mode
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AR Mode: ${currentMode.icon} ${currentMode.displayName}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = Color(currentMode.color)
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
        
        // Status - display mode description
        Text(
            text = currentMode.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 2.dp)
        )
        
        Text(
            text = planeStatus,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Complete mode control panel - includes mode switching and clear controls
 */
@Composable
fun PlacementModeControlPanel(
    placementModeManager: PlacementModeManager,
    childNodes: MutableList<Node>,
    arRenderer: ARSceneViewRenderer,
    modelCount: Int,
    onModelsCleared: () -> Unit = {},
    onPlaneDataCleared: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Mode toggle button
        PlacementModeToggleButton(
            placementModeManager = placementModeManager,
            childNodes = childNodes,
            arRenderer = arRenderer,
            modelCount = modelCount,
            onModelsCleared = onModelsCleared
        )
        
        // Clear control buttons
        ClearControlButtons(
            placementModeManager = placementModeManager,
            childNodes = childNodes,
            arRenderer = arRenderer,
            modelCount = modelCount,
            onModelsCleared = onModelsCleared,
            onPlaneDataCleared = onPlaneDataCleared
        )
    }
}

/**
 * Placement mode selection dialog
 * Optional advanced UI component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacementModeSelectionDialog(
    placementModeManager: PlacementModeManager,
    childNodes: MutableList<Node>,
    arRenderer: ARSceneViewRenderer,
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onModelsCleared: () -> Unit = {}
) {
    val currentMode by placementModeManager.currentMode
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { 
                Text(
                    text = "Select Placement Mode",
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Choose how to place AR objects:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    // Mode options
                    PlacementMode.values().forEach { mode ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                placementModeManager.setMode(
                                    mode = mode,
                                    childNodes = childNodes,
                                    arRenderer = arRenderer,
                                    clearModels = true,
                                    onModelsCleared = onModelsCleared
                                )
                                onDismiss()
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (mode == currentMode) {
                                    Color(mode.color).copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = if (mode == currentMode) {
                                androidx.compose.foundation.BorderStroke(
                                    2.dp, 
                                    Color(mode.color)
                                )
                            } else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = mode.icon,
                                    fontSize = 24.sp
                                )
                                
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(mode.color)
                                    )
                                    Text(
                                        text = mode.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                
                                if (mode == currentMode) {
                                    Text(
                                        text = "✓",
                                        color = Color(mode.color),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    
                    // Update warning information
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Mode switching behavior:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Models will be cleared\n• Plane data will be preserved\n• Use 'Clear Planes' button to reset plane data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        )
    }
}