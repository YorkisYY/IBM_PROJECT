package com.example.ibm_project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.ARTouchHandler

@Composable
fun SettingsDialog(
    showSettings: Boolean,
    touchHandler: ARTouchHandler,
    onDismiss: () -> Unit
) {
    if (showSettings) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Rotation Settings") },
            text = {
                Column {
                    Text("Adjust rotation sensitivity:")
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Y-axis sensitivity slider
                    Text("Horizontal: ${String.format("%.2f", touchHandler.rotationSensitivityY)}")
                    Slider(
                        value = touchHandler.rotationSensitivityY,
                        onValueChange = { touchHandler.rotationSensitivityY = it },
                        valueRange = 0.1f..2.0f,
                        steps = 18
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // X-axis sensitivity slider  
                    Text("Vertical: ${String.format("%.2f", touchHandler.rotationSensitivityX)}")
                    Slider(
                        value = touchHandler.rotationSensitivityX,
                        onValueChange = { touchHandler.rotationSensitivityX = it },
                        valueRange = 0.1f..2.0f,
                        steps = 18
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
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
}

@Composable
fun LogoutDialog(
    showLogoutDialog: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}