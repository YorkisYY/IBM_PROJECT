package com.example.ibm_project.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.PlacementMode

@Composable
fun ARControlButtons(
    currentMode: PlacementMode,
    modelsCount: Int,
    onModeToggle: () -> Unit,
    onClearPlanes: () -> Unit,
    onSettings: () -> Unit,
    onClearModels: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        // Mode toggle button
        ModeToggleButton(
            currentMode = currentMode,
            onClick = onModeToggle
        )
        
        // Clear plane data button
        ControlButton(
            text = "Clear",
            subText = "Planes",
            color = MaterialTheme.colorScheme.secondary,
            onClick = onClearPlanes
        )
        
        // Settings button
        SettingsButton(onClick = onSettings)
        
        // Clear Models button (only show when models exist)
        if (modelsCount > 0) {
            ControlButton(
                text = "Clear",
                subText = "Models",
                color = MaterialTheme.colorScheme.error,
                onClick = onClearModels
            )
        }
        
        // Logout button
        LogoutButton(onClick = onLogout)
    }
}

@Composable
private fun ModeToggleButton(
    currentMode: PlacementMode,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .width(80.dp)
            .height(80.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = Color(currentMode.color)
        ),
        border = BorderStroke(2.dp, Color(currentMode.color)),
        contentPadding = PaddingValues(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = currentMode.icon,
                fontSize = 20.sp,
                color = Color(currentMode.color)
            )
            Text(
                text = currentMode.displayName,
                fontSize = 10.sp,
                color = Color(currentMode.color),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ControlButton(
    text: String,
    subText: String,
    color: Color,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .width(80.dp)
            .height(80.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = color
        ),
        border = BorderStroke(2.dp, color),
        contentPadding = PaddingValues(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                color = color
            )
            Text(
                text = subText,
                fontSize = 10.sp,
                color = color,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .width(80.dp)
            .height(80.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = MaterialTheme.colorScheme.secondary
        ),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.secondary),
        contentPadding = PaddingValues(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun LogoutButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .width(80.dp)
            .height(80.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = Color.Red
        ),
        border = BorderStroke(2.dp, Color.Red),
        contentPadding = PaddingValues(8.dp)
    ) {
        Text(
            text = "Logout",
            fontSize = 14.sp,
            color = Color.Red,
            fontWeight = FontWeight.Bold
        )
    }
}