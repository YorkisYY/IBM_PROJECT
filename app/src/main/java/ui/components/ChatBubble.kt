package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Floating dialog component above 3D model - horizontally centered while preserving Y axis position
 * Supports 70% screen width, maximum 200dp height, content scrolling
 */
@Composable
fun ChatBubble(
    message: String,
    isVisible: Boolean,
    position: Pair<Float, Float>,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF2196F3),
    textColor: Color = Color.White
) {
    if (isVisible && message.isNotEmpty()) {
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val scrollState = rememberScrollState()
        
        // Calculate 70% screen width
        val screenWidth = configuration.screenWidthDp.dp
        val bubbleWidth = screenWidth * 0.7f
        
        // Maximum height 200dp
        val maxHeight = 200.dp
        
        Box(
            modifier = modifier
                .fillMaxWidth()  // Fill width
                .wrapContentSize(Alignment.TopCenter)  // Content adaptive size and horizontally centered
                .offset(y = with(density) { position.second.dp })  // Preserve GLB Y axis position
                .width(bubbleWidth)
                .heightIn(max = maxHeight)
                .shadow(
                    elevation = 8.dp, 
                    shape = RoundedCornerShape(16.dp)
                )
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp) // Compact spacing
        ) {
            Text(
                text = message,
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 16.sp, // Compact line height
                fontWeight = FontWeight.Medium,
                modifier = Modifier.verticalScroll(scrollState)
            )
        }
    }
}

/**
 * Simplified dialog component (for testing) - horizontally centered while preserving Y axis position
 */
@Composable
fun SimpleChatBubble(
    message: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (isVisible && message.isNotEmpty()) {
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val bubbleWidth = screenWidth * 0.7f
        
        Box(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentSize(Alignment.TopCenter)
                .width(bubbleWidth)
                .background(
                    color = Color(0xFF2196F3),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Adaptive height dialog component - horizontally centered while preserving Y axis position
 */
@Composable
fun AdaptiveChatBubble(
    message: String,
    isVisible: Boolean,
    position: Pair<Float, Float>,
    modifier: Modifier = Modifier
) {
    if (isVisible && message.isNotEmpty()) {
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val scrollState = rememberScrollState()
        
        val screenWidth = configuration.screenWidthDp.dp
        val bubbleWidth = screenWidth * 0.7f
        
        // Dynamically adjust maximum height based on text length
        val dynamicMaxHeight = when {
            message.length <= 50 -> 60.dp
            message.length <= 150 -> 120.dp
            else -> 200.dp
        }
        
        Box(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentSize(Alignment.TopCenter)
                .offset(y = with(density) { position.second.dp })
                .width(bubbleWidth)
                .heightIn(min = 40.dp, max = dynamicMaxHeight)
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .background(
                    color = Color(0xFF4CAF50), // Green theme
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = if (message.length > 150) {
                    Modifier.verticalScroll(scrollState)
                } else {
                    Modifier
                }
            )
        }
    }
}