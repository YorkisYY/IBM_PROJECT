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
 * 浮動在 3D 模型上方的對話框組件 - 水平置中保留 Y 軸位置
 * 支持 70% 螢幕寬度、最高 200dp、內容滾動
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
        
        // 計算 70% 螢幕寬度
        val screenWidth = configuration.screenWidthDp.dp
        val bubbleWidth = screenWidth * 0.7f
        
        // 最大高度 200dp
        val maxHeight = 200.dp
        
        Box(
            modifier = modifier
                .fillMaxWidth()  // 佔滿寬度
                .wrapContentSize(Alignment.TopCenter)  // 內容自適應大小並水平置中
                .offset(y = with(density) { position.second.dp })  // 保留 GLB Y 軸位置
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
                .padding(horizontal = 12.dp, vertical = 8.dp) // 緊湊間距
        ) {
            Text(
                text = message,
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 16.sp, // 緊湊行距
                fontWeight = FontWeight.Medium,
                modifier = Modifier.verticalScroll(scrollState)
            )
        }
    }
}

/**
 * 簡化版對話框組件（用於測試）- 水平置中保留 Y 軸位置
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
 * 自適應高度的對話框組件 - 水平置中保留 Y 軸位置
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
        
        // 根據文字長度動態調整最大高度
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
                    color = Color(0xFF4CAF50), // 綠色主題
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